package com.huaying.xstz.ui.rebalance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.ui.theme.DarkBackground
import com.huaying.xstz.ui.theme.DarkTextPrimary
import com.huaying.xstz.ui.theme.DarkTextSecondary
import com.huaying.xstz.ui.theme.LightBackground
import com.huaying.xstz.ui.theme.LightTextPrimary
import com.huaying.xstz.ui.theme.LightTextSecondary
import com.huaying.xstz.ui.theme.getColorForAssetType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebalanceScreen(
    repository: FundRepository,
    preferenceManager: PreferenceManager,
    darkTheme: Boolean = false,
    preloadedFunds: List<Fund> = emptyList(),
    preloadedTotalAssets: Double = 0.0,
    onNavigateToTargetAllocation: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkMode = darkTheme

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("再平衡")
    }

    // 使用预加载的数据作为初始值，避免闪烁
    var funds by remember(preloadedFunds) { mutableStateOf(preloadedFunds) }
    var totalAssets by remember(preloadedTotalAssets) { mutableStateOf(preloadedTotalAssets) }
    var adjustmentList by remember { mutableStateOf<List<AdjustmentItem>>(emptyList()) }
    // 如果有预加载数据，直接标记为已加载
    var isDataLoaded by remember(preloadedFunds) { mutableStateOf(preloadedFunds.isNotEmpty()) }

    // 骨架屏动画
    val shimmerAnimation = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by shimmerAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset.Zero,
        end = Offset(x = shimmerTranslate, y = 0f)
    )
    
    // New Fund Input
    var newFundInput by remember { mutableStateOf("") }
    
    // Threshold - 使用 remember 缓存避免重复初始化
    val savedThreshold by preferenceManager.rebalanceThreshold.collectAsState(initial = 20.0f)
    val savedThresholdMode by preferenceManager.rebalanceThresholdMode.collectAsState(initial = 0)
    // 直接使用 savedThreshold 作为初始值，避免从空字符串切换导致的闪烁
    var thresholdInput by remember(savedThreshold) { mutableStateOf(if (savedThreshold > 0) savedThreshold.toInt().toString() else "") }
    var thresholdMode by remember(savedThresholdMode) { mutableIntStateOf(savedThresholdMode) }
    var showThresholdDialog by remember { mutableStateOf(false) }

    // Logic Selection (For Preview)
    // 0 = Smart, 1 = Full
    var selectedLogicMode by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    // 监听数据变化，更新预加载的数据
    LaunchedEffect(Unit) {
        repository.getAllFunds().collect { fundList ->
            // Sort to match AssetOverviewScreen: Type first, then Market Value descending
            funds = fundList.sortedWith(
                compareBy<Fund> { it.type.ordinal }
                    .thenByDescending { it.holdingQuantity * it.currentPrice }
            )
            totalAssets = fundList.sumOf { it.holdingQuantity * it.currentPrice }
            isDataLoaded = true
        }
    }
    
    // Recalculate when inputs change
    LaunchedEffect(funds, selectedLogicMode, newFundInput, thresholdInput, thresholdMode) {
        if (newFundInput.isEmpty() && selectedLogicMode == 0) {
            adjustmentList = emptyList()
        } else {
            val newFundVal = newFundInput.replace(",", "").toDoubleOrNull() ?: 0.0
            val thresholdVal = thresholdInput.toFloatOrNull() ?: 20f
            val strategy = if (selectedLogicMode == 1) RebalanceStrategy.FULL_REBALANCE else RebalanceStrategy.SMART
            
            adjustmentList = calculateAdjustments(funds, totalAssets, strategy, newFundVal, thresholdVal, thresholdMode)
        }
    }

    // Helper for executing rebalance
    val executeRebalance: (RebalanceStrategy) -> Unit = { strategy ->
        scope.launch {
            val newThreshold = thresholdInput.toFloatOrNull()
            if (newThreshold != null) {
                preferenceManager.setRebalanceThreshold(newThreshold)
            }
            preferenceManager.setRebalanceThresholdMode(thresholdMode)
            
            val newFundVal = newFundInput.replace(",", "").toDoubleOrNull() ?: 0.0
            val thresholdVal = newThreshold ?: 20f
            
            // Recalculate to ensure latest data used for execution
            val finalAdjustments = calculateAdjustments(
                funds, 
                totalAssets, 
                strategy, 
                newFundVal, 
                thresholdVal,
                thresholdMode
            )
            
            if (finalAdjustments.isEmpty()) {
                // If logic returned empty (e.g. within threshold), and user clicked Confirm,
                // we should probably do nothing or show toast. 
                // But usually the button enables preview -> list -> confirm.
                // If list is empty, user sees "No adjustment".
                // If they click confirm on empty list, we just go back.
                onBack()
                return@launch
            }

            finalAdjustments.forEach { adjustment ->
                val fund = funds.find { it.id == adjustment.fundId }
                if (fund != null && fund.currentPrice > 0) {
                    // Use the calculated share change directly to avoid rounding discrepancies
                    val deltaQuantity = adjustment.changeQuantity
                    val newQuantity = fund.holdingQuantity + deltaQuantity
                    
                    // Update Total Cost (Proportional for sell, additive for buy)
                    val newTotalCost = if (deltaQuantity > 0) {
                        fund.totalCost + adjustment.adjustmentAmount
                    } else {
                        if (fund.holdingQuantity > 0) {
                            fund.totalCost * (newQuantity / fund.holdingQuantity)
                        } else {
                            0.0
                        }
                    }
                    
                    repository.updateFund(fund.copy(
                        holdingQuantity = if (newQuantity < 0) 0.0 else newQuantity,
                        totalCost = if (newTotalCost < 0) 0.0 else newTotalCost,
                        updatedAt = TimeRepository.getCurrentTimeMillis()
                    ))

                    // Record Transaction
                    if (abs(deltaQuantity) > 0) {
                        repository.insertTransaction(
                            Transaction(
                                fundId = fund.id,
                                fundCode = fund.code,
                                fundName = fund.name,
                                type = if (deltaQuantity > 0) TransactionType.BUY else TransactionType.SELL,
                                amount = abs(adjustment.adjustmentAmount),
                                price = fund.currentPrice,
                                quantity = abs(deltaQuantity),
                                remark = "再平衡调整"
                            )
                        )
                    }
                }
            }

            
            // Clear inputs and show success
            newFundInput = ""
            // Logic mode stays or resets? Resetting to Smart (0) is safer.
            selectedLogicMode = 0
            focusManager.clearFocus()
            
            // 记录操作日志
            OperationLogger.log(
                type = com.huaying.xstz.data.entity.OperationType.REBALANCE,
                title = "执行再平衡",
                description = "策略: ${if (strategy == RebalanceStrategy.FULL_REBALANCE) "完全再平衡" else "智能再平衡"}, 交易数: ${finalAdjustments.size}"
            )

            snackbarHostState.showSnackbar("交易已完成")

            // onBack() is not useful in Tab navigation
            // onBack()
        }
    }

    Scaffold(
        modifier = Modifier.graphicsLayer {
            // 启用硬件加速，提升动画性能
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    "资产再平衡",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 120.dp, // 从标题栏下方开始
                end = 16.dp,
                bottom = 140.dp // 确保最后一个项目可以滚动到导航栏上方完全可见
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Target Ratio Configuration Entry
            item {
                // 计算当前目标占比总和
                val totalTargetRatio = funds.filter { it.type != AssetType.CASH }.sumOf { it.targetRatio }
                val isValidRatio = abs(totalTargetRatio - 1.0) < 0.01
                
                // 计算是否有基金严重偏离目标配置
                val totalAssets = funds.sumOf { it.holdingQuantity * it.currentPrice }
                val hasSevereDeviation = if (totalAssets > 0) {
                    funds.any { fund ->
                        val currentRatio = (fund.holdingQuantity * fund.currentPrice) / totalAssets * 100
                        val targetRatio = fund.targetRatio * 100
                        val deviation = kotlin.math.abs(currentRatio - targetRatio)
                        deviation > 10.0 // 偏离超过10%认为需要调整
                    }
                } else false

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = {
                        OperationLogger.logButtonClick("调整目标配置", "再平衡")
                        onNavigateToTargetAllocation()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "目标占比配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // 只在需要调整时显示提示
                            if (!isDataLoaded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                // 骨架屏占位符 - 模拟文字行
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(16.dp)
                                        .background(shimmerBrush, shape = MaterialTheme.shapes.small)
                                )
                            } else if (!isValidRatio) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "非现金资产占比: %.2f%% ⚠ 需调整".format(Locale.CHINA, totalTargetRatio * 100),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (hasSevereDeviation) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "部分基金偏离目标配置 ⚠ 需调整",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 1. Threshold Setting
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "偏离度阈值",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "说明",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { showThresholdDialog = true }
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = { 
                                            thresholdMode = 0 
                                            if (thresholdInput.isNotEmpty()) {
                                                val currentVal = thresholdInput.toIntOrNull() ?: 20
                                                if (currentVal < 5) thresholdInput = "5"
                                                if (currentVal > 25) thresholdInput = "25"
                                            }
                                        },
                                        label = { Text("百分比模式", style = MaterialTheme.typography.labelSmall) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (thresholdMode == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            labelColor = if (thresholdMode == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (thresholdMode == 0) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )
                                    AssistChip(
                                        onClick = { 
                                            thresholdMode = 1
                                            if (thresholdInput.isNotEmpty()) {
                                                val currentVal = thresholdInput.toIntOrNull() ?: 5
                                                if (currentVal < 1) thresholdInput = "1"
                                                if (currentVal > 10) thresholdInput = "10"
                                            }
                                        },
                                        label = { Text("百分点模式", style = MaterialTheme.typography.labelSmall) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (thresholdMode == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            labelColor = if (thresholdMode == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (thresholdMode == 1) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                            
                            OutlinedTextField(
                                value = thresholdInput,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 2) {
                                        thresholdInput = input
                                    }
                                },
                                modifier = Modifier
                                    .width(90.dp)
                                    .onFocusChanged {
                                        if (!it.isFocused && thresholdInput.isEmpty()) {
                                            thresholdInput = if (thresholdMode == 0) "20" else "5"
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                singleLine = true,
                                maxLines = 1,
                                trailingIcon = { Text(if (thresholdMode == 0) "%" else "pt", style = MaterialTheme.typography.bodyLarge) },
                                placeholder = { 
                                    Text(
                                        if (thresholdMode == 0) "20" else "5", 
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) 
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                isError = if (thresholdMode == 0) {
                                    (thresholdInput.toIntOrNull() ?: 20) !in 5..25
                                } else {
                                    (thresholdInput.toIntOrNull() ?: 5) !in 1..10
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // Validation / Helper Message
                        val inputVal = thresholdInput.toIntOrNull()
                        val minVal = if (thresholdMode == 0) 5 else 1
                        val maxVal = if (thresholdMode == 0) 25 else 10
                        if (thresholdMode == 0) "%" else "个百分点"

                        // 骨架屏或实际内容
                        if (!isDataLoaded) {
                            // 骨架屏占位符
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .width(200.dp)
                                    .height(14.dp)
                                    .background(shimmerBrush, shape = MaterialTheme.shapes.small)
                            )
                        } else {
                            when {
                                inputVal != null && inputVal !in minVal..maxVal -> {
                                    Text(
                                        text = "请输入 $minVal-$maxVal 之间的整数",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                                thresholdInput.isEmpty() -> {
                                    Text(
                                        text = "未输入将默认使用 ${if (thresholdMode == 0) "20%" else "5个百分点"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = if (thresholdMode == 0) "计算公式：目标比例 ± (目标比例 × $inputVal%)" else "计算公式：目标比例 ± $inputVal%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. New/Withdraw Funds Input
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "新增/取出资金",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val inputVal = newFundInput.replace(",", "").toDoubleOrNull() ?: 0.0
                        val isWithdrawError = inputVal < 0 && abs(inputVal) > totalAssets
                        
                        OutlinedTextField(
                            value = newFundInput,
                            onValueChange = { input ->
                                val cleanInput = input.replace(",", "")
                                if (cleanInput.matches(Regex("^-?\\d*(\\.\\d{0,2})?$")) || 
                                    cleanInput == "-" || cleanInput == "." || cleanInput == "-.") {
                                    newFundInput = cleanInput
                                }
                            },
                            visualTransformation = CurrencyAmountInputVisualTransformation(),
                            label = { Text("金额 (正数买入，负数卖出)") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = isWithdrawError,
                            supportingText = {
                                if (isWithdrawError) {
                                    Text(
                                        text = "取出金额不能超过总资产 (¥%,.2f)".format(totalAssets),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }


            // 3. Strategy Options
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "强制再平衡",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "忽略阈值，强制对齐目标比例",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Switch(
                            checked = selectedLogicMode == 1,
                            onCheckedChange = { checked ->
                                selectedLogicMode = if (checked) 1 else 0
                                // Reset list if switching off, or recalculate automatically via LaunchedEffect
                            }
                        )
                    }
                }
            }

            // 6. Results
            if (adjustmentList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "调整后结果",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            adjustmentList.forEach { item ->
                                AdjustmentItemRow(item)
                            }

                            // Confirm Button inside the card
                            Button(
                                onClick = { 
                                    val strategy = if (selectedLogicMode == 1) RebalanceStrategy.FULL_REBALANCE else RebalanceStrategy.SMART
                                    executeRebalance(strategy) 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "确认交易 (${adjustmentList.size} 笔)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("偏离度阈值说明") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("模式A：百分比模式 (当前推荐)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("计算公式：目标比例 ± (目标比例 × 阈值%)", style = MaterialTheme.typography.bodySmall)
                        Text("适合所有比例。例如：目标 25%，阈值 20%，则允许范围为 20% - 30% (25% ± 5%)。", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Column {
                        Text("模式B：百分点模式 (经典模式)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("计算公式：目标比例 ± 阈值百分点", style = MaterialTheme.typography.bodySmall)
                        Text("适合大比例资产。例如：目标 25%，阈值 5%，则允许范围为 20% - 30% (25% ± 5%)。", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Text("提示：偏离度阈值始终以“目标比例”为固定锚点，不会随资产波动而漂移。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    
                    Text("2. 最小交易单位：A股/ETF 最低交易单位为 1手 (100股)。", style = MaterialTheme.typography.bodySmall)
                    Text("3. 流动性检查：买入需确保有足够的流动资金。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }
}

@Composable
fun ClipRoundedBar(stockRatio: Double, bondRatio: Double, goldRatio: Double, cashRatio: Double) {
    Card(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (stockRatio > 0) Box(modifier = Modifier.weight(stockRatio.toFloat()).fillMaxHeight().background(getColorForAssetType(AssetType.STOCK)))
            if (bondRatio > 0) Box(modifier = Modifier.weight(bondRatio.toFloat()).fillMaxHeight().background(getColorForAssetType(AssetType.BOND)))
            if (goldRatio > 0) Box(modifier = Modifier.weight(goldRatio.toFloat()).fillMaxHeight().background(getColorForAssetType(AssetType.COMMODITY)))
            if (cashRatio > 0) Box(modifier = Modifier.weight(cashRatio.toFloat()).fillMaxHeight().background(getColorForAssetType(AssetType.CASH)))
        }
    }
}

@Composable
fun AdjustmentItemRow(item: AdjustmentItem) {
    val color = if (item.adjustmentAmount >= 0) Color(0xFFE53935) else Color(0xFF4CAF50)
    val actionText = if (item.adjustmentAmount >= 0) "买入" else "卖出"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fundName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${"%.1f".format(item.currentRatio)}% -> ${"%.1f".format(item.targetRatio)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$actionText ¥%,.2f".format(abs(item.adjustmentAmount)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                // 只有非现金资产才显示股数变动，且数量大于0时才显示
                if (item.assetType != AssetType.CASH && abs(item.changeQuantity) > 0) {
                    Text(
                        text = "${if (item.changeQuantity > 0) "+" else ""}${"%.0f".format(item.changeQuantity)}股",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

data class AdjustmentItem(
    val fundId: Long,
    val fundName: String,
    val assetType: AssetType,
    val currentRatio: Double,
    val targetRatio: Double,
    val idealRatio: Double,
    val adjustmentAmount: Double,
    val changeQuantity: Double
)

enum class RebalanceStrategy(val displayName: String) {
    SMART("智能买卖"),
    FULL_REBALANCE("强制再平衡")
}

fun calculateAdjustments(
    funds: List<Fund>,
    totalAssets: Double,
    strategy: RebalanceStrategy,
    newFundVal: Double,
    thresholdPct: Float,
    thresholdMode: Int
): List<AdjustmentItem> {
    val hasCashFund = funds.any { it.type == AssetType.CASH }
    
    // If we have a cash fund, the total assets (including cash) is conserved + new money.
    // If NO cash fund, any sell proceeds leave the portfolio, and any unused new money is returned.
    // In that case, the "Future Total" depends on the adjustments themselves.
    // We use iteration to converge on the correct Future Total.
    
    var effectiveFutureTotal = totalAssets + newFundVal
    var adjustments: List<AdjustmentItem> = emptyList()
    
    val maxIterations = if (hasCashFund) 1 else 5
    
    for (i in 0 until maxIterations) {
        adjustments = internalCalculateAdjustments(
            funds, 
            totalAssets, 
            effectiveFutureTotal, 
            strategy, 
            newFundVal, 
            thresholdPct,
            thresholdMode
        )
        
        if (hasCashFund) break
        
        // Calculate the "Real" Future Total implied by these adjustments
        // Real Total = Start Stock Assets + Net Change in Stock Assets
        // (Since Cash is not part of the portfolio)
        val netStockChange = adjustments.sumOf { it.adjustmentAmount }
        val realFutureTotal = totalAssets + netStockChange
        
        if (abs(realFutureTotal - effectiveFutureTotal) < 10.0) {
            break // Converged
        }
        
        effectiveFutureTotal = realFutureTotal
    }
    
    return adjustments
}

private fun internalCalculateAdjustments(
    funds: List<Fund>,
    totalAssets: Double, // Current Total Assets
    futureTotal: Double, // Target Total Assets (used for ratio calc)
    strategy: RebalanceStrategy,
    newFundVal: Double,
    thresholdPct: Float,
    thresholdMode: Int
): List<AdjustmentItem> {
    if (funds.isEmpty()) {
        return emptyList()
    }
    
    // 直接使用目标占比（不再归一化）
    // Map FundID -> Target Ratio (0.0 - 1.0)
    val targetRatios = funds.associate { it.id to it.targetRatio }
    val normTargets = targetRatios // 兼容旧代码命名
    
    // futureTotal is passed in now
    val fundAllocations = mutableMapOf<Long, Double>()
    
    // Calculate Allowed Deviations (Threshold Logic)
    // Mode 0: Relative Threshold: allowedDeviation = target * (thresholdPct / 100)
    // Mode 1: Absolute Threshold: allowedDeviation = thresholdPct / 100 (percentage points)
    
    // Clamp values for safety
    val safeThreshold = if (thresholdMode == 0) thresholdPct.coerceIn(5f, 25f) else thresholdPct.coerceIn(1f, 10f)
    val minAbsDeviation = 0.005 // 0.5% absolute minimum deviation for small targets
    
    val deviations = funds.associate { fund ->
        val targetRatio = targetRatios[fund.id] ?: 0.0
        val finalDev = if (thresholdMode == 0) {
            max(targetRatio * (safeThreshold / 100.0), minAbsDeviation)
        } else {
            safeThreshold / 100.0 // Percentage points to decimal
        }
        fund.id to finalDev
    }

    // 2. Calculate Gaps
    data class FundGap(val fundId: Long, val gap: Double, val isOutsideThreshold: Boolean)
    val gaps = funds.map { fund ->
        val targetVal = futureTotal * (normTargets[fund.id] ?: 0.0)
        val currentVal = fund.holdingQuantity * fund.currentPrice
        
        // Check if outside threshold
        val currentRatio = if (futureTotal > 0) currentVal / futureTotal else 0.0
        val normTarget = normTargets[fund.id] ?: 0.0
        val allowedDev = deviations[fund.id] ?: 0.0
        val isOutside = abs(currentRatio - normTarget) > allowedDev
        
        FundGap(fund.id, targetVal - currentVal, isOutside)
    }
    
    // 3. Strategy Logic
    if (strategy == RebalanceStrategy.FULL_REBALANCE) {
        // Full Rebalance Logic
        // Force rebalance: Fill all gaps regardless of threshold
        // However, user input 0 check is handled by returning empty gaps if no funds/gaps.
        
        // If triggered, fill all gaps to reach Target
        gaps.forEach { fundAllocations[it.fundId] = it.gap }
    } else {
        // SMART Strategy
        if (newFundVal >= 0) {
            // Buying: Prioritize under-allocated funds
            // Priority 1: Funds BELOW Lower Bound (Target - Deviation)
            // Priority 2: Funds BELOW Target
            
            val lowerBoundGaps = gaps.map { gap ->
                val fund = funds.find { it.id == gap.fundId }!!
                val normTarget = normTargets[fund.id] ?: 0.0
                val allowedDev = deviations[fund.id] ?: 0.0
                val lowerBound = normTarget - allowedDev
                val currentRatio = if (futureTotal > 0) (fund.holdingQuantity * fund.currentPrice) / futureTotal else 0.0
                
                // Gap to reach Lower Bound (minimal fix)? Or Gap to Target?
                // Smart Buy usually fills to Target if possible.
                // Let's prioritize those strictly below lower bound first.
                val isBelowLower = currentRatio < lowerBound
                gap to isBelowLower
            }
            
            val criticalGaps = lowerBoundGaps.filter { it.second }.map { it.first }
            val otherPosGaps = lowerBoundGaps.filter { !it.second && it.first.gap > 0 }.map { it.first }
            
            var remaining = newFundVal
            
            // Phase 1: Fill Critical Gaps (Below Lower Bound)
            val totalCritical = criticalGaps.sumOf { it.gap }
            if (remaining >= totalCritical && totalCritical > 0) {
                criticalGaps.forEach { fundAllocations[it.fundId] = it.gap }
                remaining -= totalCritical
                
                // Phase 2: Fill Other Positive Gaps (Below Target)
                val totalOther = otherPosGaps.sumOf { it.gap }
                if (remaining >= totalOther && totalOther > 0) {
                    otherPosGaps.forEach { fundAllocations[it.fundId] = it.gap }
                    remaining -= totalOther
                    
                    // Phase 3: Distribute remainder by Target Ratio
                    funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                } else if (totalOther > 0) {
                    otherPosGaps.forEach { item ->
                        fundAllocations[item.fundId] = remaining * (item.gap / totalOther)
                    }
                } else {
                     // Remainder to all
                     funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                }
            } else if (totalCritical > 0) {
                 criticalGaps.forEach { item ->
                    fundAllocations[item.fundId] = remaining * (item.gap / totalCritical)
                }
            } else {
                // No critical gaps, try normal gaps
                val totalPosGap = gaps.filter { it.gap > 0 }.sumOf { it.gap }
                if (remaining >= totalPosGap && totalPosGap > 0) {
                     gaps.filter { it.gap > 0 }.forEach { fundAllocations[it.fundId] = it.gap }
                     remaining -= totalPosGap
                     funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                } else if (totalPosGap > 0) {
                    gaps.filter { it.gap > 0 }.forEach { item ->
                         fundAllocations[item.fundId] = remaining * (item.gap / totalPosGap)
                    }
                } else {
                     funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = remaining * ratio
                    }
                }
            }
        } else {
            // Selling: Prioritize over-allocated funds (Negative Gap)
            val negGaps = gaps.filter { it.gap < 0 }
            val totalNegGap = negGaps.sumOf { it.gap } // Negative value
            var remaining = newFundVal // Negative value
            
            if (remaining <= totalNegGap && totalNegGap < 0) {
                // Fix all negative gaps
                negGaps.forEach { fundAllocations[it.fundId] = it.gap }
                remaining -= totalNegGap
                // Sell remainder by Target Ratio from ALL funds
                funds.forEach { fund ->
                    val ratio = normTargets[fund.id] ?: 0.0
                    fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                }
            } else if (totalNegGap < 0) {
                // Partial sell
                negGaps.forEach { item ->
                    fundAllocations[item.fundId] = remaining * (item.gap / totalNegGap)
                }
            } else {
                // No negative gaps: Sell by Target Ratio
                funds.forEach { fund ->
                    val ratio = normTargets[fund.id] ?: 0.0
                    fundAllocations[fund.id] = remaining * ratio
                }
            }
        }
    }
    
    // 4. Convert to AdjustmentItems and Optimize Rounding
    val items = mutableListOf<AdjustmentItem>()
    
    // Separate Cash and Non-Cash
    val cashFunds = funds.filter { it.type == AssetType.CASH }
    val nonCashFunds = funds.filter { it.type != AssetType.CASH }
    
    // Helper data structure
    data class Plan(val fund: Fund, val rawShares: Double, var plannedShares: Double, val price: Double)
    
    val plans = nonCashFunds.mapNotNull { fund ->
        val rawAmount = fundAllocations[fund.id] ?: 0.0
        if (fund.currentPrice > 0) {
            val rawShares = rawAmount / fund.currentPrice
            // Round to Nearest 100
            var planned = kotlin.math.round(rawShares / 100.0) * 100.0
            
            // Holding Constraint: Cannot sell more than held
            if (planned < 0 && abs(planned) > fund.holdingQuantity) {
                 val maxSellable = (fund.holdingQuantity / 100.0).toInt() * 100.0
                 planned = -maxSellable
            }
            Plan(fund, rawShares, planned, fund.currentPrice)
        } else null
    }

    // Cash Validation
    val existingCash = cashFunds.sumOf { it.holdingQuantity * it.currentPrice }
    
    fun calculateProjectedCash(): Double {
        val nonCashUsed = plans.sumOf { it.plannedShares * it.price }
        return existingCash + newFundVal - nonCashUsed
    }
    
    var projectedCash = calculateProjectedCash()
    
    // Fix Deficit (If projectedCash < 0)
    // This handles cases where we rounded up buys too much, or rounded down sells too much (when needing cash)
    if (projectedCash < -1.0) { // Tolerance of 1.0
        // Strategy: Reduce Buys or Increase Sells
        // Priority: Minimize deviation increase
        
        // Loop until fixed or no options
        while (projectedCash < -1.0) {
            val candidates = plans.filter { plan ->
                 if (plan.plannedShares > 0) true // Can always reduce buy
                 else abs(plan.plannedShares - 100) <= plan.fund.holdingQuantity // Can increase sell if we have holdings
            }
            
            if (candidates.isEmpty()) break
            
            val bestCandidate = candidates.minByOrNull { plan ->
                val currentDev = abs(plan.plannedShares - plan.rawShares)
                val nextDev = abs((plan.plannedShares - 100) - plan.rawShares)
                nextDev - currentDev // Minimize increase in deviation
            }
            
            if (bestCandidate != null) {
                bestCandidate.plannedShares -= 100
                projectedCash += 100 * bestCandidate.price
            } else {
                break
            }
        }
    }
    
    // Convert Plans to Items
    var actualNonCashUsed = 0.0
    plans.forEach { plan ->
        val finalShares = plan.plannedShares
        val finalAmount = finalShares * plan.price
        
        if (abs(finalAmount) > 0.01) {
            actualNonCashUsed += finalAmount
            
            val currentVal = plan.fund.holdingQuantity * plan.fund.currentPrice
            val currentRatio = if (totalAssets > 0) currentVal / totalAssets * 100 else 0.0
            val targetVal = currentVal + finalAmount
            val targetRatio = if (futureTotal > 0) targetVal / futureTotal * 100 else 0.0
            
            items.add(AdjustmentItem(
                fundId = plan.fund.id,
                fundName = plan.fund.name,
                assetType = plan.fund.type,
                currentRatio = currentRatio,
                targetRatio = targetRatio,
                idealRatio = (normTargets[plan.fund.id] ?: 0.0) * 100,
                adjustmentAmount = finalAmount,
                changeQuantity = finalShares
            ))
        }
    }
    
    // Process Cash Funds (Absorb residual)
    // Total Cash Change = newFundVal - actualNonCashUsed
    val totalCashChange = newFundVal - actualNonCashUsed
    
    if (abs(totalCashChange) > 0.01 && cashFunds.isNotEmpty()) {
        val targetCashFund = cashFunds.first()
        val currentVal = targetCashFund.holdingQuantity * targetCashFund.currentPrice
        val currentRatio = if (totalAssets > 0) currentVal / totalAssets * 100 else 0.0
        val targetVal = currentVal + totalCashChange
        val targetRatio = if (futureTotal > 0) targetVal / futureTotal * 100 else 0.0
        
        items.add(AdjustmentItem(
            fundId = targetCashFund.id,
            fundName = targetCashFund.name,
            assetType = targetCashFund.type,
            currentRatio = currentRatio,
            targetRatio = targetRatio,
            idealRatio = (normTargets[targetCashFund.id] ?: 0.0) * 100,
            adjustmentAmount = totalCashChange,
            changeQuantity = totalCashChange // Nominal shares for cash
        ))
    }
    
    // Sort items to match original funds order
    items.sortBy { item -> funds.indexOfFirst { it.id == item.fundId } }
    
    return items
}

class CurrencyAmountInputVisualTransformation(
    private val fixedCursorAtTheEnd: Boolean = true
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val formattedText = formatAmount(originalText)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (originalText.isEmpty()) return 0
                if (offset == 0) return 0
                
                // Count commas before the cursor position in transformed text
                // This is tricky because we don't know where the commas are relative to original without mapping
                // Simple approach: Map by counting digits
                
                // Let's iterate and build the mapping
                var originalIndex = 0
                var transformedIndex = 0
                while (originalIndex < offset && originalIndex < originalText.length && transformedIndex < formattedText.length) {
                    if (originalText[originalIndex] == formattedText[transformedIndex]) {
                        originalIndex++
                        transformedIndex++
                    } else {
                        // Comma inserted in transformed
                        transformedIndex++
                    }
                }
                return transformedIndex
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (formattedText.isEmpty()) return 0
                if (offset == 0) return 0
                
                // Iterate to find original index
                var originalIndex = 0
                var transformedIndex = 0
                while (transformedIndex < offset && transformedIndex < formattedText.length && originalIndex < originalText.length) {
                    if (formattedText[transformedIndex] == originalText[originalIndex]) {
                        originalIndex++
                        transformedIndex++
                    } else {
                         // Skip comma in transformed
                        transformedIndex++
                    }
                }
                return originalIndex
            }
        }

        return TransformedText(
            AnnotatedString(formattedText),
            offsetMapping
        )
    }

    private fun formatAmount(text: String): String {
        if (text.isEmpty()) return ""
        val clean = text.replace(",", "")
        if (clean == "-") return "-"
        if (clean == ".") return "."
        if (clean == "-.") return "-."
        
        return try {
            val parts = clean.split(".")
            val integerPart = parts[0]
            val decimalPart = if (parts.size > 1) "." + parts[1] else ""
            
            val isNegative = integerPart.startsWith("-")
            val absInteger = if (isNegative) integerPart.substring(1) else integerPart
            
            if (absInteger.isEmpty()) return clean // e.g. ".5" or "-.5"
            
            // Format integer part with commas
            val formattedInteger = StringBuilder()
            val len = absInteger.length
            for (i in 0 until len) {
                if (i > 0 && (len - i) % 3 == 0) {
                    formattedInteger.append(",")
                }
                formattedInteger.append(absInteger[i])
            }
            
            (if (isNegative) "-" else "") + formattedInteger.toString() + decimalPart
        } catch (e: Exception) {
            text
        }
    }
}
