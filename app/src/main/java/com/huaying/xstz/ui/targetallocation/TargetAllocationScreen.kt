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
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.component.ThousandSeparatorTransformation
import com.huaying.xstz.ui.theme.*
import com.huaying.xstz.util.AppConstants
import com.huaying.xstz.util.HoldingsCalculator
import kotlin.math.roundToInt
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
    val focusManager = LocalFocusManager.current

    val displayFunds = if (uiState.isLoading) {
        initialFunds.sortedWith(
            compareBy<Fund> { it.type.ordinal }
                .thenByDescending { it.holdingQuantity * it.currentPrice }
        )
    } else uiState.editedFunds
    val displayTotalAssets = if (uiState.isLoading) {
        HoldingsCalculator.calculateTotalAssets(initialFunds)
    } else uiState.totalAssets
    val displayNonCashTotalRatio = if (uiState.isLoading) {
        HoldingsCalculator.calculateNonCashTotalRatio(initialFunds)
    } else uiState.nonCashTotalRatio
    val displayCashRatio = if (uiState.isLoading) {
        (AppConstants.PERCENTAGE_BASE - displayNonCashTotalRatio).coerceAtLeast(AppConstants.ZERO_DOUBLE)
    } else uiState.cashRatio
    val displayIsValid = if (uiState.isLoading) {
        displayNonCashTotalRatio <= AppConstants.PERCENTAGE_BASE && displayNonCashTotalRatio >= AppConstants.ZERO_DOUBLE
    } else uiState.isValid

    val lazyListState = rememberLazyListState()

    var isSaving by remember { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is TargetAllocationEvent.SaveSuccess -> {
                    showSaveSuccess = true
                    kotlinx.coroutines.delay(1500)
                    showSaveSuccess = false
                    isSaving = false
                    onBack()
                }
                is TargetAllocationEvent.SaveError -> {
                    isSaving = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        OperationLogger.logPageView("目标占比配置")
    }

    // 使用 Box 布局：底部区域固定在底部，内容区域可滚动
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        // 上方可滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 160.dp) // 为底部固定区域留出空间
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        "调整各基金目标占比，现金占比将自动计算",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkMode) DarkTextSecondary else LightTextSecondary
                    )
                }
            }

            // 可滚动的基金列表
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

        // 底部固定区域：占比汇总 + 保存按钮
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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

                if (!displayIsValid || uiState.saveError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (!displayIsValid) "非现金资产总和不能超过100%，无法保存" else "保存失败，请重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 保存按钮：Material3 Button，与 AddFundScreen/RebalanceScreen 统一
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        if (!displayIsValid) {
                            viewModel.setSaveError()
                            return@Button
                        }
                        isSaving = true
                        OperationLogger.logButtonClick("保存目标配置", "目标占比配置")
                        viewModel.clearSaveError()
                        viewModel.save()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showSaveSuccess) SuccessGreen else BrandBlue,
                        disabledContainerColor = BrandBlue.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (showSaveSuccess) "保存成功" else "保存",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val focusManager = LocalFocusManager.current

    val actualRatio = HoldingsCalculator.calculateCurrentRatio(fund, totalAssets)

    var inputValue by remember(fund.id) {
        mutableStateOf("%.2f".format(Locale.CHINA, ratioPercent))
    }
    LaunchedEffect(ratioPercent) {
        val currentParsed = inputValue.toDoubleOrNull() ?: 0.0
        if (kotlin.math.abs(ratioPercent - currentParsed) > 0.005) {
            inputValue = "%.2f".format(Locale.CHINA, ratioPercent)
        }
    }
    var isError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

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
                                        // 限制小数点后最多两位
                                        val limited = if (filtered.contains('.')) {
                                            val parts = filtered.split('.', limit = 2)
                                            if (parts.size == 2 && parts[1].length > 2) {
                                                parts[0] + "." + parts[1].take(2)
                                            } else {
                                                filtered
                                            }
                                        } else {
                                            filtered
                                        }
                                        val value = limited.toDoubleOrNull()
                                        if (value != null) {
                                            if (value <= AppConstants.PERCENTAGE_BASE) {
                                                inputValue = limited
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
                                            inputValue = limited
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .widthIn(min = 40.dp, max = 60.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused
                                        if (!focusState.isFocused) {
                                            val value = inputValue.toDoubleOrNull() ?: 0.0
                                            inputValue = "%.2f".format(Locale.CHINA, value)
                                        }
                                    },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = assetColor
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                            focusManager.clearFocus()
                            val rounded = (newValue * 100).roundToInt() / 100.0
                            onRatioChange(rounded)
                            inputValue = "%.2f".format(Locale.CHINA, rounded)
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
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppConstants.PRESET_RATIOS.forEachIndexed { index, preset ->
                        val isSelected = kotlin.math.abs(ratioPercent - preset) < AppConstants.PRESET_RATIO_TOLERANCE
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppConstants.PRESET_RATIOS.size
                            ),
                            onClick = {
                                focusManager.clearFocus()
                                onRatioChange(preset.toDouble())
                                inputValue = "%.2f".format(Locale.CHINA, preset.toDouble())
                                isError = false
                            },
                            selected = isSelected,
                            label = {
                                Text(
                                    text = "$preset%",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        )
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
