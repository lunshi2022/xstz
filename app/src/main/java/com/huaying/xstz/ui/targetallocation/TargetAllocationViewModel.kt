package com.huaying.xstz.ui.targetallocation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.util.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TargetAllocationUiState(
    val funds: List<Fund> = emptyList(),
    val editedFunds: List<Fund> = emptyList(),
    val totalAssets: Double = 0.0,
    val nonCashTotalRatio: Double = 0.0,
    val cashRatio: Double = 0.0,
    val isValid: Boolean = true,
    val isLoading: Boolean = true
)

@HiltViewModel
class TargetAllocationViewModel @Inject constructor(
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TargetAllocationUiState())
    val uiState: StateFlow<TargetAllocationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllFunds().collectLatest { fundList ->
                val sorted = fundList.sortedWith(
                    compareBy<Fund> { it.type.ordinal }
                        .thenByDescending { it.holdingQuantity * it.currentPrice }
                )
                val totalAssets = fundList.sumOf { it.holdingQuantity * it.currentPrice }
                val nonCashFunds = sorted.filter { it.type != AssetType.CASH }
                val nonCashTotalRatio = nonCashFunds.sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
                val cashRatio = (AppConstants.PERCENTAGE_BASE - nonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)

                // 自动更新现金占比
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

    fun updateRatio(fundId: Long, newRatioPercent: Double) {
        val state = _uiState.value
        val updated = state.editedFunds.map { fund ->
            if (fund.id == fundId && fund.type != AssetType.CASH) {
                fund.copy(targetRatio = newRatioPercent / AppConstants.PERCENTAGE_BASE)
            } else fund
        }
        recalculate(updated)
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isValid) return@launch
            state.editedFunds.forEach { repository.updateFund(it) }
            onComplete()
        }
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
