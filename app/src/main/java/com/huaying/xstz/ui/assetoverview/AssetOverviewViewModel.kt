package com.huaying.xstz.ui.assetoverview

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.NetValueRecord
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.data.repository.HolidayRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.huaying.xstz.data.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AssetSummary(
    val totalAssets: Double = 0.0,
    val principal: Double = 0.0,
    val totalReturn: Double = 0.0,
    val returnRate: Double = 0.0,
    val todayReturn: Double = 0.0,
    val todayReturnRate: Double = 0.0,
    val stockValue: Double = 0.0,
    val bondValue: Double = 0.0,
    val commodityValue: Double = 0.0,
    val cashValue: Double = 0.0,
    val stockRatio: Double = 0.0,
    val bondRatio: Double = 0.0,
    val commodityRatio: Double = 0.0,
    val cashRatio: Double = 0.0,
    val lastUpdateTime: Long = TimeRepository.getCurrentTimeMillis(),
    val isRefreshing: Boolean = false,
    val rebalanceThreshold: Double = 20.0,
    val isPrivacyMode: Boolean = false
)

enum class TimeRange(val displayName: String) {
    WEEK("近1周"),
    MONTH("近1月"),
    YEAR("今年以来")
}

enum class ChartDataType(val displayName: String) {
    RETURN_RATE("收益率"),
    ASSET_RATIO("资产占比"),
    ASSET_COMPARISON("资产对比")
}

/**
 * 图表数据缓存键
 */
internal data class ChartCacheKey(
    val timeRange: TimeRange,
    val dataType: ChartDataType,
    val dataSize: Int
)

/**
 * 图表数据缓存管理器
 * 参考：LRU缓存策略，最大缓存10个条目
 */
internal object TrendChartDataCache {
    private val cache = ConcurrentHashMap<ChartCacheKey, TrendChartData>()
    private const val MAX_CACHE_SIZE = 10
    private val accessOrder = mutableListOf<ChartCacheKey>()

    internal fun get(key: ChartCacheKey): TrendChartData? {
        val data = cache[key]
        if (data != null) {
            // 更新访问顺序（LRU）
            synchronized(accessOrder) {
                accessOrder.remove(key)
                accessOrder.add(key)
            }
        }
        return data
    }

    internal fun put(key: ChartCacheKey, data: TrendChartData) {
        synchronized(accessOrder) {
            if (cache.size >= MAX_CACHE_SIZE && !cache.containsKey(key)) {
                // 移除最久未访问的条目
                accessOrder.firstOrNull()?.let { oldestKey ->
                    cache.remove(oldestKey)
                    accessOrder.remove(oldestKey)
                }
            }
            cache[key] = data
            accessOrder.remove(key)
            accessOrder.add(key)
        }
    }

    internal fun clear() {
        synchronized(accessOrder) {
            cache.clear()
            accessOrder.clear()
        }
    }

    internal fun generateKey(timeRange: TimeRange, dataType: ChartDataType, records: List<NetValueRecord>): ChartCacheKey {
        return ChartCacheKey(timeRange, dataType, records.size)
    }
}

data class ChartDataPoint(
    val date: Long,
    val value: Double,
    val label: String
)

data class TrendChartData(
    val dataPoints: List<ChartDataPoint>,
    val minValue: Double,
    val maxValue: Double,
    val currentValue: Double,
    val changeValue: Double,
    val changePercent: Double
)

sealed class AssetOverviewUiState {
    object Loading : AssetOverviewUiState()
    data class Success(
        val summary: AssetSummary,
        val funds: List<Fund>
    ) : AssetOverviewUiState()
    data class Error(val message: String) : AssetOverviewUiState()
}

class AssetOverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = FundRepository(database)
    private val preferenceManager = PreferenceManager(application)
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private val _uiState = MutableStateFlow<AssetOverviewUiState>(AssetOverviewUiState.Loading)
    val uiState: StateFlow<AssetOverviewUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    sealed class UiEvent {
        object RecordSuccess : UiEvent()
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    private var fundDisplayRatios: Map<Fund, Double> = emptyMap()
    private var totalTargetRatio: Double = 100.0
    private var currentRebalanceThreshold: Double = 20.0
    private var currentRebalanceThresholdMode: Int = 0 // 0: Percentage, 1: Percentage Points

    private var refreshInterval = 3L  // 刷新间隔（秒）
    private var refreshMode = 0 // 0: Smart, 1: Fixed
    private var isAutoRefreshEnabled = true
    private var nextAutoRefreshTime = 0L  // 下一次允许自动刷新的时间
    private var estimatedLiquidity: Double = 0.0 // 估算可用于买入的流动资金（来自需卖出的资产）
    private var currentPrivacyMode: Boolean = false // Current privacy mode state
    private var initialInvestmentDate: Long = 0 // 初始投资日期

    // 趋势图表相关状态
    private val _selectedTimeRange = MutableStateFlow(TimeRange.WEEK)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedChartDataType = MutableStateFlow(ChartDataType.RETURN_RATE)
    val selectedChartDataType: StateFlow<ChartDataType> = _selectedChartDataType.asStateFlow()

    private val _trendChartData = MutableStateFlow<TrendChartData?>(null)
    val trendChartData: StateFlow<TrendChartData?> = _trendChartData.asStateFlow()

    // 缓存交易日状态，避免频繁调用网络接口
    private var cachedTradingDay: org.threeten.bp.LocalDate? = null
    private var cachedIsTradingDay: Boolean? = null
    private var lastTradingDayCheckTime: Long = 0
    private val TRADING_DAY_CACHE_DURATION = 5 * 60 * 1000L // 5分钟缓存

    // 图表数据计算任务
    private var chartCalculationJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            // 初始化默认配置
            repository.initDefaultTargetAllocation()

            // 初始化时加载默认的趋势图表数据（近1周收益率）
            loadTrendChartData()

            // Get initial privacy mode setting
            currentPrivacyMode = preferenceManager.privacyModeEnabled.first()

            // 监听刷新频率设置
            launch {
                preferenceManager.refreshIntervalSeconds.collect { seconds ->
                    refreshInterval = seconds.toLong()
                }
            }
            
            // 监听刷新模式设置
            launch {
                preferenceManager.refreshMode.collect { mode ->
                    refreshMode = mode
                }
            }

            // 启动自动刷新
            startAutoRefresh()
            
            // 监听阈值设置
            launch {
                preferenceManager.rebalanceThreshold.collect { threshold ->
                    currentRebalanceThreshold = threshold.toDouble()
                    // 如果已有数据，更新状态
                    (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
                         _uiState.value = currentState.copy(
                             summary = currentState.summary.copy(rebalanceThreshold = currentRebalanceThreshold)
                         )
                    }
                }
            }

            // 监听阈值模式设置
            launch {
                preferenceManager.rebalanceThresholdMode.collect { mode ->
                    currentRebalanceThresholdMode = mode
                    // 模式改变后重新计算所有状态
                    (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
                        updateSummary(currentState.funds)
                    }
                }
            }

            // 监听隐私模式设置
            launch {
                preferenceManager.privacyModeEnabled.collect { enabled ->
                    currentPrivacyMode = enabled
                    (_uiState.value as? AssetOverviewUiState.Success)?.let { currentState ->
                        _uiState.value = currentState.copy(
                            summary = currentState.summary.copy(isPrivacyMode = enabled)
                        )
                    }
                }
            }

            // 监听初始投资日期设置
            launch {
                preferenceManager.initialInvestmentDate.collect { date ->
                    initialInvestmentDate = date
                    // 如果没有设置初始投资日期，设置为当前时间
                    if (date == 0L) {
                        val currentTime = TimeRepository.getCurrentTimeMillis()
                        preferenceManager.setInitialInvestmentDate(currentTime)
                        initialInvestmentDate = currentTime
                    }
                }
            }

            // 监听基金数据变化 - 先显示本地数据
            combine(
                repository.getAllFunds(),
                repository.getTargetAllocation()
            ) { funds, _ ->
                updateSummary(funds)
            }.collect()
        }
        
        // 启动时间同步（在后台执行，不阻塞UI）
        viewModelScope.launch {
            TimeRepository.syncTime()
        }
        
        // 延迟执行网络刷新，让UI先显示本地数据
        // 使用延迟确保界面先渲染完成，提升启动速度感知
        viewModelScope.launch {
            delay(500) // 延迟500ms，让UI先渲染本地数据
            refreshData(showLoading = false) // 使用showLoading=false避免显示加载状态
        }
    }

    fun togglePrivacyMode() {
        viewModelScope.launch {
            preferenceManager.setPrivacyModeEnabled(!currentPrivacyMode)
        }
    }

    fun getMarketStatus(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
        calendar.timeInMillis = TimeRepository.getCurrentTimeMillis()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // 周末不交易
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return "周末休市"
        }
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute
        
        // 交易时间：9:15-11:30, 13:00-15:00
        9 * 60 + 15
        11 * 60 + 30
        13 * 60
        15 * 60
        
        return when {
            timeInMinutes < 9 * 60 + 15 -> "未开盘"
            timeInMinutes in 9 * 60 + 15..11 * 60 + 30 -> "交易中"
            timeInMinutes in 11 * 60 + 31..12 * 60 + 59 -> "午间休盘"
            timeInMinutes in 13 * 60..15 * 60 -> "交易中"
            else -> "已收盘"
        }
    }

    private fun isTradingTime(): Boolean {
        // 强制使用北京时间 (GMT+8) 进行判断，避免设备时区不同导致误判
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
        calendar.timeInMillis = TimeRepository.getCurrentTimeMillis()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // 获取当前日期
        val currentDate = org.threeten.bp.LocalDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // 判断是否为交易日（使用缓存，避免同步阻塞）
        val isTradingDay = checkIsTradingDay(currentDate, dayOfWeek)

        if (!isTradingDay) {
            return false
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        // 交易时间：9:15-11:30, 13:00-15:00
        val morningStart = 9 * 60 + 15
        val morningEnd = 11 * 60 + 30
        val afternoonStart = 13 * 60
        val afternoonEnd = 15 * 60

        return (timeInMinutes in morningStart..morningEnd) ||
               (timeInMinutes in afternoonStart..afternoonEnd)
    }

    /**
     * 检查是否为交易日，使用缓存避免频繁网络请求
     */
    private fun checkIsTradingDay(date: org.threeten.bp.LocalDate, dayOfWeek: Int): Boolean {
        val now = TimeRepository.getCurrentTimeMillis()

        // 如果有缓存且未过期，直接使用缓存
        if (cachedTradingDay == date && cachedIsTradingDay != null &&
            (now - lastTradingDayCheckTime) < TRADING_DAY_CACHE_DURATION) {
            return cachedIsTradingDay!!
        }

        // 先判断周末（基础判断，不需要网络）
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            cachedTradingDay = date
            cachedIsTradingDay = false
            lastTradingDayCheckTime = now
            return false
        }

        // 异步更新缓存（不阻塞当前调用）
        viewModelScope.launch {
            try {
                val result = HolidayRepository.isTradingDay(getApplication(), date)
                cachedTradingDay = date
                cachedIsTradingDay = result
                lastTradingDayCheckTime = now
            } catch (e: Exception) {
                // 忽略错误，保持当前缓存状态
            }
        }

        // 如果缓存存在（哪怕是之前的），使用缓存
        // 否则默认认为是交易日（工作日）
        return cachedIsTradingDay ?: true
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isAutoRefreshEnabled) {
                if (_uiState.value is AssetOverviewUiState.Success) {
                    val now = TimeRepository.getCurrentTimeMillis()
                    
                    val shouldRefresh = if (refreshMode == 0) {
                        // Smart mode: only refresh during trading hours
                        isTradingTime()
                    } else {
                        // Fixed mode: always refresh
                        true
                    }

                    if (shouldRefresh) {
                        // 只有当前时间超过设定的下一次刷新时间才执行刷新
                        if (now >= nextAutoRefreshTime) {
                            refreshData(showLoading = false)
                            // 设置下一次自动刷新时间
                            nextAutoRefreshTime = now + refreshInterval * 1000
                        }
                    }
                }
                delay(500)  // 每0.5秒检查一次
            }
        }
    }
    
    private suspend fun updateSummary(funds: List<Fund>) {
        try {
            totalTargetRatio = funds.sumOf { it.targetRatio }
            
            val totalAssets = repository.getTotalMarketValue()
            val principal = repository.getTotalCost()
            val totalReturn = totalAssets - principal
            val returnRate = if (principal > 0) totalReturn / principal * 100 else 0.0
            
            // 计算今日收益和今日收益率
            val todayReturn = funds.sumOf { fund ->
                val currentValue = fund.holdingQuantity * fund.currentPrice
                currentValue * fund.changePercent / 100
            }
            val todayReturnRate = if (principal > 0) todayReturn / principal * 100 else 0.0
            
            val stockValue = repository.getMarketValueByType(AssetType.STOCK)
            val bondValue = repository.getMarketValueByType(AssetType.BOND)
            val commodityValue = repository.getMarketValueByType(AssetType.COMMODITY)
            val cashValue = repository.getMarketValueByType(AssetType.CASH)
            
            val stockRatio = if (totalAssets > 0) stockValue / totalAssets * 100 else 0.0
            val bondRatio = if (totalAssets > 0) bondValue / totalAssets * 100 else 0.0
            val commodityRatio = if (totalAssets > 0) commodityValue / totalAssets * 100 else 0.0
            val cashRatio = if (totalAssets > 0) cashValue / totalAssets * 100 else 0.0

            // 计算基金显示比例（不再归一化）
            var calculatedLiquidity = 0.0
            fundDisplayRatios = if (totalAssets > 0) {
                val fundRatios = funds.associateWith { fund ->
                    val currentValue = fund.holdingQuantity * fund.currentPrice
                    currentValue / totalAssets * 100.0
                }
                
                // 计算流动性：累加所有需要卖出且满足1手交易量的资产价值
                funds.forEach { fund ->
                    val (deviationPct, _) = calculateDeviation(fund, totalAssets)
                    if (deviationPct > 0) { // 正偏离，需要卖出
                        val sellValue = totalAssets * (deviationPct / 100.0)
                        if (fund.currentPrice > 0) {
                            val sellShares = sellValue / fund.currentPrice
                            if (sellShares >= 100) {
                                val sellableHundreds = (sellShares / 100).toInt()
                                calculatedLiquidity += sellableHundreds * 100 * fund.currentPrice
                            }
                        }
                    }
                }

                // 保留两位小数，但不进行归一化
                fundRatios.mapValues { 
                    Math.round(it.value * 100.0) / 100.0 
                }
            } else {
                emptyMap()
            }
            estimatedLiquidity = calculatedLiquidity

            val previousState = (_uiState.value as? AssetOverviewUiState.Success)?.summary
            val currentTime = TimeRepository.getCurrentTimeMillis()
            
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
                stockRatio = stockRatio,
                bondRatio = bondRatio,
                commodityRatio = commodityRatio,
                cashRatio = cashRatio,
                lastUpdateTime = currentTime,
                isRefreshing = false,
                rebalanceThreshold = currentRebalanceThreshold,
                isPrivacyMode = currentPrivacyMode
            )
            
            // 振动提示（当状态改变时）
            previousState?.let { prev ->
                val hasRebalancedFunds = funds.any { fund ->
                    val (deviation, target) = calculateDeviation(fund, totalAssets)
                    isNeedRebalance(deviation, target, fund, totalAssets)
                }
                val hadRebalancedFunds = (_uiState.value as? AssetOverviewUiState.Success)?.funds?.any { fund ->
                    val (prevDeviation, prevTarget) = calculateDeviation(fund, prev.totalAssets)
                    isNeedRebalance(prevDeviation, prevTarget, fund, prev.totalAssets)
                } ?: false
                
                if (hadRebalancedFunds && !hasRebalancedFunds) {
                    vibrateLight()
                }
            }
            
            _uiState.value = AssetOverviewUiState.Success(summary, funds)
        } catch (e: Exception) {
            _uiState.value = AssetOverviewUiState.Error(e.message ?: "未知错误")
        }
    }
    
    fun calculateDeviation(fund: Fund, totalAssets: Double): Pair<Double, Double> {
        if (totalAssets == 0.0) return 0.0 to 0.0
        
        val currentValue = fund.holdingQuantity * fund.currentPrice
        val currentRatio = currentValue / totalAssets * 100
        
        val targetRatioPercent = fund.targetRatio * 100
        
        return (currentRatio - targetRatioPercent) to targetRatioPercent
    }
    
    private fun isNeedRebalance(deviation: Double, targetRatio: Double, fund: Fund? = null, totalAssets: Double = 0.0): Boolean {
        // 使用当前设定的偏离度阈值（固定锚点法）
        val threshold = if (currentRebalanceThresholdMode == 0) {
            // 模式A：百分比模式 (例如：目标25% * 阈值20% = 5%)
            kotlin.math.max(targetRatio * (currentRebalanceThreshold / 100.0), 0.5)
        } else {
            // 模式B：百分点模式 (例如：阈值5 = 5%)
            currentRebalanceThreshold
        }
        
        // 如果偏离比例小于阈值，直接返回false
        if (kotlin.math.abs(deviation) <= threshold) return false
        
        // 如果提供了基金信息，检查交易单位是否满足1手（100股）
        if (fund != null && fund.currentPrice > 0 && totalAssets > 0) {
            val deviationValue = totalAssets * (kotlin.math.abs(deviation) / 100.0)
            val deviationShares = deviationValue / fund.currentPrice
            if (deviationShares < 100) {
                return false
            }
        }
        
        return true
    }
    
    fun refreshData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                // 手动刷新时，更新下一次允许自动刷新的时间为3秒后
                nextAutoRefreshTime = TimeRepository.getCurrentTimeMillis() + refreshInterval * 1000

                (_uiState.value as? AssetOverviewUiState.Success)?.let {
                    _uiState.value = AssetOverviewUiState.Success(
                        summary = it.summary.copy(isRefreshing = true),
                        funds = it.funds
                    )
                }
            }

            val success = repository.updateAllFundsRealtimeData()
            if (!success) {
                // 网络请求失败，仅更新本地数据
                val funds = repository.getAllFunds().first()
                updateSummary(funds)
            }
            
            // 刷新完成后结束刷新状态
            if (showLoading) {
                endRefresh()
            }
        }
    }

    fun endRefresh() {
        (_uiState.value as? AssetOverviewUiState.Success)?.let {
            _uiState.value = AssetOverviewUiState.Success(
                summary = it.summary.copy(isRefreshing = false),
                funds = it.funds
            )
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
                e.printStackTrace()
                _eventFlow.emit(UiEvent.ShowSnackbar("记录失败：${e.message}"))
            }
        }
    }
    
    private fun vibrateLight() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // 振动可能被禁用，忽略错误
        }
    }
    
    fun formatCurrency(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "¥ ****"
        return "¥%,.2f".format(Locale.CHINA, value)
    }
    
    fun formatPercent(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****%"
        return if (value >= 0) "+%.2f%%".format(value) else "%.2f%%".format(value)
    }
    
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("GMT+8")
        return sdf.format(Date(timestamp))
    }
    
    fun getFundStatus(fund: Fund, totalAssets: Double, thresholdPct: Double): String {
        if (totalAssets == 0.0) return "正常"
        
        val currentValue = fund.holdingQuantity * fund.currentPrice
        val currentRatio = currentValue / totalAssets * 100
        
        // 直接使用目标占比（不再归一化）
        val targetRatioPercent = fund.targetRatio * 100
        
        val deviation = kotlin.math.abs(currentRatio - targetRatioPercent)
        
        // 偏离度阈值规则：
        // 1. 正常阈值：
        //    模式A (百分比)：Max(目标比例 * 用户设定阈值%, 0.5%)
        //    模式B (百分点)：用户设定百分点值
        // 2. 严重偏离阈值 = 正常阈值 * 2
        
        val normalThreshold = if (currentRebalanceThresholdMode == 0) {
            kotlin.math.max(targetRatioPercent * (thresholdPct / 100.0), 0.5)
        } else {
            thresholdPct
        }
        val severeThreshold = normalThreshold * 2.0
        
        // 先根据偏离程度判断状态
        val status = when {
            deviation > severeThreshold -> "严重偏离"
            deviation > normalThreshold -> "需平衡"
            else -> "正常"
        }
        
        // 如果已经是正常，直接返回
        if (status == "正常") return "正常"
        
        // 检查是否因交易限制无法操作
        // 只有当变动确实很小（<100股）时才显示为正常
        // 如果偏离很大但只是因为单只基金持仓过多，仍然显示真实状态
        if (fund.currentPrice > 0) {
            val deviationValue = totalAssets * (deviation / 100.0)
            val deviationShares = deviationValue / fund.currentPrice
            
            // 只有变动数量确实很少（不足100股）且偏离不大（<5%）时才显示正常
            if (deviationShares < 100 && deviation < 5.0) {
                return "正常"
            }
            
            // 流动性检查：如果是需要买入（负偏离），检查是否有足够的资金
            val realDeviation = currentRatio - targetRatioPercent
            if (realDeviation < 0) { // 需要买入
                val costToBuy100 = fund.currentPrice * 100
                if (estimatedLiquidity < costToBuy100) {
                    // 资金不足，但偏离很大，仍然显示真实状态
                    return status
                }
            }
        }

        return status
    }
    
    override fun onCleared() {
        super.onCleared()
        isAutoRefreshEnabled = false
    }

    fun getFundDisplayRatio(fund: Fund, totalAssets: Double): Double {
        return fundDisplayRatios[fund] ?: 0.0
    }

    fun getInvestmentDays(): Int {
        if (initialInvestmentDate == 0L) {
            return 0
        }
        Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)

        val startCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
        startCalendar.timeInMillis = initialInvestmentDate
        startCalendar.set(Calendar.HOUR_OF_DAY, 0)
        startCalendar.set(Calendar.MINUTE, 0)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)

        val currentCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
        currentCalendar.timeInMillis = TimeRepository.getCurrentTimeMillis()
        currentCalendar.set(Calendar.HOUR_OF_DAY, 0)
        currentCalendar.set(Calendar.MINUTE, 0)
        currentCalendar.set(Calendar.SECOND, 0)
        currentCalendar.set(Calendar.MILLISECOND, 0)

        val diffInMillis = currentCalendar.timeInMillis - startCalendar.timeInMillis
        val diffInDays = diffInMillis / (1000L * 60 * 60 * 24)
        return diffInDays.toInt() + 1
    }

    data class FundDailyPnL(
        val fund: Fund,
        val dailyReturn: Double,
        val dailyReturnRate: Double,
        val currentValue: Double
    )

    data class DailyPnLData(
        val date: Calendar,
        val totalDailyReturn: Double,
        val totalDailyReturnRate: Double,
        val fundDetails: List<FundDailyPnL>,
        val hasRecord: Boolean
    )

    private val _selectedDate = MutableStateFlow(Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA))
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _dailyPnLData = MutableStateFlow<DailyPnLData?>(null)
    val dailyPnLData: StateFlow<DailyPnLData?> = _dailyPnLData.asStateFlow()

    fun selectDate(date: Calendar) {
        _selectedDate.value = date
        loadDailyPnLData(date)
    }

    private fun loadDailyPnLData(date: Calendar) {
        viewModelScope.launch {
            try {
                val funds = repository.getAllFunds().first()
                val isToday = isSameDay(date, Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA))

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
                        val dailyReturn = currentValue * fund.changePercent / 100
                        val dailyReturnRate = if (currentValue > 0) fund.changePercent else 0.0
                        FundDailyPnL(
                            fund = fund,
                            dailyReturn = dailyReturn,
                            dailyReturnRate = dailyReturnRate,
                            currentValue = currentValue
                        )
                    }

                    _dailyPnLData.value = DailyPnLData(
                        date = date,
                        totalDailyReturn = todayReturn,
                        totalDailyReturnRate = todayReturnRate,
                        fundDetails = fundDetails,
                        hasRecord = true
                    )
                } else {
                    val dayStart = getDayStart(date).timeInMillis
                    val dayEnd = getDayEnd(date).timeInMillis

                    val records = repository.getRecordsByDateRange(dayStart, dayEnd).first()
                    val record = records.firstOrNull()

                    if (record != null) {
                        val prevRecord = getPreviousRecord(date)

                        val dailyReturn = if (prevRecord != null) {
                            record.totalAssets - prevRecord.totalAssets
                        } else 0.0

                        val dailyReturnRate = if (prevRecord != null && prevRecord.totalAssets > 0) {
                            (record.totalAssets - prevRecord.totalAssets) / prevRecord.totalAssets * 100
                        } else 0.0

                        val fundDetails = funds.filter { it.type != AssetType.CASH }.map { fund ->
                            val currentValue = fund.holdingQuantity * fund.currentPrice
                            val estimatedDailyReturn = currentValue * fund.changePercent / 100
                            FundDailyPnL(
                                fund = fund,
                                dailyReturn = estimatedDailyReturn,
                                dailyReturnRate = fund.changePercent,
                                currentValue = currentValue
                            )
                        }

                        _dailyPnLData.value = DailyPnLData(
                            date = date,
                            totalDailyReturn = dailyReturn,
                            totalDailyReturnRate = dailyReturnRate,
                            fundDetails = fundDetails,
                            hasRecord = true
                        )
                    } else {
                        _dailyPnLData.value = DailyPnLData(
                            date = date,
                            totalDailyReturn = 0.0,
                            totalDailyReturnRate = 0.0,
                            fundDetails = emptyList(),
                            hasRecord = false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getRecordedDates(): Set<Long> {
        return try {
            val records = repository.getAllNetValueRecords().first()
            records.map { record ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
                cal.timeInMillis = record.createdAt
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun getPreviousRecord(date: Calendar): NetValueRecord? {
        return try {
            val dayStart = getDayStart(date).timeInMillis
            val records = repository.getRecordsSince(0L).first()
            records.filter { it.createdAt < dayStart }
                .maxByOrNull { it.createdAt }
        } catch (e: Exception) {
            null
        }
    }

    private fun getDayStart(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getDayEnd(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    fun formatSelectedDate(calendar: Calendar): String {
        return "%02d月%02d日".format(
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun getWeekdayString(calendar: Calendar): String {
        val weekdays = arrayOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return weekdays[calendar.get(Calendar.DAY_OF_WEEK)]
    }

    suspend fun getHolidayDatesForCurrentYear(): Pair<Set<Long>, Map<Long, String>> {
        return try {
            val currentYear = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA).get(Calendar.YEAR)
            val holidays = HolidayRepository.getBuiltinHolidaysForCalendar(currentYear)

            val holidayDateSet = mutableSetOf<Long>()
            val holidayNameMap = mutableMapOf<Long, String>()

            holidays.forEach { localDate ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
                cal.set(localDate.year, localDate.monthValue - 1, localDate.dayOfMonth, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val timestamp = cal.timeInMillis
                holidayDateSet.add(timestamp)

                val month = localDate.monthValue
                val day = localDate.dayOfMonth
                val name = when (month) {
                    1 -> if (day == 1) "元旦" else null
                    2 -> when {
                        day in 16..23 -> "春节"
                        else -> null
                    }
                    4 -> if (day in 4..6) "清明" else null
                    5 -> if (day in 1..5) "劳动节" else null
                    6 -> if (day in 19..21) "端午" else null
                    9 -> if (day in 25..27) "中秋" else null
                    10 -> if (day in 1..8) "国庆" else null
                    else -> null
                }
                if (name != null) {
                    holidayNameMap[timestamp] = name
                } else {
                    holidayNameMap[timestamp] = "休"
                }
            }

            Pair(holidayDateSet, holidayNameMap)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptySet(), emptyMap())
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

    private fun loadTrendChartData() {
        // 取消之前的计算任务
        chartCalculationJob?.cancel()

        chartCalculationJob = viewModelScope.launch {
            try {
                val timeRange = _selectedTimeRange.value
                val dataType = _selectedChartDataType.value

                val currentTime = TimeRepository.getCurrentTimeMillis()
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
                calendar.timeInMillis = currentTime

                val startTime = when (timeRange) {
                    TimeRange.WEEK -> {
                        calendar.add(Calendar.WEEK_OF_YEAR, -1)
                        calendar.timeInMillis
                    }
                    TimeRange.MONTH -> {
                        calendar.add(Calendar.MONTH, -1)
                        calendar.timeInMillis
                    }
                    TimeRange.YEAR -> {
                        calendar.set(Calendar.DAY_OF_YEAR, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        calendar.timeInMillis
                    }
                }

                val records = repository.getRecordsByDateRange(startTime, currentTime).first()

                // 检查缓存
                val cacheKey = TrendChartDataCache.generateKey(timeRange, dataType, records)
                val cachedData = TrendChartDataCache.get(cacheKey)

                // 如果有缓存，先显示缓存数据（秒开体验）
                if (cachedData != null) {
                    _trendChartData.value = cachedData
                }

                // 后台计算最新数据
                val chartData = withContext(Dispatchers.Default) {
                    // 数据采样优化：如果数据点过多，进行采样
                    val sampledRecords = if (records.size > 100) {
                        sampleRecords(records, 100)
                    } else {
                        records
                    }

                    when (dataType) {
                        ChartDataType.RETURN_RATE -> calculateReturnRateData(sampledRecords)
                        ChartDataType.ASSET_RATIO -> calculateAssetRatioData(sampledRecords)
                        ChartDataType.ASSET_COMPARISON -> calculateAssetComparisonData(sampledRecords)
                    }
                }

                // 缓存结果
                TrendChartDataCache.put(cacheKey, chartData)

                // 更新UI
                _trendChartData.value = chartData
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 数据采样：减少数据点数量，提升渲染性能
     * 参考：股票软件的K线采样算法
     */
    private fun sampleRecords(records: List<NetValueRecord>, targetSize: Int): List<NetValueRecord> {
        if (records.size <= targetSize) return records

        val step = records.size.toFloat() / targetSize
        val result = ArrayList<NetValueRecord>(targetSize)

        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceIn(0, records.size - 1)
            result.add(records[index])
        }

        // 确保包含最后一个数据点
        if (result.lastOrNull() != records.last()) {
            result.add(records.last())
        }

        return result
    }

    private fun calculateReturnRateData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }
        val basePrincipal = sortedRecords.first().principal

        val dataPoints = sortedRecords.map { record ->
            val returnRate = if (basePrincipal > 0) {
                (record.totalAssets - basePrincipal) / basePrincipal * 100
            } else 0.0

            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
            cal.timeInMillis = record.createdAt

            ChartDataPoint(
                date = record.createdAt,
                value = returnRate,
                label = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
            )
        }

        val values = dataPoints.map { it.value }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 0.0
        val currentValue = values.lastOrNull() ?: 0.0
        val firstValue = values.firstOrNull() ?: 0.0
        val changeValue = currentValue - firstValue
        val changePercent = if (firstValue != 0.0) (changeValue / kotlin.math.abs(firstValue)) * 100 else 0.0

        return TrendChartData(
            dataPoints = dataPoints,
            minValue = minValue,
            maxValue = maxValue,
            currentValue = currentValue,
            changeValue = changeValue,
            changePercent = changePercent
        )
    }

    private fun calculateAssetRatioData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }
        sortedRecords.last()

        val dataPoints = sortedRecords.map { record ->
            val totalValue = record.stockValue + record.bondValue + record.goldValue + record.cashValue
            val stockRatio = if (totalValue > 0) record.stockValue / totalValue * 100 else 0.0

            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
            cal.timeInMillis = record.createdAt

            ChartDataPoint(
                date = record.createdAt,
                value = stockRatio,
                label = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
            )
        }

        val values = dataPoints.map { it.value }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 0.0
        val currentValue = values.lastOrNull() ?: 0.0
        val firstValue = values.firstOrNull() ?: 0.0
        val changeValue = currentValue - firstValue

        return TrendChartData(
            dataPoints = dataPoints,
            minValue = minValue,
            maxValue = maxValue,
            currentValue = currentValue,
            changeValue = changeValue,
            changePercent = changeValue
        )
    }

    private fun calculateAssetComparisonData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }

        val dataPoints = sortedRecords.map { record ->
            val totalValue = record.totalAssets

            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
            cal.timeInMillis = record.createdAt

            ChartDataPoint(
                date = record.createdAt,
                value = totalValue,
                label = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
            )
        }

        val values = dataPoints.map { it.value }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 0.0
        val currentValue = values.lastOrNull() ?: 0.0
        val firstValue = values.firstOrNull() ?: 0.0
        val changeValue = currentValue - firstValue
        val changePercent = if (firstValue != 0.0) (changeValue / firstValue) * 100 else 0.0

        return TrendChartData(
            dataPoints = dataPoints,
            minValue = minValue,
            maxValue = maxValue,
            currentValue = currentValue,
            changeValue = changeValue,
            changePercent = changePercent
        )
    }
}


