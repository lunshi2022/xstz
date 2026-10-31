package com.huaying.xstz.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.designsystem.tokens.Spacing
import com.huaying.xstz.core.ui.ButtonVariant
import com.huaying.xstz.core.ui.GlassButton

/**
 * 首次引导页（方案 5.2「引导页：首次使用流程」）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 设计取舍：三页、纯本地、可跳过
 *
 * * 三页各回答一个问题：这是什么 / 数据安全吗 / 怎么开始。
 *   引导页的目标是消除"打开就一堆 Tab 不知道干嘛"的茫然，
 *   不是功能说明书——超过三屏没人看。
 * * 「跳过」永远可见：引导的价值在自愿，被迫看完只会产生反感。
 * * 不请求任何权限：首版没有需要权限的功能，无权限开场
 *   对信任建立至关重要（这是旧版 P2 系列问题的反面教训）。
 * * 纯实色背景，不用玻璃：引导页在主题装载之前出现，
 *   玻璃依赖 haze 状态与背景层，此时都还没建立。
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
) {
    val palette = XstzTheme.palette
    var page by remember { mutableIntStateOf(0) }
    val pages = remember { onboardingPages() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screenEdge),
    ) {
        // 跳过按钮：引导第一屏就能离开
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "跳过",
                style = XstzType.footnote,
                color = palette.textTertiary,
                modifier = Modifier
                    .padding(top = Spacing.sm, bottom = Spacing.xs)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDone,
                    )
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            )
        }

        // 页面主体
        Crossfade(
            targetState = page,
            animationSpec = tween(Motion.PAGE_MS),
            label = "onboarding-page",
            modifier = Modifier.weight(1f),
        ) { index ->
            val p = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.6f))
                // 图标位：不引入图标库新依赖，用色块 + emoji 表达
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(palette.accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.emoji, style = XstzType.displayLarge)
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = p.title,
                    style = XstzType.title1,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = p.body,
                    style = XstzType.callout,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
                Spacer(Modifier.weight(1f))
            }
        }

        // 页点指示器：当前页是长条，其余是圆点
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.forEachIndexed { i, _ ->
                val selected = i == page
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(if (selected) 22.dp else 7.dp)
                        .height(7.dp)
                        .background(
                            if (selected) palette.accent else palette.divider,
                            CircleShape,
                        ),
                )
            }
        }

        // 底部按钮
        GlassButton(
            text = if (page == pages.lastIndex) "开始使用" else "下一步",
            onClick = {
                if (page == pages.lastIndex) onDone() else page++
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
        )
    }
}

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
)

private fun onboardingPages() = listOf(
    OnboardingPage(
        emoji = "📊",
        title = "看懂你的每一笔投资",
        body = "基金、A股、ETF、债券与现金放在同一个组合里。\n" +
            "总资产、收益率、当日盈亏一屏尽览，全程本地计算。",
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "数据只属于你",
        body = "所有数据经 SQLCipher 整库加密后存在本机，\n" +
            "行情直连交易所行情源，不经任何第三方服务器。\n备份用你自己的口令加密，换机可恢复。",
    ),
    OnboardingPage(
        emoji = "⚖️",
        title = "再平衡让纪律代替情绪",
            body = "为每个标的设置目标仓位，偏离超过阈值时\n" +
                // 不能写"给出买卖建议/直接照做"：那是投资建议表述，
                // 验收清单 §11 合规项明令禁止。这里只陈述"算出了差额"这一事实。
                "算出买卖差额——买多少卖多少一目了然，决策始终在你自己。",
    ),
)
