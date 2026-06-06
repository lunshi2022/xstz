package com.huaying.xstz.ui.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogger
import kotlinx.coroutines.flow.collectLatest
import org.threeten.bp.LocalDate

/**
 * 日历屏幕
 * 整合现代化日历组件和详情面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: FundRepository,
    darkTheme: Boolean = false
) {
    var dailyData by remember { mutableStateOf<List<DailyAssetData>>(emptyList()) }
    var holidays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var workdays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedData by remember { mutableStateOf<DailyAssetData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("日历分析")
    }

    // 加载数据
    LaunchedEffect(Unit) {
        // 加载节假日数据
        val year = LocalDate.now().year
        holidays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinHolidaysForCalendar(year)
        workdays = com.huaying.xstz.data.repository.HolidayRepository.getBuiltinWorkdaysForCalendar(year)

        // 加载资产数据
        repository.getDailyAssetDataFlow().collectLatest { data ->
            dailyData = data
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "收益日历",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            dailyData.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        CalendarEmptyState()
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = 100.dp
                    )
                ) {
                    // 日历组件
                    item {
                        ModernCalendar(
                            data = dailyData,
                            holidays = holidays,
                            workdays = workdays,
                            onDateSelected = { date, data ->
                                selectedDate = date
                                selectedData = data
                                if (data != null) {
                                    OperationLogger.logChartInteraction(
                                        "日历日期选择",
                                        date.toString()
                                    )
                                }
                            }
                        )
                    }

                    // 选中日期详情
                    item {
                        AnimatedVisibility(
                            visible = selectedDate != null && selectedData != null,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                        ) {
                            if (selectedDate != null && selectedData != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CalendarDayDetailPanel(
                                    date = selectedDate!!,
                                    data = selectedData!!
                                )
                            }
                        }
                    }

                    // 空状态提示（未选中日期时）
                    item {
                        AnimatedVisibility(
                            visible = selectedDate == null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "点击日历日期查看详情",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
