package com.huaying.xstz.feature.rebalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

/**
 * 目标仓位设置页的状态。
 *
 * [sumRatio] 是**核心校验量**：所有标的的目标占比之和必须等于 1.0（100%）。
 * 若允许不等于 1，再平衡算法算出的买卖金额就没有意义——
 * 差多少都没有"该买多少"的答案。
 */
data class TargetAllocationUiState(
    val holdings: List<Holding> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
) {
    /** 目标占比合计 */
    val sumRatio: Double get() = holdings.sumOf { it.targetRatio }

    /** 是否已配平（容忍 0.01% 浮点误差） */
    val isBalanced: Boolean get() = kotlin.math.abs(sumRatio - 1.0) < 1e-4

    /** 差值 = 已分配 - 1.0。正数表示超配，负数表示未分配完 */
    val deltaRatio: Double get() = sumRatio - 1.0

    val isEmpty: Boolean get() = holdings.isEmpty()
}

/**
 * 目标仓位设置。
 *
 * 为什么单独成页而不是塞进再平衡页的弹窗：
 *   配平目标占比通常要来回调多个标的（A 加 5% 就要从 B 减 5%），
 *   是个反复试算的过程。放在弹窗里空间不够，且看不到合计变化，
 *   用户只能凭感觉调，几乎不可能配到正好 100%。
 *
 * 这一页的核心价值就是把「合计」实时显示出来。
 */
@HiltViewModel
class TargetAllocationViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
) : ViewModel() {

    /** 本地的待提交改动：仅记录被改过的 id → 新占比，未提交前不落库 */
    private val draft = MutableStateFlow<Map<Long, Double>>(emptyMap())
    private val saving = MutableStateFlow(false)

    val state: StateFlow<TargetAllocationUiState> =
        combine(holdingRepository.observeHoldings(), draft, saving) { holdings, pending, isSaving ->
            TargetAllocationUiState(
                // 用 draft 覆盖显示值，让用户看到"将要变成多少"而不是"现在是多少"
                holdings = holdings.map { h ->
                    pending[h.fundId]?.let { h.copy(targetRatio = it) } ?: h
                },
                loading = false,
                saving = isSaving,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TargetAllocationUiState(),
        )

    /** 修改某个标的的目标占比（仅改草稿，不落库） */
    fun setRatio(id: Long, ratio: Double) {
        draft.value = draft.value + (id to ratio.coerceIn(0.0, 1.0))
    }

    /** 按现有占比等比例缩放，使合计正好 1.0 */
    fun normalizeToFull() {
        val current = state.value.holdings
        if (current.isEmpty()) return
        val sum = current.sumOf { it.targetRatio }
        val changed = if (sum <= 0.0) {
            // 全为 0 时无法等比缩放，改为均分
            val each = 1.0 / current.size
            current.associate { it.fundId to each }
        } else {
            current.associate { it.fundId to it.targetRatio / sum }
        }
        draft.value = draft.value + changed
    }

    /** 把所有目标占比清零 */
    fun clearAll() {
        draft.value = state.value.holdings.associate { it.fundId to 0.0 }
    }

    /**
     * 提交：逐条写入。
     *
     * 不校验"合计必须为 1"才允许保存——用户可能想先存一半再继续调。
     * 但页面会持续显示合计与差值，把判断权交给用户而非替他拦下。
     * 真正的强校验放在再平衡计算前，那里才是"配平"的必要条件。
     */
    fun save(onDone: () -> Unit = {}) {
        val pending = draft.value
        if (pending.isEmpty()) {
            onDone()
            return
        }
        saving.value = true
        viewModelScope.launch {
            runCatching {
                pending.forEach { (id, ratio) ->
                    holdingRepository.updateTargetRatio(id, ratio)
                }
            }
            draft.value = emptyMap()
            saving.value = false
            onDone()
        }
    }

    /** 是否有未保存改动 */
    val hasChanges: StateFlow<Boolean> = draft
        .let { d -> d.map { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/** 供 UI 显示用的合计金额（按目标占比 × 总市值） */
fun TargetAllocationUiState.targetValue(h: Holding, total: Money): Money =
    Money((total.micros * h.targetRatio).toLong())
