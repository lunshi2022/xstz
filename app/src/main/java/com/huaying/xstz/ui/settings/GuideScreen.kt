package com.huaying.xstz.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    darkTheme: Boolean = false,
    onBack: () -> Unit
) {
    val isDarkMode = darkTheme

    val backgroundColor = if (isDarkMode) DarkBackground else LightBackground
    val textPrimaryColor = if (isDarkMode) DarkTextPrimary else LightTextPrimary

    Scaffold(
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
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回", tint = textPrimaryColor)
                }
                Text(
                    "使用说明",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        containerColor = backgroundColor
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 120.dp, // 从标题栏下方开始
                end = 16.dp,
                bottom = 140.dp // 确保最后一个项目可以滚动到导航栏上方完全可见
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "开启您的理性投资",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "先生投资是一个基于资产配置理论（Modern Portfolio Theory）的辅助决策系统。通过以下步骤，您可以建立并维护一个稳健的投资组合。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            items(detailedGuideSections) { section ->
                DetailedGuideCard(section)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("核心逻辑说明", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BulletPoint("智能刷新", "交易日 09:15-11:30, 13:00-15:00 自动开启秒级更新，非交易时段停止刷新以节省电量和流量。")
                        BulletPoint("再平衡计算", "系统会优先将新增资金投入比例偏离度最大的资产，通过“增量调整”减少不必要的卖出交易成本。")
                        BulletPoint("数据安全", "所有数据均存储在您的手机本地，建议通过“导出数据”功能定期将备份文件保存至云盘或电脑。")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun DetailedGuideCard(section: DetailedGuideSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            section.steps.forEachIndexed { index, step ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (index + 1).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    if (index < section.steps.size - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BulletPoint(title: String, content: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class DetailedGuideSection(
    val title: String,
    val icon: ImageVector,
    val steps: List<GuideStep>
)

data class GuideStep(
    val title: String,
    val content: String
)

val detailedGuideSections = listOf(
    DetailedGuideSection(
        title = "第一步：建立持仓",
        icon = Icons.Default.AddBusiness,
        steps = listOf(
            GuideStep("添加基金/资产", "在首页点击“+”，输入基金代码。如果是银行存款、现金等，可以手动添加并归类为“现金”资产。"),
            GuideStep("设置目标比例", "点击基金进入详情页，在底部设置该资产在您整个投资组合中理想的占比（如沪深300占40%）。"),
            GuideStep("录入初始金额", "在详情页点击“交易记录”，可以查看到该基金所有交易记录，系统会自动计算持仓成本。")
        )
    ),
    DetailedGuideSection(
        title = "第二步：日常监控",
        icon = Icons.Default.TrendingUp,
        steps = listOf(
            GuideStep("行情实时看", "首页会实时展示您的盈亏状态。红色代表上涨/盈利，绿色代表下跌/亏损（遵循中国市场习惯）。"),
            GuideStep("查看资产分布", "切换到“图表”页面，查看各资产类别的实际占比。系统会自动对比实际比例与您设置的目标比例。"),
            GuideStep("关注再平衡信号", "当某项资产涨跌过大导致比例严重偏离时，首页会出现明显的调仓建议标识。"),
            GuideStep("理解状态标识", "基金卡片右上角显示当前持仓与目标配置的偏离状态：\n• 正常：实际占比与目标占比偏差在允许范围内\n• 需平衡：偏离超过阈值，建议调整持仓\n• 严重偏离：偏离较大，需要尽快调整\n\n注：只有当变动数量不足100股且偏离小于5%时，才会显示为“正常”状态。")
        )
    ),
    DetailedGuideSection(
        title = "第三步：执行再平衡",
        icon = Icons.Default.Balance,
        steps = listOf(
            GuideStep("进入再平衡页面", "切换到“再平衡”标签页，这里是应用的核心功能。"),
            GuideStep("输入新增/提取资金", "如果您打算今天追加投资，输入金额。系统会计算这笔钱应该买入哪只基金才能让组合回归平衡。"),
            GuideStep("查看调仓建议", "系统会给出精确到“手”（100股/份）的买卖建议。您可以选择“强制再平衡”来完全对齐目标。"),
            GuideStep("一键确认交易", "点击执行后，系统会自动为您生成对应的交易记录，更新您的持仓状态。")
        )
    )
)
