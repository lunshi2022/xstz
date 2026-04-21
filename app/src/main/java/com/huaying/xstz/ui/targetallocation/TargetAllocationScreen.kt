package com.huaying.xstz.ui.targetallocation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.theme.*
import java.util.Locale

/**
 * 全局仓位配比配置页面
 * 允许用户协同调整所有基金的目标占比，确保总和为100%
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAllocationScreen(
    funds: List<Fund>,
    isDarkMode: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    onSave: (List<Fund>) -> Unit
) {
    // 按类型排序，同类型按市值降序（与首页一致）
    val sortedFunds = remember(funds) {
        funds.sortedWith(
            compareBy<Fund> { it.type.ordinal }
                .thenByDescending { it.holdingQuantity * it.currentPrice }
        )
    }
    
    // 创建可编辑的临时状态（保持排序后的顺序）
    var editedFunds by remember(sortedFunds) {
        mutableStateOf(sortedFunds.map { it.copy() })
    }

    // 分离现金和非现金资产
    val nonCashFunds = editedFunds.filter { it.type != AssetType.CASH }
    val cashFund = editedFunds.find { it.type == AssetType.CASH }
    
    // 计算总资产（用于显示当前实际占比）
    val totalAssets = editedFunds.sumOf { it.holdingQuantity * it.currentPrice }
    
    // 计算非现金资产的总和
    val nonCashTotalRatio = nonCashFunds.sumOf { it.targetRatio * 100 }
    // 现金占比自动计算：100% - 非现金资产总和
    val cashRatio = (100.0 - nonCashTotalRatio).coerceAtLeast(0.0)
    
    // 更新现金账户的占比（自动计算）
    if (cashFund != null) {
        val cashIndex = editedFunds.indexOfFirst { it.type == AssetType.CASH }
        if (cashIndex >= 0 && kotlin.math.abs(editedFunds[cashIndex].targetRatio * 100 - cashRatio) > 0.01) {
            editedFunds = editedFunds.toMutableList().apply {
                this[cashIndex] = cashFund.copy(targetRatio = cashRatio / 100.0)
            }
        }
    }
    
    // 验证：非现金资产总和必须 <= 100%（给现金留出空间）
    val isValid = nonCashTotalRatio <= 100.0 && nonCashTotalRatio >= 0

    // 添加LazyListState以支持自动滚动
    val lazyListState = rememberLazyListState()

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("目标占比配置")
    }
    
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    OperationLogger.logBack("目标占比配置")
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = if (isDarkMode) DarkTextPrimary else LightTextPrimary)
                }
                Text(
                    "目标占比配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                )
            }
        },
        containerColor = if (isDarkMode) DarkBackground else LightBackground,
        bottomBar = {
            // 底部状态栏和操作按钮
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDarkMode) DarkSurface else LightSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = 16.dp)
                ) {
                    // 非现金资产总和
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "非现金资产目标总和",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%.2f%%".format(Locale.CHINA, nonCashTotalRatio),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isValid) SuccessGreen else DangerRed
                            )
                            if (!isValid) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    // 现金占比（自动计算）- 始终显示，避免高度抖动
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "现金账户占比（自动）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                        Text(
                            "%.2f%%".format(Locale.CHINA, cashRatio),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }
                    
                    if (!isValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "非现金资产总和不能超过100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 保存按钮
                    Button(
                        onClick = {
                            OperationLogger.logButtonClick("保存目标配置", "目标占比配置")
                            onSave(editedFunds)
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 提示信息
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkMode) DarkPriceBox else LightPriceBox
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 调整各基金目标占比，现金占比将自动计算",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                    )
                }
            }
            
            // 基金列表
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(editedFunds, key = { it.id }) { fund ->
                    val index = editedFunds.indexOf(fund)
                    val isCash = fund.type == AssetType.CASH
                    FundRatioCard(
                        fund = fund,
                        totalAssets = totalAssets,
                        isDarkMode = isDarkMode,
                        isReadOnly = isCash,
                        onRatioChange = { newRatio ->
                            if (!isCash) {
                                editedFunds = editedFunds.toMutableList().apply {
                                    this[index] = fund.copy(targetRatio = newRatio / 100.0)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FundRatioCard(
    fund: Fund,
    totalAssets: Double,
    isDarkMode: Boolean,
    isReadOnly: Boolean = false,
    onRatioChange: (Double) -> Unit
) {
    val assetColor = getColorForAssetType(fund.type)
    val ratioPercent = fund.targetRatio * 100
    
    // 计算当前实际占比
    val currentValue = fund.holdingQuantity * fund.currentPrice
    val actualRatio = if (totalAssets > 0) (currentValue / totalAssets * 100) else 0.0
    
    var inputValue by remember(ratioPercent) { 
        mutableStateOf("%.2f".format(Locale.CHINA, ratioPercent)) 
    }
    var isError by remember { mutableStateOf(false) }
    
    // 添加FocusRequester以支持自动聚焦
    val focusRequester = remember { FocusRequester() }
    
    // 只读状态下的颜色
    val disabledColor = if (isDarkMode) DarkTextSecondary.copy(alpha = 0.5f) else LightTextSecondary.copy(alpha = 0.5f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) DarkSurface else LightSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 第一行：基金名称 + 目标占比输入
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 类型指示点
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(assetColor, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            fund.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                        )
                        if (!isReadOnly) {
                            Text(
                                fund.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                            )
                        }
                    }
                }
                
                // 目标占比显示/输入
                if (isReadOnly) {
                    // 现金账户显示占比
                    Text(
                        "%.2f%%".format(Locale.CHINA, ratioPercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                } else {
                    // 可编辑的占比输入框 - 简洁样式，百分比在内部居中
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(36.dp)
                            .background(
                                color = if (isDarkMode) DarkPriceBox else LightPriceBox,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isError) 2.dp else 1.dp,
                                color = if (isError) DangerRed else if (isDarkMode) DarkTextSecondary.copy(alpha = 0.3f) else LightTextSecondary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            BasicTextField(
                                value = inputValue,
                                onValueChange = { newText ->
                                    val filtered = newText.filter { it.isDigit() || it == '.' }
                                    val dotCount = filtered.count { it == '.' }
                                    if (dotCount <= 1) {
                                        val value = filtered.toDoubleOrNull()
                                        if (value != null) {
                                            if (value <= 100.0) {
                                                inputValue = filtered
                                                if (value >= 0.0) {
                                                    onRatioChange(value)
                                                    isError = false
                                                } else {
                                                    isError = true
                                                }
                                            } else {
                                                inputValue = "100"
                                                onRatioChange(100.0)
                                                isError = false
                                            }
                                        } else {
                                            inputValue = filtered
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .widthIn(min = 40.dp, max = 60.dp)
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = assetColor
                                )
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkMode) DarkTextSecondary else LightTextSecondary,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // 现金账户只显示基本信息
            if (!isReadOnly) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // 第二行：当前实际占比 + 滑块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 当前实际占比
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                        Text(
                            "%.1f%%".format(Locale.CHINA, actualRatio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                    }
                    
                    // 滑块
                    Slider(
                        value = ratioPercent.toFloat(),
                        onValueChange = { newValue ->
                            onRatioChange(newValue.toDouble())
                            inputValue = "%.2f".format(Locale.CHINA, newValue)
                            isError = false
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = assetColor,
                            activeTrackColor = assetColor,
                            inactiveTrackColor = if (isDarkMode) DarkPriceBox else LightPriceBox
                        )
                    )
                    
                    // 目标占比标签
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "目标",
                            style = MaterialTheme.typography.labelSmall,
                            color = assetColor
                        )
                        Text(
                            "%.1f%%".format(Locale.CHINA, ratioPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = assetColor
                        )
                    }
                }
                
                // 快捷按钮（简化版）
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 10, 20, 30, 50).forEach { preset ->
                        val isSelected = kotlin.math.abs(ratioPercent - preset) < 0.5
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) assetColor else (if (isDarkMode) DarkPriceBox else LightPriceBox),
                            onClick = {
                                onRatioChange(preset.toDouble())
                                inputValue = preset.toString()
                                isError = false
                            }
                        ) {
                            Text(
                                text = "$preset%",
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else (if (isDarkMode) DarkTextSecondary else LightTextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                
                if (isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "请输入0-100之间的数值",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
            }
        }
    }
}
