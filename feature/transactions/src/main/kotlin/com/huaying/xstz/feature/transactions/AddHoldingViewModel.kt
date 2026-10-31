package com.huaying.xstz.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.QuoteCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 查询阶段，界面据此决定显示"查找中 / 候选 / 查不到 / 提示输入" */
enum class QueryPhase {
    /** 输入不构成查询条件（空或过短） */
    IDLE,
    QUERYING,
    /** 有命中 */
    HIT,
    /** 查完了，一条都没有 */
    MISS,
    /** 网络或接口失败——与 MISS 必须区分，处置方式完全不同 */
    ERROR,
}

data class AddHoldingUiState(
    val text: String = "",
    /** 输入是否为纯数字且满 6 位：此时走"按代码查"，否则走"按名称搜" */
    val codeMode: Boolean = false,
    val phase: QueryPhase = QueryPhase.IDLE,
    /** 命中的全部候选。A股与基金代码段重叠时会有多条 */
    val candidates: List<QuoteCandidate> = emptyList(),
    val selectedKey: String? = null,
    /** 接口查不到时，用户可以选择手动填写 */
    val manualMode: Boolean = false,
    val manualName: String = "",
    val manualKind: InstrumentKind = InstrumentKind.FUND,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val selected: QuoteCandidate? get() = candidates.firstOrNull { it.key == selectedKey }

    val querying: Boolean get() = phase == QueryPhase.QUERYING

    /** 输入满 6 位数字、查询结束、一条候选都没有 */
    val notFound: Boolean get() = phase == QueryPhase.MISS && !manualMode

    val failed: Boolean get() = phase == QueryPhase.ERROR

    /** 有效代码：用于保存。手动模式下取输入里的数字 */
    val effectiveCode: String
        get() = selected?.code ?: text.filter { it.isDigit() }.take(6)

    val canSubmit: Boolean
        get() = !saving && (
            selected != null ||
                (manualMode && manualName.isNotBlank() && effectiveCode.length == 6)
            )
}

/** 候选的唯一键：同一代码可能既是基金又是股票，必须带上类型 */
val QuoteCandidate.key: String get() = "${kind.name}:$code"

/**
 * 添加标的。
 *
 * 两条查询路径，靠输入形态自动切换，不需要用户选模式：
 *
 *   1. **纯数字满 6 位** → 按代码查。同时问场内与场外两个接口，
 *      把命中的全部摆出来（000001 = 平安银行 / 华夏成长）。
 *   2. **含汉字或非满 6 位** → 按名称搜。走 smartbox 联想接口，
 *      一次返回最多 20 条，涵盖 A股 / ETF / 场外基金。
 *
 * 为什么不能只做路径 1：代码记不住。要求用户为加"茅台"去查 600519，
 * 等于把"背代码"当成使用前提。
 *
 * 为什么不能只做路径 2：名称搜不出歧义。搜"000001"时接口只回一条，
 * 但那个代码确实对应两个标的，只有逐接口查才能发现。
 *
 * 两条路径都失败时**不猜**：宁可用户手填，也不要把一只股票当基金记进账。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class AddHoldingViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
) : ViewModel() {

    private val textInput = MutableStateFlow("")
    private val candidates = MutableStateFlow<List<QuoteCandidate>>(emptyList())
    private val selectedKey = MutableStateFlow<String?>(null)
    private val phase = MutableStateFlow(QueryPhase.IDLE)
    private val manualMode = MutableStateFlow(false)
    private val manualName = MutableStateFlow("")
    private val manualKind = MutableStateFlow(InstrumentKind.FUND)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            textInput
                .debounce(QUERY_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { raw -> runQuery(raw) }
        }
    }

    private suspend fun runQuery(raw: String) {
        val q = raw.trim()
        if (!isQueryable(q)) {
            candidates.value = emptyList()
            selectedKey.value = null
            phase.value = QueryPhase.IDLE
            return
        }

        phase.value = QueryPhase.QUERYING
        val list = if (isCode(q)) {
            // 按代码：走 candidates，能发现"同一代码对应多个标的"这种歧义
            runCatching { holdingRepository.candidates(q) }
        } else {
            // 按名称：走 search，一次拿到跨市场的候选
            runCatching { holdingRepository.search(q) }
        }

        // 查询期间用户可能又改了输入，collectLatest 会取消本次协程；
        // 这里再判一次，避免旧结果盖掉新结果
        if (textInput.value.trim() != q) return

        list.onSuccess { hits ->
            candidates.value = hits
            // 只有一条命中就自动选中：常见情况（510300、600519）不该多一次点击
            selectedKey.value = if (hits.size == 1) hits.first().key else null
            phase.value = if (hits.isEmpty()) QueryPhase.MISS else QueryPhase.HIT
            if (hits.isNotEmpty()) manualMode.value = false
        }.onFailure {
            candidates.value = emptyList()
            selectedKey.value = null
            phase.value = QueryPhase.ERROR
        }
    }

    private val baseFlow: Flow<AddHoldingUiState> = combine(
        textInput, candidates, selectedKey, phase, manualMode,
    ) { text, list, sel, ph, manual ->
        AddHoldingUiState(
            text = text,
            codeMode = isCode(text.trim()),
            phase = ph,
            candidates = list,
            selectedKey = sel,
            manualMode = manual,
        )
    }

    /** 手动填写相关状态先合成一对，否则最后一步 combine 会超过 5 个参数的上限 */
    private val manualFlow: Flow<Pair<String, InstrumentKind>> =
        combine(manualName, manualKind) { name, kind -> name to kind }

    val state: StateFlow<AddHoldingUiState> = combine(
        baseFlow, manualFlow, saving, error, done,
    ) { base, (name, kind), s, e, d ->
        base.copy(manualName = name, manualKind = kind, saving = s, error = e, done = d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddHoldingUiState())

    fun onTextChange(raw: String) {
        // 代码模式下只留数字：粘进 "sh600519" 时把前缀吃掉，否则查不到
        val next = if (raw.any { it.isDigit() } && raw.all { it.isDigit() || it.isWhitespace() }) {
            raw.filter { it.isDigit() }.take(6)
        } else {
            raw.take(MAX_TEXT)
        }

        if (next != textInput.value) {
            // 输入变了，之前的选中与手动输入全部作废，避免把 A 的名字加到 B 上
            selectedKey.value = null
            error.value = null
            done.value = false
            manualName.value = ""
            manualMode.value = false
            if (!isQueryable(next.trim())) phase.value = QueryPhase.IDLE
        }
        textInput.value = next
    }

    fun select(candidate: QuoteCandidate) {
        selectedKey.value = candidate.key
        error.value = null
    }

    fun enableManualMode() {
        manualMode.value = true
        error.value = null
    }

    fun onManualNameChange(v: String) {
        manualName.value = v
        error.value = null
    }

    fun onManualKindChange(kind: InstrumentKind) {
        manualKind.value = kind
    }

    fun submit() {
        val picked = candidates.value.firstOrNull { it.key == selectedKey.value }
        val code = picked?.code ?: textInput.value.filter { it.isDigit() }.take(6)
        if (code.length != 6) {
            error.value = "请先选择一个标的，或输入 6 位代码后手动填写"
            return
        }

        val name = picked?.name ?: manualName.value.trim()
        val kind = picked?.kind ?: manualKind.value
        val type = picked?.assetType
            ?: QuoteCandidate.assetTypeOf(kind, code, name)

        if (name.isBlank()) {
            error.value = "请先选择一个标的，或手动填写名称"
            return
        }

        saving.value = true
        error.value = null
        viewModelScope.launch {
            runCatching {
                holdingRepository.addHolding(code = code, name = name, type = type, kind = kind)
            }.onSuccess {
                saving.value = false
                done.value = true
            }.onFailure {
                saving.value = false
                error.value = it.message ?: "添加失败，该代码可能已在列表中"
            }
        }
    }

    private companion object {
        /**
         * 400ms 防抖。不用更短：手机上连打 6 个数字通常要 500ms 以上，
         * 太短会在输入中途打出三四次无效请求（每次要查两个接口）。
         * 名称搜索同理——逐字触发会在输入"易方达消费"时发 5 次请求。
         */
        const val QUERY_DEBOUNCE_MS = 400L

        /** 名称最长 20 字。再长就是误粘贴，没有标的叫这个名字。 */
        const val MAX_TEXT = 20

        /** 纯数字满 6 位才走"按代码查"，其余都当名称搜索 */
        fun isCode(s: String) = s.length == 6 && s.all { it.isDigit() }

        /** 一个字符就能搜（输入"茅"就该出结果），但纯空不算 */
        fun isQueryable(s: String) = s.isNotEmpty()
    }
}
