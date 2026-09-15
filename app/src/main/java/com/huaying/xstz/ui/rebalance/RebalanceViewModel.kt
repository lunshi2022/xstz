package com.huaying.xstz.ui.rebalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
import com.huaying.xstz.util.HoldingsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

sealed interface RebalanceUiState {
    object Loading : RebalanceUiState
    data class Success(
        val funds: List<Fund> = emptyList(),
        val totalAssets: Double = 0.0,
        val adjustmentList: List<AdjustmentItem> = emptyList(),
        val thresholdInput: String = "",
        val thresholdMode: Int = 0,
        val selectedLogicMode: Int = 0,
        val newFundInput: String = ""
    ) : RebalanceUiState
}

@HiltViewModel
class RebalanceViewModel @Inject constructor(
    private val repository: FundRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RebalanceUiState>(RebalanceUiState.Loading)
    val uiState: StateFlow<RebalanceUiState> = _uiState.asStateFlow()

    private var currentFunds: List<Fund> = emptyList()
    private var currentTotalAssets: Double = 0.0

    init {
        viewModelScope.launch {
            preferenceManager.rebalanceThreshold.collectLatest { threshold ->
                val savedMode = preferenceManager.rebalanceThresholdMode.first()
                val thresholdStr = if (threshold > 0) threshold.toInt().toString() else ""
                if (_uiState.value is RebalanceUiState.Success) {
                    _uiState.value = (_uiState.value as RebalanceUiState.Success).copy(
                        thresholdInput = thresholdStr,
                        thresholdMode = savedMode
                    )
                } else {
                    _uiState.value = RebalanceUiState.Success(
                        thresholdInput = thresholdStr,
                        thresholdMode = savedMode
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.getAllFunds().collectLatest { fundList ->
                val sorted = fundList.sortedWith(
                    compareBy<Fund> { it.type.ordinal }
                        .thenByDescending { it.holdingQuantity * it.currentPrice }
                )
                currentFunds = sorted
                currentTotalAssets = HoldingsCalculator.calculateTotalAssets(fundList)
                recalculateAdjustments()
            }
        }
    }

    fun setThresholdInput(input: String) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        _uiState.value = state.copy(thresholdInput = input)
        // 有效输入实时持久化
        val floatVal = input.toFloatOrNull()
        if (floatVal != null) {
            viewModelScope.launch {
                preferenceManager.setRebalanceThreshold(floatVal)
            }
        }
        recalculateAdjustments()
    }

    /** 失焦时恢复默认值并持久化 */
    fun onThresholdInputFocusLost() {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        if (state.thresholdInput.isEmpty()) {
            viewModelScope.launch {
                preferenceManager.setRebalanceThreshold(AppConstants.DEFAULT_THRESHOLD)
            }
            _uiState.value = state.copy(thresholdInput = AppConstants.DEFAULT_THRESHOLD.toInt().toString())
            recalculateAdjustments()
        }
    }

    fun setThresholdMode(mode: Int) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        if (state.thresholdMode == mode) return
        viewModelScope.launch {
            preferenceManager.setRebalanceThresholdMode(mode)
        }
        _uiState.value = state.copy(thresholdMode = mode)
        recalculateAdjustments()
    }

    fun setLogicMode(mode: Int) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        _uiState.value = state.copy(selectedLogicMode = mode)
        recalculateAdjustments()
    }

    fun setNewFundInput(input: String) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        _uiState.value = state.copy(newFundInput = input)
        recalculateAdjustments()
    }

    private fun recalculateAdjustments() {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        if (state.newFundInput.isEmpty() && state.selectedLogicMode == 0) {
            _uiState.value = state.copy(
                funds = currentFunds,
                totalAssets = currentTotalAssets,
                adjustmentList = emptyList()
            )
            return
        }

        val newFundVal = state.newFundInput.toDoubleOrNull() ?: 0.0
        val thresholdVal = state.thresholdInput.toFloatOrNull() ?: AppConstants.DEFAULT_THRESHOLD
        val strategy = if (state.selectedLogicMode == 1) RebalanceStrategy.FULL_REBALANCE else RebalanceStrategy.SMART

        val adjustments = calculateAdjustments(
            currentFunds,
            currentTotalAssets,
            strategy,
            newFundVal,
            thresholdVal,
            state.thresholdMode
        )

        _uiState.value = state.copy(
            funds = currentFunds,
            totalAssets = currentTotalAssets,
            adjustmentList = adjustments
        )
    }

    fun executeRebalance(strategy: RebalanceStrategy, onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value as? RebalanceUiState.Success ?: return@launch
            val newThreshold = state.thresholdInput.toFloatOrNull()
            if (newThreshold != null) {
                preferenceManager.setRebalanceThreshold(newThreshold)
            }
            preferenceManager.setRebalanceThresholdMode(state.thresholdMode)

            val newFundVal = state.newFundInput.toDoubleOrNull() ?: 0.0
            val thresholdVal = newThreshold ?: AppConstants.DEFAULT_THRESHOLD

            val finalAdjustments = calculateAdjustments(
                currentFunds,
                currentTotalAssets,
                strategy,
                newFundVal,
                thresholdVal,
                state.thresholdMode
            )

            if (finalAdjustments.isEmpty()) {
                onComplete()
                return@launch
            }

            // 分两轮处理：先处理非现金基金，再处理现金基金
            // 目的：卖出盈利基金时，现金totalCost应只增加卖出份额的成本（非卖出所得），
            // 以保持总本金守恒，避免累计收益被压缩
            val nonCashAdjustments = finalAdjustments.filter { it.assetType != AssetType.CASH }
            val cashAdjustments = finalAdjustments.filter { it.assetType == AssetType.CASH }

            // 跟踪卖出产生的成本减少和卖出所得，用于修正现金totalCost
            var totalCostReductionFromSales = 0.0
            var totalSaleProceeds = 0.0

            // 第一轮：处理非现金基金
            nonCashAdjustments.forEach { adjustment ->
                val fund = currentFunds.find { it.id == adjustment.fundId }
                if (fund != null && fund.currentPrice > 0) {
                    val deltaQuantity = adjustment.changeQuantity
                    val newQuantity = fund.holdingQuantity + deltaQuantity
                    val oldTotalCost = fund.totalCost

                    val newTotalCost = if (deltaQuantity > 0) {
                        fund.totalCost + adjustment.adjustmentAmount
                    } else {
                        if (fund.holdingQuantity > 0) {
                            fund.totalCost * (newQuantity / fund.holdingQuantity)
                        } else {
                            0.0
                        }
                    }

                    // 跟踪卖出操作的成本变化
                    if (deltaQuantity < 0 && oldTotalCost > newTotalCost) {
                        totalCostReductionFromSales += oldTotalCost - newTotalCost
                        totalSaleProceeds += abs(adjustment.adjustmentAmount)
                    }

                    repository.updateFund(fund.copy(
                        holdingQuantity = if (newQuantity < 0) AppConstants.ZERO_DOUBLE else newQuantity,
                        totalCost = if (newTotalCost < 0) AppConstants.ZERO_DOUBLE else newTotalCost,
                        updatedAt = TimeRepository.getCurrentTimeMillis()
                    ))

                    if (abs(deltaQuantity) > 0) {
                        repository.insertTransaction(
                            Transaction(
                                fundId = fund.id,
                                fundCode = fund.code,
                                fundName = fund.name,
                                type = if (deltaQuantity > 0) TransactionType.BUY else TransactionType.SELL,
                                amount = abs(adjustment.adjustmentAmount),
                                price = fund.currentPrice,
                                quantity = abs(deltaQuantity),
                                remark = "再平衡调整"
                            )
                        )
                    }
                }
            }

            // 第二轮：处理现金基金，修正totalCost以保持本金守恒
            val profitFromSales = totalSaleProceeds - totalCostReductionFromSales
            cashAdjustments.forEach { adjustment ->
                val fund = currentFunds.find { it.id == adjustment.fundId }
                if (fund != null) {
                    val deltaQuantity = adjustment.changeQuantity
                    val newQuantity = fund.holdingQuantity + deltaQuantity

                    val newTotalCost = if (deltaQuantity > 0) {
                        // 现金增加（来自卖出和/或新增资金）：扣除卖出利润，只计入成本
                        fund.totalCost + adjustment.adjustmentAmount - profitFromSales
                    } else {
                        // 现金减少（用于买入）：按比例缩减
                        if (fund.holdingQuantity > 0) {
                            fund.totalCost * (newQuantity / fund.holdingQuantity)
                        } else {
                            0.0
                        }
                    }

                    repository.updateFund(fund.copy(
                        holdingQuantity = if (newQuantity < 0) AppConstants.ZERO_DOUBLE else newQuantity,
                        totalCost = if (newTotalCost < 0) AppConstants.ZERO_DOUBLE else newTotalCost,
                        updatedAt = TimeRepository.getCurrentTimeMillis()
                    ))

                    if (abs(deltaQuantity) > 0) {
                        repository.insertTransaction(
                            Transaction(
                                fundId = fund.id,
                                fundCode = fund.code,
                                fundName = fund.name,
                                type = if (deltaQuantity > 0) TransactionType.BUY else TransactionType.SELL,
                                amount = abs(adjustment.adjustmentAmount),
                                price = fund.currentPrice,
                                quantity = abs(deltaQuantity),
                                remark = "再平衡调整"
                            )
                        )
                    }
                }
            }

            OperationLogger.log(
                type = com.huaying.xstz.data.entity.OperationType.REBALANCE,
                title = "执行再平衡",
                description = "策略: ${if (strategy == RebalanceStrategy.FULL_REBALANCE) "完全再平衡" else "智能再平衡"}, 交易数: ${finalAdjustments.size}"
            )

            // 主动获取最新基金数据，避免用旧 state 覆盖
            val latestFunds = repository.getAllFunds().first()
            val sorted = latestFunds.sortedWith(
                compareBy<Fund> { it.type.ordinal }
                    .thenByDescending { it.holdingQuantity * it.currentPrice }
            )
            currentFunds = sorted
            currentTotalAssets = HoldingsCalculator.calculateTotalAssets(latestFunds)

            _uiState.value = RebalanceUiState.Success(
                funds = currentFunds,
                totalAssets = currentTotalAssets,
                adjustmentList = emptyList(),
                thresholdInput = state.thresholdInput,
                thresholdMode = state.thresholdMode,
                selectedLogicMode = 0,
                newFundInput = ""
            )
            onComplete()
        }
    }
}
