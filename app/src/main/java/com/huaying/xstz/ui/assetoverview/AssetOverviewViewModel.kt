package com.huaying.xstz.ui.assetoverview

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AssetOverviewUiState {
    object Loading : AssetOverviewUiState()
    data class Success(val summary: AssetSummary, val funds: List<Fund>) : AssetOverviewUiState()
    data class Error(val message: String) : AssetOverviewUiState()
}

sealed class UiEvent {
    object RecordSuccess : UiEvent()
    data class ShowSnackbar(val message: String) : UiEvent()
}

class AssetOverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FundRepository(database)
    private val preferenceManager = PreferenceManager(application)
    private val vibrator = application.getSystemService(Vibrator::class.java)
    private val tradingTimeChecker = TradingTimeChecker(application)

    private val _uiState = MutableStateFlow<AssetOverviewUiState>(AssetOverviewUiState.Loading)
    val uiState: StateFlow<AssetOverviewUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.WEEK)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedChartDataType = MutableStateFlow(ChartDataType.RETURN_RATE)
    val selectedChartDataType: StateFlow<ChartDataType> = _selectedChartDataType.asStateFlow()

    private val _trendChartData = MutableStateFlow<TrendChartData?>(null)
    val trendChartData: StateFlow<TrendChartData?> = _trendChartData.asStateFlow()

    private val _selectedDate = MutableStateFlow(FormatUtils.getChinaCalendar())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _dailyPnLData = MutableStateFlow<DailyPnLData?>(null)
    val dailyPnLData: StateFlow<DailyPnLData?> = _dailyPnLData.asStateFlow()

    private var currentRebalanceThreshold: Double = 20.0
    private var currentRebalanceThresholdMode: Int = 0
    private var currentPrivacyMode: Boolean = false
    private var initialInvestmentDate: Long = 0
    private var refreshInterval = AppConstants.DEFAULT_REFRESH_INTERVAL_SECONDS
    private var refreshMode = 0
    private var isAutoRefreshEnabled = true
    private var nextAutoRefreshTime = 0L
    private var chartCalculationJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            repository.initDefaultTargetAllocation()
            loadTrendChartData()
            currentPrivacyMode = preferenceManager.privacyModeEnabled.first()

            launch { preferenceManager.refreshIntervalSeconds.collect { refreshInterval = it.toLong() } }
            launch { preferenceManager.refreshMode.collect { refreshMode = it } }
            launch {
                preferenceManager.rebalanceThreshold.collect {
                    currentRebalanceThreshold = it.toDouble()
                    updateSummaryRebalanceThreshold()
                }
            }
            launch {
                preferenceManager.rebalanceThresholdMode.collect {
                    currentRebalanceThresholdMode = it
                    refreshCurrentSummary()
                }
            }
            launch {
                preferenceManager.privacyModeEnabled.collect {
                    currentPrivacyMode = it
                    updateSummaryPrivacyMode()
                }
            }
            launch {
                preferenceManager.initialInvestmentDate.collect { date ->
                    initialInvestmentDate = date
                    if (date == 0L) {
                        val now = TimeRepository.getCurrentTimeMillis()
                        preferenceManager.setInitialInvestmentDate(now)
                        initialInvestmentDate = now
                    }
                }
            }

            combine(repository.getAllFunds(), repository.getTargetAllocation()) { funds, _ ->
                updateSummary(funds)
            }.collect()
        }

        viewModelScope.launch { TimeRepository.syncTime() }
        viewModelScope.launch {
            delay(AppConstants.REFRESH_STARTUP_DELAY_MS)
            refreshData(showLoading = false)
        }

        startAutoRefresh()
    }

    fun togglePrivacyMode() {
        viewModelScope.launch {
            preferenceManager.setPrivacyModeEnabled(!currentPrivacyMode)
        }
    }

    fun getMarketStatus(): String = tradingTimeChecker.getMarketStatus()

    fun refreshData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                nextAutoRefreshTime = TimeRepository.getCurrentTimeMillis() + refreshInterval * AppConstants.MILLISECONDS_PER_SECOND
                setRefreshingState(true)
            }

            val success = repository.updateAllFundsRealtimeData()
            if (!success) {
                val funds = repository.getAllFunds().first()
                updateSummary(funds)
            }

            if (showLoading) setRefreshingState(false)
        }
    }

    fun recordNetValue() {
        viewModelScope.launch {
            try {
                val record = repository.recordCurrentSnapshot()
                if (record != null) {
                    repository.insertNetValueRecord(record)
                    _eventFlow.emit(UiEvent.RecordSuccess)
                } else {
                    _eventFlow.emit(UiEvent.ShowSnackbar("记录失败：无法获取快照"))
                }
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("记录失败：${e.message}"))
            }
        }
    }

    fun selectTimeRange(timeRange: TimeRange) {
        _selectedTimeRange.value = timeRange
        loadTrendChartData()
    }

    fun selectChartDataType(dataType: ChartDataType) {
        _selectedChartDataType.value = dataType
        loadTrendChartData()
    }

    fun selectDate(date: Calendar) {
        _selectedDate.value = date
        loadDailyPnLData(date)
    }

    fun getFundDisplayRatio(fund: Fund, totalAssets: Double): Double {
        return RebalanceCalculator.calculateFundDisplayRatios(listOf(fund), totalAssets)[fund] ?: 0.0
    }

    fun getInvestmentDays(): Int = FormatUtils.getInvestmentDays(initialInvestmentDate)

    fun formatCurrency(value: Double): String = FormatUtils.formatCurrency(value, currentPrivacyMode)
    fun formatPercent(value: Double): String = FormatUtils.formatPercent(value, currentPrivacyMode)
    fun formatTime(timestamp: Long): String = FormatUtils.formatTime(timestamp)
    fun formatSelectedDate(calendar: Calendar): String = FormatUtils.formatSelectedDate(calendar)
    fun getWeekdayString(calendar: Calendar): String = FormatUtils.getWeekdayString(calendar)

    fun getFundStatus(fund: Fund, totalAssets: Double): String {
        return RebalanceCalculator.getFundStatus(
            fund, totalAssets, currentRebalanceThreshold,
            currentRebalanceThresholdMode, 0.0
        )
    }

    suspend fun getRecordedDates(): Set<Long> {
        return try {
            val records = repository.getAllNetValueRecords().first()
            records.map { record ->
                FormatUtils.getDayStart(FormatUtils.getChinaCalendar().apply {
                    timeInMillis = record.createdAt
                }).timeInMillis
            }.toSet()
        } catch (e: Exception) {
            android.util.Log.e("AssetOverviewViewModel", "getRecordedDates failed", e)
            emptySet()
        }
    }

    suspend fun getHolidayDatesForCurrentYear(): Pair<Set<Long>, Map<Long, String>> {
        return HolidayDateProvider.getHolidayDatesForCurrentYear()
    }

    override fun onCleared() {
        super.onCleared()
        isAutoRefreshEnabled = false
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isAutoRefreshEnabled) {
                if (_uiState.value is AssetOverviewUiState.Success) {
                    val now = TimeRepository.getCurrentTimeMillis()
                    val shouldRefresh = if (refreshMode == 0) tradingTimeChecker.isTradingTime() else true
                    if (shouldRefresh && now >= nextAutoRefreshTime) {
                        refreshData(showLoading = false)
                        nextAutoRefreshTime = now + refreshInterval * AppConstants.MILLISECONDS_PER_SECOND
                    }
                }
                delay(AppConstants.AUTO_REFRESH_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun updateSummary(funds: List<Fund>) {
        try {
            val totalAssets = repository.getTotalMarketValue()
            val principal = repository.getTotalCost()
            val totalReturn = totalAssets - principal
            val returnRate = if (principal > 0) totalReturn / principal * 100 else 0.0

            val todayReturn = funds.sumOf { fund ->
                val currentValue = fund.holdingQuantity * fund.currentPrice
                currentValue * fund.changePercent / 100
            }
            val todayReturnRate = if (principal > 0) todayReturn / principal * 100 else 0.0

            val stockValue = repository.getMarketValueByType(AssetType.STOCK)
            val bondValue = repository.getMarketValueByType(AssetType.BOND)
            val commodityValue = repository.getMarketValueByType(AssetType.COMMODITY)
            val cashValue = repository.getMarketValueByType(AssetType.CASH)

            val summary = AssetSummary(
                totalAssets = totalAssets,
                principal = principal,
                totalReturn = totalReturn,
                returnRate = returnRate,
                todayReturn = todayReturn,
                todayReturnRate = todayReturnRate,
                stockValue = stockValue,
                bondValue = bondValue,
                commodityValue = commodityValue,
                cashValue = cashValue,
                stockRatio = if (totalAssets > 0) stockValue / totalAssets * 100 else 0.0,
                bondRatio = if (totalAssets > 0) bondValue / totalAssets * 100 else 0.0,
                commodityRatio = if (totalAssets > 0) commodityValue / totalAssets * 100 else 0.0,
                cashRatio = if (totalAssets > 0) cashValue / totalAssets * 100 else 0.0,
                lastUpdateTime = TimeRepository.getCurrentTimeMillis(),
                isRefreshing = false,
                rebalanceThreshold = currentRebalanceThreshold,
                isPrivacyMode = currentPrivacyMode
            )

            _uiState.value = AssetOverviewUiState.Success(summary, funds)
        } catch (e: Exception) {
            _uiState.value = AssetOverviewUiState.Error(e.message ?: "未知错误")
        }
    }

    private fun updateSummaryRebalanceThreshold() {
        (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
            _uiState.value = currentState.copy(
                summary = currentState.summary.copy(rebalanceThreshold = currentRebalanceThreshold)
            )
        }
    }

    private fun updateSummaryPrivacyMode() {
        (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
            _uiState.value = currentState.copy(
                summary = currentState.summary.copy(isPrivacyMode = currentPrivacyMode)
            )
        }
    }

    private fun refreshCurrentSummary() {
        (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
            viewModelScope.launch { updateSummary(currentState.funds) }
        }
    }

    private fun setRefreshingState(refreshing: Boolean) {
        (_uiState.value as? AssetOverviewUiState.Success)?.let {
            _uiState.value = AssetOverviewUiState.Success(
                summary = it.summary.copy(isRefreshing = refreshing),
                funds = it.funds
            )
        }
    }

    private fun loadTrendChartData() {
        chartCalculationJob?.cancel()
        chartCalculationJob = viewModelScope.launch {
            try {
                val timeRange = _selectedTimeRange.value
                val dataType = _selectedChartDataType.value
                val currentTime = TimeRepository.getCurrentTimeMillis()

                val startTime = resolveStartTime(timeRange, currentTime)
                val records = repository.getRecordsByDateRange(startTime, currentTime).first()

                val cacheKey = TrendChartDataCache.generateKey(timeRange, dataType, records)
                TrendChartDataCache.get(cacheKey)?.let { _trendChartData.value = it }

                val chartData = ChartDataProcessor.calculateChartData(dataType, records)
                TrendChartDataCache.put(cacheKey, chartData)
                _trendChartData.value = chartData
            } catch (e: Exception) {
                android.util.Log.e("AssetOverviewViewModel", "loadTrendChartData failed", e)
            }
        }
    }

    private fun resolveStartTime(timeRange: TimeRange, currentTime: Long): Long {
        val calendar = FormatUtils.getChinaCalendar().apply { timeInMillis = currentTime }
        return when (timeRange) {
            TimeRange.WEEK -> calendar.apply { add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
            TimeRange.MONTH -> calendar.apply { add(Calendar.MONTH, -1) }.timeInMillis
            TimeRange.YEAR -> calendar.apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    private fun loadDailyPnLData(date: Calendar) {
        viewModelScope.launch {
            try {
                val funds = repository.getAllFunds().first()
                val isToday = FormatUtils.isSameDay(date, FormatUtils.getChinaCalendar())

                if (isToday) {
                    val todayReturn = funds.sumOf { fund ->
                        val currentValue = fund.holdingQuantity * fund.currentPrice
                        currentValue * fund.changePercent / 100
                    }
                    val todayReturnRate = if (repository.getTotalCost() > 0) {
                        todayReturn / repository.getTotalCost() * 100
                    } else 0.0

                    val fundDetails = funds.filter { it.type != AssetType.CASH }.map { fund ->
                        val currentValue = fund.holdingQuantity * fund.currentPrice
                        FundDailyPnL(
                            fund = fund,
                            dailyReturn = currentValue * fund.changePercent / 100,
                            dailyReturnRate = if (currentValue > 0) fund.changePercent else 0.0,
                            currentValue = currentValue
                        )
                    }

                    _dailyPnLData.value = DailyPnLData(
                        date = date, totalDailyReturn = todayReturn,
                        totalDailyReturnRate = todayReturnRate,
                        fundDetails = fundDetails, hasRecord = true
                    )
                } else {
                    loadHistoricalDailyPnL(date, funds)
                }
            } catch (e: Exception) {
                android.util.Log.e("AssetOverviewViewModel", "calculateDailyPnL failed", e)
            }
        }
    }

    private suspend fun loadHistoricalDailyPnL(date: Calendar, funds: List<Fund>) {
        val dayStart = FormatUtils.getDayStart(date).timeInMillis
        val dayEnd = FormatUtils.getDayEnd(date).timeInMillis
        val records = repository.getRecordsByDateRange(dayStart, dayEnd).first()
        val record = records.firstOrNull()

        if (record != null) {
            val prevRecord = getPreviousRecord(date)
            val dailyReturn = if (prevRecord != null) record.totalAssets - prevRecord.totalAssets else 0.0
            val dailyReturnRate = if (prevRecord != null && prevRecord.totalAssets > 0) {
                (record.totalAssets - prevRecord.totalAssets) / prevRecord.totalAssets * 100
            } else 0.0

            val fundDetails = funds.filter { it.type != AssetType.CASH }.map { fund ->
                val currentValue = fund.holdingQuantity * fund.currentPrice
                FundDailyPnL(
                    fund = fund,
                    dailyReturn = currentValue * fund.changePercent / 100,
                    dailyReturnRate = fund.changePercent,
                    currentValue = currentValue
                )
            }

            _dailyPnLData.value = DailyPnLData(
                date = date, totalDailyReturn = dailyReturn,
                totalDailyReturnRate = dailyReturnRate,
                fundDetails = fundDetails, hasRecord = true
            )
        } else {
            _dailyPnLData.value = DailyPnLData(
                date = date, totalDailyReturn = 0.0,
                totalDailyReturnRate = 0.0,
                fundDetails = emptyList(), hasRecord = false
            )
        }
    }

    private suspend fun getPreviousRecord(date: Calendar): com.huaying.xstz.data.entity.NetValueRecord? {
        return try {
            val dayStart = FormatUtils.getDayStart(date).timeInMillis
            repository.getRecordsSince(0L).first()
                .filter { it.createdAt < dayStart }
                .maxByOrNull { it.createdAt }
        } catch (e: Exception) {
            android.util.Log.e("AssetOverviewViewModel", "getPreviousRecord failed", e)
            null
        }
    }

    private fun vibrateLight() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            android.util.Log.w("AssetOverviewViewModel", "vibrate failed", e)
        }
    }
}

private fun ChartDataProcessor.calculateChartData(dataType: ChartDataType, records: List<com.huaying.xstz.data.entity.NetValueRecord>): TrendChartData {
    val sampled = if (records.size > 100) sampleRecords(records, 100) else records
    return when (dataType) {
        ChartDataType.RETURN_RATE -> calculateReturnRateData(sampled)
        ChartDataType.ASSET_RATIO -> calculateAssetRatioData(sampled)
        ChartDataType.ASSET_COMPARISON -> calculateAssetComparisonData(sampled)
    }
}
