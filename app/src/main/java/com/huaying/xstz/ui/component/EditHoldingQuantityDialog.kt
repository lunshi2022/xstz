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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.ui.animation.interaction.clickableScale
import com.huaying.xstz.ui.theme.BrandBlue
import com.huaying.xstz.ui.theme.DarkPriceBox
import com.huaying.xstz.ui.theme.LightPriceBox
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

    val inputVal = inputQuantity.toDoubleOrNull()
    val currentQuantity = fund.holdingQuantity
    val delta = if (inputVal != null) (if (isCash) inputVal else inputVal - currentQuantity) else 0.0
    val showPriceInput = !isCash && abs(delta) > 0.001

    val previewBalance = if (isCash && inputVal != null) currentQuantity + inputVal else null
    val isExceedingBalance = previewBalance != null && previewBalance < -0.005

    LaunchedEffect(inputQuantity) {
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
                errorMessage = null
            }
        } else {
            errorMessage = null
        }
    }

    val isInputError = errorMessage != null || isExceedingBalance
    val isPriceError = showPriceInput && (inputPrice.toDoubleOrNull() == null || inputPrice.toDoubleOrNull()!! < 0)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.9f)
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

                    Text(
                        text = if (isCash) "当前余额: ¥%,.2f".format(Locale.CHINA, currentQuantity)
                        else "当前持仓: %.0f".format(Locale.CHINA, if (currentQuantity.isNaN() || currentQuantity.isInfinite()) 0.0 else currentQuantity),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (isCash && previewBalance != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val color = if (isExceedingBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            text = "变动后余额: ¥%,.2f".format(Locale.CHINA, if (previewBalance.isNaN() || previewBalance.isInfinite()) 0.0 else previewBalance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                    }

                    if (isCash) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "提示: 直接输入金额，正数存入，负数取出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = inputQuantity,
                        onValueChange = { inputQuantity = it },
                        label = { Text(if (isCash) "变动金额" else "新持仓份额") },
                        singleLine = true,
                        isError = isInputError,
                        supportingText = {
                            if (errorMessage != null) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showPriceInput) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { inputPrice = it },
                            label = { Text("成交单价") },
                            singleLine = true,
                            isError = isPriceError,
                            prefix = { Text("¥") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
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
                                    if (quantityVal != null) {
                                        onConfirm(quantityVal, priceVal)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = BrandBlue
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
}
