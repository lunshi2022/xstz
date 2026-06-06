package com.huaying.xstz.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huaying.xstz.data.entity.OperationLog
import com.huaying.xstz.data.entity.OperationType
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.data.entity.toIcon
import com.huaying.xstz.data.repository.OperationLogRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationLogScreen(
    operationLogRepository: OperationLogRepository,
    darkTheme: Boolean,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val operationLogs by operationLogRepository.getAllOperationLogs().collectAsState(initial = emptyList())
    val logCount by operationLogRepository.getOperationLogCount().collectAsState(initial = 0)
    
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<OperationType?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    
    val filteredLogs = remember(operationLogs, selectedFilter) {
        if (selectedFilter == null) {
            operationLogs
        } else {
            operationLogs.filter { it.operationType == selectedFilter }
        }
    }
    
    val groupedLogs = remember(filteredLogs) {
        filteredLogs.groupBy { log ->
            val date = Date(log.createdAt)
            val today = Calendar.getInstance()
            val logDate = Calendar.getInstance().apply { time = date }
            
            when {
                isSameDay(today, logDate) -> "今天"
                isYesterday(today, logDate) -> "昨天"
                isSameWeek(today, logDate) -> "本周"
                isSameMonth(today, logDate) -> "本月"
                else -> {
                    val sdf = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
                    sdf.format(date)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("操作记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 统计信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        title = "总记录数",
                        value = logCount.toString()
                    )
                    StatItem(
                        title = "显示记录",
                        value = filteredLogs.size.toString()
                    )
                    StatItem(
                        title = "筛选条件",
                        value = selectedFilter?.toDisplayName() ?: "全部"
                    )
                }
            }
            
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "📝",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无操作记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedFilter != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { selectedFilter = null }) {
                                Text("清除筛选")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedLogs.forEach { (groupTitle, logs) ->
                        item {
                            Text(
                                text = groupTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(logs) { log ->
                            OperationLogItem(log = log)
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // 筛选对话框
    if (showFilterDialog) {
        FilterDialog(
            currentFilter = selectedFilter,
            onFilterSelected = { filter ->
                selectedFilter = filter
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
    
    // 清空确认对话框
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空所有操作记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            operationLogRepository.deleteAllOperationLogs()
                            showClearConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun StatItem(
    title: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OperationLogItem(log: OperationLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    
    val date = Date(log.createdAt)
    val today = Calendar.getInstance()
    val logDate = Calendar.getInstance().apply { time = date }
    
    val timeText = when {
        isSameDay(today, logDate) -> timeFormat.format(date)
        isYesterday(today, logDate) -> "昨天 ${timeFormat.format(date)}"
        else -> dateFormat.format(date)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getOperationTypeColor(log.operationType).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = log.operationType.toIcon(),
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 内容
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (log.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (log.targetName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.targetName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterDialog(
    currentFilter: OperationType?,
    onFilterSelected: (OperationType?) -> Unit,
    onDismiss: () -> Unit
) {
    // 按类别分组的操作类型
    val groupedTypes = remember {
        mapOf(
            "基金操作" to listOf(
                OperationType.ADD_FUND,
                OperationType.DELETE_FUND,
                OperationType.EDIT_FUND
            ),
            "交易操作" to listOf(
                OperationType.BUY,
                OperationType.SELL,
                OperationType.REBALANCE,
                OperationType.ADD_CASH,
                OperationType.WITHDRAW_CASH,
                OperationType.EDIT_HOLDING,
                OperationType.EDIT_COST,
                OperationType.EDIT_TARGET_RATIO,
                OperationType.EDIT_ASSET_TYPE
            ),
            "数据操作" to listOf(
                OperationType.CLEAR_DATA,
                OperationType.EXPORT_DATA,
                OperationType.IMPORT_DATA
            ),
            "设置操作" to listOf(
                OperationType.SETTINGS_CHANGE
            ),
            "导航操作" to listOf(
                OperationType.PAGE_VIEW,
                OperationType.PAGE_SWITCH
            ),
            "交互操作" to listOf(
                OperationType.BUTTON_CLICK,
                OperationType.ITEM_CLICK,
                OperationType.CHART_INTERACTION
            ),
            "系统操作" to listOf(
                OperationType.SEARCH,
                OperationType.FILTER,
                OperationType.SORT,
                OperationType.REFRESH,
                OperationType.EXPAND_COLLAPSE
            ),
            "其他" to listOf(
                OperationType.OTHER
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // 标题
                Text(
                    text = "筛选操作类型",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 全部选项
                FilterOptionCard(
                    text = "全部",
                    icon = "🔍",
                    selected = currentFilter == null,
                    onClick = { onFilterSelected(null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 分隔线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 分组的操作类型列表
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedTypes.forEach { (groupName, types) ->
                        // 类别标题
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        // 该类别下的操作类型
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            types.forEach { type ->
                                FilterOptionCard(
                                    text = type.toDisplayName(),
                                    icon = type.toIcon(),
                                    selected = currentFilter == type,
                                    onClick = { onFilterSelected(type) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "关闭",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterOptionCard(
    text: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标背景
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun getOperationTypeColor(type: OperationType): Color {
    return when (type) {
        // 基金操作 - 蓝色系
        OperationType.ADD_FUND -> Color(0xFF1976D2)
        OperationType.DELETE_FUND -> Color(0xFFF44336)
        OperationType.EDIT_FUND -> Color(0xFF2196F3)

        // 交易操作 - 橙/紫色系
        OperationType.BUY -> Color(0xFF4CAF50)
        OperationType.SELL -> Color(0xFFF44336)
        OperationType.REBALANCE -> Color(0xFF9C27B0)
        OperationType.ADD_CASH -> Color(0xFF4CAF50)
        OperationType.WITHDRAW_CASH -> Color(0xFFF44336)
        OperationType.EDIT_HOLDING -> Color(0xFFFF9800)
        OperationType.EDIT_COST -> Color(0xFF795548)
        OperationType.EDIT_TARGET_RATIO -> Color(0xFFE91E63)
        OperationType.EDIT_ASSET_TYPE -> Color(0xFF009688)

        // 数据操作 - 灰/青色系
        OperationType.CLEAR_DATA -> Color(0xFFF44336)
        OperationType.EXPORT_DATA -> Color(0xFF607D8B)
        OperationType.IMPORT_DATA -> Color(0xFF607D8B)

        // 设置操作 - 灰色系
        OperationType.SETTINGS_CHANGE -> Color(0xFF757575)

        // 导航操作 - 青/绿色系
        OperationType.PAGE_VIEW -> Color(0xFF00BCD4)
        OperationType.PAGE_SWITCH -> Color(0xFF009688)

        // 交互操作 - 橙/蓝色系
        OperationType.BUTTON_CLICK -> Color(0xFFFF5722)
        OperationType.ITEM_CLICK -> Color(0xFF3F51B5)
        OperationType.CHART_INTERACTION -> Color(0xFF673AB7)

        // 系统操作 - 靛/蓝色系
        OperationType.SEARCH -> Color(0xFF3F51B5)
        OperationType.FILTER -> Color(0xFF2196F3)
        OperationType.SORT -> Color(0xFF009688)
        OperationType.REFRESH -> Color(0xFF00BCD4)
        OperationType.EXPAND_COLLAPSE -> Color(0xFF795548)

        // 其他
        OperationType.OTHER -> Color(0xFF9E9E9E)
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, other: Calendar): Boolean {
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(yesterday, other)
}

private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
}

private fun isSameMonth(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
}
