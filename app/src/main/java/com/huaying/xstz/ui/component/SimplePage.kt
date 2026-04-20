package com.huaying.xstz.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huaying.xstz.ui.theme.*

/**
 * 简化版页面布局组件，参考 gkd-main 项目设计
 * 用于标准的页面布局，包含顶部导航栏和内容区域
 *
 * @param title 页面标题
 * @param showBackButton 是否显示返回按钮
 * @param onBackClick 返回按钮点击事件
 * @param topBarActions 顶部栏右侧操作按钮
 * @param contentWindowInsets 内容区域的 WindowInsets
 * @param content 页面内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePage(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    topBarActions: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.ime,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                            )
                        }
                    }
                },
                actions = {
                    topBarActions()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = if (isDarkMode) DarkBackground else LightBackground
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * 带底部导航栏的页面布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePageWithBottomBar(
    title: String,
    bottomBar: @Composable () -> Unit,
    contentWindowInsets: WindowInsets = WindowInsets.ime,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = if (isDarkMode) DarkTextPrimary else LightTextPrimary
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        bottomBar = bottomBar,
        containerColor = if (isDarkMode) DarkBackground else LightBackground,
        contentWindowInsets = contentWindowInsets
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * 空状态页面
 */
@Composable
fun EmptyPage(
    message: String = "暂无数据",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
