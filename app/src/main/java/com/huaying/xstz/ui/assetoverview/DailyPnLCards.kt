package com.huaying.xstz.ui.assetoverview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.toDisplayName

@Composable
fun MergedDailyPnLCard(
    dailyPnLData: AssetOverviewViewModel.DailyPnLData?,
    viewModel: AssetOverviewViewModel,
    isPrivacyMode: Boolean = false,
    onFundClick: (AssetOverviewViewModel.FundDailyPnL) -> Unit = {}
) {
    val returnColor by animateColorAsState(
        targetValue = if (dailyPnLData != null && dailyPnLData.totalDailyReturn >= 0) {
            Color(0xFFEF4444)
        } else {
            com.huaying.xstz.ui.theme.SuccessGreen
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "returnColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部：日期和当日盈亏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (dailyPnLData != null) {
                            viewModel.formatSelectedDate(dailyPnLData.date)
                        } else "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (dailyPnLData != null) {
                            viewModel.getWeekdayString(dailyPnLData.date)
                        } else "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (dailyPnLData != null && dailyPnLData.hasRecord) {
                            viewModel.formatCurrency(dailyPnLData.totalDailyReturn, false)
                        } else "--",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = returnColor,
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "当日盈亏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }

            if (dailyPnLData != null && dailyPnLData.hasRecord) {
                // 分隔线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )

                // 日收益率和持仓基金
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DailyPnLItem(
                        label = "日收益率",
                        value = viewModel.formatPercent(dailyPnLData.totalDailyReturnRate, false),
                        color = returnColor
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )

                    DailyPnLItem(
                        label = "持仓基金",
                        value = "${dailyPnLData.fundDetails.size} 只",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 分隔线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )

                // 基金明细标题
                Text(
                    text = "基金明细",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 基金明细列表
                if (dailyPnLData.fundDetails.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无基金数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dailyPnLData.fundDetails.forEach { fundPnL ->
                            DailyFundListItem(
                                fundPnL = fundPnL,
                                isPrivacyMode = isPrivacyMode,
                                onClick = { onFundClick(fundPnL) }
                            )
                        }
                    }
                }
            } else if (dailyPnLData != null && !dailyPnLData.hasRecord) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该日期暂无记录数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPnLItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 15.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

// 保留旧函数以保持向后兼容，但内部调用新的合并卡片
@Composable
fun DailyPnLCard(
    dailyPnLData: AssetOverviewViewModel.DailyPnLData?,
    viewModel: AssetOverviewViewModel,
    isDarkMode: Boolean = false
) {
    MergedDailyPnLCard(
        dailyPnLData = dailyPnLData,
        viewModel = viewModel,
        isPrivacyMode = false,
        onFundClick = {}
    )
}

@Composable
fun DailyFundDetailList(
    dailyPnLData: AssetOverviewViewModel.DailyPnLData?,
    isPrivacyMode: Boolean = false,
    onFundClick: (AssetOverviewViewModel.FundDailyPnL) -> Unit = {}
) {
    // 此函数现在为空实现，因为功能已合并到 MergedDailyPnLCard
    // 保留此函数以保持向后兼容
}

@Composable
private fun DailyFundListItem(
    fundPnL: AssetOverviewViewModel.FundDailyPnL,
    isPrivacyMode: Boolean = false,
    onClick: () -> Unit
) {
    val returnColor = if (fundPnL.dailyReturn >= 0) Color(0xFFEF4444) else com.huaying.xstz.ui.theme.SuccessGreen

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fundPnL.fund.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = fundPnL.fund.type.toDisplayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPrivacyMode) "****" else 
                        "${if (fundPnL.dailyReturn >= 0) "+" else ""}%.2f".format(fundPnL.dailyReturn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = returnColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isPrivacyMode) "****%" else 
                        "${if (fundPnL.dailyReturnRate >= 0) "+" else ""}%.2f%%".format(fundPnL.dailyReturnRate),
                    style = MaterialTheme.typography.bodySmall,
                    color = returnColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
