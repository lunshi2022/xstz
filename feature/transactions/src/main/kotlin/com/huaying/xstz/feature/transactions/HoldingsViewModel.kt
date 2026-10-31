package com.huaying.xstz.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HoldingsUiState(
    val holdings: List<Holding> = emptyList(),
    val summary: PortfolioSummary = PortfolioSummary(),
    val ratios: Map<Long, Double> = emptyMap(),
    val loading: Boolean = true,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = holdings.isEmpty()
}

@HiltViewModel
class HoldingsViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val tradeRepository: TradeRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<HoldingsUiState> = combine(
        holdingRepository.observeHoldings(),
        message,
    ) { holdings, msg ->
        HoldingsUiState(
            holdings = holdings,
            summary = HoldingMath.summarize(holdings),
            ratios = HoldingMath.displayRatios(holdings),
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingsUiState())

    /** 添加一只基金（份额为 0，等用户录入交易后才计入市值） */
    fun addHolding(code: String, name: String, type: AssetType) {
        val trimmed = code.trim()
        if (trimmed.length != 6) {
            message.value = "基金代码应为 6 位数字"
            return
        }
        viewModelScope.launch {
            runCatching { holdingRepository.addHolding(trimmed, name.trim(), type) }
                .onSuccess { message.value = "已添加 $trimmed" }
                .onFailure { message.value = "添加失败：${it.message ?: "该代码可能已存在"}" }
        }
    }

    /**
     * 录入一笔交易。
     *
     * 校验三件事：份额为正、净值合理、卖出不超过持有。
     * 第三条在仓储层还有一道兜底，但在这里先拦能给用户更清楚的提示。
     */
    fun recordTrade(
        holding: Holding,
        type: TradeType,
        sharesText: String,
        navText: String,
        feeText: String,
    ) {
        val shares = sharesText.toDoubleOrNull()
        val nav = navText.toDoubleOrNull()
        val fee = feeText.toDoubleOrNull() ?: 0.0

        // 用提前 return 而不是 when，这样 Kotlin 能做智能转换，
        // 后面就不必写 shares!! 这种在 lambda 里还可能失效的断言
        if (shares == null || shares <= 0.0) {
            message.value = "份额必须大于 0"
            return
        }
        if (nav == null || nav <= 0.0) {
            message.value = "净值必须大于 0"
            return
        }
        if (fee < 0.0) {
            message.value = "手续费不能为负"
            return
        }
        if (type == TradeType.SELL && Shares.fromDouble(shares) > holding.shares) {
            message.value = "卖出份额超过持有 ${holding.shares.toDouble()}"
            return
        }

        viewModelScope.launch {
            runCatching {
                tradeRepository.recordTrade(
                    Trade(
                        fundId = holding.fundId,
                        type = type,
                        shares = Shares.fromDouble(shares),
                        nav = Nav.fromDouble(nav),
                        fee = Money.fromYuan(fee),
                        amount = Money.fromYuan(shares * nav),
                        epochDay = LocalDate.now().toEpochDay(),
                        note = "",
                    )
                )
            }.onSuccess { message.value = "${type.displayName}成功" }
                .onFailure { message.value = "${type.displayName}失败：${it.message}" }
        }
    }

    fun deleteHolding(holding: Holding) {
        viewModelScope.launch {
            runCatching { holdingRepository.deleteHolding(holding.fundId) }
                .onSuccess { message.value = "已删除 ${holding.name}" }
                .onFailure { message.value = "删除失败：${it.message}" }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
