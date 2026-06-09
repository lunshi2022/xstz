package com.huaying.xstz.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.animation.interaction.bouncyClickable
import com.huaying.xstz.ui.navigation.Route

/**
 * 自定义底部导航栏组件
 *
 * 浮动胶囊造型 + 滑动胶囊指示器 + Filled/Outlined 双图标 + 渐变边框
 *
 * @param currentRoute 当前路由路径
 * @param onNavigate 导航回调
 * @param onAddClick 添加按钮点击回调
 * @param darkTheme 是否使用深色主题
 */
@Composable
fun CustomBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit,
    darkTheme: Boolean,
    exitOffsetFraction: Float = 0f,
    exitAlpha: Float = 1f
) {
    data class NavItem(
        val route: String,
        val activeIcon: ImageVector,
        val inactiveIcon: ImageVector,
        val label: String
    )

    val navItems = listOf(
        NavItem(Route.HOME, Icons.Filled.Home, Icons.Outlined.Home, "概览"),
        NavItem(Route.CHARTS, Icons.Filled.PieChart, Icons.Outlined.PieChart, "分析"),
        NavItem(Route.REBALANCE, Icons.Filled.CompareArrows, Icons.Outlined.CompareArrows, "再平衡"),
        NavItem(Route.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings, "设置")
    )

    // 记住上次有效的选中索引，避免导航到子页面时胶囊跳到最左边
    val matchedIndex = navItems.indexOfFirst { it.route == currentRoute }
    var lastSelectedIndex by remember { mutableIntStateOf(0) }
    if (matchedIndex >= 0) lastSelectedIndex = matchedIndex
    val selectedIndex = lastSelectedIndex

    val navigationBarsInsets = WindowInsets.navigationBars
    val density = LocalDensity.current
    val bottomInset = with(density) { navigationBarsInsets.getBottom(this).toDp() }

    val shadowColor = Color.Black.copy(alpha = if (darkTheme) 0.35f else 0.15f)

    // 渐变边框颜色
    val borderGradientColors = if (darkTheme) {
        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.02f))
    } else {
        listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.12f))
    }

    // 滑动指示器相关状态
    var capsuleContentWidth by remember { mutableIntStateOf(0) }
    val itemWidthPx = if (capsuleContentWidth > 0) capsuleContentWidth.toFloat() / navItems.size else 0f

    val animatedOffsetPx by animateFloatAsState(
        targetValue = selectedIndex * itemWidthPx,
        animationSpec = tween(
            durationMillis = AnimationConstants.Duration.NORMAL,
            easing = FastOutSlowInEasing
        ),
        label = "indicator_offset"
    )

    // 浮动胶囊容器 + 独立 FAB
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset)
            .clickable(
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer {
                translationY = size.height * exitOffsetFraction
                alpha = exitAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 导航胶囊容器（改为 Box 以支持滑动指示器叠加）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(299.dp),
                        clip = false,
                        ambientColor = shadowColor,
                        spotColor = shadowColor
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.88f else 0.92f),
                        shape = RoundedCornerShape(299.dp)
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(colors = borderGradientColors),
                        shape = RoundedCornerShape(299.dp)
                    )
                    .padding(horizontal = 8.dp)
            ) {
                // 滑动胶囊指示器（背景层）
                if (capsuleContentWidth > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { animatedOffsetPx.toDp() })
                            .fillMaxHeight()
                            .width(with(density) { itemWidthPx.toDp() })
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(299.dp))
                            .background(
                                if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (darkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(299.dp)
                            )
                    )
                }

                // 导航项（前景层，无独立背景）
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { capsuleContentWidth = it.width },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    navItems.forEachIndexed { index, item ->
                        CapsuleNavItem(
                            selected = index == selectedIndex,
                            onClick = { onNavigate(item.route) },
                            activeIcon = item.activeIcon,
                            inactiveIcon = item.inactiveIcon,
                            label = item.label,
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = if (darkTheme) Color(0xFFAAAAAA) else Color(0xFF555555)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 独立添加按钮
            FloatingActionButton(
                onClick = onAddClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .size(56.dp)
                    .bouncyClickable { onAddClick() }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 胶囊导航项（无背景，由滑动指示器提供高亮）
 */
@Composable
private fun RowScope.CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    selectedColor: Color,
    unselectedColor: Color
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(AnimationConstants.Duration.FAST),
        label = "nav_text_color"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(AnimationConstants.Duration.FAST),
        label = "nav_icon_color"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(299.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .height(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) activeIcon else inactiveIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
