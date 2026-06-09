package com.huaying.xstz.ui.targetallocation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.component.ThousandSeparatorTransformation
import com.huaying.xstz.ui.theme.*
import com.huaying.xstz.util.AppConstants
import java.util.Locale

/**
 * 全局仓位配比配置页面
 * 允许用户协同调整所有基金的目标占比，确保总和为100%
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAllocationScreen(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    initialFunds: List<Fund> = emptyList(),
    viewModel: TargetAllocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 当 ViewModel 仍在加载时，使用预加载数据计算显示值，避免导航动画期间布局跳变
    val displayFunds = if (uiState.isLoading) {
        initialFunds.sortedWith(
            compareBy<Fund> { it.type.ordinal }
                .thenByDescending { it.holdingQuantity * it.currentPrice }
        )
    } else uiState.editedFunds
    val displayTotalAssets = if (uiState.isLoading) {
        initialFunds.sumOf { it.holdingQuantity * it.currentPrice }
    } else uiState.totalAssets
    val displayNonCashTotalRatio = if (uiState.isLoading) {
        initialFunds.filter { it.type != AssetType.CASH }.sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
    } else uiState.nonCashTotalRatio
    val displayCashRatio = if (uiState.isLoading) {
        (AppConstants.PERCENTAGE_BASE - displayNonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)
    } else uiState.cashRatio
    val displayIsValid = if (uiState.isLoading) {
        displayNonCashTotalRatio <= AppConstants.PERCENTAGE_BASE && displayNonCashTotalRatio >= AppConstants.ZERO_DOUBLE
    } else uiState.isValid

    val lazyListState = rememberLazyListState()

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("目标占比配置")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.background
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
                    "目标占比配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = 16.dp)
                ) {
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
                                ThemeConstants.Format.PERCENT_2F.format(Locale.CHINA, displayNonCashTotalRatio),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (displayIsValid) SuccessGreen else DangerRed
                            )
                            if (!displayIsValid) {
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
                            ThemeConstants.Format.PERCENT_2F.format(Locale.CHINA, displayCashRatio),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }

                    if (!displayIsValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "非现金资产总和不能超过100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            OperationLogger.logButtonClick("保存目标配置", "目标占比配置")
                            viewModel.save(onComplete = onBack)
                        },
                        enabled = displayIsValid,
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
                        "调整各基金目标占比，现金占比将自动计算",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                    )
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayFunds, key = { it.id }) { fund ->
                    FundRatioCard(
                        fund = fund,
                        totalAssets = displayTotalAssets,
                        isDarkMode = isDarkMode,
                        isReadOnly = fund.type == AssetType.CASH,
                        onRatioChange = { newRatio ->
                            viewModel.updateRatio(fund.id, newRatio)
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
    val ratioPercent = fund.targetRatio * AppConstants.PERCENTAGE_BASE

    val currentValue = fund.holdingQuantity * fund.currentPrice
    val actualRatio = if (totalAssets > 0) (currentValue / totalAssets * 100) else 0.0

    var inputValue by remember(ratioPercent) {
        mutableStateOf("%.2f".format(Locale.CHINA, ratioPercent))
    }
    var isError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                if (isReadOnly) {
                    Text(
                        ThemeConstants.Format.PERCENT_2F.format(Locale.CHINA, ratioPercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                } else {
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
                                            if (value <= AppConstants.PERCENTAGE_BASE) {
                                                inputValue = filtered
                                                if (value >= 0.0) {
                                                    onRatioChange(value)
                                                    isError = false
                                                } else {
                                                    isError = true
                                                }
                                            } else {
                                                inputValue = AppConstants.PERCENTAGE_BASE_INT.toString()
                                                onRatioChange(AppConstants.PERCENTAGE_BASE)
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
                                ),
                                visualTransformation = ThousandSeparatorTransformation()
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

            if (!isReadOnly) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                        Text(
                            ThemeConstants.Format.PERCENT_1F.format(Locale.CHINA, actualRatio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                        )
                    }

                    Slider(
                        value = ratioPercent.toFloat(),
                        onValueChange = { newValue ->
                            onRatioChange(newValue.toDouble())
                            inputValue = newValue.toDouble().toString()
                            isError = false
                        },
                        valueRange = AppConstants.ZERO_FLOAT..AppConstants.PERCENTAGE_BASE_INT.toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = assetColor,
                            activeTrackColor = assetColor,
                            inactiveTrackColor = if (isDarkMode) DarkPriceBox else LightPriceBox
                        )
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "目标",
                            style = MaterialTheme.typography.labelSmall,
                            color = assetColor
                        )
                        Text(
                            ThemeConstants.Format.PERCENT_1F.format(Locale.CHINA, ratioPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = assetColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppConstants.PRESET_RATIOS.forEach { preset ->
                        val isSelected = kotlin.math.abs(ratioPercent - preset) < AppConstants.PRESET_RATIO_TOLERANCE
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
