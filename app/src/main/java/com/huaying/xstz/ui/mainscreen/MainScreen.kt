package com.huaying.xstz.ui.mainscreen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.OperationType
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.entity.toDisplayName
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.ui.addfund.AddFundScreen
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.animation.navigation.NavigationAnimations
import com.huaying.xstz.ui.navigation.Route
import com.huaying.xstz.ui.navigation.NavigationDirection
import com.huaying.xstz.ui.navigation.getNavigationDirection
import androidx.compose.runtime.saveable.rememberSaveable
import com.huaying.xstz.ui.assetoverview.AssetOverviewScreen
import com.huaying.xstz.ui.charts.OptimizedChartsScreen
import com.huaying.xstz.ui.component.CustomBottomNavigationBar
import com.huaying.xstz.ui.component.EditHoldingQuantityDialog
import com.huaying.xstz.ui.component.ThousandSeparatorTransformation
import com.huaying.xstz.ui.fund.FundDetailScreen
import com.huaying.xstz.ui.fund.TransactionHistoryScreen
import com.huaying.xstz.ui.rebalance.RebalanceScreen
import com.huaying.xstz.ui.settings.AboutScreen
import com.huaying.xstz.ui.settings.GuideScreen
import com.huaying.xstz.ui.settings.OperationLogScreen
import com.huaying.xstz.ui.settings.SettingsScreen
import com.huaying.xstz.ui.targetallocation.TargetAllocationScreen
import com.huaying.xstz.ui.update.UpdateCheckErrorDialog
import com.huaying.xstz.ui.update.UpdateDialog
import com.huaying.xstz.ui.update.UpdateViewModel
import com.huaying.xstz.ui.update.CheckingUpdateDialog
import com.huaying.xstz.ui.update.NoUpdateDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 主界面
 * 负责应用的主要界面布局和导航逻辑
 *
 * Tab切换使用自定义 AnimatedContent + SizeTransform(clip=false)，
 * 避免 NavHost 内部 AnimatedContent 的 SizeTransform(clip=true)
 * 在页面首帧尺寸不一致时产生"从中心向四周展开"的裁剪效果。
 */
@Composable
fun MainScreen(
    repository: FundRepository,
    operationLogRepository: OperationLogRepository,
    preferenceManager: PreferenceManager,
    themeMode: Int,
    dynamicColorEnabled: Boolean,
    refreshIntervalSeconds: Int,
    refreshMode: Int,
    privacyModeEnabled: Boolean,
    darkTheme: Boolean,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Route.HOME

    val mainRoutes = Route.MAIN_ROUTES
    val showBottomBar = currentRoute in mainRoutes

    // Tab导航防抖：动画期间忽略重复点击
    var lastTabSwitchTime by remember { mutableStateOf(0L) }
    val tabSwitchCooldown = AnimationConstants.Duration.TAB_SWITCH.toLong()

    // 当前Tab路由状态，用于 AnimatedContent 切换
    var currentTabRoute by rememberSaveable { mutableStateOf(Route.HOME) }
    // 上一个Tab路由，用于判断动画方向
    var previousTabRoute by rememberSaveable { mutableStateOf(Route.HOME) }

    // 当从子页面返回时，同步 currentTabRoute
    LaunchedEffect(currentRoute) {
        if (currentRoute in mainRoutes) {
            previousTabRoute = currentTabRoute
            currentTabRoute = currentRoute
        }
    }

    // 更新检查 ViewModel
    val updateViewModel = remember { UpdateViewModel(context) }

    // 预加载基金数据，供所有页面共享使用
    val allFunds by repository.getAllFunds().collectAsState(initial = emptyList())
    // 预加载所有交易数据，供子页面复用
    val allTransactions by repository.getAllTransactions().collectAsState(initial = emptyList())
    // 预加载操作日志数据
    val allOperationLogs by operationLogRepository.getAllOperationLogs().collectAsState(initial = emptyList())
    val operationLogCount by operationLogRepository.getOperationLogCount().collectAsState(initial = 0)
    val totalMarketValue by remember(allFunds) {
        derivedStateOf { allFunds.sumOf { it.holdingQuantity * it.currentPrice } }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditCostDialog by remember { mutableStateOf(false) }
    var showEditQuantityDialog by remember { mutableStateOf(false) }
    var newCostPrice by remember { mutableStateOf(0.0) }

    // 使用 Scaffold 标准布局
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            val bottomBarOffset by animateFloatAsState(
                targetValue = if (showBottomBar) 0f else 1f,
                animationSpec = tween(AnimationConstants.Duration.NORMAL, easing = AnimationConstants.Easing.Decelerate),
                label = "bottomBarOffset"
            )
            val bottomBarAlpha by animateFloatAsState(
                targetValue = if (showBottomBar) 1f else 0f,
                animationSpec = tween(AnimationConstants.Duration.NORMAL),
                label = "bottomBarAlpha"
            )
            CustomBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                        // 防抖：动画期间忽略重复点击
                        val now = System.currentTimeMillis()
                        if (now - lastTabSwitchTime < tabSwitchCooldown) return@CustomBottomNavigationBar
                        lastTabSwitchTime = now

                        // 记录导航切换
                        val fromPage = when (currentRoute) {
                            Route.HOME -> "资产概览"
                            Route.CHARTS -> "图表分析"
                            Route.REBALANCE -> "再平衡"
                            Route.SETTINGS -> "设置"
                            else -> currentRoute
                        }
                        val toPage = when (route) {
                            Route.HOME -> "资产概览"
                            Route.CHARTS -> "图表分析"
                            Route.REBALANCE -> "再平衡"
                            Route.SETTINGS -> "设置"
                            else -> route
                        }
                        if (fromPage != toPage) {
                            OperationLogger.logNavigationSwitch(fromPage, toPage)
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddClick = {
                        OperationLogger.logButtonClick("添加基金", "底部导航")
                        navController.navigate(Route.ADD_FUND)
                    },
                    darkTheme = darkTheme,
                exitOffsetFraction = bottomBarOffset,
                exitAlpha = bottomBarAlpha
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
            )
            .consumeWindowInsets(innerPadding)

        // 始终渲染 NavHost，确保 navController.graph 始终可用
        // Tab 路由渲染空占位（实际内容由 AnimatedContent 叠加层显示）
        // 子页面渲染实际内容并使用 push/pop 动画
        Box(modifier = contentModifier) {
            NavHost(
                navController = navController,
                startDestination = Route.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab 路由占位 - 实际内容由 AnimatedContent 叠加层渲染
                composable(Route.HOME, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { Box(Modifier.fillMaxSize()) }
                composable(Route.CHARTS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { Box(Modifier.fillMaxSize()) }
                composable(Route.REBALANCE, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { Box(Modifier.fillMaxSize()) }
                composable(Route.SETTINGS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { Box(Modifier.fillMaxSize()) }

                composable(
                    Route.OPERATION_LOG,
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) {
                    OperationLogScreen(
                        operationLogRepository = operationLogRepository,
                        operationLogs = allOperationLogs,
                        logCount = operationLogCount,
                        darkTheme = darkTheme,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    Route.ADD_FUND,
                    enterTransition = { NavigationAnimations.sheetEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.sheetExit()(this) }
                ) {
                    AddFundScreen(
                        darkTheme = darkTheme,
                        onBack = { navController.navigateUp() },
                        onFundAdded = { navController.navigateUp() }
                    )
                }

                composable(
                    Route.TARGET_ALLOCATION,
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) {
                    TargetAllocationScreen(
                        isDarkMode = darkTheme,
                        onBack = { navController.popBackStack() },
                        initialFunds = allFunds
                    )
                }

                composable(
                    Route.ABOUT,
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) { AboutScreen(darkTheme = darkTheme, onBack = { navController.popBackStack() }) }

                composable(
                    Route.GUIDE,
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) { GuideScreen(darkTheme = darkTheme, onBack = { navController.popBackStack() }) }

                composable(
                    route = Route.FUND_DETAIL,
                    arguments = listOf(navArgument("fundId") { type = NavType.LongType }),
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) { backStackEntry ->
                    val fundId = backStackEntry.arguments?.getLong("fundId") ?: 0L
                    val fund = allFunds.find { it.id == fundId }
                    val transactions = allTransactions.filter { it.fundId == fundId }
                    val totalTargetRatio = allFunds.sumOf { if (it.targetRatio.isNaN()) 0.0 else it.targetRatio }

                    if (fund != null) {
                        FundDetailScreen(
                            fund = fund,
                            transactions = transactions,
                            totalTargetRatio = totalTargetRatio,
                            isPrivacyMode = privacyModeEnabled,
                            isDarkMode = darkTheme,
                            onBack = { navController.popBackStack() },
                            onEditHoldingQuantity = { showEditQuantityDialog = true },
                            onClearCash = {
                                scope.launch {
                                    if (fund.holdingQuantity != 0.0) {
                                        val updated = fund.copy(holdingQuantity = 0.0, totalCost = 0.0, updatedAt = TimeRepository.getCurrentTimeMillis())
                                        repository.updateFund(updated)
                                        repository.insertTransaction(Transaction(
                                            fundId = fund.id, fundCode = fund.code, fundName = fund.name,
                                            type = if (fund.holdingQuantity > 0) TransactionType.WITHDRAW else TransactionType.ADD_FUNDS,
                                            amount = fund.holdingQuantity, price = 1.0, quantity = abs(fund.holdingQuantity), remark = "手动清空金额"
                                        ))
                                        operationLogRepository.logOperation(
                                            type = if (fund.holdingQuantity > 0) OperationType.WITHDRAW_CASH else OperationType.ADD_CASH,
                                            title = if (fund.holdingQuantity > 0) "取出资金" else "添加资金",
                                            description = "基金: ${fund.code}, 金额: ¥${String.format("%.2f", abs(fund.holdingQuantity))}",
                                            targetId = fund.id,
                                            targetName = fund.name
                                        )
                                    } else {
                                        val toast = Toast.makeText(context, "金额已经是0", Toast.LENGTH_SHORT)
                                        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                                        toast.show()
                                    }
                                }
                            },
                            onEditTargetRatio = { _, new ->
                                scope.launch {
                                    val up = fund.copy(targetRatio = new, updatedAt = TimeRepository.getCurrentTimeMillis())
                                    repository.updateFund(up)
                                    operationLogRepository.logOperation(
                                        type = OperationType.EDIT_TARGET_RATIO,
                                        title = "修改目标比例",
                                        description = "基金: ${fund.code}, 新比例: ${String.format("%.1f", new * 100)}%",
                                        targetId = fund.id,
                                        targetName = fund.name
                                    )
                                }
                            },
                            onEditAssetType = { _, new ->
                                scope.launch {
                                    val oldType = fund.type
                                    val up = fund.copy(type = new, updatedAt = TimeRepository.getCurrentTimeMillis())
                                    repository.updateFund(up)
                                    operationLogRepository.logOperation(
                                        type = OperationType.EDIT_ASSET_TYPE,
                                        title = "修改资产类型",
                                        description = "基金: ${fund.code}, ${oldType.toDisplayName()} → ${new.toDisplayName()}",
                                        targetId = fund.id,
                                        targetName = fund.name
                                    )
                                }
                            },
                            onDelete = { showDeleteDialog = true },
                            onNavigateToTransactionHistory = { navController.navigate(Route.transactionHistory(fundId)) },
                            onNavigateToTargetAllocation = { navController.navigate(Route.TARGET_ALLOCATION) }
                        )
                    }

                    val currentFund = fund
                    if (showEditCostDialog && currentFund != null) {
                        var inputCost by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showEditCostDialog = false },
                            title = { Text("修改成本价格") },
                            text = {
                                Column {
                                    Text("当前成本: ¥%.3f".format(newCostPrice))
                                    OutlinedTextField(value = inputCost, onValueChange = { inputCost = it }, label = { Text("新成本价格") }, visualTransformation = ThousandSeparatorTransformation())
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    inputCost.toDoubleOrNull()?.let { cost ->
                                        scope.launch {
                                            val up = currentFund.copy(totalCost = cost * currentFund.holdingQuantity, updatedAt = TimeRepository.getCurrentTimeMillis())
                                            repository.updateFund(up)
                                            showEditCostDialog = false
                                        }
                                    }
                                }) { Text("确定") }
                            },
                            dismissButton = { TextButton(onClick = { showEditCostDialog = false }) { Text("取消") } }
                        )
                    }

                    if (showEditQuantityDialog && currentFund != null) {
                        EditHoldingQuantityDialog(
                            fund = currentFund, darkTheme = darkTheme,
                            onDismiss = { showEditQuantityDialog = false },
                            onConfirm = { q, p ->
                                scope.launch {
                                    val delta = q - currentFund.holdingQuantity
                                    val tPrice = p ?: currentFund.currentPrice
                                    val newCost = if (delta > 0) currentFund.totalCost + (delta * tPrice) else (if (currentFund.holdingQuantity > 0) currentFund.totalCost * (q / currentFund.holdingQuantity) else 0.0)
                                    val up = currentFund.copy(holdingQuantity = if (q < 0) 0.0 else q, totalCost = newCost, updatedAt = TimeRepository.getCurrentTimeMillis())
                                    repository.updateFund(up)
                                    if (abs(delta) > 0.001) {
                                        repository.insertTransaction(Transaction(
                                            fundId = currentFund.id, fundCode = currentFund.code, fundName = currentFund.name,
                                            type = if (delta > 0) TransactionType.BUY else TransactionType.SELL,
                                            amount = -(delta * tPrice), price = tPrice, quantity = abs(delta), remark = "手动调整"
                                        ))
                                        operationLogRepository.logOperation(
                                            type = OperationType.EDIT_HOLDING,
                                            title = if (delta > 0) "增加持仓" else "减少持仓",
                                            description = "基金: ${currentFund.code}, 变动: ${String.format("%.2f", abs(delta))}份",
                                            targetId = currentFund.id,
                                            targetName = currentFund.name
                                        )
                                    }
                                    showEditQuantityDialog = false
                                }
                            }
                        )
                    }

                    if (showDeleteDialog && currentFund != null) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("确认删除") },
                            text = { Text("确定要删除吗？此操作无法撤销。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        operationLogRepository.logOperation(
                                            type = OperationType.DELETE_FUND,
                                            title = "删除基金",
                                            description = "代码: ${currentFund.code}",
                                            targetId = currentFund.id,
                                            targetName = currentFund.name
                                        )
                                        repository.deleteFund(currentFund)
                                        navController.popBackStack()
                                    }
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
                        )
                    }
                }

                composable(
                    route = Route.TRANSACTION_HISTORY,
                    arguments = listOf(navArgument("fundId") { type = NavType.LongType }),
                    enterTransition = { NavigationAnimations.pushEnter()(this) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { NavigationAnimations.popExit()(this) }
                ) { entry ->
                    val fId = entry.arguments?.getLong("fundId") ?: 0L
                    val trans = allTransactions.filter { it.fundId == fId }
                    val f = allFunds.find { it.id == fId }
                    TransactionHistoryScreen(transactions = trans, fundType = f?.type ?: AssetType.STOCK, darkTheme = darkTheme, onBack = { navController.popBackStack() })
                }
            }

            // Tab 内容叠加层：使用自定义 AnimatedContent + SizeTransform(clip = false)
            // 避免 NavHost 内部 AnimatedContent 的 SizeTransform(clip = true)
            // 在页面首帧尺寸不一致时产生"从中心向四周展开"的裁剪效果
            if (currentRoute in mainRoutes) {
                val direction = getNavigationDirection(previousTabRoute, currentTabRoute, mainRoutes)
                val directionOrdinal = direction.ordinal
                val tabDuration = if (direction == NavigationDirection.NONE) AnimationConstants.Duration.TAB_RESUME else AnimationConstants.Duration.TAB_SWITCH

                AnimatedContent(
                    targetState = currentTabRoute,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val enterSlide = when (directionOrdinal) {
                            NavigationDirection.FORWARD.ordinal -> slideInHorizontally(
                                initialOffsetX = { it / 4 },
                                animationSpec = tween(tabDuration, easing = FastOutSlowInEasing)
                            )
                            NavigationDirection.BACKWARD.ordinal -> slideInHorizontally(
                                initialOffsetX = { -it / 4 },
                                animationSpec = tween(tabDuration, easing = FastOutSlowInEasing)
                            )
                            else -> slideInHorizontally(
                                initialOffsetX = { 0 },
                                animationSpec = tween(tabDuration)
                            )
                        }
                        val exitSlide = when (directionOrdinal) {
                            NavigationDirection.FORWARD.ordinal -> slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(tabDuration, easing = FastOutSlowInEasing)
                            )
                            NavigationDirection.BACKWARD.ordinal -> slideOutHorizontally(
                                targetOffsetX = { it / 4 },
                                animationSpec = tween(tabDuration, easing = FastOutSlowInEasing)
                            )
                            else -> slideOutHorizontally(
                                targetOffsetX = { 0 },
                                animationSpec = tween(tabDuration)
                            )
                        }
                        val enterFade = fadeIn(tween(tabDuration))
                        val exitFade = fadeOut(tween(tabDuration))
                        ContentTransform(
                            targetContentEnter = enterSlide + enterFade,
                            initialContentExit = exitSlide + exitFade,
                            sizeTransform = SizeTransform(clip = false)
                        )
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "tabSwitch"
                ) { tabRoute: String ->
                    when (tabRoute) {
                        Route.HOME -> AssetOverviewScreen(
                            darkTheme = darkTheme,
                            onNavigateToAddFund = { navController.navigate(Route.ADD_FUND) },
                            onNavigateToFundDetail = { fund -> navController.navigate(Route.fundDetail(fund.id)) },
                            initialFunds = allFunds
                        )
                        Route.CHARTS -> OptimizedChartsScreen(
                            repository = repository,
                            preferenceManager = preferenceManager,
                            darkTheme = darkTheme
                        )
                        Route.REBALANCE -> RebalanceScreen(
                            darkTheme = darkTheme,
                            onNavigateToTargetAllocation = { navController.navigate(Route.TARGET_ALLOCATION) },
                            initialFunds = allFunds
                        )
                        Route.SETTINGS -> SettingsScreen(
                            preferenceManager = preferenceManager,
                            themeMode = themeMode,
                            dynamicColorEnabled = dynamicColorEnabled,
                            refreshIntervalSeconds = refreshIntervalSeconds,
                            refreshMode = refreshMode,
                            privacyModeEnabled = privacyModeEnabled,
                            onExportData = {
                                scope.launch {
                                    operationLogRepository.logOperation(
                                        type = OperationType.EXPORT_DATA,
                                        title = "导出数据",
                                        description = "导出备份文件"
                                    )
                                    exportLauncher.launch("xstz_backup_${System.currentTimeMillis()}.json")
                                }
                            },
                            onImportData = {
                                scope.launch {
                                    operationLogRepository.logOperation(
                                        type = OperationType.IMPORT_DATA,
                                        title = "导入数据",
                                        description = "从文件导入数据"
                                    )
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            },
                            onClearRecords = {
                                scope.launch {
                                    operationLogRepository.logOperation(
                                        type = OperationType.CLEAR_DATA,
                                        title = "清空数据",
                                        description = "清空所有持仓和交易记录"
                                    )
                                    repository.clearAllDataPreservingCash()
                                    val toast = Toast.makeText(context, "数据已清空", Toast.LENGTH_SHORT)
                                    toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                                    toast.show()
                                }
                            },
                            onNavigateToAbout = { navController.navigate(Route.ABOUT) },
                            onNavigateToGuide = { navController.navigate(Route.GUIDE) },
                            onNavigateToOperationLog = { navController.navigate(Route.OPERATION_LOG) },
                            onCheckUpdate = { updateViewModel.checkForUpdate(showNoUpdate = true) }
                        )
                    }
                }
            }
        }

        // 更新对话框
        if (updateViewModel.showCheckingDialog) {
            CheckingUpdateDialog()
        }

        if (updateViewModel.showUpdateDialog && updateViewModel.updateInfo != null) {
            UpdateDialog(
                updateInfo = updateViewModel.updateInfo!!,
                onDismiss = { updateViewModel.dismissUpdateDialog() },
                onConfirm = { updateViewModel.downloadAndInstallUpdate() }
            )
        }

        if (updateViewModel.showNoUpdateDialog) {
            NoUpdateDialog(onDismiss = { updateViewModel.dismissNoUpdateDialog() })
        }

        if (updateViewModel.showErrorDialog) {
            UpdateCheckErrorDialog(
                errorMessage = updateViewModel.errorMessage,
                onDismiss = { updateViewModel.dismissErrorDialog() }
            )
        }
    }
}
