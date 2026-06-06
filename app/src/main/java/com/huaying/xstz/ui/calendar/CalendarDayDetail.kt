package com.huaying.xstz.ui.calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.ui.theme.*
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

/**
 * 日历日期详情面板
 * 展示选中日期的详细资产数据
 * 参考设计：支付宝账单详情、雪球资产明细
 */

@Composable
fun CalendarDayDetailPanel(
    date: LocalDate,
    data: DailyAssetData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 头部：日期和总览
            DateHeader(date = date, data = data)

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 资产明细
            AssetDetailSection(data = data)

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 汇总信息
            SummarySection(data = data)
        }
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    data: DailyAssetData
) {
    val totalProfit = data.totalAsset - data.principal
    // A股习惯：盈红亏绿
    val profitColor = if (totalProfit >= 0) DangerRed else SuccessGreen
    val returnRate = data.returnRate

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期信息
        Column {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("yyyy年 EEEE", Locale.CHINA)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 当日总盈亏
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (totalProfit >= 0) "+" else ""}${formatAmount(totalProfit)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = profitColor
            )
            Text(
                text = "${if (returnRate >= 0) "+" else ""}${String.format("%.2f%%", returnRate * 100)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = profitColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AssetDetailSection(data: DailyAssetData) {
    Column {
        Text(
            text = "资产明细",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssetDetailItem(
                name = "股票",
                value = data.stockValue,
                totalAsset = data.totalAsset,
                color = StockColor
            )
            AssetDetailItem(
                name = "债券",
                value = data.bondValue,
                totalAsset = data.totalAsset,
                color = BondColor
            )
            AssetDetailItem(
                name = "商品",
                value = data.goldValue,
                totalAsset = data.totalAsset,
                color = GoldColor
            )
            AssetDetailItem(
                name = "现金",
                value = data.cashValue,
                totalAsset = data.totalAsset,
                color = CashColor
            )
        }
    }
}

@Composable
private fun AssetDetailItem(
    name: String,
    value: Double,
    totalAsset: Double,
    color: Color
) {
    val ratio = if (totalAsset > 0) (value / totalAsset * 100) else 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：类型标识
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 右侧：金额和占比
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatAmount(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.1f%%", ratio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(45.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SummarySection(data: DailyAssetData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryItem(
            label = "总资产",
            value = formatAmount(data.totalAsset),
            valueColor = MaterialTheme.colorScheme.onSurface
        )
        SummaryItem(
            label = "投入本金",
            value = formatAmount(data.principal),
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SummaryItem(
            label = "当日收益",
            value = "${if (data.returnRate >= 0) "+" else ""}${String.format("%.2f%%", data.returnRate * 100)}",
            // A股习惯：盈红亏绿
            valueColor = if (data.returnRate >= 0) DangerRed else SuccessGreen
        )
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

/**
 * 格式化金额显示
 */
private fun formatAmount(value: Double): String {
    return when {
        value >= 100000000 -> String.format("%.2f亿", value / 100000000)
        value >= 10000 -> String.format("%.2f万", value / 10000)
        else -> String.format("%.2f", value)
    }
}

/**
 * 空状态展示
 */
@Composable
fun CalendarEmptyState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请在首页点击\"记录净值\"以生成数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
