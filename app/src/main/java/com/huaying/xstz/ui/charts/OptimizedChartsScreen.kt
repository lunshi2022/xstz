package com.huaying.xstz.ui.charts

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.huaying.xstz.R
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.ui.assetoverview.AssetOverviewViewModel
import com.huaying.xstz.ui.assetoverview.CalendarView
import com.huaying.xstz.ui.assetoverview.MergedDailyPnLCard
import com.huaying.xstz.ui.theme.*
import com.huaying.xstz.util.AppConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap

// 缓存 DateTimeFormatter 实例，避免每次 update 回调重复创建
private val DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("MM/dd")

/**
 * 图表数据缓存管理器
 * 使用 LinkedHashMap 实现真正的 LRU 淘汰策略
 */
object ChartDataCache {
    private val cache = object : LinkedHashMap<String, PreparedChartData>(
        AppConstants.MAX_CHART_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreparedChartData>): Boolean {
            return size > AppConstants.MAX_CHART_CACHE_SIZE
        }
    }

    @Synchronized
    fun get(key: String): PreparedChartData? = cache[key]

    @Synchronized
    fun put(key: String, data: PreparedChartData) {
        cache[key] = data
    }

    @Synchronized
    fun clear() = cache.clear()

    fun getCacheKey(timeRange: TimeRange, data: List<DailyAssetData>): String {
        return "${timeRange.name}_${data.size}"
    }
}

/**
 * 优化的图表ViewModel
 * 采用后台计算 + 增量更新策略
 */
class OptimizedChartsViewModel(
    private val repository: FundRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChartsUiState>(ChartsUiState.Loading)
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    // 用于骨架屏显示的数据流，初始值非null确保首帧就显示骨架屏
    private val _skeletonData = MutableStateFlow<SkeletonData?>(SkeletonData(TimeRange.WEEK, true))
    val skeletonData: StateFlow<SkeletonData?> = _skeletonData.asStateFlow()

    private var calculationJob: Job? = null

    data class SkeletonData(
        val timeRange: TimeRange,
        val hasData: Boolean
    )

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

    private fun loadData(range: TimeRange, showPrincipal: Boolean) {
        calculationJob?.cancel()

        calculationJob = viewModelScope.launch {
            _skeletonData.value = SkeletonData(range, true)

            if (_uiState.value !is ChartsUiState.Success) {
                _uiState.value = ChartsUiState.Loading
            }

            combine(
                repository.getDailyAssetDataFlow(),
                repository.getAllFunds()
            ) { dailyData, funds ->
                Pair(dailyData, funds)
            }.collect { (dailyData, funds) ->
                val cacheKey = ChartDataCache.getCacheKey(range, dailyData)
                val cachedData = ChartDataCache.get(cacheKey)

                if (dailyData.isEmpty()) {
                    _uiState.value = ChartsUiState.Success(
                        timeRange = range,
                        data = emptyList(),
                        showPrincipal = showPrincipal,
                        funds = funds
                    )
                    _skeletonData.value = null
                    return@collect
                }

                // 如果有缓存，先显示缓存数据（秒开体验）
                if (cachedData != null) {
                    _uiState.value = ChartsUiState.Success(
                        timeRange = range,
                        data = cachedData.sortedData,
                        showPrincipal = showPrincipal,
                        funds = funds,
                        preparedData = cachedData
                    )
                    _skeletonData.value = null
                }

                // 后台计算最新数据
                val preparedData = withContext(Dispatchers.Default) {
                    calculateChartData(dailyData, range)
                }

                ChartDataCache.put(cacheKey, preparedData)

                _uiState.value = ChartsUiState.Success(
                    timeRange = range,
                    data = preparedData.sortedData,
                    showPrincipal = showPrincipal,
                    funds = funds,
                    preparedData = preparedData
                )
                _skeletonData.value = null
            }
        }
    }

    /**
     * 在后台线程计算图表数据，包含 Y 轴范围预计算
     */
    private fun calculateChartData(
        dailyData: List<DailyAssetData>,
        range: TimeRange
    ): PreparedChartData {
        val endDate = LocalDate.now()
        val startDate = when (range) {
            TimeRange.WEEK -> endDate.minusWeeks(1)
            TimeRange.MONTH -> endDate.minusMonths(1)
            TimeRange.YEAR -> endDate.minusYears(1)
        }

        val filteredData = dailyData.filter {
            !it.date.isBefore(startDate) && !it.date.isAfter(endDate)
        }.sortedBy { it.date }

        if (filteredData.isEmpty()) {
            return PreparedChartData(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList())
        }

        // 数据聚合优化：如果数据点过多，进行采样
        val sampledData = if (filteredData.size > 100) {
            sampleData(filteredData, 100)
        } else {
            filteredData
        }

        // 计算收益率条目
        val returnEntries = ArrayList<Entry>(sampledData.size)
        sampledData.forEachIndexed { index, item ->
            returnEntries.add(Entry(index.toFloat(), (item.returnRate * 100).toFloat()))
        }

        // 计算资产配置条目
        val assetPriority = listOf("股票", "债券", "商品", "现金")
        val allocationEntries = HashMap<String, List<Entry>>()
        val dailyTotals = sampledData.map { it.totalAsset }

        assetPriority.indices.forEach { i ->
            val assetName = assetPriority[i]
            val entries = ArrayList<Entry>(sampledData.size)

            sampledData.forEachIndexed { index, item ->
                val total = dailyTotals[index]
                val cumulativeValue = when (i) {
                    0 -> item.stockValue
                    1 -> item.stockValue + item.bondValue
                    2 -> item.stockValue + item.bondValue + item.goldValue
                    3 -> item.totalAsset
                    else -> 0.0
                }
                val percent = if (total > 0) (cumulativeValue / total * 100).toFloat() else 0f
                entries.add(Entry(index.toFloat(), percent))
            }
            allocationEntries[assetName] = entries
        }

        // 计算资产和本金条目
        val assetEntries = ArrayList<Entry>(sampledData.size)
        val principalEntries = ArrayList<Entry>(sampledData.size)

        sampledData.forEachIndexed { index, item ->
            assetEntries.add(Entry(index.toFloat(), item.totalAsset.toFloat()))
            principalEntries.add(Entry(index.toFloat(), item.principal.toFloat()))
        }

        return PreparedChartData(
            sortedData = sampledData,
            returnEntries = returnEntries,
            allocationEntries = allocationEntries,
            assetEntries = assetEntries,
            principalEntries = principalEntries
        )
    }

    /**
     * 数据采样：减少数据点数量，提升渲染性能
     */
    private fun sampleData(data: List<DailyAssetData>, targetSize: Int): List<DailyAssetData> {
        if (data.size <= targetSize) return data

        val step = data.size.toFloat() / targetSize
        val result = ArrayList<DailyAssetData>(targetSize)

        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceIn(0, data.size - 1)
            result.add(data[index])
        }

        if (result.last() != data.last()) {
            result.add(data.last())
        }

        return result
    }

    override fun onCleared() {
        super.onCleared()
        calculationJob?.cancel()
    }
}

/**
 * 骨架屏组件
 */
@Composable
fun ChartSkeletonScreen(
    darkTheme: Boolean = false
) {
    val backgroundColor = if (darkTheme) DarkBackground else LightBackground
    if (darkTheme) DarkSurface else LightSurface
    val shimmerColor = if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by shimmerAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投资组合分析",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (darkTheme) DarkTextPrimary else LightTextPrimary
                )
            }
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )

            Spacer(modifier = Modifier.height(16.dp))

            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )

            Spacer(modifier = Modifier.height(16.dp))

            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )

            Spacer(modifier = Modifier.height(16.dp))

            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )
        }
    }
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    shimmerColor: Color,
    shimmerTranslate: Float
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(shimmerTranslate - 200f, 0f),
                        end = androidx.compose.ui.geometry.Offset(shimmerTranslate, 0f)
                    )
                )
        )
    }
}

/**
 * 优化的图表主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizedChartsScreen(
    repository: FundRepository,
    preferenceManager: PreferenceManager,
    darkTheme: Boolean = false,
    viewModel: OptimizedChartsViewModel = viewModel(
        factory = OptimizedChartsViewModelFactory(repository, preferenceManager)
    ),
    assetOverviewViewModel: AssetOverviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val skeletonData by viewModel.skeletonData.collectAsState()

    val showSkeleton = skeletonData != null && uiState is ChartsUiState.Loading

    if (showSkeleton) {
        ChartSkeletonScreen(darkTheme = darkTheme)
    } else {
        ChartsContent(
            uiState = uiState,
            darkTheme = darkTheme,
            onTimeRangeSelected = viewModel::setTimeRange,
            onTogglePrincipal = viewModel::togglePrincipal,
            assetOverviewViewModel = assetOverviewViewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsContent(
    uiState: ChartsUiState,
    darkTheme: Boolean,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onTogglePrincipal: (Boolean) -> Unit,
    assetOverviewViewModel: AssetOverviewViewModel
) {
    val isDarkMode = darkTheme

    Scaffold(
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投资组合分析",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is ChartsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
            }
            is ChartsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}")
                }
            }
            is ChartsUiState.Success -> {
                SuccessContent(
                    state = state,
                    paddingValues = paddingValues,
                    onTimeRangeSelected = onTimeRangeSelected,
                    onTogglePrincipal = onTogglePrincipal,
                    darkTheme = isDarkMode,
                    assetOverviewViewModel = assetOverviewViewModel
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    state: ChartsUiState.Success,
    paddingValues: PaddingValues,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onTogglePrincipal: (Boolean) -> Unit,
    darkTheme: Boolean,
    assetOverviewViewModel: AssetOverviewViewModel
) {
    var clearTrigger by remember { mutableLongStateOf(0L) }
    var currentChartType by rememberSaveable { mutableStateOf(ChartType.RETURN) }

    val preparedData = state.preparedData

    var holidays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var workdays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }

    val selectedDate by assetOverviewViewModel.selectedDate.collectAsState()
    val dailyPnLData by assetOverviewViewModel.dailyPnLData.collectAsState()

    var recordedDates by remember { mutableStateOf(emptySet<Long>()) }
    var holidayDates by remember { mutableStateOf(emptySet<Long>()) }
    var holidayNames by remember { mutableStateOf(emptyMap<Long, String>()) }

    // 从 DailyAssetData 计算每日收益率，用于日历热力图
    val dailyReturnRates = remember(state.data) {
        val rates = mutableMapOf<Long, Double>()
        val sorted = state.data.sortedBy { it.date }
        for (i in sorted.indices) {
            val current = sorted[i]
            val dailyRate = if (i > 0) {
                val prev = sorted[i - 1]
                ((current.returnRate - prev.returnRate) * 100) // 转为百分比
            } else {
                0.0
            }
            val cal = java.util.Calendar.getInstance(java.util.Locale.CHINA)
            cal.set(current.date.year, current.date.monthValue - 1, current.date.dayOfMonth, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            rates[cal.timeInMillis] = dailyRate
        }
        rates
    }

    LaunchedEffect(state.timeRange) {
        clearTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(Unit) {
        val year = LocalDate.now().year
        holidays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinHolidaysForCalendar(year)
        workdays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinWorkdaysForCalendar(year)

        recordedDates = assetOverviewViewModel.getRecordedDates()
        val (hDates, hNames) = assetOverviewViewModel.getHolidayDatesForCurrentYear()
        holidayDates = hDates
        holidayNames = hNames

        assetOverviewViewModel.selectDate(selectedDate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    clearTrigger = System.currentTimeMillis()
                })
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (preparedData == null || preparedData.sortedData.isEmpty()) {
                item {
                    EmptyChartState()
                }
            } else {
                item {
                    OptimizedChartSection(
                        chartType = currentChartType,
                        preparedData = preparedData,
                        showPrincipal = state.showPrincipal,
                        onTogglePrincipal = onTogglePrincipal,
                        clearTrigger = clearTrigger,
                        darkTheme = darkTheme,
                        timeRange = state.timeRange,
                        onTimeRangeSelected = onTimeRangeSelected,
                        onChartTypeSelected = {
                            currentChartType = it
                            clearTrigger = System.currentTimeMillis()
                        }
                    )
                }
            }

            item {
                CalendarView(
                    selectedDate = selectedDate,
                    onDateSelected = { date ->
                        assetOverviewViewModel.selectDate(date)
                    },
                    recordedDates = recordedDates,
                    holidayDates = holidayDates,
                    holidayNames = holidayNames,
                    dailyReturnRates = dailyReturnRates
                )
            }

            item {
                MergedDailyPnLCard(
                    dailyPnLData = dailyPnLData,
                    viewModel = assetOverviewViewModel,
                    isPrivacyMode = false,
                    onFundClick = { }
                )
            }
        }
    }
}

@Composable
private fun EmptyChartState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请在首页点击\"记录净值\"以生成数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OptimizedChartSection(
    chartType: ChartType,
    preparedData: PreparedChartData,
    showPrincipal: Boolean,
    onTogglePrincipal: (Boolean) -> Unit,
    clearTrigger: Long,
    darkTheme: Boolean,
    timeRange: TimeRange,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onChartTypeSelected: (ChartType) -> Unit
) {
    val title = when (chartType) {
        ChartType.RETURN -> "累计收益率趋势"
        ChartType.ALLOCATION -> "资产占比趋势"
        ChartType.PERSPECTIVE -> "总资产与投入本金对比趋势"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OptimizedTimeRangeSelector(
                selectedRange = timeRange,
                onRangeSelected = onTimeRangeSelected,
                isDarkMode = darkTheme
            )

            OptimizedChartTypeSelector(
                selectedType = chartType,
                onTypeSelected = onChartTypeSelected,
                isDarkMode = darkTheme
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 图表切换动画
            AnimatedContent(
                targetState = chartType,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(150))
                },
                label = "chartTransition"
            ) { targetChartType ->
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    when (targetChartType) {
                        ChartType.RETURN -> OptimizedReturnChart(
                            preparedData = preparedData,
                            clearTrigger = clearTrigger
                        )
                        ChartType.ALLOCATION -> OptimizedAllocationChart(
                            preparedData = preparedData,
                            clearTrigger = clearTrigger
                        )
                        ChartType.PERSPECTIVE -> OptimizedPerspectiveChart(
                            preparedData = preparedData,
                            showPrincipal = showPrincipal,
                            onTogglePrincipal = onTogglePrincipal,
                            clearTrigger = clearTrigger
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptimizedTimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeRange.values().forEach { range ->
            val isSelected = range == selectedRange
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) BrandBlue else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onRangeSelected(range) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (range) {
                        TimeRange.WEEK -> "近1周"
                        TimeRange.MONTH -> "近1月"
                        TimeRange.YEAR -> "今年以来"
                    },
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun OptimizedChartTypeSelector(
    selectedType: ChartType,
    onTypeSelected: (ChartType) -> Unit,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ChartType.values().forEach { type ->
            val isSelected = type == selectedType
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) BrandBlue else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTypeSelected(type) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (type) {
                        ChartType.RETURN -> "收益率"
                        ChartType.ALLOCATION -> "资产占比"
                        ChartType.PERSPECTIVE -> "资产对比"
                    },
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ==================== 图表组件 ====================

@Composable
private fun OptimizedReturnChart(
    preparedData: PreparedChartData,
    clearTrigger: Long
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("return") }
    }
    LaunchedEffect(preparedData.sortedData) { marker.setData(preparedData.sortedData) }

    val gradientDrawable = remember(primaryColor) {
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                Color(primaryColor).copy(alpha = 0.0f).toArgb()
            )
        )
    }

    val chartData = remember(preparedData.returnEntries, primaryColor, gradientDrawable) {
        val dataSet = LineDataSet(preparedData.returnEntries, "累计收益率").apply {
            color = primaryColor
            setDrawCircles(false)
            setDrawCircleHole(false)
            lineWidth = 2.0f
            setDrawValues(false)
            setDrawFilled(true)
            fillDrawable = gradientDrawable
            mode = LineDataSet.Mode.LINEAR
            highLightColor = highLightColorValue
            highlightLineWidth = 1.2f
            enableDashedHighlightLine(6f, 6f, 0f)
        }
        LineData(dataSet)
    }

    // 预计算 Y 轴范围
    val yAxisConfig = remember(preparedData.returnEntries) {
        if (preparedData.returnEntries.isEmpty()) {
            YAxisConfig(0f, 1f)
        } else {
            val yMin = preparedData.returnEntries.minOf { it.y }
            val yMax = preparedData.returnEntries.maxOf { it.y }
            val rawRange = yMax - yMin
            val padding = if (rawRange < 0.1f) 1f else rawRange * 0.2f
            var min = yMin - padding
            var max = yMax + padding
            if (min > 0) min = 0f
            if (max < 0) max = 0f
            YAxisConfig(min, max)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 累计收益率信息
        val latestItem = preparedData.sortedData.lastOrNull()
        val earliestItem = preparedData.sortedData.firstOrNull()
        val latestReturn = latestItem?.returnRate ?: 0.0
        val intervalReturn = latestReturn - (earliestItem?.returnRate ?: 0.0)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "累计收益率: %.2f%%".format(latestReturn * 100),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            val intervalColor = if (intervalReturn >= 0) Color(0xFFD32F2F) else Color(0xFF388E3C)
            Text(
                text = "(区间: %+.2f%%)".format(intervalReturn * 100),
                style = MaterialTheme.typography.bodyMedium,
                color = intervalColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary))
                Spacer(modifier = Modifier.width(4.dp))
                Text("累计收益率", style = MaterialTheme.typography.bodySmall)
            }
        }

        UnifiedLineChart(
            modifier = Modifier.fillMaxWidth().weight(1f),
            data = preparedData.sortedData,
            chartData = chartData,
            marker = marker,
            clearTrigger = clearTrigger,
            yAxisFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
            },
            animate = !hasAnimated,
            onAnimationDone = { hasAnimated = true },
            yAxisConfig = yAxisConfig
        )
    }
}

@Composable
private fun OptimizedAllocationChart(
    preparedData: PreparedChartData,
    clearTrigger: Long
) {
    val context = LocalContext.current
    val assetColors = mapOf(
        "股票" to getColorForAssetType(AssetType.STOCK).toArgb(),
        "债券" to getColorForAssetType(AssetType.BOND).toArgb(),
        "商品" to getColorForAssetType(AssetType.COMMODITY).toArgb(),
        "现金" to getColorForAssetType(AssetType.CASH).toArgb()
    )
    val assetComposeColors = mapOf(
        "股票" to getColorForAssetType(AssetType.STOCK),
        "债券" to getColorForAssetType(AssetType.BOND),
        "商品" to getColorForAssetType(AssetType.COMMODITY),
        "现金" to getColorForAssetType(AssetType.CASH)
    )
    val separatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    // 聚焦交互状态
    var focusedAsset by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clearTrigger) { focusedAsset = null }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("percent") }
    }
    LaunchedEffect(preparedData.sortedData) { marker.setData(preparedData.sortedData) }

    val chartData = remember(
        preparedData.allocationEntries, focusedAsset, assetColors, separatorColor, surfaceColor
    ) {
        if (preparedData.allocationEntries.isEmpty()) return@remember LineData()

        val assetPriority = listOf("股票", "债券", "商品", "现金")
        val dataSets = assetPriority.indices.reversed().map { i ->
            val currentAssetName = assetPriority[i]
            val currentAssetColorInt = assetColors[currentAssetName] ?: Color.Black.toArgb()
            val isFocused = focusedAsset == currentAssetName
            val isDimmed = focusedAsset != null && !isFocused
            val entries = preparedData.allocationEntries[currentAssetName] ?: emptyList()

            LineDataSet(entries, currentAssetName).apply {
                setDrawFilled(true)
                if (isDimmed) {
                    val transparentColor = Color(currentAssetColorInt).copy(alpha = 0.2f).toArgb()
                    fillColor = ColorUtils.compositeColors(transparentColor, surfaceColor.toArgb())
                    fillAlpha = 255
                } else {
                    fillColor = currentAssetColorInt
                    fillAlpha = 230
                }
                color = separatorColor
                lineWidth = 1f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = highLightColorValue
                highlightLineWidth = 1.2f
                enableDashedHighlightLine(6f, 6f, 0f)
            }
        }
        LineData(dataSets as List<ILineDataSet>)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 图例 - 支持点击聚焦
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("股票", "债券", "商品", "现金").forEach { name ->
                val color = assetComposeColors[name] ?: Color.Black
                val isFocused = focusedAsset == name
                val isDimmed = focusedAsset != null && !isFocused

                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusedAsset = if (isFocused) null else name }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color.copy(alpha = if (isDimmed) 0.3f else 1f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDimmed) 0.3f else 1f),
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        UnifiedLineChart(
            modifier = Modifier.fillMaxWidth().weight(1f),
            data = preparedData.sortedData,
            chartData = chartData,
            marker = marker,
            clearTrigger = clearTrigger,
            yAxisFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
            },
            animate = !hasAnimated,
            onAnimationDone = { hasAnimated = true },
            yAxisConfig = YAxisConfig(0f, 100f, labelCount = 6)
        )
    }
}

@Composable
private fun OptimizedPerspectiveChart(
    preparedData: PreparedChartData,
    showPrincipal: Boolean,
    onTogglePrincipal: (Boolean) -> Unit,
    clearTrigger: Long
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val principalColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("comparison") }
    }
    LaunchedEffect(preparedData.sortedData) { marker.setData(preparedData.sortedData) }

    val gradientDrawable = remember(primaryColor) {
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                Color(primaryColor).copy(alpha = 0.0f).toArgb()
            )
        )
    }

    val chartData = remember(
        preparedData.assetEntries,
        preparedData.principalEntries,
        showPrincipal,
        primaryColor,
        principalColor,
        gradientDrawable
    ) {
        if (preparedData.assetEntries.isEmpty()) return@remember LineData()

        val assetDataSet = LineDataSet(preparedData.assetEntries, "总资产").apply {
            color = primaryColor
            setDrawCircles(false)
            setDrawCircleHole(false)
            lineWidth = 2.0f
            setDrawValues(false)
            setDrawFilled(true)
            fillDrawable = gradientDrawable
            mode = LineDataSet.Mode.LINEAR
            highLightColor = highLightColorValue
            highlightLineWidth = 1.2f
            enableDashedHighlightLine(6f, 6f, 0f)
        }

        val dataSets = mutableListOf<ILineDataSet>(assetDataSet)

        if (showPrincipal) {
            val pDataSet = LineDataSet(preparedData.principalEntries, "投入本金").apply {
                color = Color(principalColor).copy(alpha = 0.8f).toArgb()
                setDrawCircles(false)
                lineWidth = 2.0f
                setDrawValues(false)
                enableDashedLine(6f, 6f, 0f)
                mode = LineDataSet.Mode.LINEAR
                highLightColor = highLightColorValue
                highlightLineWidth = 1.2f
                enableDashedHighlightLine(6f, 6f, 0f)
            }
            dataSets.add(pDataSet)
        }
        LineData(dataSets)
    }

    // 预计算 Y 轴范围
    val yAxisConfig = remember(preparedData.sortedData, showPrincipal) {
        if (preparedData.sortedData.isEmpty()) {
            YAxisConfig(0f, 1000f)
        } else {
            var yMin = preparedData.sortedData.minOf { it.totalAsset }.toFloat()
            var yMax = preparedData.sortedData.maxOf { it.totalAsset }.toFloat()
            if (showPrincipal) {
                yMin = minOf(yMin, preparedData.sortedData.minOf { it.principal }.toFloat())
                yMax = maxOf(yMax, preparedData.sortedData.maxOf { it.principal }.toFloat())
            }
            val rawRange = yMax - yMin
            val padding = if (rawRange < 100f) 500f else rawRange * 0.2f
            YAxisConfig((yMin - padding).coerceAtLeast(0f), yMax + padding)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 图例 + 本金开关
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary))
                Spacer(modifier = Modifier.width(4.dp))
                Text("总资产", style = MaterialTheme.typography.bodySmall)
            }
            if (showPrincipal) {
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.tertiary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("投入本金", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示本金", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = showPrincipal,
                    onCheckedChange = onTogglePrincipal,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        UnifiedLineChart(
            modifier = Modifier.fillMaxWidth().weight(1f),
            data = preparedData.sortedData,
            chartData = chartData,
            marker = marker,
            clearTrigger = clearTrigger,
            animate = !hasAnimated,
            onAnimationDone = { hasAnimated = true },
            yAxisConfig = yAxisConfig
        )
    }
}

// ==================== 统一的 LineChart 组件 ====================

/**
 * Y 轴配置，在数据预处理阶段计算，避免每次 update 重复遍历
 */
private data class YAxisConfig(
    val min: Float,
    val max: Float,
    val labelCount: Int = 6
)

/**
 * 统一的 LineChart 组件，合并了原 BaseLineChart 和 OptimizedBaseLineChart
 * - 增加数据变更检测，避免无效 invalidate()
 * - 缓存 DateTimeFormatter 实例
 * - Y 轴范围预计算
 */
@Composable
private fun UnifiedLineChart(
    modifier: Modifier = Modifier,
    data: List<DailyAssetData>,
    chartData: LineData,
    marker: CustomMarkerView,
    clearTrigger: Long = 0L,
    yAxisFormatter: ValueFormatter? = null,
    xAxisLabelCount: Int = 5,
    animate: Boolean = false,
    onAnimationDone: () -> Unit = {},
    yAxisConfig: YAxisConfig = YAxisConfig(0f, 100f)
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f).toArgb()

    // 用于数据变更检测
    var lastDataSignature by remember { mutableStateOf("") }
    val currentSignature = remember(chartData, yAxisConfig, axisColor, gridColor) {
        "${chartData.entryCount}_${yAxisConfig.min}_${yAxisConfig.max}_${axisColor}_${gridColor}"
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(false)
                setDrawGridBackground(false)
                setHardwareAccelerationEnabled(true)

                onChartGestureListener = object : OnChartGestureListener {
                    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                        if (lastPerformedGesture == ChartTouchListener.ChartGesture.LONG_PRESS) {
                            highlightValue(null)
                        }
                    }
                    override fun onChartLongPressed(me: MotionEvent?) {}
                    override fun onChartDoubleTapped(me: MotionEvent?) {}
                    override fun onChartSingleTapped(me: MotionEvent?) {
                        me?.let {
                            val h = getHighlightByTouchPoint(it.x, it.y)
                            if (h == null) {
                                highlightValue(null)
                            } else {
                                marker.resetAutoHideTimer()
                            }
                        }
                    }
                    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
                    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
                }

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.textColor = axisColor
                xAxis.gridColor = gridColor
                xAxis.granularity = 1f
                xAxis.labelCount = xAxisLabelCount

                axisRight.isEnabled = false
                axisLeft.textColor = axisColor
                axisLeft.gridColor = gridColor
                axisLeft.gridLineWidth = 1.2f
                axisLeft.enableGridDashedLine(6f, 6f, 0f)
                axisLeft.setDrawZeroLine(true)
                axisLeft.zeroLineColor = gridColor
                axisLeft.zeroLineWidth = 1.2f
                axisLeft.setLabelCount(yAxisConfig.labelCount, false)

                isSaveEnabled = false
            }
        },
        update = { chart ->
            // 清除高亮
            val lastTrigger = chart.tag as? Long ?: 0L
            if (clearTrigger > lastTrigger) {
                chart.highlightValue(null)
                chart.tag = clearTrigger
            }

            // 设置 Marker
            if (chart.marker != marker) {
                marker.chartView = chart
                chart.marker = marker
            }

            // 仅在数据变化时更新
            val needsUpdate = currentSignature != lastDataSignature
            if (needsUpdate) {
                lastDataSignature = currentSignature

                // 使用预计算的 Y 轴范围
                chart.axisLeft.axisMinimum = yAxisConfig.min
                chart.axisLeft.axisMaximum = yAxisConfig.max

                chart.data = chartData

                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in data.indices) {
                            data[index].date.format(DATE_FORMATTER_SHORT)
                        } else ""
                    }
                }

                if (yAxisFormatter != null) {
                    chart.axisLeft.valueFormatter = yAxisFormatter
                }

                chart.invalidate()
            }

            if (animate && chartData.entryCount > 0) {
                chart.animateX(600)
                onAnimationDone()
            }
        }
    )
}

// ==================== ViewModel 工厂 ====================

class OptimizedChartsViewModelFactory(
    private val repository: FundRepository,
    private val preferenceManager: PreferenceManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OptimizedChartsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OptimizedChartsViewModel(repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Switch 缩放扩展
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)
