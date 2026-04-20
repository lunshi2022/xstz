package com.huaying.xstz.ui.charts

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 图表数据缓存管理器
 * 参考：支付宝、微信等应用的缓存策略
 */
object ChartDataCache {
    private val cache = ConcurrentHashMap<String, PreparedChartData>()
    private const val MAX_CACHE_SIZE = 10
    
    fun get(key: String): PreparedChartData? = cache[key]
    
    fun put(key: String, data: PreparedChartData) {
        if (cache.size >= MAX_CACHE_SIZE) {
            // LRU策略：移除最早的条目
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
        cache[key] = data
    }
    
    fun clear() = cache.clear()
    
    private fun generateKey(timeRange: TimeRange, dataSize: Int): String {
        return "${timeRange.name}_$dataSize"
    }
    
    fun getCacheKey(timeRange: TimeRange, data: List<DailyAssetData>): String {
        return generateKey(timeRange, data.size)
    }
}

// PreparedChartData 定义已移至 ChartsViewModel.kt

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
    
    // 用于骨架屏显示的数据流
    private val _skeletonData = MutableStateFlow<SkeletonData?>(null)
    val skeletonData: StateFlow<SkeletonData?> = _skeletonData.asStateFlow()
    
    // 后台计算任务
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
            // 先显示骨架屏
            _skeletonData.value = SkeletonData(range, true)
            
            // 如果当前没有成功状态，显示loading
            if (_uiState.value !is ChartsUiState.Success) {
                _uiState.value = ChartsUiState.Loading
            }
            
            combine(
                repository.getDailyAssetDataFlow(),
                repository.getAllFunds()
            ) { dailyData, funds ->
                Pair(dailyData, funds)
            }.collect { (dailyData, funds) ->
                // 检查缓存
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
                
                // 缓存结果
                ChartDataCache.put(cacheKey, preparedData)
                
                // 更新UI
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
     * 在后台线程计算图表数据
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
        
        // 过滤数据
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
                val cumulativeValue = when(i) {
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
     * 参考：股票软件的K线采样算法
     */
    private fun sampleData(data: List<DailyAssetData>, targetSize: Int): List<DailyAssetData> {
        if (data.size <= targetSize) return data
        
        val step = data.size.toFloat() / targetSize
        val result = ArrayList<DailyAssetData>(targetSize)
        
        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceIn(0, data.size - 1)
            result.add(data[index])
        }
        
        // 确保包含最后一个数据点
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

// ChartsUiState 定义已移至 ChartsViewModel.kt

/**
 * 骨架屏组件
 * 参考：支付宝、京东等应用的骨架屏设计
 */
@Composable
fun ChartSkeletonScreen(
    darkTheme: Boolean = false
) {
    val backgroundColor = if (darkTheme) DarkBackground else LightBackground
    val surfaceColor = if (darkTheme) DarkSurface else LightSurface
    val shimmerColor = if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
    
    // 骨架屏闪光动画
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
            
            // 时间选择器骨架
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 图表类型标签骨架
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主图表骨架
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shimmerColor = shimmerColor,
                shimmerTranslate = shimmerTranslate
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 日历热力图骨架
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
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
        // 闪光效果
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor,
                            Color.Transparent
                        ),
                        start = androidx.compose.ui.geometry.Offset(
                            shimmerTranslate - 200f,
                            0f
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            shimmerTranslate,
                            0f
                        )
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
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val skeletonData by viewModel.skeletonData.collectAsState()
    
    // 判断是否显示骨架屏
    val showSkeleton = skeletonData != null && uiState is ChartsUiState.Loading
    
    if (showSkeleton) {
        // 显示骨架屏，提供即时反馈
        ChartSkeletonScreen(darkTheme = darkTheme)
    } else {
        // 显示实际内容
        ChartsContent(
            uiState = uiState,
            darkTheme = darkTheme,
            onTimeRangeSelected = viewModel::setTimeRange,
            onTogglePrincipal = viewModel::togglePrincipal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsContent(
    uiState: ChartsUiState,
    darkTheme: Boolean,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onTogglePrincipal: (Boolean) -> Unit
) {
    val isDarkMode = darkTheme
    
    Scaffold(
        modifier = Modifier.graphicsLayer {
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        },
        topBar = {
            // 使用主题背景色半透明，与页面背景协调
            val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
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
                    darkTheme = isDarkMode
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
    darkTheme: Boolean
) {
    var clearTrigger by remember { mutableLongStateOf(0L) }
    var currentChartType by rememberSaveable { mutableStateOf(ChartType.RETURN) }
    
    // 使用预处理的数据
    val preparedData = state.preparedData
    
    // 加载节假日数据
    var holidays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var workdays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    
    LaunchedEffect(state.timeRange) {
        clearTrigger = System.currentTimeMillis()
    }
    
    LaunchedEffect(Unit) {
        val year = LocalDate.now().year
        holidays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinHolidaysForCalendar(year)
        workdays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinWorkdaysForCalendar(year)
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
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = 140.dp
            )
        ) {
            // 时间范围选择器
            item {
                TimeRangeSelector(
                    selected = state.timeRange,
                    onSelect = onTimeRangeSelected,
                    darkTheme = darkTheme
                )
            }
            
            // 图表类型标签
            item {
                ChartTypeTabs(
                    currentType = currentChartType,
                    onTypeSelected = { 
                        currentChartType = it
                        clearTrigger = System.currentTimeMillis()
                    }
                )
            }
            
            if (preparedData == null || preparedData.sortedData.isEmpty()) {
                item {
                    EmptyChartState()
                }
            } else {
                // 主图表区域
                item {
                    OptimizedChartSection(
                        chartType = currentChartType,
                        preparedData = preparedData,
                        showPrincipal = state.showPrincipal,
                        onTogglePrincipal = onTogglePrincipal,
                        clearTrigger = clearTrigger,
                        darkTheme = darkTheme
                    )
                }
                
                // 日历热力图
                item {
                    LazyCalendarHeatmap(
                        data = state.data,
                        darkTheme = darkTheme,
                        holidays = holidays,
                        workdays = workdays
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartTypeTabs(
    currentType: ChartType,
    onTypeSelected: (ChartType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ChartType.values().forEach { chartType ->
            val isSelected = currentType == chartType
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onTypeSelected(chartType) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (chartType) {
                        ChartType.RETURN -> "收益率"
                        ChartType.ALLOCATION -> "资产占比"
                        ChartType.PERSPECTIVE -> "资产对比"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    darkTheme: Boolean
) {
    val title = when (chartType) {
        ChartType.RETURN -> "累计收益率趋势"
        ChartType.ALLOCATION -> "资产占比趋势"
        ChartType.PERSPECTIVE -> "总资产与投入本金对比趋势"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (chartType == ChartType.PERSPECTIVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("显示本金", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = showPrincipal,
                            onCheckedChange = onTogglePrincipal,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                when (chartType) {
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
                        clearTrigger = clearTrigger
                    )
                }
            }
        }
    }
}

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
    
    OptimizedBaseLineChart(
        data = preparedData.sortedData,
        chartData = chartData,
        marker = marker,
        clearTrigger = clearTrigger,
        yAxisFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        },
        animate = !hasAnimated,
        onAnimationDone = { hasAnimated = true },
        updateAxis = { chart ->
            if (preparedData.returnEntries.isNotEmpty()) {
                val yMin = preparedData.returnEntries.minOf { it.y }
                val yMax = preparedData.returnEntries.maxOf { it.y }
                val rawRange = yMax - yMin
                val padding = if (rawRange < 0.1f) 1f else rawRange * 0.2f
                var min = yMin - padding
                var max = yMax + padding
                if (min > 0) min = 0f
                if (max < 0) max = 0f
                chart.axisLeft.axisMinimum = min
                chart.axisLeft.axisMaximum = max
            }
        }
    )
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
    val separatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).toArgb()
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    
    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("percent") }
    }
    LaunchedEffect(preparedData.sortedData) { marker.setData(preparedData.sortedData) }
    
    val chartData = remember(preparedData.allocationEntries, assetColors, separatorColor) {
        if (preparedData.allocationEntries.isEmpty()) return@remember LineData()
        
        val assetPriority = listOf("股票", "债券", "商品", "现金")
        val dataSets = assetPriority.indices.reversed().map { i ->
            val currentAssetName = assetPriority[i]
            val currentAssetColorInt = assetColors[currentAssetName] ?: Color.Black.toArgb()
            val entries = preparedData.allocationEntries[currentAssetName] ?: emptyList()
            
            LineDataSet(entries, currentAssetName).apply {
                setDrawFilled(true)
                fillColor = currentAssetColorInt
                fillAlpha = 230
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
    
    OptimizedBaseLineChart(
        data = preparedData.sortedData,
        chartData = chartData,
        marker = marker,
        clearTrigger = clearTrigger,
        yAxisFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
        },
        animate = !hasAnimated,
        onAnimationDone = { hasAnimated = true },
        updateAxis = { chart ->
            chart.axisLeft.axisMaximum = 100f
            chart.axisLeft.axisMinimum = 0f
            chart.axisLeft.setLabelCount(6, true)
        }
    )
}

@Composable
private fun OptimizedPerspectiveChart(
    preparedData: PreparedChartData,
    showPrincipal: Boolean,
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
    
    OptimizedBaseLineChart(
        data = preparedData.sortedData,
        chartData = chartData,
        marker = marker,
        clearTrigger = clearTrigger,
        animate = !hasAnimated,
        onAnimationDone = { hasAnimated = true },
        updateAxis = { chart ->
            if (preparedData.sortedData.isNotEmpty()) {
                var yMin = preparedData.sortedData.minOf { it.totalAsset }.toFloat()
                var yMax = preparedData.sortedData.maxOf { it.totalAsset }.toFloat()
                if (showPrincipal) {
                    yMin = minOf(yMin, preparedData.sortedData.minOf { it.principal }.toFloat())
                    yMax = maxOf(yMax, preparedData.sortedData.maxOf { it.principal }.toFloat())
                }
                val rawRange = yMax - yMin
                val padding = if (rawRange < 100f) 500f else rawRange * 0.2f
                chart.axisLeft.axisMinimum = (yMin - padding).coerceAtLeast(0f)
                chart.axisLeft.axisMaximum = yMax + padding
            }
        }
    )
}

@Composable
private fun OptimizedBaseLineChart(
    modifier: Modifier = Modifier,
    data: List<DailyAssetData>,
    chartData: LineData,
    marker: CustomMarkerView,
    clearTrigger: Long = 0L,
    yAxisFormatter: ValueFormatter? = null,
    xAxisLabelCount: Int = 5,
    yAxisLabelCount: Int = 6,
    animate: Boolean = false,
    onAnimationDone: () -> Unit = {},
    updateAxis: (LineChart) -> Unit = {}
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f).toArgb()
    
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
                
                // 性能优化：禁用不必要的渲染
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
                axisLeft.setLabelCount(yAxisLabelCount, false)
                
                isSaveEnabled = false
            }
        },
        update = { chart ->
            val lastTrigger = chart.tag as? Long ?: 0L
            if (clearTrigger > lastTrigger) {
                chart.highlightValue(null)
                chart.tag = clearTrigger
            }
            
            if (chart.marker != marker) {
                marker.chartView = chart
                chart.marker = marker
            }
            
            updateAxis(chart)
            chart.data = chartData
            
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index in data.indices) {
                        data[index].date.format(DateTimeFormatter.ofPattern("MM/dd"))
                    } else ""
                }
            }
            
            if (yAxisFormatter != null) {
                chart.axisLeft.valueFormatter = yAxisFormatter
            }
            
            chart.invalidate()
            
            if (animate && chartData.entryCount > 0) {
                chart.animateX(600)
                onAnimationDone()
            }
        }
    )
}

// 完整的日历热力图组件，支持月份切换和点击展开详情
@Composable
private fun LazyCalendarHeatmap(
    data: List<DailyAssetData>,
    darkTheme: Boolean,
    holidays: Set<LocalDate> = emptySet(),
    workdays: Set<LocalDate> = emptySet()
) {
    val calendarData = data.associateBy { it.date }
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(today) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    
    val monthStart = currentMonth.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    // 一周从周一开始（ISO标准: 周一=1, 周日=7）
    val startDate = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
    val endDate = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())
    
    // 计算月度统计
    val monthData = data.filter { 
        it.date.month == currentMonth.month && it.date.year == currentMonth.year 
    }
    val profitDays = monthData.count { it.returnRate > 0 }
    val lossDays = monthData.count { it.returnRate < 0 }
    val monthReturn = monthData.lastOrNull()?.returnRate?.minus(monthData.firstOrNull()?.returnRate ?: 0.0) ?: 0.0
    
    // 计算全局最大盈亏用于热力图颜色映射
    val maxProfit = data.filter { it.returnRate > 0 }.maxOfOrNull { it.returnRate } ?: 0.0
    val maxLoss = data.filter { it.returnRate < 0 }.minOfOrNull { it.returnRate } ?: 0.0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 月份标题和导航
            CalendarHeader(
                currentMonth = currentMonth,
                onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                onToday = { currentMonth = LocalDate.now() },
                profitDays = profitDays,
                lossDays = lossDays,
                monthReturn = monthReturn
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 星期标题（周一到周日）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 日历网格 with swipe gesture and animation
            val swipeState = remember { androidx.compose.animation.core.Animatable(0f) }
            val scope = rememberCoroutineScope()
            var isAnimating by remember { mutableStateOf(false) }
            
            // 月份切换动画 - 使用简单的淡入淡出
            val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isAnimating) 0.5f else 1f,
                animationSpec = tween(durationMillis = 200),
                label = "calendarAlpha"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        swipeState.value > 80f -> {
                                            isAnimating = true
                                            currentMonth = currentMonth.minusMonths(1)
                                            kotlinx.coroutines.delay(100)
                                            isAnimating = false
                                        }
                                        swipeState.value < -80f -> {
                                            isAnimating = true
                                            currentMonth = currentMonth.plusMonths(1)
                                            kotlinx.coroutines.delay(100)
                                            isAnimating = false
                                        }
                                    }
                                    swipeState.animateTo(0f, animationSpec = tween(300))
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val newValue = (swipeState.value + dragAmount).coerceIn(-200f, 200f)
                                    swipeState.snapTo(newValue)
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        translationX = swipeState.value * 0.5f
                        alpha = animatedAlpha
                    }
            ) {
                Column {
                    var current = startDate
                    while (current.isBefore(endDate) || current.isEqual(endDate)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(7) {
                                val date = current
                                val dailyData = calendarData[date]
                                val isSelected = selectedDate == date
                                val isHoliday = holidays.contains(date)
                                val isWorkday = workdays.contains(date)
                                
                                CalendarDayCell(
                                    date = date,
                                    currentMonth = currentMonth,
                                    today = today,
                                    dailyData = dailyData,
                                    isSelected = isSelected,
                                    isHoliday = isHoliday,
                                    isWorkday = isWorkday,
                                    maxProfit = maxProfit,
                                    maxLoss = maxLoss,
                                    onClick = { 
                                        selectedDate = if (selectedDate == date) null else date
                                    }
                                )
                                
                                current = current.plusDays(1)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            // 选中日期详情面板
            val selectedData = selectedDate?.let { calendarData[it] }
            AnimatedVisibility(
                visible = selectedData != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                selectedData?.let { data ->
                    DayDetailPanel(
                        date = selectedDate!!,
                        data = data
                    )
                }
            }
        }
    }
}

// 以下函数复用 ChartsScreen.kt 中的定义：
// CalendarHeader, CalendarDayCell, CalendarLegend, DayDetailPanel, 
// AssetDetailRow, SummaryInfoItem, formatMoney

// ViewModel工厂
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

// 使用已定义的 scale 扩展函数