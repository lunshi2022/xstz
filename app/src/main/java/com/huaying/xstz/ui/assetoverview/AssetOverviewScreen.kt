package com.huaying.xstz.ui.assetoverview

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.component.StandardHorizontalPadding
import com.huaying.xstz.ui.component.StandardVerticalPadding
import com.huaying.xstz.ui.theme.BrandBlue
import com.huaying.xstz.ui.theme.DarkBackground
import com.huaying.xstz.ui.theme.DarkPriceBox
import com.huaying.xstz.ui.theme.DarkSurface
import com.huaying.xstz.ui.theme.DarkSurfaceSecondary
import com.huaying.xstz.ui.theme.DarkTextPrimary
import com.huaying.xstz.ui.theme.DarkTextSecondary
import com.huaying.xstz.ui.theme.LightBackground
import com.huaying.xstz.ui.theme.LightPriceBox
import com.huaying.xstz.ui.theme.LightSurface
import com.huaying.xstz.ui.theme.LightSurfaceSecondary
import com.huaying.xstz.ui.theme.LightTextPrimary
import com.huaying.xstz.ui.theme.LightTextSecondary
import com.huaying.xstz.ui.theme.SuccessGreen
import com.huaying.xstz.ui.theme.getColorForAssetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AssetOverviewScreen(
    viewModel: AssetOverviewViewModel = viewModel(),
    darkTheme: Boolean = false,
    onNavigateToAddFund: () -> Unit = {},
    onNavigateToFundDetail: (Fund) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val isDarkMode = darkTheme
    
    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("资产概览")
    }
    
    // 防抖处理，防止快速连续刷新
    var lastRefreshTime by remember { mutableStateOf(0L) }

    val onRefresh = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime > 1000) { // 1秒防抖
            lastRefreshTime = currentTime
            viewModel.refreshData(showLoading = true)
            OperationLogger.logRefresh("资产概览")
        }
    }

    val isRefreshing by remember {
        derivedStateOf { (uiState as? AssetOverviewUiState.Success)?.summary?.isRefreshing == true }
    }

    var showIndicator by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh)

    // 计算下拉距离
    val pullDistance by remember {
        derivedStateOf {
            pullRefreshState.progress * 100f // 转换为0-100范围
        }
    }

    // 控制指示器显示：下拉过程中和刷新时都显示
    val isIndicatorVisible by remember {
        derivedStateOf {
            isRefreshing || pullRefreshState.progress > 0.05f
        }
    }

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 启用硬件加速，提升动画性能
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
    ) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "先生投资",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "坚持长期投资 第${viewModel.getInvestmentDays()}天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            when (val state = uiState) {
            is AssetOverviewUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandBlue)
                }
            }

            is AssetOverviewUiState.Success -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 120.dp, // 从标题栏下方开始，确保内容不被标题栏遮挡
                            end = 16.dp,
                            bottom = 140.dp // 增加底部padding，确保最后一个项目可以滚动到导航栏上方完全可见
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SummaryCard(
                                summary = state.summary,
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                onRecordNav = {
                                    viewModel.recordNetValue()
                                }
                            )
                        }

                        item {
                            AssetDistributionCard(
                                summary = state.summary,
                                isDarkMode = isDarkMode
                            )
                        }

                        item {
                            FundListSection(
                                funds = state.funds,
                                totalAssets = state.summary.totalAssets,
                                rebalanceThreshold = state.summary.rebalanceThreshold,
                                isPrivacyMode = state.summary.isPrivacyMode,
                                isDarkMode = isDarkMode,
                                viewModel = viewModel,
                                onFundClick = onNavigateToFundDetail
                            )
                        }

                        if (state.summary.isRefreshing) {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = BrandBlue
                                )
                            }
                        }
                    }
                }
            }

            is AssetOverviewUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refreshData() }) {
                            Text("重试")
                        }
                    }
                }
            }
            }
        }

        // 下拉刷新指示器，简化实现以避免错位和闪烁
        if (isIndicatorVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp) // 调整顶部padding，确保在标题栏下方
            ) {
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier,
                    backgroundColor = if (isDarkMode) DarkSurface else LightSurface,
                    contentColor = BrandBlue
                )
            }
        }

    }
}

@Composable
fun SummaryCard(
    summary: AssetSummary,
    viewModel: AssetOverviewViewModel,
    isDarkMode: Boolean,
    onRecordNav: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val handleRecordClick = fun() {
        isRecording = true
        onRecordNav()
        showSuccess = true
        
        // 3秒后恢复按钮状态
        coroutineScope.launch {
            delay(3000)
            showSuccess = false
            isRecording = false
        }
    }

    // 使用主题阴影颜色，适配动态主题
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.4f else 0.08f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkMode) 4.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "资产总览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { viewModel.togglePrivacyMode() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (summary.isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Privacy Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "数据更新于 ${viewModel.formatTime(summary.lastUpdateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SummaryItem(
                        label = "总资产",
                        value = viewModel.formatCurrency(summary.totalAssets, summary.isPrivacyMode),
                        color = MaterialTheme.colorScheme.onSurface,
                        isDarkMode = isDarkMode
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SummaryItem(
                        label = "本金",
                        value = viewModel.formatCurrency(summary.principal, summary.isPrivacyMode),
                        color = MaterialTheme.colorScheme.onSurface,
                        isDarkMode = isDarkMode
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                val todayReturnColor = if (summary.todayReturn >= 0) Color(0xFFEF4444) else SuccessGreen
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SummaryItem(
                        label = "今日收益",
                        value = viewModel.formatCurrency(summary.todayReturn, summary.isPrivacyMode),
                        color = todayReturnColor,
                        isDarkMode = isDarkMode
                    )
                }
                val returnColor = if (summary.totalReturn >= 0) Color(0xFFEF4444) else SuccessGreen
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SummaryItem(
                        label = "累计收益",
                        value = if (summary.isPrivacyMode) "¥ ****" else "${viewModel.formatCurrency(summary.totalReturn, false)} (${viewModel.formatPercent(summary.returnRate, false)})",
                        color = returnColor,
                        isDarkMode = isDarkMode
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandBlue)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isRecording,
                        onClick = handleRecordClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showSuccess) "净值记录成功" else "记录净值",
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    label: String,
    value: String,
    color: Color,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val textStyle = when {
            value.length > 15 -> MaterialTheme.typography.titleSmall
            value.length > 12 -> MaterialTheme.typography.titleMedium
            value.length > 9 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.headlineSmall
        }

        Text(
            text = value,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AssetDistributionCard(
    summary: AssetSummary,
    isDarkMode: Boolean
) {
    // 使用主题阴影颜色，适配动态主题
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.4f else 0.08f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkMode) 4.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "资产占比",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (summary.stockRatio > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.stockRatio.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(
                                    color = getColorForAssetType(AssetType.STOCK).copy(alpha = 0.9f)
                                )
                        )
                    }
                    if (summary.bondRatio > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.bondRatio.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(
                                    color = getColorForAssetType(AssetType.BOND).copy(alpha = 0.9f)
                                )
                        )
                    }
                    if (summary.commodityRatio > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.commodityRatio.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(
                                    color = getColorForAssetType(AssetType.COMMODITY).copy(alpha = 0.9f)
                                )
                        )
                    }
                    if (summary.cashRatio > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.cashRatio.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(
                                    color = getColorForAssetType(AssetType.CASH).copy(alpha = 0.9f)
                                )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AssetDistributionItem(
                    label = "股票",
                    value = summary.stockValue,
                    ratio = summary.stockRatio,
                    color = getColorForAssetType(AssetType.STOCK),
                    isPrivacyMode = summary.isPrivacyMode,
                    isDarkMode = isDarkMode
                )
                AssetDistributionItem(
                    label = "债券",
                    value = summary.bondValue,
                    ratio = summary.bondRatio,
                    color = getColorForAssetType(AssetType.BOND),
                    isPrivacyMode = summary.isPrivacyMode,
                    isDarkMode = isDarkMode
                )
                AssetDistributionItem(
                    label = "商品",
                    value = summary.commodityValue,
                    ratio = summary.commodityRatio,
                    color = getColorForAssetType(AssetType.COMMODITY),
                    isPrivacyMode = summary.isPrivacyMode,
                    isDarkMode = isDarkMode
                )
                AssetDistributionItem(
                    label = "现金",
                    value = summary.cashValue,
                    ratio = summary.cashRatio,
                    color = getColorForAssetType(AssetType.CASH),
                    isPrivacyMode = summary.isPrivacyMode,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

@Composable
fun AssetDistributionItem(
    label: String,
    value: Double,
    ratio: Double,
    color: Color,
    isPrivacyMode: Boolean = false,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (isPrivacyMode) "**%" else "%.2f%%".format(Locale.CHINA, ratio),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 12.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
    }
}

@Composable
fun FundListSection(
    funds: List<Fund>,
    totalAssets: Double,
    rebalanceThreshold: Double,
    isPrivacyMode: Boolean = false,
    isDarkMode: Boolean,
    viewModel: AssetOverviewViewModel,
    onFundClick: (Fund) -> Unit
) {
    // 使用主题阴影颜色，适配动态主题
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) 0.4f else 0.08f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkMode) 4.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "我的持仓",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${funds.size} 只",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val marketStatus = viewModel.getMarketStatus()
                val statusColor = when (marketStatus) {
                    "交易中" -> BrandBlue
                    "午间休盘", "未开盘" -> Color(0xFF8B5CF6)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (marketStatus == "交易中") {
                        val infiniteTransition = rememberInfiniteTransition(label = "trading")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = marketStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (funds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无持仓，点击右下角添加基金",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val groupedFunds = funds.groupBy { it.type }
                val sortedTypes = AssetType.values().filter { groupedFunds.containsKey(it) }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortedTypes.forEach { type ->
                        val fundsInType = groupedFunds[type] ?: emptyList()
                        val sortedFunds = fundsInType.sortedByDescending { it.holdingQuantity * it.currentPrice }

                        sortedFunds.forEach { fund ->
                            FundListItem(
                                fund = fund,
                                totalAssets = totalAssets,
                                rebalanceThreshold = rebalanceThreshold,
                                isPrivacyMode = isPrivacyMode,
                                isDarkMode = isDarkMode,
                                viewModel = viewModel,
                                onClick = {
                                    OperationLogger.logItemClick(fund.name, "基金")
                                    onFundClick(fund)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FundListItem(
    fund: Fund,
    totalAssets: Double,
    rebalanceThreshold: Double,
    isPrivacyMode: Boolean = false,
    isDarkMode: Boolean,
    viewModel: AssetOverviewViewModel,
    onClick: () -> Unit
) {
    val currentValue = fund.holdingQuantity * fund.currentPrice
    val currentRatio = viewModel.getFundDisplayRatio(fund, totalAssets)
    val (deviation, _) = viewModel.calculateDeviation(fund, totalAssets)
    val status = viewModel.getFundStatus(fund, totalAssets, rebalanceThreshold)

    val statusColor = when (status) {
        "正常" -> SuccessGreen
        "需平衡" -> Color(0xFF8B5CF6)
        "严重偏离" -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val changeColor = if (fund.changePercent >= 0) Color(0xFFEF4444) else SuccessGreen

    var previousPrice by remember { mutableStateOf(fund.currentPrice) }
    val flashColor = remember { Animatable(Color.Transparent) }
    
    LaunchedEffect(fund.currentPrice) {
        val priceDiff = fund.currentPrice - previousPrice
        val oldPrice = previousPrice
        previousPrice = fund.currentPrice

        if (oldPrice != 0.0 && priceDiff != 0.0) {
            val targetColor = if (priceDiff > 0) {
                Color(0xFFE53935).copy(alpha = 0.25f)
            } else {
                Color(0xFF4CAF50).copy(alpha = 0.25f)
            }
            
            flashColor.animateTo(
                targetValue = targetColor, 
                animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
            )
            flashColor.animateTo(
                targetValue = targetColor.copy(alpha = 0f), 
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        flashColor.value.copy(alpha = 0f),
                        flashColor.value
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fund.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fund.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " | ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = when (fund.type) {
                                AssetType.STOCK -> "股票"
                                AssetType.BOND -> "债券"
                                AssetType.COMMODITY -> "商品"
                                AssetType.CASH -> "现金"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (fund.type != AssetType.CASH || fund.targetRatio > 0) {
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (fund.type == AssetType.CASH) {
                    InfoItem(
                        label = "当前市值", 
                        value = viewModel.formatCurrency(currentValue, isPrivacyMode),
                        alignment = Alignment.Start,
                        isDarkMode = isDarkMode
                    )
                    InfoItem(
                        label = "占比", 
                        value = if (isPrivacyMode) "**%" else "%.2f%%".format(currentRatio),
                        alignment = Alignment.End,
                        isDarkMode = isDarkMode
                    )
                } else {
                    InfoItem(
                        label = "最新", 
                        value = if (isPrivacyMode) "****" else "%.3f".format(java.util.Locale.CHINA, fund.currentPrice),
                        alignment = Alignment.Start,
                        isDarkMode = isDarkMode
                    )
                    InfoItem(
                        label = "涨幅",
                        value = viewModel.formatPercent(fund.changePercent, isPrivacyMode),
                        valueColor = changeColor,
                        alignment = Alignment.CenterHorizontally,
                        isDarkMode = isDarkMode
                    )
                    InfoItem(
                        label = "当前市值", 
                        value = viewModel.formatCurrency(currentValue, isPrivacyMode),
                        alignment = Alignment.CenterHorizontally,
                        isDarkMode = isDarkMode
                    )
                    InfoItem(
                        label = "占比", 
                        value = if (isPrivacyMode) "**%" else "%.2f%%".format(currentRatio),
                        alignment = Alignment.End,
                        isDarkMode = isDarkMode
                    )
                }
            }
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    valueColor: Color? = null,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = alignment
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


