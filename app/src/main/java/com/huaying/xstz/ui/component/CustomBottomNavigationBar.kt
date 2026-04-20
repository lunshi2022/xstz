package com.huaying.xstz.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.ui.animation.interaction.bouncyClickable
import com.huaying.xstz.ui.animation.interaction.clickableScale

/**
 * 自定义底部导航栏组件
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
    darkTheme: Boolean
) {
    val navItems = listOf(
        Triple("home", Icons.Default.Home, "首页"),
        Triple("charts", Icons.Default.PieChart, "图表"),
        Triple("rebalance", Icons.Default.CompareArrows, "再平衡"),
        Triple("settings", Icons.Default.Settings, "设置")
    )

    // 获取系统导航栏（小白条）的高度
    val navigationBarsInsets = WindowInsets.navigationBars
    val bottomInset = with(LocalDensity.current) { navigationBarsInsets.getBottom(this).toDp() }
    // 使用主题阴影颜色，适配动态主题
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.3f else 0.1f)

    // 全透明背景
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = shadowColor,
                        ambientColor = shadowColor
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { (route, icon, label) ->
                    val isSelected = currentRoute == route
                    // 使用主题主色，适配动态主题
                    val selectedColor = MaterialTheme.colorScheme.primary
                    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

                    // 选中项的动画
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "nav_icon_scale"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) selectedColor else unselectedColor,
                        animationSpec = tween(200),
                        label = "nav_text_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickableScale(scale = 0.9f) { onNavigate(route) }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier
                                    .size(if (isSelected) 24.dp else 22.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                                tint = textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 添加按钮带动画效果
            val fabScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "fab_scale"
            )

            FloatingActionButton(
                onClick = onAddClick,
                shape = CircleShape,
                // 使用主题主色，适配动态主题
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                    .bouncyClickable { onAddClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
