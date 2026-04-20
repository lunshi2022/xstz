package com.huaying.xstz.ui.targetallocation

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    // 创建可编辑的临时状态
    var editedFunds by remember(funds) {
        mutableStateOf(funds.map { it.copy() })
    }

    // 计算总和
    val totalRatio = editedFunds.sumOf { it.targetRatio * 100 }
    val isValid = kotlin.math.abs(totalRatio - 100.0) < 0.01

    // 自动平衡确认对话框
    var showAutoBalanceDialog by remember { mutableStateOf(false) }

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
                        .navigationBarsPadding()
                ) {
                    // 总和状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "所有基金目标总和",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%.2f%%".format(Locale.CHINA, totalRatio),
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
                    
                    if (!isValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "需调整为100%才可保存",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 自动平衡按钮
                        OutlinedButton(
                            onClick = {
                                OperationLogger.logButtonClick("自动平衡", "目标占比配置")
                                showAutoBalanceDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("自动平衡")
                        }
                        
                        // 保存按钮
                        Button(
                            onClick = {
                                OperationLogger.logButtonClick("保存目标配置", "目标占比配置")
                                onSave(editedFunds)
                            },
                            enabled = isValid,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
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
                        "💡 调整各基金目标占比，确保总和为100%后保存",
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
                    FundRatioCard(
                        fund = fund,
                        totalRatio = totalRatio,
                        isDarkMode = isDarkMode,
                        onRatioChange = { newRatio ->
                            editedFunds = editedFunds.toMutableList().apply {
                                this[index] = fund.copy(targetRatio = newRatio / 100.0)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 自动平衡确认对话框
    if (showAutoBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showAutoBalanceDialog = false },
            title = { Text("自动平衡") },
            text = { 
                Text("将所有基金的目标占比按比例缩放至总和100%，是否继续？") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 执行自动平衡
                        val currentSum = editedFunds.sumOf { it.targetRatio }
                        editedFunds = if (currentSum > 0) {
                            editedFunds.map { fund ->
                                fund.copy(targetRatio = (fund.targetRatio / currentSum).coerceIn(0.0, 1.0))
                            }
                        } else {
                            // 如果当前总和为0，均分100%
                            val avg = 1.0 / editedFunds.size
                            editedFunds.map { it.copy(targetRatio = avg) }
                        }
                        showAutoBalanceDialog = false
                    }
                ) {
                    Text("确认", color = BrandBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoBalanceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FundRatioCard(
    fund: Fund,
    totalRatio: Double,
    isDarkMode: Boolean,
    onRatioChange: (Double) -> Unit
) {
    val assetColor = getColorForAssetType(fund.type)
    val ratioPercent = fund.targetRatio * 100
    // 计算当前市值占比（用于参考）
    val currentValueRatio = if (totalRatio > 0) (fund.targetRatio * 100 / totalRatio * 100) else 0.0
    
    var inputValue by remember(ratioPercent) { 
        mutableStateOf("%.2f".format(Locale.CHINA, ratioPercent)) 
    }
    var isError by remember { mutableStateOf(false) }
    
    // 添加FocusRequester以支持自动聚焦
    val focusRequester = remember { FocusRequester() }
    
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
            // 基金名称和类型
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
                    Text(
                        fund.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                    )
                }
                // 基金代码
                Surface(
                    color = if (isDarkMode) DarkCodeBg else LightCodeBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        fund.code,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandBlue
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 滑块
            Slider(
                value = ratioPercent.toFloat(),
                onValueChange = { newValue ->
                    onRatioChange(newValue.toDouble())
                    inputValue = "%.2f".format(Locale.CHINA, newValue)
                    isError = false
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = assetColor,
                    activeTrackColor = assetColor,
                    inactiveTrackColor = if (isDarkMode) DarkPriceBox else LightPriceBox
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 快捷按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 5, 10, 20, 25, 50).forEach { preset ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (kotlin.math.abs(ratioPercent - preset) < 0.01) 
                            assetColor.copy(alpha = 0.2f) 
                        else 
                            if (isDarkMode) DarkPriceBox else LightPriceBox,
                        onClick = {
                            onRatioChange(preset.toDouble())
                            inputValue = preset.toString()
                            isError = false
                        }
                    ) {
                        Text(
                            text = "$preset%",
                            modifier = Modifier.padding(vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (kotlin.math.abs(ratioPercent - preset) < 0.01) 
                                assetColor 
                            else 
                                if (isDarkMode) DarkTextSecondary else LightTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 自定义输入
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "自定义",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                )
                OutlinedTextField(
                        value = inputValue,
                        onValueChange = { newText ->
                            val filtered = newText.filter { it.isDigit() || it == '.' }
                            val dotCount = filtered.count { it == '.' }
                            if (dotCount <= 1) {
                                inputValue = filtered
                                filtered.toDoubleOrNull()?.let { value ->
                                    if (value in 0.0..100.0) {
                                        onRatioChange(value)
                                        isError = false
                                    } else {
                                        isError = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.width(100.dp).focusRequester(focusRequester),
                        singleLine = true,
                        isError = isError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = assetColor,
                            unfocusedBorderColor = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        ),
                        suffix = { Text("%") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    )
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
