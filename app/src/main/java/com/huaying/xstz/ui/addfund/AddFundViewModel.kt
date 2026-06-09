package com.huaying.xstz.ui.addfund

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.OperationType
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddFundUiState {
    object Loading : AddFundUiState
    data class Success(
        val fundCode: TextFieldValue = TextFieldValue(""),
        val fundName: String = "",
        val selectedType: AssetType = AssetType.STOCK,
        val existingFund: Fund? = null,
        val isOverwriteMode: Boolean = false,
        val inputMode: Boolean = true, // true = 按份额, false = 按市值
        val holdingQuantity: TextFieldValue = TextFieldValue(""),
        val totalCost: TextFieldValue = TextFieldValue(""),
        val marketValue: TextFieldValue = TextFieldValue(""),
        val costPrice: TextFieldValue = TextFieldValue(""),
        val isLoading: Boolean = false,
        val queryResult: FundRepository.FundQueryResult? = null
    ) : AddFundUiState
}

@HiltViewModel
class AddFundViewModel @Inject constructor(
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddFundUiState>(AddFundUiState.Success())
    val uiState: StateFlow<AddFundUiState> = _uiState.asStateFlow()

    fun setFundCode(code: String) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        val filtered = code.filter { it.isDigit() }
        if (filtered.length <= AppConstants.FUND_CODE_LENGTH) {
            _uiState.value = state.copy(
                fundCode = TextFieldValue(text = filtered, selection = TextRange(filtered.length))
            )
        }
        checkExistingFund()
    }

    fun setFundName(name: String) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        _uiState.value = state.copy(fundName = name)
        checkExistingFund()
    }

    fun setSelectedType(type: AssetType) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        _uiState.value = state.copy(selectedType = type)
    }

    fun setOverwriteMode(overwrite: Boolean) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        _uiState.value = state.copy(isOverwriteMode = overwrite)
    }

    fun setInputMode(mode: Boolean) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        _uiState.value = state.copy(inputMode = mode)
    }

    fun setHoldingQuantity(value: TextFieldValue) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        val filtered = value.text.filter { it.isDigit() }
        val cursorAdjustment = value.text.length - filtered.length
        val newSelection = TextRange(maxOf(0, value.selection.start - cursorAdjustment))
        _uiState.value = state.copy(holdingQuantity = TextFieldValue(filtered, newSelection))
    }

    fun setTotalCost(value: TextFieldValue) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        val filtered = value.text.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            val cursorAdjustment = value.text.length - filtered.length
            val newSelection = TextRange(maxOf(0, value.selection.start - cursorAdjustment))
            _uiState.value = state.copy(totalCost = TextFieldValue(filtered, newSelection))
        }
    }

    fun setMarketValue(value: TextFieldValue) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        val filtered = value.text.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            val cursorAdjustment = value.text.length - filtered.length
            val newSelection = TextRange(maxOf(0, value.selection.start - cursorAdjustment))
            _uiState.value = state.copy(marketValue = TextFieldValue(filtered, newSelection))
        }
    }

    fun setCostPrice(value: TextFieldValue) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        val filtered = value.text.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            val cursorAdjustment = value.text.length - filtered.length
            val newSelection = TextRange(maxOf(0, value.selection.start - cursorAdjustment))
            _uiState.value = state.copy(costPrice = TextFieldValue(filtered, newSelection))
        }
    }

    fun clearQueryResult() {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        _uiState.value = state.copy(queryResult = null)
    }

    private fun checkExistingFund() {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        if (state.fundCode.text.length == AppConstants.FUND_CODE_LENGTH && state.fundName.isNotBlank()) {
            viewModelScope.launch {
                val fund = repository.getFundByCodeAndName(state.fundCode.text, state.fundName)
                if (fund != null) {
                    _uiState.value = state.copy(existingFund = fund, selectedType = fund.type)
                } else {
                    _uiState.value = state.copy(existingFund = null)
                }
            }
        } else {
            _uiState.value = state.copy(existingFund = null)
        }
    }

    fun fetchFundInfo() {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            try {
                val result = repository.fetchFundInfoWithOptions(state.fundCode.text)
                val newState = _uiState.value as? AddFundUiState.Success ?: return@launch
                _uiState.value = newState.copy(queryResult = result, isLoading = false)
                when {
                    result.hasBothResults -> _uiState.value = (_uiState.value as AddFundUiState.Success).copy(fundName = "")
                    result.fundName != null -> _uiState.value = (_uiState.value as AddFundUiState.Success).copy(fundName = result.fundName)
                    result.stockName != null -> _uiState.value = (_uiState.value as AddFundUiState.Success).copy(fundName = result.stockName)
                    else -> _uiState.value = (_uiState.value as AddFundUiState.Success).copy(fundName = "获取失败，请检查基金代码")
                }
            } catch (e: Exception) {
                android.util.Log.e("AddFundViewModel", "fetchFundInfo failed", e)
                val newState = _uiState.value as? AddFundUiState.Success ?: return@launch
                _uiState.value = newState.copy(fundName = "获取失败，请检查基金代码", isLoading = false)
            }
        }
    }

    fun saveFund(
        operationLogRepository: OperationLogRepository?,
        onFundAdded: () -> Unit
    ) {
        val state = _uiState.value as? AddFundUiState.Success ?: return
        viewModelScope.launch {
            val inputQuantity: Double
            val inputCost: Double
            val inputPrice: Double

            val hasShareInput = state.holdingQuantity.text.isNotBlank() || state.totalCost.text.isNotBlank()
            val hasMarketInput = state.marketValue.text.isNotBlank() || state.costPrice.text.isNotBlank()
            val currentInputMode = when {
                hasShareInput && !hasMarketInput -> true
                hasMarketInput && !hasShareInput -> false
                state.holdingQuantity.text.isNotBlank() && state.totalCost.text.isNotBlank() -> true
                state.marketValue.text.isNotBlank() && state.costPrice.text.isNotBlank() -> false
                else -> state.inputMode
            }

            if (currentInputMode) {
                inputQuantity = state.holdingQuantity.text.toDoubleOrNull() ?: 0.0
                inputCost = state.totalCost.text.toDoubleOrNull() ?: 0.0
                inputPrice = if (inputQuantity > 0) inputCost / inputQuantity else 0.0
            } else {
                val value = state.marketValue.text.toDoubleOrNull() ?: 0.0
                inputPrice = state.costPrice.text.toDoubleOrNull() ?: 0.0
                inputQuantity = if (inputPrice > 0) value / inputPrice else 0.0
                inputCost = value
            }

            if (state.existingFund != null) {
                val fund = state.existingFund!!
                if (state.isOverwriteMode) {
                    val updatedFund = fund.copy(
                        name = state.fundName,
                        type = state.selectedType,
                        holdingQuantity = inputQuantity,
                        totalCost = inputCost,
                        updatedAt = TimeRepository.getCurrentTimeMillis()
                    )
                    repository.updateFund(updatedFund)
                    repository.insertTransaction(
                        Transaction(
                            fundId = fund.id,
                            fundCode = fund.code,
                            fundName = fund.name,
                            type = TransactionType.ADD_FUNDS,
                            amount = inputCost,
                            price = inputPrice,
                            quantity = inputQuantity,
                            remark = "覆盖持仓"
                        )
                    )
                } else {
                    val newQuantity = fund.holdingQuantity + inputQuantity
                    val newCost = fund.totalCost + inputCost
                    val updatedFund = fund.copy(
                        holdingQuantity = newQuantity,
                        totalCost = newCost,
                        updatedAt = TimeRepository.getCurrentTimeMillis()
                    )
                    repository.updateFund(updatedFund)
                    repository.insertTransaction(
                        Transaction(
                            fundId = fund.id,
                            fundCode = fund.code,
                            fundName = fund.name,
                            type = TransactionType.BUY,
                            amount = inputCost,
                            price = inputPrice,
                            quantity = inputQuantity,
                            remark = "添加基金-加仓"
                        )
                    )
                    operationLogRepository?.logOperation(
                        type = OperationType.ADD_FUND,
                        title = "加仓基金",
                        description = "代码: ${fund.code}, 份额: ${inputQuantity.toLong()}, 成本: ¥${String.format("%.2f", inputCost)}",
                        targetId = fund.id,
                        targetName = fund.name
                    )
                }
            } else {
                val fund = Fund(
                    code = state.fundCode.text,
                    name = state.fundName,
                    type = state.selectedType,
                    holdingQuantity = inputQuantity,
                    totalCost = inputCost,
                    currentPrice = inputPrice
                )
                val id = repository.insertFund(fund)
                repository.insertTransaction(
                    Transaction(
                        fundId = id,
                        fundCode = fund.code,
                        fundName = fund.name,
                        type = TransactionType.ADD_FUNDS,
                        amount = inputCost,
                        price = inputPrice,
                        quantity = inputQuantity,
                        remark = "初始持仓"
                    )
                )
                operationLogRepository?.logOperation(
                    type = OperationType.ADD_FUND,
                    title = "添加基金",
                    description = "代码: ${fund.code}, 份额: ${inputQuantity.toLong()}, 成本: ¥${String.format("%.2f", inputCost)}",
                    targetId = id,
                    targetName = fund.name
                )
            }
            onFundAdded()
        }
    }
}
