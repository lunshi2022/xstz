package com.huaying.xstz.ui.targetallocation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
import com.huaying.xstz.util.HoldingsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TargetAllocationEvent {
    object SaveSuccess : TargetAllocationEvent()
    object SaveError : TargetAllocationEvent()
}

data class TargetAllocationUiState(
    val funds: List<Fund> = emptyList(),
    val editedFunds: List<Fund> = emptyList(),
    val totalAssets: Double = 0.0,
    val nonCashTotalRatio: Double = 0.0,
    val cashRatio: Double = 0.0,
    val isValid: Boolean = true,
    val isLoading: Boolean = true,
    val saveError: Boolean = false,
)

@HiltViewModel
class TargetAllocationViewModel @Inject constructor(
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TargetAllocationUiState())
    val uiState: StateFlow<TargetAllocationUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<TargetAllocationEvent>()
    val eventFlow: SharedFlow<TargetAllocationEvent> = _eventFlow.asSharedFlow()

    // 标记用户是否有未保存的编辑，防止 collectLatest 覆盖
    private var isDirty = false

    init {
        viewModelScope.launch {
            repository.getAllFunds().collectLatest { fundList ->
                val sorted = fundList.sortedWith(
                    compareBy<Fund> { it.type.ordinal }
                        .thenByDescending { it.holdingQuantity * it.currentPrice }
                )
                val totalAssets = HoldingsCalculator.calculateTotalAssets(fundList)

                if (isDirty) {
                    // 用户有未保存的编辑：保留用户的 targetRatio 编辑，仅更新其他字段（如价格）
                    val currentEdited = _uiState.value.editedFunds
                    val merged = sorted.map { dbFund ->
                        val editedFund = currentEdited.find { it.id == dbFund.id }
                        if (editedFund != null) {
                            dbFund.copy(targetRatio = editedFund.targetRatio)
                        } else {
                            dbFund
                        }
                    }
                    val nonCashFunds = merged.filter { it.type != AssetType.CASH }
                    val nonCashTotalRatio = nonCashFunds.sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
                    val cashRatio = (AppConstants.PERCENTAGE_BASE - nonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)

                    val cashFund = merged.find { it.type == AssetType.CASH }
                    val finalEdited = if (cashFund != null) {
                        merged.map {
                            if (it.type == AssetType.CASH) it.copy(targetRatio = cashRatio / AppConstants.PERCENTAGE_BASE) else it
                        }
                    } else merged

                    _uiState.value = _uiState.value.copy(
                        funds = sorted,
                        editedFunds = finalEdited,
                        totalAssets = totalAssets,
                        nonCashTotalRatio = nonCashTotalRatio,
                        cashRatio = cashRatio,
                        isValid = nonCashTotalRatio <= AppConstants.PERCENTAGE_BASE && nonCashTotalRatio >= AppConstants.ZERO_DOUBLE,
                        isLoading = false
                    )
                } else {
                    // 无未保存编辑：完整从数据库更新
                    val nonCashFunds = sorted.filter { it.type != AssetType.CASH }
                    val nonCashTotalRatio = nonCashFunds.sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
                    val cashRatio = (AppConstants.PERCENTAGE_BASE - nonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)

                    val cashFund = sorted.find { it.type == AssetType.CASH }
                    val edited = if (cashFund != null) {
                        sorted.map {
                            if (it.type == AssetType.CASH) it.copy(targetRatio = cashRatio / AppConstants.PERCENTAGE_BASE) else it
                        }
                    } else sorted

                    _uiState.value = TargetAllocationUiState(
                        funds = sorted,
                        editedFunds = edited,
                        totalAssets = totalAssets,
                        nonCashTotalRatio = nonCashTotalRatio,
                        cashRatio = cashRatio,
                        isValid = nonCashTotalRatio <= AppConstants.PERCENTAGE_BASE && nonCashTotalRatio >= AppConstants.ZERO_DOUBLE,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateRatio(fundId: Long, newRatioPercent: Double) {
        isDirty = true
        val state = _uiState.value
        val updated = state.editedFunds.map { fund ->
            if (fund.id == fundId && fund.type != AssetType.CASH) {
                fund.copy(targetRatio = newRatioPercent / AppConstants.PERCENTAGE_BASE)
            } else fund
        }
        recalculate(updated)
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isValid) {
                _uiState.value = _uiState.value.copy(saveError = true)
                _eventFlow.emit(TargetAllocationEvent.SaveError)
                return@launch
            }
            try {
                val currentTime = TimeRepository.getCurrentTimeMillis()
                state.editedFunds.forEach { fund ->
                    repository.updateFundTargetRatio(fund.id, fund.targetRatio, currentTime)
                }
                isDirty = false
                _uiState.value = _uiState.value.copy(saveError = false)
                _eventFlow.emit(TargetAllocationEvent.SaveSuccess)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saveError = true)
                _eventFlow.emit(TargetAllocationEvent.SaveError)
            }
        }
    }

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveError = false)
    }

    fun setSaveError() {
        _uiState.value = _uiState.value.copy(saveError = true)
    }

    private fun recalculate(edited: List<Fund>) {
        val nonCashFunds = edited.filter { it.type != AssetType.CASH }
        val nonCashTotalRatio = nonCashFunds.sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
        val cashRatio = (AppConstants.PERCENTAGE_BASE - nonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)

        val cashFund = edited.find { it.type == AssetType.CASH }
        val finalEdited = if (cashFund != null) {
            edited.map {
                if (it.type == AssetType.CASH) it.copy(targetRatio = cashRatio / AppConstants.PERCENTAGE_BASE) else it
            }
        } else edited

        _uiState.value = _uiState.value.copy(
            editedFunds = finalEdited,
            nonCashTotalRatio = nonCashTotalRatio,
            cashRatio = cashRatio,
            isValid = nonCashTotalRatio <= AppConstants.PERCENTAGE_BASE && nonCashTotalRatio >= AppConstants.ZERO_DOUBLE
        )
    }
}
