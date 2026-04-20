package com.huaying.xstz.ui.charts

import androidx.compose.foundation.layout.IntrinsicSize
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.model.DailyAssetData
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.WeekFields
import java.util.Locale
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.ui.theme.BrandBlue
import com.huaying.xstz.ui.theme.DangerRed
import com.huaying.xstz.ui.theme.SuccessGreen
import com.huaying.xstz.ui.theme.StockColor
import com.huaying.xstz.ui.theme.BondColor
import com.huaying.xstz.ui.theme.GoldColor
import com.huaying.xstz.ui.theme.CashColor
import com.huaying.xstz.ui.theme.DarkBackground
import com.huaying.xstz.ui.theme.DarkSurface
import com.huaying.xstz.ui.theme.DarkTextPrimary
import com.huaying.xstz.ui.theme.DarkTextSecondary
import com.huaying.xstz.ui.theme.LightBackground
import com.huaying.xstz.ui.theme.LightSurface
import com.huaying.xstz.ui.theme.LightTextPrimary
import com.huaying.xstz.ui.theme.LightTextSecondary
import com.huaying.xstz.ui.theme.getColorForAssetType
import com.huaying.xstz.R


enum class ChartType {
    RETURN, ALLOCATION, PERSPECTIVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    repository: FundRepository,
    preferenceManager: com.huaying.xstz.data.PreferenceManager,
    darkTheme: Boolean = false,
    viewModel: ChartsViewModel = viewModel(factory = ChartsViewModelFactory(repository, preferenceManager))
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode = darkTheme

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("图表分析")
    }

    Scaffold(
        modifier = Modifier.graphicsLayer {
            // 启用硬件加速，提升动画性能
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
                        onClick = { /* 拦截点击事件 */ }
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
                ChartsContent(
                    state = state,
                    paddingValues = paddingValues,
                    onTimeRangeSelected = viewModel::setTimeRange,
                    onTogglePrincipal = viewModel::togglePrincipal,
                    darkTheme = isDarkMode
                )
            }
        }
    }
}

@Composable
fun ChartsContent(
    state: ChartsUiState.Success,
    paddingValues: PaddingValues,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onTogglePrincipal: (Boolean) -> Unit,
    darkTheme: Boolean
) {
    var clearTrigger by remember { mutableLongStateOf(0L) }
    var currentChartType by rememberSaveable { mutableStateOf(ChartType.RETURN) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Clear highlight when time range changes
    LaunchedEffect(state.timeRange) {
        clearTrigger = System.currentTimeMillis()
    }
    
    // 加载节假日数据
    var holidays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var workdays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    
    LaunchedEffect(Unit) {
        val year = LocalDate.now().year
        // 使用内置节假日数据
        holidays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinHolidaysForCalendar(year)
        workdays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinWorkdaysForCalendar(year)
    }

    // Pre-sort and prepare all chart data once
    val preparedData = remember(state.data) {
        if (state.data.isEmpty()) null
        else {
            val sorted = state.data.sortedBy { it.date }
            val assetPriority = listOf("股票", "债券", "商品", "现金")
            
            val returnEntries = sorted.mapIndexed { index, item ->
                Entry(index.toFloat(), (item.returnRate * 100).toFloat())
            }
            
            val dailyTotals = sorted.map { it.totalAsset }
            val allocationEntries = assetPriority.indices.map { i ->
                val assetName = assetPriority[i]
                val entries = sorted.mapIndexed { index, item ->
                    val total = dailyTotals[index]
                    val cumulativeValue = (0..i).sumOf { k ->
                        when(assetPriority[k]) {
                            "股票" -> item.stockValue
                            "债券" -> item.bondValue
                            "商品" -> item.goldValue
                            "现金" -> item.cashValue
                            else -> 0.0
                        }
                    }
                    val percent = if (total > 0) (cumulativeValue / total * 100).toFloat() else 0f
                    Entry(index.toFloat(), percent)
                }
                assetName to entries
            }.toMap()
            
            val assetEntries = sorted.mapIndexed { index, item -> 
                Entry(index.toFloat(), item.totalAsset.toFloat()) 
            }
            
            val principalEntries = sorted.mapIndexed { index, item -> 
                Entry(index.toFloat(), item.principal.toFloat()) 
            }

            PreparedData(
                sortedData = sorted,
                returnEntries = returnEntries,
                allocationEntries = allocationEntries,
                assetEntries = assetEntries,
                principalEntries = principalEntries
            )
        }
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
                top = 120.dp, // 从标题栏下方开始
                bottom = 140.dp // 确保最后一个项目可以滚动到导航栏上方完全可见
            )
        ) {
            // 1. Time Range Selector
            item {
                TimeRangeSelector(
                    selected = state.timeRange,
                    onSelect = onTimeRangeSelected,
                    darkTheme = darkTheme
                )
            }

            // 2. Chart Type Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ChartType.values().forEach { chartType ->
                        val isSelected = currentChartType == chartType
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
                                ) {
                                    currentChartType = chartType
                                    clearTrigger = System.currentTimeMillis()
                                    OperationLogger.logChartInteraction(
                                        "切换图表",
                                        when (chartType) {
                                            ChartType.RETURN -> "收益率"
                                            ChartType.ALLOCATION -> "资产占比"
                                            ChartType.PERSPECTIVE -> "资产对比"
                                        }
                                    )
                                }
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

            if (preparedData == null) {
                item {
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
                                text = "请在首页点击“记录净值”以生成数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                val sortedData = preparedData.sortedData
                // 3. Combined Chart Section with Swipe Navigation
                item {
                    val chartTitle = when (currentChartType) {
                        ChartType.RETURN -> "累计收益率趋势"
                        ChartType.ALLOCATION -> "资产占比趋势"
                        ChartType.PERSPECTIVE -> "总资产与投入本金对比趋势"
                    }
                    
                    ChartSection(
                        title = chartTitle,
                        action = {
                            if (currentChartType == ChartType.PERSPECTIVE) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("显示本金", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = state.showPrincipal,
                                        onCheckedChange = onTogglePrincipal,
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            } else {
                                // 为了保持标题行高度一致，添加一个占位符
                                Box(modifier = Modifier.width(100.dp).height(24.dp))
                            }
                        }
                    ) {
                        Column {
                            // 统一高度的容器，确保所有图表的内容高度一致
                            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                                when (currentChartType) {
                                    ChartType.RETURN -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            val latestItem = sortedData.lastOrNull()
                                            val earliestItem = sortedData.firstOrNull()
                                            val latestReturn = latestItem?.returnRate ?: 0.0
                                            val earliestReturn = earliestItem?.returnRate ?: 0.0
                                            val intervalReturn = latestReturn - earliestReturn
                                            
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
                                            
                                            // 图表
                                            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                                            val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                                            val context = androidx.compose.ui.platform.LocalContext.current
                                            var hasAnimated by rememberSaveable { mutableStateOf(false) }

                                            val marker = remember(context) {
                                                CustomMarkerView(context, R.layout.marker_view).apply { setChartType("return") }
                                            }
                                            LaunchedEffect(sortedData) { marker.setData(sortedData) }

                                            val gradientDrawable = remember(primaryColor) {
                                                GradientDrawable(
                                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                                    intArrayOf(
                                                        androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                                                        androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.0f).toArgb()
                                                    )
                                                )
                                            }

                                            val chartData = remember(preparedData.returnEntries, primaryColor, gradientDrawable, highLightColorValue) {
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

                                            BaseLineChart(
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                data = sortedData,
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
                                    }
                                    ChartType.ALLOCATION -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // 图例
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                listOf("股票", "债券", "商品", "现金").forEach { name ->
                                                    val color = when (name) {
                                                        "股票" -> getColorForAssetType(AssetType.STOCK)
                                                        "债券" -> getColorForAssetType(AssetType.BOND)
                                                        "商品" -> getColorForAssetType(AssetType.COMMODITY)
                                                        else -> getColorForAssetType(AssetType.CASH)
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(modifier = Modifier.size(10.dp).background(color))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // 图表
                                            val assetColors = mapOf(
                                                "股票" to getColorForAssetType(AssetType.STOCK).toArgb(),
                                                "债券" to getColorForAssetType(AssetType.BOND).toArgb(),
                                                "商品" to getColorForAssetType(AssetType.COMMODITY).toArgb(),
                                                "现金" to getColorForAssetType(AssetType.CASH).toArgb()
                                            )

                                            val separatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).toArgb()
                                            val surfaceColor = MaterialTheme.colorScheme.surface
                                            val context = androidx.compose.ui.platform.LocalContext.current
                                            val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                                            var hasAnimated by rememberSaveable { mutableStateOf(false) }

                                            val marker = remember(context) {
                                                CustomMarkerView(context, R.layout.marker_view).apply { setChartType("percent") }
                                            }
                                            LaunchedEffect(sortedData) { marker.setData(sortedData) }

                                            val chartData = remember(preparedData.allocationEntries, assetColors, separatorColor, surfaceColor, highLightColorValue) {
                                                val assetPriority = listOf("股票", "债券", "商品", "现金")
                                                if (preparedData.allocationEntries.isEmpty()) return@remember LineData()

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

                                            BaseLineChart(
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                data = sortedData,
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
                                    }
                                    ChartType.PERSPECTIVE -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // 图例 - 固定高度，确保显示/隐藏本金时高度一致
                                            Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("总资产", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    if (state.showPrincipal) {
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.tertiary))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("投入本金", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    } else {
                                                        // 添加占位符，确保高度一致
                                                        Spacer(modifier = Modifier.width(80.dp))
                                                    }
                                                }
                                            }
                                            
                                            // 图表
                                            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                                            val principalColor = MaterialTheme.colorScheme.tertiary.toArgb()
                                            val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                                            val context = androidx.compose.ui.platform.LocalContext.current
                                            var hasAnimated by rememberSaveable { mutableStateOf(false) }

                                            val marker = remember(context) {
                                                CustomMarkerView(context, R.layout.marker_view).apply { setChartType("comparison") }
                                            }
                                            LaunchedEffect(sortedData) { marker.setData(sortedData) }

                                            val gradientDrawable = remember(primaryColor) {
                                                GradientDrawable(
                                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                                    intArrayOf(
                                                        androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                                                        androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.0f).toArgb()
                                                    )
                                                )
                                            }

                                            val chartData = remember(preparedData.assetEntries, preparedData.principalEntries, state.showPrincipal, primaryColor, principalColor, gradientDrawable, highLightColorValue) {
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

                                                if (state.showPrincipal) {
                                                    val pDataSet = LineDataSet(preparedData.principalEntries, "投入本金").apply {
                                                        color = androidx.compose.ui.graphics.Color(principalColor).copy(alpha = 0.8f).toArgb()
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

                                            BaseLineChart(
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                data = sortedData,
                                                chartData = chartData,
                                                marker = marker,
                                                clearTrigger = clearTrigger,
                                                animate = !hasAnimated,
                                                onAnimationDone = { hasAnimated = true },
                                                updateAxis = { chart ->
                                                    if (sortedData.isNotEmpty()) {
                                                        var yMin = sortedData.minOf { it.totalAsset }.toFloat()
                                                        var yMax = sortedData.maxOf { it.totalAsset }.toFloat()
                                                        if (state.showPrincipal) {
                                                            yMin = minOf(yMin, sortedData.minOf { it.principal }.toFloat())
                                                            yMax = maxOf(yMax, sortedData.maxOf { it.principal }.toFloat())
                                                        }
                                                        val rawRange = yMax - yMin
                                                        val padding = if (rawRange < 100f) 500f else rawRange * 0.2f
                                                        chart.axisLeft.axisMinimum = (yMin - padding).coerceAtLeast(0f)
                                                        chart.axisLeft.axisMaximum = yMax + padding
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Profit/Loss Calendar Heatmap
                item {
                    CalendarHeatmapSection(
                        data = state.data,
                        holidays = holidays,
                        workdays = workdays
                    )
                }
            }
        }
    }
}

private data class PreparedData(
    val sortedData: List<DailyAssetData>,
    val returnEntries: List<Entry>,
    val allocationEntries: Map<String, List<Entry>>,
    val assetEntries: List<Entry>,
    val principalEntries: List<Entry>
)

@Composable
fun TimeRangeSelector(selected: TimeRange, onSelect: (TimeRange) -> Unit, darkTheme: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeRange.values().forEach { range ->
            val isSelected = range == selected
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
                    ) {
                        onSelect(range)
                        OperationLogger.logFilter(
                            "时间范围",
                            when (range) {
                                TimeRange.WEEK -> "近1周"
                                TimeRange.MONTH -> "近1月"
                                TimeRange.YEAR -> "今年以来"
                            }
                        )
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (range) {
                        TimeRange.WEEK -> "近1周"
                        TimeRange.MONTH -> "近1月"
                        TimeRange.YEAR -> "今年以来"
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
fun ChartSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
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
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

// ---------------- Charts Implementation ----------------

@Composable
fun BaseLineChart(
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

                onChartGestureListener = object : OnChartGestureListener {
                    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                        // 只有在点击空白区域时才隐藏高亮，滑动时不隐藏
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
                                // 点击空白区域时隐藏高亮
                                highlightValue(null)
                            } else {
                                // 点击数据点时，重置浮窗的自动隐藏计时器
                                if (marker is CustomMarkerView) {
                                    marker.resetAutoHideTimer()
                                }
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
            // Check clear trigger
            val lastTrigger = chart.tag as? Long ?: 0L
            if (clearTrigger > lastTrigger) {
                chart.highlightValue(null)
                chart.tag = clearTrigger
            }

            // Set Marker
            if (chart.marker != marker) {
                marker.chartView = chart
                chart.marker = marker
            }

            updateAxis(chart)

            chart.data = chartData

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    if (index in data.indices) {
                        return data[index].date.format(DateTimeFormatter.ofPattern("MM/dd"))
                    }
                    return ""
                }
            }

            if (yAxisFormatter != null) {
                chart.axisLeft.valueFormatter = yAxisFormatter
            }

            chart.invalidate()

            if (animate && chartData.entryCount > 0) {
                chart.animateX(800)
                onAnimationDone()
            }
        }
    )
}

@Composable
fun ReturnTrendChart(
    data: List<DailyAssetData>,
    entries: List<Entry>,
    clearTrigger: Long = 0L
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("return") }
    }
    LaunchedEffect(data) { marker.setData(data) }

    val gradientDrawable = remember(primaryColor) {
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.0f).toArgb()
            )
        )
    }

    val chartData = remember(entries, primaryColor, gradientDrawable, highLightColorValue) {
        val dataSet = LineDataSet(entries, "累计收益率").apply {
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

    BaseLineChart(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        data = data,
        chartData = chartData,
        marker = marker,
        clearTrigger = clearTrigger,
        yAxisFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        },
        animate = !hasAnimated,
        onAnimationDone = { hasAnimated = true },
        updateAxis = { chart ->
            if (entries.isNotEmpty()) {
                val yMin = entries.minOf { it.y }
                val yMax = entries.maxOf { it.y }
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
fun AssetAllocationChart(
    data: List<DailyAssetData>,
    allocationEntries: Map<String, List<Entry>>,
    clearTrigger: Long = 0L
) {
    val assetColors = mapOf(
        "股票" to getColorForAssetType(AssetType.STOCK).toArgb(),
        "债券" to getColorForAssetType(AssetType.BOND).toArgb(),
        "商品" to getColorForAssetType(AssetType.COMMODITY).toArgb(),
        "现金" to getColorForAssetType(AssetType.CASH).toArgb()
    )

    val separatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val context = androidx.compose.ui.platform.LocalContext.current
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()

    var focusedAsset by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clearTrigger) { focusedAsset = null }
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("percent") }
    }
    LaunchedEffect(data) { marker.setData(data) }

    val chartData = remember(allocationEntries, focusedAsset, assetColors, separatorColor, surfaceColor, highLightColorValue) {
        val assetPriority = listOf("股票", "债券", "商品", "现金")
        if (allocationEntries.isEmpty()) return@remember LineData()

        val dataSets = assetPriority.indices.reversed().map { i ->
            val currentAssetName = assetPriority[i]
            val currentAssetColorInt = assetColors[currentAssetName] ?: Color.Black.toArgb()
            val isFocused = focusedAsset == currentAssetName
            val isDimmed = focusedAsset != null && !isFocused

            val entries = allocationEntries[currentAssetName] ?: emptyList()

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

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("股票", "债券", "商品", "现金").forEach { name ->
                val color = assetColors[name] ?: Color.Black.toArgb()
                val isFocused = focusedAsset == name
                val isDimmed = focusedAsset != null && !isFocused

                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { focusedAsset = if (isFocused) null else name }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).background(Color(color).copy(alpha = if (isDimmed) 0.3f else 1f)))
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

        BaseLineChart(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            data = data,
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
}

@Composable
fun AssetPerspectiveChart(
    data: List<DailyAssetData>,
    assetEntries: List<Entry>,
    principalEntries: List<Entry>,
    showPrincipal: Boolean,
    clearTrigger: Long = 0L
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val principalColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val highLightColorValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    val marker = remember(context) {
        CustomMarkerView(context, R.layout.marker_view).apply { setChartType("comparison") }
    }
    LaunchedEffect(data) { marker.setData(data) }

    val gradientDrawable = remember(primaryColor) {
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.4f).toArgb(),
                androidx.compose.ui.graphics.Color(primaryColor).copy(alpha = 0.0f).toArgb()
            )
        )
    }

    val chartData = remember(assetEntries, principalEntries, showPrincipal, primaryColor, principalColor, gradientDrawable, highLightColorValue) {
        if (assetEntries.isEmpty()) return@remember LineData()

        val assetDataSet = LineDataSet(assetEntries, "总资产").apply {
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
            val pDataSet = LineDataSet(principalEntries, "投入本金").apply {
                color = androidx.compose.ui.graphics.Color(principalColor).copy(alpha = 0.8f).toArgb()
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

    Column {
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
        }

        BaseLineChart(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            data = data,
            chartData = chartData,
            marker = marker,
            clearTrigger = clearTrigger,
            animate = !hasAnimated,
            onAnimationDone = { hasAnimated = true },
            updateAxis = { chart ->
                if (data.isNotEmpty()) {
                    var yMin = data.minOf { it.totalAsset }.toFloat()
                    var yMax = data.maxOf { it.totalAsset }.toFloat()
                    if (showPrincipal) {
                        yMin = minOf(yMin, data.minOf { it.principal }.toFloat())
                        yMax = maxOf(yMax, data.maxOf { it.principal }.toFloat())
                    }
                    val rawRange = yMax - yMin
                    val padding = if (rawRange < 100f) 500f else rawRange * 0.2f
                    chart.axisLeft.axisMinimum = (yMin - padding).coerceAtLeast(0f)
                    chart.axisLeft.axisMaximum = yMax + padding
                }
            }
        )
    }
}

// ---------------- Profit/Loss Calendar Heatmap ----------------

@Composable
fun CalendarHeatmapSection(
    data: List<DailyAssetData>,
    holidays: Set<LocalDate> = emptySet(),
    workdays: Set<LocalDate> = emptySet()
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            CalendarHeatmap(
                data = data,
                holidays = holidays,
                workdays = workdays
            )
        }
    }
}

@Composable
fun CalendarHeatmap(
    data: List<DailyAssetData>,
    holidays: Set<LocalDate> = emptySet(),
    workdays: Set<LocalDate> = emptySet()
) {
    val calendarData = data.associateBy { it.date }
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(today) }
    
    // 计算当前月的日期范围（一周从周一开始）
    val monthStart = currentMonth.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    // 周一=1, 周日=7，计算到周一的天数
    val startDate = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
    val endDate = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())
    
    val weeks = generateWeeks(startDate, endDate)
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    // 用于触发"今"按钮点击动画的状态
    var todayClickTrigger by remember { mutableLongStateOf(0L) }
    // 标记是否是通过"今"按钮触发的月份切换
    var isTodayNavigation by remember { mutableStateOf(false) }
    val selectedData = selectedDate?.let { calendarData[it] }
    
    // 处理"今"按钮点击后的选中逻辑 - 在月份切换完成后执行
    LaunchedEffect(currentMonth, todayClickTrigger) {
        if (todayClickTrigger > 0) {
            // 确保当前显示的是今天所在的月份
            if (currentMonth.month == today.month && currentMonth.year == today.year) {
                selectedDate = today
                isTodayNavigation = false
            }
        }
    }
    
    // 计算月度统计数据
    val monthData = data.filter { 
        it.date.month == currentMonth.month && it.date.year == currentMonth.year 
    }
    val profitDays = monthData.count { it.returnRate > 0 }
    val lossDays = monthData.count { it.returnRate < 0 }
    val monthReturn = monthData.lastOrNull()?.returnRate?.minus(monthData.firstOrNull()?.returnRate ?: 0.0) ?: 0.0
    
    // 计算全局最大盈亏用于热力图颜色映射
    val maxProfit = data.filter { it.returnRate > 0 }.maxOfOrNull { it.returnRate } ?: 0.0
    val maxLoss = data.filter { it.returnRate < 0 }.minOfOrNull { it.returnRate } ?: 0.0
    
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // Header with month navigation and stats
        val today = LocalDate.now()
        CalendarHeader(
            currentMonth = currentMonth,
            onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
            onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
            onToday = { 
                // 无论当前选中什么日期，都切换到今天的月份并选中今天
                isTodayNavigation = true
                currentMonth = today
                todayClickTrigger = System.currentTimeMillis()
            },
            todayClickTrigger = todayClickTrigger,
            profitDays = profitDays,
            lossDays = lossDays,
            monthReturn = monthReturn
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weekday Headers
        WeekdayHeader()
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Calendar Grid with swipe gesture and animation
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
                                        // 向右滑动，切换到上一个月
                                        isAnimating = true
                                        currentMonth = currentMonth.minusMonths(1)
                                        kotlinx.coroutines.delay(100)
                                        isAnimating = false
                                    }
                                    swipeState.value < -80f -> {
                                        // 向左滑动，切换到下一个月
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
                weeks.forEach { week ->
                    CalendarWeekRow(
                        week = week,
                        currentMonth = currentMonth,
                        today = today,
                        calendarData = calendarData,
                        selectedDate = selectedDate,
                        holidays = holidays,
                        workdays = workdays,
                        maxProfit = maxProfit,
                        maxLoss = maxLoss,
                        onDateSelected = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // Detailed Information Panel
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

@Composable
fun CalendarHeader(
    currentMonth: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    todayClickTrigger: Long = 0L,
    profitDays: Int,
    lossDays: Int,
    monthReturn: Double
) {
    Column {
        // Month Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // 月度收益率标签
                if (monthReturn != 0.0) {
                    val returnColor = if (monthReturn >= 0) SuccessGreen else DangerRed
                    Surface(
                        color = returnColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${if (monthReturn >= 0) "+" else ""}${String.format("%.2f%%", monthReturn * 100)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = returnColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // 今天按钮 - 带有点击动画效果
            val todayButtonScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (todayClickTrigger > 0 && System.currentTimeMillis() - todayClickTrigger < 150) 0.85f else 1f,
                animationSpec = tween(durationMillis = 100),
                label = "todayButtonScale"
            )
            val todayButtonAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (todayClickTrigger > 0 && System.currentTimeMillis() - todayClickTrigger < 150) 0.7f else 1f,
                animationSpec = tween(durationMillis = 100),
                label = "todayButtonAlpha"
            )
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = todayButtonScale
                        scaleY = todayButtonScale
                        alpha = todayButtonAlpha
                    }
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onToday
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "今",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 月度统计
        if (profitDays > 0 || lossDays > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SuccessGreen, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "盈利 $profitDays 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(DangerRed, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "亏损 $lossDays 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WeekdayHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 周一到周日
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
}

@Composable
fun CalendarWeekRow(
    week: List<LocalDate>,
    currentMonth: LocalDate,
    today: LocalDate,
    calendarData: Map<LocalDate, DailyAssetData>,
    selectedDate: LocalDate?,
    holidays: Set<LocalDate> = emptySet(),
    workdays: Set<LocalDate> = emptySet(),
    maxProfit: Double = 0.0,
    maxLoss: Double = 0.0,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        week.forEach { date ->
            val isHoliday = holidays.contains(date)
            val isWorkday = workdays.contains(date)
            CalendarDayCell(
                date = date,
                currentMonth = currentMonth,
                today = today,
                dailyData = calendarData[date],
                isSelected = selectedDate == date,
                isHoliday = isHoliday,
                isWorkday = isWorkday,
                maxProfit = maxProfit,
                maxLoss = maxLoss,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
fun CalendarDayCell(
    date: LocalDate,
    currentMonth: LocalDate,
    today: LocalDate,
    dailyData: DailyAssetData?,
    isSelected: Boolean,
    isHoliday: Boolean = false,
    isWorkday: Boolean = false,
    maxProfit: Double = 0.0,
    maxLoss: Double = 0.0,
    onClick: () -> Unit
) {
    val isCurrentMonth = date.month == currentMonth.month && date.year == currentMonth.year
    val isToday = date == today
    val returnRate = dailyData?.returnRate ?: 0.0
    
    // 计算盈亏金额（当日盈亏 = 当日总资产 - 前日总资产，这里用收益率估算）
    val dailyProfit = dailyData?.let {
        val prevAsset = it.totalAsset / (1 + it.returnRate)
        it.totalAsset - prevAsset
    } ?: 0.0
    
    // 热力图颜色计算 - 根据盈亏程度计算颜色深浅
    val (backgroundColor, dateTextColor, amountTextColor) = when {
        dailyData == null -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrentMonth) 0.2f else 0.1f),
            if (isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            Color.Transparent
        )
        returnRate > 0 -> {
            // 盈利热力图 - 暖色调（粉红/橙红）
            // 根据最大盈利计算强度
            val intensity = if (maxProfit > 0) (returnRate / maxProfit).coerceIn(0.0, 1.0) else 0.5
            val bgAlpha = (0.3f + (intensity * 0.6f)).toFloat().coerceIn(0.3f, 0.9f)
            // 基础暖色 #E8AFA8，根据强度调整
            val baseColor = Color(0xFFE8AFA8)
            val intenseColor = Color(0xFFD4847C) // 更深的暖色
            val finalBgColor = androidx.compose.ui.graphics.lerp(baseColor, intenseColor, intensity.toFloat())
            
            Triple(
                finalBgColor.copy(alpha = bgAlpha),
                Color(0xFF8B4545),
                Color(0xFFB85450)
            )
        }
        returnRate < 0 -> {
            // 亏损热力图 - 冷色调（蓝绿/青灰）
            val intensity = if (maxLoss < 0) (kotlin.math.abs(returnRate) / kotlin.math.abs(maxLoss)).coerceIn(0.0, 1.0) else 0.5
            val bgAlpha = (0.3f + (intensity * 0.6f)).toFloat().coerceIn(0.3f, 0.9f)
            // 基础冷色 #8FB8C9，根据强度调整
            val baseColor = Color(0xFF8FB8C9)
            val intenseColor = Color(0xFF6A9AAA) // 更深的冷色
            val finalBgColor = androidx.compose.ui.graphics.lerp(baseColor, intenseColor, intensity.toFloat())
            
            Triple(
                finalBgColor.copy(alpha = bgAlpha),
                Color(0xFF3D5A6A),
                Color(0xFF4A7C8B)
            )
        }
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    // 选中状态的边框颜色
    val borderColor = when {
        isSelected -> Color(0xFFE8AFA8)
        isToday -> Color(0xFFE8AFA8).copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    
    // 正方形格子 - 非当前月显示为空白
    if (!isCurrentMonth) {
        Box(
            modifier = Modifier.size(48.dp)
        )
        return
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 2.dp else if (isToday) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // 日期数字 - 小字体，位置上移
        Text(
            text = date.dayOfMonth.toString(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            color = dateTextColor,
            fontSize = 13.sp
        )
        
        // 盈亏金额 - 位置在底部，显示完整数字
        if (dailyData != null && returnRate != 0.0) {
            val amountText = String.format("%.0f", dailyProfit)
            Text(
                text = amountText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = amountTextColor,
                fontSize = 11.sp
            )
        }
        
        // 右上角：节假日标记（休/班）
        if (isHoliday || isWorkday) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp)
                    .background(
                        color = if (isHoliday) Color(0xFF52C41A).copy(alpha = 0.15f) else Color(0xFFFA8C16).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isHoliday) "休" else "班",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHoliday) Color(0xFF52C41A) else Color(0xFFFA8C16)
                )
            }
        }
    }
}

@Composable
fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 亏损图例 - 冷色调
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 12.dp)
                    .background(Color(0xFF8FB8C9).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            )
            Text(
                text = "亏损",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 盈利图例 - 暖色调
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 12.dp)
                    .background(Color(0xFFE8AFA8).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            )
            Text(
                text = "盈利",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DayDetailPanel(
    date: LocalDate,
    data: DailyAssetData
) {
    val totalProfit = data.totalAsset - data.principal
    val profitColor = if (totalProfit >= 0) SuccessGreen else DangerRed
    val returnRate = data.returnRate
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 日期和总览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("MM月dd日")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("yyyy年 EEEE", Locale.CHINA)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 当日总盈亏
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (totalProfit >= 0) "+" else ""}${formatMoney(totalProfit)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = profitColor
                    )
                    Text(
                        text = "${if (returnRate >= 0) "+" else ""}${String.format("%.2f%%", returnRate * 100)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = profitColor.copy(alpha = 0.8f)
                    )
                }
            }
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // 资产明细
            Text(
                text = "资产明细",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetDetailRow(
                    name = "股票",
                    value = data.stockValue,
                    color = StockColor,
                    totalAsset = data.totalAsset
                )
                AssetDetailRow(
                    name = "债券",
                    value = data.bondValue,
                    color = BondColor,
                    totalAsset = data.totalAsset
                )
                AssetDetailRow(
                    name = "商品",
                    value = data.goldValue,
                    color = GoldColor,
                    totalAsset = data.totalAsset
                )
                AssetDetailRow(
                    name = "现金",
                    value = data.cashValue,
                    color = CashColor,
                    totalAsset = data.totalAsset
                )
            }
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // 汇总信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryInfoItem("总资产", formatMoney(data.totalAsset))
                SummaryInfoItem("本金", formatMoney(data.principal))
            }
        }
    }
}

@Composable
fun AssetDetailRow(
    name: String,
    value: Double,
    color: Color,
    totalAsset: Double
) {
    val ratio = if (totalAsset > 0) (value / totalAsset * 100) else 0.0
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatMoney(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.1f%%", ratio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun SummaryInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AssetProfitItem(name: String, value: Double, profit: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${if (profit >= 0) "+" else ""}${String.format("%.2f", profit)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (profit >= 0) SuccessGreen else DangerRed
        )
    }
}

@Composable
fun AssetDetailItem(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatMoney(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun generateWeeks(startDate: LocalDate, endDate: LocalDate): List<List<LocalDate>> {
    val weeks = mutableListOf<List<LocalDate>>()
    var current = startDate
    
    while (current.isBefore(endDate) || current.isEqual(endDate)) {
        val week = mutableListOf<LocalDate>()
        // 一周从周一开始 (ISO标准: 周一=1, 周日=7)
        val dayOfWeek = current.dayOfWeek.value  // 周一=1, 周二=2, ..., 周日=7
        val daysToMonday = (dayOfWeek - 1).toLong()  // 到周一的天数
        var weekStart = current.minusDays(daysToMonday)
        
        for (i in 0 until 7) {
            week.add(weekStart.plusDays(i.toLong()))
        }
        
        weeks.add(week)
        current = weekStart.plusDays(7)
    }
    
    return weeks
}

@Composable
private fun getHeatmapColor(level: Int): Color {
    return when (level) {
        0 -> DangerRed.copy(alpha = 0.2f)
        1 -> DangerRed.copy(alpha = 0.4f)
        2 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        3 -> SuccessGreen.copy(alpha = 0.4f)
        4 -> SuccessGreen.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
}

@Composable
private fun getHeatmapColorForReturn(returnRate: Double): Color {
    val normalizedReturn = (returnRate * 100).coerceIn(-5.0, 5.0)
    val level = ((normalizedReturn + 5) / 10 * 4).toInt().coerceIn(0, 4)
    return getHeatmapColor(level)
}

fun formatMoney(value: Double): String {
    return "%,.2f".format(value)
}

fun Int.toComposeColor(): Color {
    return Color(this)
}

// Factory for ViewModel
class ChartsViewModelFactory(
    private val repository: FundRepository,
    private val preferenceManager: com.huaying.xstz.data.PreferenceManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChartsViewModel(repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Extensions for scaling Switch
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)
