package com.huaying.xstz.ui.assetoverview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

data class CalendarDay(
    val day: Int,
    val isCurrentMonth: Boolean = true,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val hasRecord: Boolean = false,
    val isWeekend: Boolean = false,
    val isHoliday: Boolean = false,
    val holidayName: String? = null
)

@Composable
fun CalendarView(
    selectedDate: Calendar,
    onDateSelected: (Calendar) -> Unit,
    recordedDates: Set<Long> = emptySet(),
    holidayDates: Set<Long> = emptySet(),
    holidayNames: Map<Long, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance(Locale.CHINA).apply { time = selectedDate.time }) }
    
    val today = remember { Calendar.getInstance(Locale.CHINA) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MonthHeader(
                currentMonth = currentMonth,
                today = today,
                onPreviousMonth = {
                    currentMonth = Calendar.getInstance(Locale.CHINA).apply {
                        time = currentMonth.time
                        add(Calendar.MONTH, -1)
                    }
                },
                onNextMonth = {
                    currentMonth = Calendar.getInstance(Locale.CHINA).apply {
                        time = currentMonth.time
                        add(Calendar.MONTH, 1)
                    }
                },
                onTodayClick = {
                    currentMonth = today.clone() as Calendar
                    onDateSelected(today.clone() as Calendar)
                },
                isCurrentMonthToday = isSameMonth(currentMonth, today)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragX += dragAmount
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDragX) > 80) {
                                    currentMonth = Calendar.getInstance(Locale.CHINA).apply {
                                        time = currentMonth.time
                                        if (totalDragX > 0) add(Calendar.MONTH, -1) else add(Calendar.MONTH, 1)
                                    }
                                }
                            }
                        )
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WeekdayHeader()

                    Spacer(modifier = Modifier.height(8.dp))

                    CalendarGrid(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        recordedDates = recordedDates,
                        holidayDates = holidayDates,
                        holidayNames = holidayNames,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    currentMonth: Calendar,
    today: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    isCurrentMonthToday: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            onClick = onPreviousMonth
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上个月",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%d年%02d月".format(
                    Locale.CHINA,
                    currentMonth.get(Calendar.YEAR),
                    currentMonth.get(Calendar.MONTH) + 1
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Surface(
                color = if (isCurrentMonthToday) 
                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                onClick = onTodayClick
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = if (isCurrentMonthToday) "今天已选中" else "回到今天",
                        tint = if (isCurrentMonthToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isCurrentMonthToday) "今天" else "回到今天",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentMonthToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            onClick = onNextMonth
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "下个月",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekdays.forEachIndexed { index, weekday ->
            Text(
                text = weekday,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (index >= 5) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: Calendar,
    selectedDate: Calendar,
    recordedDates: Set<Long>,
    holidayDates: Set<Long>,
    holidayNames: Map<Long, String>,
    onDateSelected: (Calendar) -> Unit
) {
    val calendar = Calendar.getInstance(Locale.CHINA)
    calendar.time = currentMonth.time
    
    val firstDayOfMonth = calendar.clone() as Calendar
    firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
    
    val startDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
    val adjustedStartDay = if (startDayOfWeek == Calendar.SUNDAY) 6 else startDayOfWeek - 2
    
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance(Locale.CHINA)
    
    val days = mutableListOf<CalendarDay>()
    
    for (i in adjustedStartDay downTo 1) {
        val prevMonthDay = firstDayOfMonth.clone() as Calendar
        prevMonthDay.add(Calendar.DAY_OF_MONTH, -i)
        days.add(
            CalendarDay(
                day = prevMonthDay.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false,
                isWeekend = prevMonthDay.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                           prevMonthDay.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            )
        )
    }
    
    for (day in 1..daysInMonth) {
        val dayCalendar = firstDayOfMonth.clone() as Calendar
        dayCalendar.set(Calendar.DAY_OF_MONTH, day)
        
        val dayTimestamp = getDayTimestamp(dayCalendar)
        
        days.add(
            CalendarDay(
                day = day,
                isToday = isSameDay(dayCalendar, today),
                isSelected = isSameDay(dayCalendar, selectedDate),
                hasRecord = recordedDates.contains(dayTimestamp),
                isWeekend = dayCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                           dayCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
                isHoliday = holidayDates.contains(dayTimestamp),
                holidayName = holidayNames[dayTimestamp]
            )
        )
    }
    
    val remainingCells = 42 - days.size
    for (i in 1..remainingCells) {
        val nextMonthDay = firstDayOfMonth.clone() as Calendar
        nextMonthDay.add(Calendar.DAY_OF_MONTH, daysInMonth + i - 1)
        days.add(
            CalendarDay(
                day = nextMonthDay.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false,
                isWeekend = nextMonthDay.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                           nextMonthDay.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            )
        )
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var rowIndex = 0
        while (rowIndex * 7 < days.size) {
            val rowDays = days.subList(rowIndex * 7, minOf((rowIndex + 1) * 7, days.size))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowDays.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        onClick = {
                            if (day.isCurrentMonth) {
                                val clickedCalendar = firstDayOfMonth.clone() as Calendar
                                clickedCalendar.set(Calendar.DAY_OF_MONTH, day.day)
                                onDateSelected(clickedCalendar)
                            }
                        }
                    )
                }
            }
            rowIndex++
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            day.isSelected -> MaterialTheme.colorScheme.primary
            day.isToday && !day.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.day}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = when {
                    day.isSelected || day.isToday -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                color = when {
                    day.isSelected -> MaterialTheme.colorScheme.onPrimary
                    !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    day.isHoliday -> Color(0xFFEF4444)
                    day.isWeekend -> Color(0xFFEF4444).copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 14.sp
            )
            
            when {
                day.isHoliday && day.hasRecord -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "休",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "●",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = if (day.isSelected) MaterialTheme.colorScheme.onPrimary 
                                   else com.huaying.xstz.ui.theme.SuccessGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                day.isHoliday -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "休",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                day.hasRecord -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (day.isSelected) MaterialTheme.colorScheme.onPrimary 
                               else com.huaying.xstz.ui.theme.SuccessGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun getDayTimestamp(calendar: Calendar): Long {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
           cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
}

private fun isSameMonth(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
}
