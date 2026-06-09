package com.huaying.xstz.ui.rebalance

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.component.ThousandSeparatorTransformation
import com.huaying.xstz.ui.theme.ThemeConstants
import com.huaying.xstz.ui.theme.getColorForAssetType
import com.huaying.xstz.util.AppConstants
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebalanceScreen(
    darkTheme: Boolean = false,
    onNavigateToTargetAllocation: () -> Unit = {},
    onBack: () -> Unit = {},
    initialFunds: List<Fund> = emptyList(),
    viewModel: RebalanceViewModel = hiltViewModel()
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // 当 ViewModel 仍在 Loading 时，使用预加载数据创建合成状态，避免导航动画期间布局跳变
    val state = when (uiState) {
        is RebalanceUiState.Success -> uiState as RebalanceUiState.Success
        is RebalanceUiState.Loading -> RebalanceUiState.Success(
            funds = initialFunds.sortedWith(
                compareBy<Fund> { it.type.ordinal }
                    .thenByDescending { it.holdingQuantity * it.currentPrice }
            ),
            totalAssets = initialFunds.sumOf { it.holdingQuantity * it.currentPrice }
        )
    }

    darkTheme

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("再平衡")
    }

    // 是否为 ViewModel 真实加载完成的数据（非预加载合成数据）
    val isRealData = uiState is RebalanceUiState.Success
    val funds = state.funds
    val totalAssets = state.totalAssets
    val adjustmentList = state.adjustmentList
    val thresholdInput = state.thresholdInput
    val thresholdMode = state.thresholdMode
    val selectedLogicMode = state.selectedLogicMode
    val newFundInput = state.newFundInput

    var showThresholdDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
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
                start = ThemeConstants.CardPadding,
                top = AppConstants.TOP_BAR_OFFSET_DP.dp,
                end = ThemeConstants.CardPadding,
                bottom = AppConstants.BOTTOM_NAV_OFFSET_DP.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Target Ratio Configuration Entry
            item {
                val totalTargetRatio = funds.filter { it.type != AssetType.CASH }.sumOf { it.targetRatio }
                val isValidRatio = abs(totalTargetRatio - 1.0) < AppConstants.RATIO_EQUALITY_THRESHOLD
                val totalAssetsLocal = funds.sumOf { it.holdingQuantity * it.currentPrice }
                val hasSevereDeviation = if (totalAssetsLocal > 0) {
                    funds.any { fund ->
                        val currentRatio = (fund.holdingQuantity * fund.currentPrice) / totalAssetsLocal * 100
                        val targetRatio = fund.targetRatio * 100
                        val deviation = abs(currentRatio - targetRatio)
                        deviation > AppConstants.SEVERE_DEVIATION_THRESHOLD
                    }
                } else false

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
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
                            if (!isValidRatio) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "非现金资产占比: ${ThemeConstants.Format.PERCENT_2F.format(Locale.CHINA, totalTargetRatio * AppConstants.PERCENTAGE_BASE)} ⚠ 需调整",
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
                        containerColor = MaterialTheme.colorScheme.surface
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
                                            viewModel.setThresholdMode(0)
                                            if (thresholdInput.isNotEmpty()) {
                                                val currentVal = thresholdInput.toIntOrNull() ?: 20
                                                if (currentVal < AppConstants.THRESHOLD_PERCENT_MIN) viewModel.setThresholdInput(AppConstants.THRESHOLD_PERCENT_MIN.toString())
                                                if (currentVal > AppConstants.THRESHOLD_PERCENT_MAX) viewModel.setThresholdInput(AppConstants.THRESHOLD_PERCENT_MAX.toString())
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
                                            viewModel.setThresholdMode(1)
                                            if (thresholdInput.isNotEmpty()) {
                                                val currentVal = thresholdInput.toIntOrNull() ?: 5
                                                if (currentVal < AppConstants.THRESHOLD_POINT_MIN) viewModel.setThresholdInput(AppConstants.THRESHOLD_POINT_MIN.toString())
                                                if (currentVal > AppConstants.THRESHOLD_POINT_MAX) viewModel.setThresholdInput(AppConstants.THRESHOLD_POINT_MAX.toString())
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

                            val thresholdMinVal = if (thresholdMode == 0) 5 else 1
                            val thresholdMaxVal = if (thresholdMode == 0) 25 else 10
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if ((thresholdInput.toIntOrNull() ?: 0) !in thresholdMinVal..thresholdMaxVal && thresholdInput.isNotEmpty()) 2.dp else 1.dp,
                                        color = if ((thresholdInput.toIntOrNull() ?: 0) !in thresholdMinVal..thresholdMaxVal && thresholdInput.isNotEmpty())
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    BasicTextField(
                                        value = thresholdInput,
                                        onValueChange = { input ->
                                            if (input.all { it.isDigit() } && input.length <= AppConstants.THRESHOLD_INPUT_MAX_LENGTH) {
                                                viewModel.setThresholdInput(input)
                                            }
                                        },
                                        modifier = Modifier.widthIn(min = 30.dp, max = 50.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                        ),
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = if (thresholdMode == 0) "%" else "pt",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }

                        val inputVal = thresholdInput.toIntOrNull()
                        val minVal = if (thresholdMode == 0) 5 else 1
                        val maxVal = if (thresholdMode == 0) 25 else 10

                        if (!isRealData) {
                            // 预加载数据阶段，不显示阈值提示（ViewModel 尚未加载偏好设置）
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "新增/取出资金",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val inputVal = newFundInput.toDoubleOrNull() ?: 0.0
                        val isWithdrawError = inputVal < 0 && abs(inputVal) > totalAssets

                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isWithdrawError) 2.dp else 1.dp,
                                        color = if (isWithdrawError)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "¥",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    BasicTextField(
                                        value = newFundInput,
                                        onValueChange = { input ->
                                            if (input.matches(Regex("^-?\\d*(\\.\\d{0,2})?$")) ||
                                                input == "-" || input == "." || input == "-.") {
                                                viewModel.setNewFundInput(input)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        visualTransformation = ThousandSeparatorTransformation(),
                                        decorationBox = { innerTextField ->
                                            if (newFundInput.isEmpty()) {
                                                Text(
                                                    text = "正数买入，负数卖出",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            if (isWithdrawError) {
                                Text(
                                    text = "取出金额不能超过总资产 (${ThemeConstants.Format.CURRENCY_2F.format(totalAssets)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Strategy Options
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
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
                            checked = selectedLogicMode == AppConstants.LOGIC_MODE_FULL,
                            onCheckedChange = { checked ->
                                viewModel.setLogicMode(if (checked) AppConstants.LOGIC_MODE_FULL else AppConstants.LOGIC_MODE_SMART)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // 6. Results
            if (adjustmentList.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
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

                            Button(
                                onClick = {
                                    val strategy = if (selectedLogicMode == AppConstants.LOGIC_MODE_FULL) RebalanceStrategy.FULL_REBALANCE else RebalanceStrategy.SMART
                                    viewModel.executeRebalance(strategy) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("交易已完成")
                                        }
                                    }
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
            title = {
                Text(
                    "偏离度阈值说明",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "A",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "百分比模式",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "目标比例 ± (目标比例 × 阈值%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "适合所有比例。例：目标25%，阈值20%，允许范围20%-30%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.secondary,
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "B",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "百分点模式",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                "目标比例 ± 阈值百分点",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "适合大比例资产。例：目标25%，阈值5%，允许范围20%-30%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "重要提示",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                "偏离度阈值始终以「目标比例」为固定锚点，不会随资产波动而漂移。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "交易规则",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• 最小交易单位：A股/ETF 最低1手(100股)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                "• 流动性检查：买入需确保有足够流动资金",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThresholdDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "我知道了",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
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
    val isBuy = item.adjustmentAmount >= 0
    val color = if (isBuy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bgColor = if (isBuy)
        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBuy) "买" else "卖",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.fundName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${ThemeConstants.Format.PERCENT_1F.format(item.currentRatio)} → ${ThemeConstants.Format.PERCENT_1F.format(item.targetRatio)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = ThemeConstants.Format.CURRENCY_2F.format(abs(item.adjustmentAmount)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (item.assetType != AssetType.CASH && abs(item.changeQuantity) > 0) {
                    Text(
                        text = "${if (item.changeQuantity > 0) "+" else ""}${ThemeConstants.Format.NUMBER_0F.format(item.changeQuantity)}股",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
