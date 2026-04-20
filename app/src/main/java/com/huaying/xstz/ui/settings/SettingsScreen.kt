package com.huaying.xstz.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight as FontWeightCompose
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.ThemeMode
import com.huaying.xstz.data.entity.TargetAllocation
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.ui.theme.*
import com.huaying.xstz.ui.theme.ThemeConstants
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferenceManager: PreferenceManager,
    themeMode: Int,
    dynamicColorEnabled: Boolean,
    refreshIntervalSeconds: Int,
    refreshMode: Int,
    privacyModeEnabled: Boolean,
    onBack: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onClearRecords: () -> Unit = {},
    onGenerateTestData: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    onNavigateToOperationLog: () -> Unit = {},
    onCheckUpdate: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isDarkMode = isSystemInDarkTheme()

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("设置")
    }

    // themeMode and dynamicColorEnabled are now passed as parameters to avoid initial value flicker

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
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* 拦截点击事件 */ }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 确保内容完全贴合底部
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = 0.dp,
                    top = 120.dp, // 从标题栏下方开始
                    end = 0.dp,
                    bottom = 140.dp // 确保最后一个项目可以滚动到导航栏上方完全可见
                )
        ) {
            // 常规
            SettingsSection(title = "常规") {
                val refreshOptions = listOf("智能", "3秒", "5秒")
                val currentRefreshIndex = when {
                    refreshMode == 0 -> 0 // Smart
                    refreshIntervalSeconds == 3 -> 1 // Fixed 3s
                    refreshIntervalSeconds == 5 -> 2 // Fixed 5s
                    else -> 0
                }

                var showRefreshInfoDialog by remember { mutableStateOf(false) }

                DropdownSettingItem(
                    title = "行情刷新频率",
                    currentValue = refreshOptions[currentRefreshIndex],
                    options = refreshOptions,
                    onOptionSelected = { index ->
                        scope.launch {
                            val oldValue = refreshOptions[currentRefreshIndex]
                            val newValue = refreshOptions[index]
                            when (index) {
                                0 -> { // Smart
                                    preferenceManager.setRefreshMode(0)
                                    preferenceManager.setRefreshIntervalSeconds(1) // Default interval for smart (1s)
                                }
                                1 -> { // 3s Fixed
                                    preferenceManager.setRefreshMode(1)
                                    preferenceManager.setRefreshIntervalSeconds(3)
                                }
                                2 -> { // 5s Fixed
                                    preferenceManager.setRefreshMode(1)
                                    preferenceManager.setRefreshIntervalSeconds(5)
                                }
                            }
                            OperationLogger.logSettingChange("行情刷新频率", oldValue, newValue)
                        }
                    },
                    onInfoClick = {
                        OperationLogger.logButtonClick("刷新频率说明", "设置")
                        showRefreshInfoDialog = true
                    }
                )

                if (showRefreshInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showRefreshInfoDialog = false },
                        title = { Text("行情刷新说明") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("智能：", fontWeight = FontWeight.Bold)
                                Text("• 仅在交易时间段内（周一至周五 09:15-11:30, 13:00-15:00）开启秒级实时刷新")
                                Text("• 自动识别中国法定节假日和调休安排，在节假日智能不刷新")
                                Text("• 非交易时间保持显示收盘数据")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("3秒/5秒：", fontWeight = FontWeight.Bold)
                                Text("• 无论是否在交易时间，始终按照设定的时间间隔进行自动刷新")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showRefreshInfoDialog = false }) {
                                Text("知道了")
                            }
                        }
                    )
                }
            }

            // 外观设置
            SettingsSection(title = "外观") {
                // 深色模式
                val themeOptions = listOf("自动", "浅色", "深色")
                val currentThemeIndex = when (themeMode) {
                    0 -> 0 // Auto
                    1 -> 1 // Light
                    2 -> 2 // Dark
                    else -> 0
                }
                
                DropdownSettingItem(
                    title = "深色模式",
                    currentValue = themeOptions[currentThemeIndex],
                    options = themeOptions,
                    onOptionSelected = { index ->
                        scope.launch {
                            val oldValue = themeOptions[currentThemeIndex]
                            val newValue = themeOptions[index]
                            val modeValue = when (index) {
                                0 -> 0 // Auto
                                1 -> 1 // Light
                                2 -> 2 // Dark
                                else -> 0
                            }
                            preferenceManager.setThemeMode(modeValue)
                            OperationLogger.logSettingChange("深色模式", oldValue, newValue)
                        }
                    }
                )

                // 动态主题
                SettingItemWithSwitch(
                    title = "动态主题",
                    subtitle = "跟随系统主题自动切换应用配色",
                    onClick = {
                        scope.launch {
                            preferenceManager.setDynamicColorEnabled(!dynamicColorEnabled)
                            OperationLogger.logSettingChange(
                                "动态主题",
                                if (dynamicColorEnabled) "开启" else "关闭",
                                if (!dynamicColorEnabled) "开启" else "关闭"
                            )
                        }
                    },
                    showSwitch = true,
                    isChecked = dynamicColorEnabled
                )
            }
            
            // 数据管理
            SettingsSection(title = "数据管理") {
                SettingItem(
                    title = "操作记录",
                    subtitle = "查看所有操作历史",
                    onClick = {
                        OperationLogger.logButtonClick("操作记录", "设置-数据管理")
                        onNavigateToOperationLog()
                    }
                )
                SettingItem(
                    title = "导出数据",
                    subtitle = "导出持仓记录和交易记录",
                    onClick = {
                        OperationLogger.logButtonClick("导出数据", "设置-数据管理")
                        onExportData()
                    }
                )
                SettingItem(
                    title = "导入数据",
                    subtitle = "从文件导入数据",
                    onClick = {
                        OperationLogger.logButtonClick("导入数据", "设置-数据管理")
                        onImportData()
                    }
                )

                var showClearConfirmDialog by remember { mutableStateOf(false) }

                SettingItem(
                    title = "清空记录",
                    subtitle = "清空所有持仓和交易记录",
                    onClick = {
                        OperationLogger.logButtonClick("清空记录", "设置-数据管理")
                        showClearConfirmDialog = true
                    }
                )

                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        title = { Text("确认清空") },
                        text = { Text("确定要清空所有数据吗？此操作不可恢复。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearConfirmDialog = false
                                    onClearRecords()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("清空") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) { Text("取消") }
                        }
                    )
                }
            }
            
            // 关于
            SettingsSection(title = "关于") {
                SettingItem(
                    title = "版本",
                    subtitle = "1.0.0",
                    onClick = {
                        OperationLogger.logButtonClick("版本信息", "设置-关于")
                        onNavigateToAbout()
                    }
                )
                SettingItem(
                    title = "检查更新",
                    subtitle = "检查是否有新版本",
                    onClick = {
                        OperationLogger.logButtonClick("检查更新", "设置-关于")
                        onCheckUpdate()
                    }
                )
                SettingItem(
                    title = "使用说明",
                    subtitle = "查看应用帮助",
                    onClick = {
                        OperationLogger.logButtonClick("使用说明", "设置-关于")
                        onNavigateToGuide()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 主题模式选择对话框 (已移除，改为 DropdownSettingItem)
}

@Composable
fun DropdownSettingItem(
    title: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    onInfoClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    // 使用与成本价格卡片相同的颜色
    val priceBoxColor = MaterialTheme.colorScheme.surface
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    // 用于移除点击反馈
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, 
                fontWeight = FontWeight.Medium, 
                fontSize = 15.sp,
                color = textPrimaryColor
            )
            if (onInfoClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "说明",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null // 移除点击反馈
                        ) { onInfoClick() },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null // 移除点击反馈
                    ) {
                        expanded = true
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = currentValue,
                    fontSize = 14.sp,
                    color = textSecondaryColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "选择",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            // 完全自定义的现代化下拉菜单 - 使用 Popup
            if (expanded) {
                androidx.compose.ui.window.Popup(
                    alignment = androidx.compose.ui.Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, 8),
                    onDismissRequest = { expanded = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = priceBoxColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                options.forEachIndexed { index, option ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null // 移除点击反馈
                                            ) {
                                                onOptionSelected(index)
                                                expanded = false
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            option,
                                            color = textPrimaryColor,
                                            style = if (option == currentValue) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }



@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = ThemeConstants.CardCornerRadius,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(ThemeConstants.CardPadding, ThemeConstants.CardVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(ThemeConstants.SmallItemSpacing)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title, 
                fontWeight = FontWeight.Medium, 
                fontSize = 15.sp,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle, 
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingItemWithSwitch(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showSwitch: Boolean = false,
    isChecked: Boolean = false,
    showArrow: Boolean = false
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (!showSwitch) it.clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() } else it }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title, 
                fontWeight = FontWeight.Medium, 
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle, 
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showSwitch) {
            Switch(
                checked = isChecked,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        } else if (showArrow) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RatioSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(text = "${String.format("%.1f", value)}%", fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = 0f..100f,
            steps = 100
        )
    }
}

@Composable
fun SliderItem(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    suffix: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(text = "${String.format("%.1f", value)}$suffix", fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = ((range.endInclusive - range.start) / 1).toInt()
        )
    }
}

enum class ColorTheme(val displayName: String) {
    MONET("自动莫奈取色"),
    CLASSIC("经典蓝")
}


