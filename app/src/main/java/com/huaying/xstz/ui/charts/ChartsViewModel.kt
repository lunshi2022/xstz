package com.huaying.xstz.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.data.model.DailyAssetData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import kotlin.random.Random

enum class TimeRange {
    WEEK, MONTH, YEAR
}

sealed interface ChartsUiState {
    object Loading : ChartsUiState
    data class Success(
        val timeRange: TimeRange,
        val data: List<DailyAssetData>,
        val showPrincipal: Boolean = true,
        val funds: List<com.huaying.xstz.data.entity.Fund> = emptyList(),
        val preparedData: PreparedChartData? = null
    ) : ChartsUiState
    data class Error(val message: String) : ChartsUiState
}

/**
 * 预处理的图表数据
 */
data class PreparedChartData(
    val sortedData: List<DailyAssetData>,
    val returnEntries: List<com.github.mikephil.charting.data.Entry>,
    val allocationEntries: Map<String, List<com.github.mikephil.charting.data.Entry>>,
    val assetEntries: List<com.github.mikephil.charting.data.Entry>,
    val principalEntries: List<com.github.mikephil.charting.data.Entry>,
    val timestamp: Long = System.currentTimeMillis()
)


class ChartsViewModel(
    private val repository: FundRepository,
    private val preferenceManager: com.huaying.xstz.data.PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChartsUiState>(ChartsUiState.Loading)
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val initialShowPrincipal = preferenceManager.showPrincipal.first()
            loadData(TimeRange.WEEK, initialShowPrincipal)
        }
    }

    fun setTimeRange(range: TimeRange) {
        viewModelScope.launch {
            val currentShowPrincipal = if (_uiState.value is ChartsUiState.Success) {
                (_uiState.value as ChartsUiState.Success).showPrincipal
            } else {
                preferenceManager.showPrincipal.first()
            }
            loadData(range, currentShowPrincipal)
        }
    }

    fun togglePrincipal(show: Boolean) {
        viewModelScope.launch {
            preferenceManager.setShowPrincipal(show)
        }
        val currentState = _uiState.value
        if (currentState is ChartsUiState.Success) {
            _uiState.value = currentState.copy(showPrincipal = show)
        }
    }

    private var loadDataJob: kotlinx.coroutines.Job? = null

    private fun loadData(range: TimeRange, showPrincipal: Boolean = true) {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            if (_uiState.value !is ChartsUiState.Success) {
                _uiState.value = ChartsUiState.Loading
            }
            
            combine(
                repository.getDailyAssetDataFlow(),
                repository.getAllFunds()
            ) { dailyData, funds ->
                // 使用当前状态中的 showPrincipal 值，而不是传入的参数
                val currentShowPrincipal = if (_uiState.value is ChartsUiState.Success) {
                    (_uiState.value as ChartsUiState.Success).showPrincipal
                } else {
                    showPrincipal
                }
                
                if (dailyData.isEmpty()) {
                    ChartsUiState.Success(
                        timeRange = range,
                        data = emptyList(),
                        showPrincipal = currentShowPrincipal,
                        funds = funds
                    )
                } else {
                    val endDate = TimeRepository.getCurrentLocalDate()
                    val startDate = when (range) {
                        TimeRange.WEEK -> endDate.minusWeeks(1)
                        TimeRange.MONTH -> endDate.minusMonths(1)
                        TimeRange.YEAR -> endDate.minusYears(1)
                    }

                    val filteredData = dailyData.filter { 
                        !it.date.isBefore(startDate) && !it.date.isAfter(endDate)
                    }.sortedByDescending { it.date }

                    ChartsUiState.Success(
                        timeRange = range,
                        data = filteredData,
                        showPrincipal = currentShowPrincipal,
                        funds = funds
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}
