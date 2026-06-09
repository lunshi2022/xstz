package com.huaying.xstz.ui.rebalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
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
                currentTotalAssets = fundList.sumOf { it.holdingQuantity * it.currentPrice }
                recalculateAdjustments()
            }
        }
    }

    fun setThresholdInput(input: String) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
        _uiState.value = state.copy(thresholdInput = input)
        recalculateAdjustments()
    }

    fun setThresholdMode(mode: Int) {
        val state = _uiState.value as? RebalanceUiState.Success ?: return
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
        val thresholdVal = state.thresholdInput.toFloatOrNull() ?: AppConstants.DEFAULT_THRESHOLD_PERCENT
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
            val thresholdVal = newThreshold ?: AppConstants.DEFAULT_THRESHOLD_PERCENT

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

            finalAdjustments.forEach { adjustment ->
                val fund = currentFunds.find { it.id == adjustment.fundId }
                if (fund != null && fund.currentPrice > 0) {
                    val deltaQuantity = adjustment.changeQuantity
                    val newQuantity = fund.holdingQuantity + deltaQuantity

                    val newTotalCost = if (deltaQuantity > 0) {
                        fund.totalCost + adjustment.adjustmentAmount
                    } else {
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

            _uiState.value = state.copy(newFundInput = "", selectedLogicMode = 0)
            onComplete()
        }
    }
}
