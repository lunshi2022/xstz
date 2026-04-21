package com.huaying.xstz.ui.fund

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.ui.draw.shadow

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.data.repository.OperationLogger
import android.util.Log
import java.util.Locale

// 引入主题颜色
import com.huaying.xstz.ui.theme.*

// 分隔线颜色
private val DarkDividerColor = Color(0xFF2A2A2A)
private val LightDividerColor = Color(0xFFF0F0F0)

// 安全的数值格式化函数
private fun safeFormatDouble(value: Double, format: String, defaultValue: String = "0.00"): String {
    return try {
        if (value.isNaN() || value.isInfinite()) {
            defaultValue
        } else {
            String.format(Locale.CHINA, format, value)
        }
    } catch (e: Exception) {
        Log.e("FundDetail", "Error formatting value: $value", e)
        defaultValue
    }
}

@Composable
fun EditTargetRatioDialog(
    currentRatio: Double,
    totalTargetRatio: Double = 0.0,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val safeCurrentRatio = if (currentRatio.isNaN() || currentRatio.isInfinite()) 0.0 else currentRatio
    var sliderValue by remember { mutableFloatStateOf((safeCurrentRatio * 100).toFloat().coerceIn(0f, 100f)) }
    var inputValue by remember { mutableStateOf(safeFormatDouble(sliderValue.toDouble(), "%.2f")) }
    var isError by remember { mutableStateOf(false) }

    val newTotalRatio = if (totalTargetRatio.isNaN() || totalTargetRatio.isInfinite())
        (sliderValue / 100.0)
    else
        totalTargetRatio - currentRatio + (sliderValue / 100.0)
    val isValidTotal = kotlin.math.abs(newTotalRatio - 1.0) < 0.01

    val surfaceColor = MaterialTheme.colorScheme.surface
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val priceBoxColor = if (isDarkMode) DarkPriceBox else LightPriceBox

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(surfaceColor)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).imePadding()
            ) {
                Text(
                    text = "目标占比",
                    color = textSecondaryColor,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "当前设置: ${safeFormatDouble(currentRatio * 100, "%.2f")}%",
                    color = textPrimaryColor,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column {
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            inputValue = safeFormatDouble(newValue.toDouble(), "%.2f")
                            isError = false
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = BrandBlue,
                            activeTrackColor = BrandBlue,
                            inactiveTrackColor = priceBoxColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 10, 20, 30, 50).forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(priceBoxColor)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            sliderValue = preset.toFloat()
                                            inputValue = safeFormatDouble(preset.toDouble(), "%.0f")
                                            isError = false
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$preset%",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = if (sliderValue == preset.toFloat()) BrandBlue
                                           else textPrimaryColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "自定义",
                        color = textSecondaryColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(priceBoxColor)
                    ) {
                        BasicTextField(
                            value = inputValue,
                            onValueChange = { newText ->
                                val filteredText = if (newText.count { it == '.' } <= 1) {
                                    newText.filter { it.isDigit() || it == '.' }
                                } else {
                                    newText.filter { it.isDigit() }
                                }
                                inputValue = filteredText
                                filteredText.toDoubleOrNull()?.let { value ->
                                    if (value in 0.0..100.0) {
                                        sliderValue = value.toFloat()
                                        isError = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .width(80.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = BrandBlue,
                                textAlign = TextAlign.End
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    inputValue.toDoubleOrNull()?.let { value ->
                                        if (value in 0.0..100.0) {
                                            sliderValue = value.toFloat()
                                            isError = false
                                        } else {
                                            isError = true
                                        }
                                    }
                                }
                            )
                        )
                    }
                    Text(
                        "%",
                        color = textPrimaryColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "请输入有效的数字 (0-100)",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isValidTotal) {
                                Color(0xFF10B981).copy(alpha = 0.1f)
                            } else {
                                DangerRed.copy(alpha = 0.1f)
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "设置值",
                                color = textSecondaryColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${safeFormatDouble(sliderValue.toDouble(), "%.2f")}%",
                                color = textPrimaryColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "所有基金目标总和",
                                color = textSecondaryColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${safeFormatDouble(if (newTotalRatio.isNaN() || newTotalRatio.isInfinite()) 0.0 else newTotalRatio * 100, "%.2f")}%",
                                color = if (isValidTotal) SuccessGreen else DangerRed,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isValidTotal) {
                                "✓ 目标占比设置合理"
                            } else {
                                "⚠ 请前往「再平衡」页面调整所有基金目标总和至100%"
                            },
                            color = if (isValidTotal) SuccessGreen else DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(priceBoxColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "取消",
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = textPrimaryColor,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBlue)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val ratio = inputValue.toDoubleOrNull()
                                    if (ratio != null && !ratio.isNaN() && !ratio.isInfinite() && ratio in 0.0..100.0) {
                                        onConfirm(ratio / 100.0)
                                    } else {
                                        isError = true
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "确定",
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FundDetailScreen(
    fund: Fund,
    transactions: List<Transaction>,
    totalTargetRatio: Double = 0.0,
    isPrivacyMode: Boolean = false,
    isDarkMode: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit,
    onEditHoldingQuantity: (Fund) -> Unit,
    onClearCash: () -> Unit,
    onEditTargetRatio: (Fund, Double) -> Unit,
    onEditAssetType: (Fund, AssetType) -> Unit,
    onDelete: () -> Unit,
    onNavigateToTransactionHistory: () -> Unit,
    onNavigateToTargetAllocation: () -> Unit = {}
) {
    Log.d("FundDetail", "Rendering FundDetailScreen for fund: ${fund.name} (id: ${fund.id})")

    // 记录页面查看
    LaunchedEffect(fund.id) {
        OperationLogger.logPageView("基金详情", "${fund.name} (${fund.code})")
    }

    var showRatioDialog by remember { mutableStateOf(false) }
    var showClearCashDialog by remember { mutableStateOf(false) }

    val assetTypes = remember { AssetType.entries.filter { it != AssetType.CASH } }
    val assetTypeOptions = remember { assetTypes.map { it.toDisplayName() } }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dangerBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
    val dividerColor = if (isDarkMode) DarkDividerColor else LightDividerColor.copy(alpha = 0.1f)

    // 使用普通Column替代Scaffold，避免Scaffold的默认动画
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                OperationLogger.logBack("基金详情")
                onBack()
            }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimaryColor)
            }
            Text(
                fund.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textPrimaryColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailCard(title = "基本信息", isDarkMode = isDarkMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fund.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = textPrimaryColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = fund.code,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandBlue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                if (fund.type == AssetType.CASH) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "类型",
                            color = textSecondaryColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            fund.type.toDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandBlue
                        )
                    }
                } else {
                    DropdownRow(
                        label = "类型",
                        currentValue = fund.type.toDisplayName(),
                        options = assetTypeOptions,
                        onOptionSelected = { index ->
                            if (index >= 0 && index < assetTypes.size) {
                                val oldType = fund.type.toDisplayName()
                                val newType = assetTypes[index].toDisplayName()
                                OperationLogger.logSettingChange("资产类型", oldType, newType)
                                onEditAssetType(fund, assetTypes[index])
                            } else {
                                Log.e("FundDetail", "Invalid asset type index: $index, array size: ${assetTypes.size}")
                            }
                        }
                    )
                }
            }

            DetailCard(title = "持仓信息", isDarkMode = isDarkMode) {
                val isCash = fund.type == AssetType.CASH
                val quantityLabel = if (isCash) "金额" else "持仓份额"
                val quantityValue = if (isPrivacyMode) {
                    "****"
                } else if (isCash) {
                    "¥${safeFormatDouble(if (fund.holdingQuantity.isNaN() || fund.holdingQuantity.isInfinite()) 0.0 else fund.holdingQuantity, "%.2f")}"
                } else {
                    safeFormatDouble(if (fund.holdingQuantity.isNaN() || fund.holdingQuantity.isInfinite()) 0.0 else fund.holdingQuantity, "%.0f")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        quantityLabel,
                        color = textSecondaryColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            OperationLogger.logButtonClick("修改持仓", "基金详情")
                            onEditHoldingQuantity(fund)
                        }
                    ) {
                        Text(
                            quantityValue,
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandBlue
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondaryColor.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!isCash) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val costPrice = if (fund.holdingQuantity > 0.0 && !fund.totalCost.isNaN() && !fund.totalCost.isInfinite() && !fund.holdingQuantity.isNaN() && !fund.holdingQuantity.isInfinite()) {
                        fund.totalCost / fund.holdingQuantity
                    } else 0.0

                    Row(
                        modifier = Modifier.height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PriceBox(
                            label = "成本价格",
                            value = if (isPrivacyMode) "****" else "¥${safeFormatDouble(if (costPrice.isNaN() || costPrice.isInfinite()) 0.0 else costPrice, "%.3f")}",
                            change = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            isDarkMode = isDarkMode
                        )

                        val currentPrice = if (fund.currentPrice.isNaN() || fund.currentPrice.isInfinite()) 0.0 else fund.currentPrice
                        val priceChange = if (costPrice > 0.0 && !currentPrice.isNaN() && !currentPrice.isInfinite()) {
                            ((currentPrice - costPrice) / costPrice * 100)
                        } else 0.0
                        val safePriceChange = if (priceChange.isNaN() || priceChange.isInfinite()) 0.0 else priceChange
                        val changeText = when {
                            safePriceChange > 0 -> "+${safeFormatDouble(safePriceChange, "%.2f")}%"
                            safePriceChange < 0 -> "${safeFormatDouble(safePriceChange, "%.2f")}%"
                            else -> "0.00%"
                        }

                        PriceBox(
                            label = "当前价格",
                            value = if (isPrivacyMode) "****" else "¥${safeFormatDouble(if (currentPrice.isNaN() || currentPrice.isInfinite()) 0.0 else currentPrice, "%.3f")}",
                            change = changeText,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            isDarkMode = isDarkMode
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = dividerColor
                )
                Spacer(modifier = Modifier.height(16.dp))

                val safeTargetRatio = if (fund.targetRatio.isNaN() || fund.targetRatio.isInfinite()) 0.0 else fund.targetRatio
                val targetRatioValue = if (isPrivacyMode) "**%" else "${safeFormatDouble(safeTargetRatio * 100, "%.2f")}%"
                val isGlobalRatioValid = if (totalTargetRatio.isNaN() || totalTargetRatio.isInfinite()) false else kotlin.math.abs(totalTargetRatio - 1.0) < 0.01

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            OperationLogger.logButtonClick("修改目标比例", "基金详情")
                            onNavigateToTargetAllocation()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "目标占比",
                        color = textSecondaryColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                targetRatioValue,
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandBlue
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondaryColor.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!isPrivacyMode && !isGlobalRatioValid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(dangerBgColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠ 所有基金目标总和: ${safeFormatDouble(if (totalTargetRatio.isNaN() || totalTargetRatio.isInfinite()) 0.0 else totalTargetRatio * 100, "%.2f")}%，需调整为100%",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed
                            )
                        }
                    }
                }
            }

            // 使用主题阴影颜色
            val cardShadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.4f else 0.08f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isDarkMode) 4.dp else 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = cardShadowColor,
                        ambientColor = cardShadowColor
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            OperationLogger.logButtonClick("交易记录", "基金详情")
                            onNavigateToTransactionHistory()
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "交易记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (transactions.isNotEmpty()) {
                            Text(
                                "${transactions.size}笔记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // 底部删除按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = if (isDarkMode) 4.dp else 2.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = cardShadowColor,
                            ambientColor = cardShadowColor
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (fund.type == AssetType.CASH) {
                                    OperationLogger.logButtonClick("清空现金", "基金详情")
                                    showClearCashDialog = true
                                } else {
                                    OperationLogger.logButtonClick("删除基金", "基金详情")
                                    onDelete()
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 48.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (fund.type == AssetType.CASH) "清空金额" else "删除资产",
                            color = DangerRed,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (showRatioDialog) {
            EditTargetRatioDialog(
                currentRatio = fund.targetRatio,
                totalTargetRatio = totalTargetRatio,
                isDarkMode = isDarkMode,
                onDismiss = { showRatioDialog = false },
                onConfirm = { newRatio ->
                    onEditTargetRatio(fund, newRatio)
                    showRatioDialog = false
                }
            )
        }

        if (showClearCashDialog) {
            AlertDialog(
                onDismissRequest = { showClearCashDialog = false },
                title = { Text("确认清空") },
                text = { Text("确定要清空现金账户吗？此操作无法撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCashDialog = false
                            onClearCash()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("清空") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCashDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    isDarkMode: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 使用主题阴影颜色，适配动态主题
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.4f else 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkMode) 4.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                color = textSecondaryColor,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun PriceBox(
    label: String,
    value: String,
    change: String?,
    modifier: Modifier,
    isDarkMode: Boolean = false
) {
    // 使用主题色替代固定颜色，适配动态主题
    val priceBoxColor = MaterialTheme.colorScheme.surfaceVariant
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    // 使用主题阴影颜色
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.3f else 0.05f)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDarkMode) 2.dp else 1.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            )
            .clip(RoundedCornerShape(12.dp))
            .background(priceBoxColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                label,
                color = textSecondaryColor,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = textPrimaryColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = change ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    change.isNullOrEmpty() -> Color.Transparent
                    change.startsWith("+") -> Color(0xFFEF4444)
                    change.startsWith("-") -> Color(0xFF10B981)
                    else -> textSecondaryColor
                }
            )
        }
    }
}

@Composable
private fun DropdownRow(
    label: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val priceBoxColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = textSecondaryColor,
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBlue
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textSecondaryColor.copy(alpha = 0.7f)
                )
            }

            if (expanded) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, 8),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
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
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            onOptionSelected(index)
                                            expanded = false
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        option,
                                        color = textPrimaryColor,
                                        style = if (currentValue == option) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
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
