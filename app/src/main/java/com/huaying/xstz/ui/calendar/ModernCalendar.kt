package com.huaying.xstz.ui.calendar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.ui.theme.DangerRed
import com.huaying.xstz.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 现代化日历组件
 * 参考设计：支付宝账单日历、雪球盈亏日历、同花顺资产日历
 */

/**
 * 日历配置
 */
object CalendarConfig {
    // 盈利颜色渐变 - 从浅红到深红（A股习惯：涨红跌绿）
    val ProfitColors = listOf(
        Color(0xFFFFEBEE),  // 最浅
        Color(0xFFFFCDD2),
        Color(0xFFEF9A9A),
        Color(0xFFE57373),
        Color(0xFFEF5350),
        Color(0xFFF44336),
        Color(0xFFE53935),
        Color(0xFFD32F2F),
        Color(0xFFC62828),
        Color(0xFFB71C1C)   // 最深
    )

    // 亏损颜色渐变 - 从浅绿到深绿（A股习惯：涨红跌绿）
    val LossColors = listOf(
        Color(0xFFE8F5E9),  // 最浅
        Color(0xFFC8E6C9),
        Color(0xFFA5D6A7),
        Color(0xFF81C784),
        Color(0xFF66BB6A),
        Color(0xFF4CAF50),
        Color(0xFF43A047),
        Color(0xFF388E3C),
        Color(0xFF2E7D32),
        Color(0xFF1B5E20)   // 最深
    )

    // 中性颜色
    val NeutralColor = Color(0xFFF5F5F5)
    val NeutralTextColor = Color(0xFF9E9E9E)

    // 选中状态颜色
    val SelectedBorderColor = Color(0xFF1890FF)
    val TodayIndicatorColor = Color(0xFF1890FF)

    // 节假日标记颜色
    val HolidayColor = Color(0xFF52C41A)
    val WorkdayColor = Color(0xFFFA8C16)

    // 动画时长
    const val ANIMATION_DURATION = 300
    const val SWIPE_THRESHOLD = 80f

    // 单元格大小
    val CellSize = 48.dp
    val CellCornerRadius = 12.dp
}

/**
 * 主日历组件
 */
@Composable
fun ModernCalendar(
    data: List<DailyAssetData>,
    funds: List<Fund> = emptyList(),
    holidays: Set<LocalDate> = emptySet(),
    workdays: Set<LocalDate> = emptySet(),
    onDateSelected: (LocalDate, DailyAssetData?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val calendarData = data.associateBy { it.date }
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(today) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    // 是否显示"今"按钮（当选中其他日期时显示）
    val showTodayButton = selectedDate != null && selectedDate != today

    // 计算全局最大盈亏用于热力图颜色映射
    val maxProfit = data.filter { it.returnRate > 0 }.maxOfOrNull { it.returnRate } ?: 0.0
    val maxLoss = data.filter { it.returnRate < 0 }.minOfOrNull { it.returnRate } ?: 0.0

    // 计算月度统计
    val monthData = data.filter {
        it.date.month == currentMonth.month && it.date.year == currentMonth.year
    }
    val profitDays = monthData.count { it.returnRate > 0 }
    val lossDays = monthData.count { it.returnRate < 0 }
    // 只有当该月有数据时才计算收益率
    val monthReturn = if (monthData.size >= 2) {
        monthData.last().returnRate - monthData.first().returnRate
    } else null  // 无数据时返回 null，不显示标签

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部：月份导航和统计
            CalendarHeader(
                currentMonth = currentMonth,
                monthReturn = monthReturn,
                profitDays = profitDays,
                lossDays = lossDays,
                showTodayButton = showTodayButton,
                onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                onToday = {
                    // 如果当前不在今天所在月份，先切换月份
                    if (currentMonth.month != today.month || currentMonth.year != today.year) {
                        currentMonth = today
                    }
                    selectedDate = today
                    // 触发回调更新详情面板
                    onDateSelected(today, calendarData[today])
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 星期标题
            WeekdayHeader()

            Spacer(modifier = Modifier.height(12.dp))

            // 日历网格，支持滑动切换
            CalendarGrid(
                currentMonth = currentMonth,
                today = today,
                selectedDate = selectedDate,
                calendarData = calendarData,
                holidays = holidays,
                workdays = workdays,
                maxProfit = maxProfit,
                maxLoss = maxLoss,
                onDateSelected = { date ->
                    selectedDate = if (selectedDate == date) null else date
                    onDateSelected(date, calendarData[date])
                },
                onMonthChange = { direction ->
                    currentMonth = if (direction > 0) {
                        currentMonth.plusMonths(1)
                    } else {
                        currentMonth.minusMonths(1)
                    }
                }
            )

            // 选中日期详情面板（集成在日历卡片内）
            // 使用 animateContentSize 处理卡片高度变化，详情面板只使用淡入淡出
            if (selectedDate != null && calendarData[selectedDate] != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                selectedDate?.let { date ->
                    calendarData[date]?.let { data ->
                        CalendarDayDetailContent(
                            date = date,
                            data = data,
                            funds = funds
                        )
                    }
                }
            }
        }
    }
}

/**
 * 日历头部组件
 */
@Composable
private fun CalendarHeader(
    currentMonth: LocalDate,
    monthReturn: Double?,
    profitDays: Int,
    lossDays: Int,
    showTodayButton: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    Column {
        // 第一行：月份导航和收益率
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：月份和月度收益率标签组合
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 月份显示（可点击切换月份）
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPreviousMonth
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月", Locale.CHINA)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 月度收益率标签 - 紧跟在月份后面，有数据时才显示
                if (monthReturn != null) {
                    val isProfit = monthReturn >= 0
                    val returnColor = if (isProfit) DangerRed else SuccessGreen
                    Surface(
                        color = returnColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = returnColor.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${if (isProfit) "+" else ""}${String.format("%.2f%%", monthReturn * 100)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = returnColor
                            )
                            Text(
                                text = "月收益",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = returnColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // "今"按钮 - 带放大/缩小动画，只在选中其他日期时显示
            // 高度与月收益标签保持一致（约36dp）
            AnimatedVisibility(
                visible = showTodayButton,
                enter = scaleIn(
                    animationSpec = tween(300),
                    initialScale = 0f
                ) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(
                    animationSpec = tween(200),
                    targetScale = 0f
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToday
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 第二行：盈亏天数统计
        if (profitDays > 0 || lossDays > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 盈利天数 - 红色
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(DangerRed, CircleShape)
                    )
                    Text(
                        text = "盈利 $profitDays 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 亏损天数 - 绿色
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(SuccessGreen, CircleShape)
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

/**
 * 星期标题
 */
@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, day ->
            val isWeekend = index >= 5
            Box(
                modifier = Modifier.size(CalendarConfig.CellSize),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isWeekend) {
                        DangerRed.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 日历网格，支持滑动切换月份
 * 简化自然的滑动效果
 */
@Composable
private fun CalendarGrid(
    currentMonth: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate?,
    calendarData: Map<LocalDate, DailyAssetData>,
    holidays: Set<LocalDate>,
    workdays: Set<LocalDate>,
    maxProfit: Double,
    maxLoss: Double,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    // 计算当前月的日期范围
    val monthStart = currentMonth.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    val startDate = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
    val endDate = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())

    // 滑动动画状态
    val swipeState = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(currentMonth) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                swipeState.value > 50f -> {
                                    // 向右滑动，切换到上一月
                                    onMonthChange(-1)
                                }
                                swipeState.value < -50f -> {
                                    // 向左滑动，切换到下一月
                                    onMonthChange(1)
                                }
                            }
                            // 回弹动画
                            swipeState.animateTo(0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // 限制滑动范围，增加阻力感
                            val resistance = 0.6f
                            val newValue = (swipeState.value + dragAmount * resistance).coerceIn(-150f, 150f)
                            swipeState.snapTo(newValue)
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = swipeState.value
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
                        val isHoliday = holidays.contains(date)
                        val isWorkday = workdays.contains(date)

                        CalendarDayCell(
                            date = date,
                            currentMonth = currentMonth,
                            today = today,
                            dailyData = dailyData,
                            isSelected = selectedDate == date,
                            isHoliday = isHoliday,
                            isWorkday = isWorkday,
                            maxProfit = maxProfit,
                            maxLoss = maxLoss,
                            onClick = {
                                // 点击时重置滑动状态，避免抖动
                                scope.launch {
                                    if (swipeState.value != 0f) {
                                        swipeState.snapTo(0f)
                                    }
                                }
                                onDateSelected(date)
                            }
                        )

                        current = current.plusDays(1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 日历日期单元格
 */
@Composable
private fun CalendarDayCell(
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

    // 计算盈亏金额
    val dailyProfit = dailyData?.let {
        val prevAsset = it.totalAsset / (1 + it.returnRate)
        it.totalAsset - prevAsset
    } ?: 0.0

    // 根据盈亏计算颜色 - A股习惯：盈红亏绿
    val (backgroundColor, textColor, amountColor) = when {
        !isCurrentMonth -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.0f),
            Color.Transparent
        )
        dailyData == null -> Triple(
            CalendarConfig.NeutralColor,
            MaterialTheme.colorScheme.onSurface,
            Color.Transparent
        )
        returnRate > 0 -> {
            // 盈利 - 红色渐变
            val intensity = if (maxProfit > 0) (returnRate / maxProfit).coerceIn(0.0, 1.0) else 0.5
            val colorIndex = (intensity * (CalendarConfig.ProfitColors.size - 1)).toInt()
                .coerceIn(0, CalendarConfig.ProfitColors.size - 1)
            val bgColor = CalendarConfig.ProfitColors[colorIndex]
            // 根据背景色深度调整文字颜色
            val textColorValue = if (intensity > 0.6) Color.White else Color(0xFFB71C1C)
            val amountColorValue = if (intensity > 0.6) Color.White.copy(alpha = 0.9f) else Color(0xFFC62828)
            Triple(
                bgColor,
                textColorValue,
                amountColorValue
            )
        }
        returnRate < 0 -> {
            // 亏损 - 绿色渐变
            val intensity = if (maxLoss < 0) (abs(returnRate) / abs(maxLoss)).coerceIn(0.0, 1.0) else 0.5
            val colorIndex = (intensity * (CalendarConfig.LossColors.size - 1)).toInt()
                .coerceIn(0, CalendarConfig.LossColors.size - 1)
            val bgColor = CalendarConfig.LossColors[colorIndex]
            // 根据背景色深度调整文字颜色
            val textColorValue = if (intensity > 0.6) Color.White else Color(0xFF1B5E20)
            val amountColorValue = if (intensity > 0.6) Color.White.copy(alpha = 0.9f) else Color(0xFF2E7D32)
            Triple(
                bgColor,
                textColorValue,
                amountColorValue
            )
        }
        else -> Triple(
            CalendarConfig.NeutralColor,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 选中状态的边框颜色
    val borderColor = when {
        isSelected -> CalendarConfig.SelectedBorderColor
        isToday -> CalendarConfig.TodayIndicatorColor.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    // 非当前月显示空白
    if (!isCurrentMonth) {
        Box(modifier = Modifier.size(CalendarConfig.CellSize))
        return
    }

    Box(
        modifier = Modifier
            .size(CalendarConfig.CellSize)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(CalendarConfig.CellCornerRadius)
            )
            .border(
                width = if (isSelected) 2.dp else if (isToday) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(CalendarConfig.CellCornerRadius)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 日期数字
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp
            )

            // 盈亏金额 - 显示完整数字
            if (dailyData != null && returnRate != 0.0) {
                Text(
                    text = String.format("%.0f", dailyProfit),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = amountColor,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1
                )
            }
        }

        // 节假日标记 - 改为"休"/"班"文字显示
        // 调休工作日优先显示"班"
        if (isWorkday) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .background(
                        color = CalendarConfig.WorkdayColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "班",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = CalendarConfig.WorkdayColor
                )
            }
        } else if (isHoliday) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .background(
                        color = CalendarConfig.HolidayColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "休",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = CalendarConfig.HolidayColor
                )
            }
        }

        // 今天标记点
        if (isToday && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .background(
                        color = CalendarConfig.TodayIndicatorColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 日历日期详情内容（集成在卡片内，无外层Card）
 * 显示日期、当日盈亏和基金明细
 */
@Composable
private fun CalendarDayDetailContent(
    date: LocalDate,
    data: DailyAssetData,
    funds: List<Fund>
) {
    val dailyProfit = data.totalAsset - data.principal
    // A股习惯：盈红亏绿
    val profitColor = if (dailyProfit >= 0) DangerRed else SuccessGreen

    Column {
        // 头部：日期和当日盈亏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日期信息
            Column {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 当日盈亏金额
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (dailyProfit >= 0) "+" else ""}${String.format("%.2f", dailyProfit)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = profitColor
                )
                Text(
                    text = "当日盈亏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 基金明细列表 - 按资产类型分组，每组内按市值降序排列（与首页一致）
        if (funds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "基金明细",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 按资产类型分组
            val groupedFunds = funds.groupBy { it.type }
            // 按AssetType枚举顺序排序类型
            val sortedTypes = com.huaying.xstz.data.entity.AssetType.values()
                .filter { groupedFunds.containsKey(it) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedTypes.forEach { type ->
                    val fundsInType = groupedFunds[type] ?: emptyList()
                    // 按市值降序排列（与首页一致）
                    val sortedFunds = fundsInType.sortedByDescending { it.holdingQuantity * it.currentPrice }

                    // 该类型下的基金列表
                    sortedFunds.forEach { fund ->
                        // 计算该基金当日盈亏（简化计算：使用涨跌幅估算）
                        val fundValue = fund.holdingQuantity * fund.currentPrice
                        val fundDailyProfit = fundValue * fund.changePercent / 100
                        val fundProfitColor = if (fundDailyProfit >= 0) DangerRed else SuccessGreen

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 基金名称 + 类型标签
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = fund.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                // 类型标签 - 显示在基金名称后面
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = type.toDisplayName(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // 当日盈亏
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (fundDailyProfit >= 0) "+" else ""}${String.format("%.2f", fundDailyProfit)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = fundProfitColor
                                )
                                Text(
                                    text = "${if (fund.changePercent >= 0) "+" else ""}${String.format("%.2f%%", fund.changePercent)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fundProfitColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
