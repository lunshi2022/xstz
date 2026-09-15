package com.huaying.xstz.ui.assetdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.TransactionRepository
import com.huaying.xstz.util.FormatUtils
import com.huaying.xstz.util.HoldingsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AssetDetailUiState {
    object Loading : AssetDetailUiState()
    data class Success(
        val fund: Fund,
        val transactions: List<Transaction> = emptyList()
    ) : AssetDetailUiState()
    data class Error(val message: String) : AssetDetailUiState()
}

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val fundRepository: FundRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AssetDetailUiState>(AssetDetailUiState.Loading)
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    private var currentFundId: Long = -1L

    fun loadFund(fundId: Long) {
        currentFundId = fundId
        viewModelScope.launch {
            try {
                // 使用 Flow 观察基金数据变化，实现实时更新
                fundRepository.getFundByIdFlow(fundId).collect { fund ->
                    if (fund != null) {
                        val transactions = transactionRepository.getTransactionsByFundCode(fund.code).first()
                        _uiState.value = AssetDetailUiState.Success(fund, transactions)
                    } else {
                        _uiState.value = AssetDetailUiState.Error("资产不存在")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AssetDetailUiState.Error("加载资产详情失败: ${e.message}")
            }
        }
    }

    fun deleteFund() {
        viewModelScope.launch {
            try {
                if (currentFundId != -1L) {
                    fundRepository.deleteFundById(currentFundId)
                }
            } catch (e: Exception) {
                _uiState.value = AssetDetailUiState.Error("删除资产失败: ${e.message}")
            }
        }
    }

    fun formatCurrency(value: Double): String = FormatUtils.formatCurrency(value)

    fun formatPercent(value: Double): String = FormatUtils.formatPercent(value)

    fun calculateReturnRate(cost: Double, currentValue: Double): Double {
        return HoldingsCalculator.calculateReturnRate(currentValue, cost)
    }
}
