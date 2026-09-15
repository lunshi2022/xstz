package com.huaying.xstz.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.animation.interaction.clickableScale
import com.huaying.xstz.ui.theme.BrandBlue
import com.huaying.xstz.ui.theme.DangerRed
import com.huaying.xstz.ui.theme.DarkPriceBox
import com.huaying.xstz.ui.theme.LightPriceBox
import com.huaying.xstz.ui.theme.SuccessGreen
import java.util.Locale
import kotlin.math.abs

/**
 * 持仓数量编辑对话框
 * 用于修改基金持仓份额或现金余额
 *
 * @param fund 要编辑的基金对象，如果为null则不显示对话框
 * @param darkTheme 是否使用深色主题
 * @param onDismiss 对话框关闭回调
 * @param onConfirm 确认回调，参数为(数量变化, 价格)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHoldingQuantityDialog(
    fund: Fund?,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?) -> Unit
) {
    if (fund == null) return

    val isCash = fund.type == AssetType.CASH
    var inputQuantity by remember { mutableStateOf("") }
    var inputPrice by remember { mutableStateOf(fund.currentPrice.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isWithdrawMode by remember { mutableStateOf(false) }

    val inputVal = inputQuantity.toDoubleOrNull()
    val currentQuantity = fund.holdingQuantity
    val delta = if (inputVal != null) (if (isCash) {
        if (isWithdrawMode) -inputVal else inputVal
    } else inputVal - currentQuantity) else 0.0
    val showPriceInput = !isCash && abs(delta) > 0.001

    val previewBalance = if (isCash && inputVal != null) {
        currentQuantity + if (isWithdrawMode) -inputVal else inputVal
    } else null
    val isExceedingBalance = previewBalance != null && previewBalance < -0.005

    LaunchedEffect(inputQuantity, isWithdrawMode) {
        if (inputVal != null) {
            if (!isCash) {
                if (inputVal < 0) {
                    errorMessage = "持仓份额不能为负数"
                } else if (inputQuantity.contains(".")) {
                    errorMessage = "持仓份额必须为整数"
                } else if (inputVal % 100 != 0.0) {
                    errorMessage = "持仓份额必须是100的整数倍"
                } else {
                    errorMessage = null
                }
            } else {
                if (inputVal < 0) {
                    errorMessage = "请输入正数金额"
                } else if (isExceedingBalance) {
                    errorMessage = "取出金额不能超过当前余额 (¥%,.2f)".format(Locale.CHINA, currentQuantity)
                } else {
                    errorMessage = null
                }
            }
        } else {
            errorMessage = null
        }
    }

    val isInputError = errorMessage != null || isExceedingBalance
    val isPriceError = showPriceInput && (inputPrice.toDoubleOrNull() == null || inputPrice.toDoubleOrNull()!! < 0)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(AnimationConstants.Duration.FAST)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut(animationSpec = tween(AnimationConstants.Duration.FAST)) + scaleOut(targetScale = 0.9f)
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).imePadding()
                ) {
                    Text(
                        text = if (isCash) "存入/取出资金" else "修改持仓份额",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 当前余额/持仓
                    Text(
                        text = if (isCash) "当前余额: ¥%,.2f".format(Locale.CHINA, currentQuantity)
                        else "当前持仓: %.0f".format(Locale.CHINA, if (currentQuantity.isNaN() || currentQuantity.isInfinite()) 0.0 else currentQuantity),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 现金模式：存入/取出切换 + 变动后余额预览
                    if (isCash) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 存入/取出切换（与项目其他界面统一使用 Material3 SegmentedButton）
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { isWithdrawMode = false },
                                selected = !isWithdrawMode,
                                label = {
                                    Text(
                                        "存入",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (!isWithdrawMode) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            )
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { isWithdrawMode = true },
                                selected = isWithdrawMode,
                                label = {
                                    Text(
                                        "取出",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isWithdrawMode) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            )
                        }

                        // 变动后余额预览（始终占位，避免错误出现时布局抖动）
                        Spacer(modifier = Modifier.height(12.dp))
                        val previewColor = if (isExceedingBalance) {
                            MaterialTheme.colorScheme.error
                        } else if (isWithdrawMode) {
                            SuccessGreen
                        } else {
                            DangerRed
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "变动后余额",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (previewBalance != null && inputVal != null && inputVal > 0)
                                    "¥%,.2f".format(Locale.CHINA, if (previewBalance.isNaN() || previewBalance.isInfinite()) 0.0 else previewBalance)
                                else "—",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (previewBalance != null && inputVal != null && inputVal > 0) previewColor
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = inputQuantity,
                        onValueChange = { raw ->
                            val filtered = if (isCash) {
                                // 现金：只允许数字和小数点，最多2位小数
                                raw.filter { it.isDigit() || it == '.' }
                                    .let { s ->
                                        val dotIndex = s.indexOf('.')
                                        if (dotIndex >= 0) s.substring(0, minOf(dotIndex + 3, s.length)) else s
                                    }
                            } else {
                                // 非现金：只允许整数
                                raw.filter { it.isDigit() }
                            }
                            inputQuantity = filtered
                        },
                        label = { Text(if (isCash) "金额" else "新持仓份额") },
                        singleLine = true,
                        isError = isInputError,
                        prefix = if (isCash) { { Text("¥") } } else null,
                        supportingText = {
                            // 始终占位，避免错误出现时布局抖动
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                minLines = 1
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = ThousandSeparatorTransformation()
                    )

                    if (showPriceInput) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { raw ->
                                // 价格：只允许数字和小数点，最多4位小数
                                val filtered = raw.filter { it.isDigit() || it == '.' }
                                    .let { s ->
                                        val dotIndex = s.indexOf('.')
                                        if (dotIndex >= 0) s.substring(0, minOf(dotIndex + 5, s.length)) else s
                                    }
                                inputPrice = filtered
                            },
                            label = { Text("成交单价") },
                            singleLine = true,
                            isError = isPriceError,
                            prefix = { Text("¥") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = ThousandSeparatorTransformation()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (delta > 0) "买入价格" else "卖出价格",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickableScale { onDismiss() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (darkTheme) DarkPriceBox else LightPriceBox
                        ) {
                            Text(
                                "取消",
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickableScale {
                                    val quantityVal = inputQuantity.toDoubleOrNull()
                                    val priceVal = if (showPriceInput) inputPrice.toDoubleOrNull() else null
                                    if (quantityVal != null && !isInputError) {
                                        // 现金模式：取出时传负数，存入时传正数
                                        val finalVal = if (isCash && isWithdrawMode) -quantityVal else quantityVal
                                        onConfirm(finalVal, priceVal)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isInputError) BrandBlue.copy(alpha = 0.4f) else BrandBlue
                        ) {
                            Text(
                                if (isCash) (if (isWithdrawMode) "取出" else "存入") else "确定",
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = if (isInputError) Color.White.copy(alpha = 0.6f) else Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
