package com.huaying.xstz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huaying.xstz.core.designsystem.glass.GlassBackground
import com.huaying.xstz.core.designsystem.theme.ProvideXstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.ui.GlassTabBar
import com.huaying.xstz.core.ui.MainTab
import com.huaying.xstz.core.ui.MeshBackdrop
import com.huaying.xstz.feature.charts.ChartsScreen
import com.huaying.xstz.feature.charts.ChartsViewModel
import com.huaying.xstz.feature.overview.OverviewScreen
import com.huaying.xstz.feature.overview.OverviewViewModel
import com.huaying.xstz.feature.rebalance.RebalanceScreen
import com.huaying.xstz.feature.rebalance.RebalanceViewModel
import com.huaying.xstz.feature.rebalance.TargetAllocationScreen
import com.huaying.xstz.feature.settings.OperationLogScreen
import com.huaying.xstz.feature.settings.SettingsScreen
import com.huaying.xstz.feature.settings.SettingsViewModel
import com.huaying.xstz.feature.transactions.AddHoldingScreen
import com.huaying.xstz.feature.transactions.AddHoldingViewModel
import com.huaying.xstz.feature.transactions.HoldingDetailScreen
import com.huaying.xstz.feature.transactions.HoldingDetailViewModel
import com.huaying.xstz.feature.transactions.HoldingsScreen
import com.huaying.xstz.feature.transactions.HoldingsViewModel
import com.huaying.xstz.feature.transactions.TradeHistoryScreen
import dev.chrisbanes.haze.rememberHazeState

/**
 * 页面上方的一层"覆盖式导航"。
 *
 * 没有引 Navigation Compose：全应用只有这两层（Tab 内页面 + 全屏覆盖页），
 * 为两个目标引入一条 route 表、一个 NavHost、一套参数序列化，
 * 换来的灵活性这里用不上。等目标仓位、日历、引导这些页面进来以后，
 * 再统一改成 NavHost 更划算——那时路由关系才真的复杂到需要它。
 */
private sealed interface Overlay {
    data object AddHolding : Overlay
    data class HoldingDetail(val id: Long) : Overlay

    /** 目标仓位设置页：从再平衡页进入 */
    data object TargetAllocation : Overlay

    /** 交易历史页：从持仓详情或再平衡页进入，null 表示看全部标的 */
    data class TradeHistory(val holdingId: Long?) : Overlay

    /** 操作日志页：从设置页进入 */
    data object OperationLog : Overlay
}

/**
 * 应用根组合。
 *
 * 层级自下而上：MeshBackdrop（光斑）→ hazeSource（注册为折射源）→ 内容 → 玻璃 Tab 栏。
 * 顺序不能错：hazeSource 必须在玻璃的下层，否则采样到的是玻璃自己。
 */
@Composable
fun XstzApp(
    darkTheme: Boolean,
    reduceMotion: Boolean,
    reduceTransparency: Boolean,
    highContrast: Boolean = false,
) {
    ProvideXstzTheme(
        dark = darkTheme,
        reduceMotion = reduceMotion,
        reduceTransparency = reduceTransparency,
        highContrast = highContrast,
    ) {
        val hazeState = rememberHazeState()
        var tab by remember { mutableStateOf(MainTab.Overview) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        // 每次打开添加页都换一个 key，强制新建 ViewModel：
        // 否则会带着上次输入的代码和名称，用户以为已经清空了
        var addSession by remember { mutableIntStateOf(0) }

        // 持仓 ViewModel 提到顶层：Tab 内列表与覆盖的详情页共用同一份数据，
        // 详情页录完一笔交易后，列表要立刻反映出来
        val holdingsVm: HoldingsViewModel = hiltViewModel()
        val holdingsState by holdingsVm.state.collectAsStateWithLifecycle()

        // 系统返回键：覆盖层打开时先关覆盖层。没有这个，详情页按返回会直接退出应用
        BackHandler(enabled = overlay != null) { overlay = null }

        MaterialTheme(typography = XstzType.asMaterialTypography()) {
            Box(Modifier.fillMaxSize()) {
                GlassBackground(state = hazeState, modifier = Modifier.fillMaxSize()) {
                    MeshBackdrop()
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    val motion = XstzTheme.motion
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (motion.animationsEnabled) {
                                val enter: EnterTransition =
                                    slideInHorizontally(
                                        initialOffsetX = { it / 4 },
                                        animationSpec = tween(Motion.TAB_MS, easing = Motion.iosEaseOut),
                                    ) + fadeIn(animationSpec = tween(Motion.TAB_MS))
                                val exit: ExitTransition =
                                    slideOutHorizontally(
                                        targetOffsetX = { -it / 4 },
                                        animationSpec = tween(Motion.TAB_MS, easing = Motion.iosEaseOut),
                                    ) + fadeOut(animationSpec = tween(Motion.TAB_MS))
                                enter togetherWith exit
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "main-tab",
                    ) { currentTab ->
                        when (currentTab) {
                        MainTab.Overview -> {
                            val vm: OverviewViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            OverviewScreen(
                                state = state,
                                                                onRefresh = vm::refresh,
                                onTogglePrivacy = vm::togglePrivacy,
                                onOpenHolding = { id -> overlay = Overlay.HoldingDetail(id) },
                                onSeedSamples = vm::seedSamples,
                            )
                        }
                        MainTab.Holdings -> {
                            HoldingsScreen(
                                state = holdingsState,
                                onOpenAdd = {
                                    addSession++
                                    overlay = Overlay.AddHolding
                                },
                                onOpenDetail = { h: Holding -> overlay = Overlay.HoldingDetail(h.fundId) },
                                onSortChange = holdingsVm::setSort,
                                onToggleGrouped = holdingsVm::toggleGrouped,
                                onDismissMessage = holdingsVm::dismissMessage,
                            )
                        }
                        MainTab.Charts -> {
                            val vm: ChartsViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            ChartsScreen(
                                state = state,
                                onSelect = vm::select,
                                onRangeChange = vm::setRange,
                                onDimensionChange = vm::setDimension,
                                onShiftMonth = vm::shiftMonth,
                            )
                        }
                        MainTab.Rebalance -> {
                            val vm: RebalanceViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            RebalanceScreen(
                                state = state,
                                                                onStrategyChange = vm::setStrategy,
                                onThresholdChange = vm::setThreshold,
                                onOpenTargetAllocation = { overlay = Overlay.TargetAllocation },
                                onOpenTradeHistory = { overlay = Overlay.TradeHistory(null) },
                            )
                        }
                        MainTab.Settings -> {
                            val vm: SettingsViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            /*
                             * 版本串在 app 模块算好再传给设置页：feature/settings 拿不到
                             * app 的 BuildConfig，若在本地写死就会随 gradle 升版本而漂移。
                             *
                             * 去掉 debug 变体的 "-debug" 后缀：关于页面向用户，
                             * 展示"这是 debug 包"没有意义，用户只关心版本号本身。
                             */
                            val versionText = remember {
                                val name = BuildConfig.VERSION_NAME.substringBefore("-debug")
                                "$name (${BuildConfig.VERSION_CODE} · Alpha)"
                            }
                            SettingsScreen(
                                state = state,
                                onThemeChange = vm::setThemeMode,
                                onPrivacyChange = vm::setPrivacy,
                                onReduceTransparency = vm::setReduceTransparency,
                                onExport = vm::export,
                                onImport = vm::import,
                                onDismissExport = vm::dismissExport,
                                onDismissMessage = vm::dismissMessage,
                                onOpenOperationLog = { overlay = Overlay.OperationLog },
                                onRefreshIntervalChange = vm::setRefreshInterval,
                                onBackgroundThrottleChange = vm::setBackgroundThrottle,
                                onInitialInvestDayChange = vm::setInitialInvestDay,
                                onSeedSamples = vm::seedSamples,
                                onClearOperationLog = vm::clearOperationLog,
                                onExportCsv = vm::exportCsv,
                                onClearAllHoldings = vm::clearAllHoldings,
                                versionText = versionText,
                            )
                        }
                        }
                    }

                    // ---------- 覆盖层 ----------
                    // 放在 Tab 内容之上、Tab 栏之外的位置不出效果：
                    // 这两页是全屏的，必须盖住 Tab 栏，所以放在同一个 Box 的最后
                    // 用 lastOverlay 捕获最后一次非空值：退出动画期间 overlay 已变 null，
                    // 但 AnimatedVisibility 仍需旧内容完成退场，否则会 NPE。
                    var lastOverlay by remember { mutableStateOf<Overlay?>(overlay) }
                    if (overlay != null) lastOverlay = overlay
                    AnimatedVisibility(
                        visible = overlay != null,
                        enter = if (motion.animationsEnabled) {
                            fadeIn(animationSpec = tween(Motion.DIALOG_MS)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(Motion.PAGE_MS, easing = Motion.iosEaseOut),
                                )
                        } else EnterTransition.None,
                        exit = if (motion.animationsEnabled) {
                            fadeOut(animationSpec = tween(Motion.DIALOG_MS)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(Motion.PAGE_MS, easing = Motion.iosEaseOut),
                                )
                        } else ExitTransition.None,
                    ) {
                        when (val current = lastOverlay ?: return@AnimatedVisibility) {
                        is Overlay.AddHolding -> {
                            val vm: AddHoldingViewModel = hiltViewModel(key = "add-$addSession")
                            val state by vm.state.collectAsStateWithLifecycle()
                            AddHoldingScreen(
                                state = state,
                                onTextChange = vm::onTextChange,
                                onSelect = vm::select,
                                onEnableManual = vm::enableManualMode,
                                onManualNameChange = vm::onManualNameChange,
                                onManualKindChange = vm::onManualKindChange,
                                onSubmit = vm::submit,
                                onDone = { overlay = null },
                                onBack = { overlay = null },
                            )
                        }
                        is Overlay.HoldingDetail -> {
                            val vm: HoldingDetailViewModel = hiltViewModel(key = "detail-${current.id}")
                            LaunchedEffect(current.id) { vm.load(current.id) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            HoldingDetailScreen(
                                state = state,
                                onBack = { overlay = null },
                                onRangeChange = vm::setRange,
                                onTrade = holdingsVm::recordTrade,
                                onDelete = { h ->
                                    holdingsVm.deleteHolding(h)
                                    overlay = null
                                },
                                onOpenTradeHistory = { id ->
                                    overlay = Overlay.TradeHistory(id)
                                },
                            )
                        }
                        is Overlay.TargetAllocation -> {
                            TargetAllocationScreen(onBack = { overlay = null })
                        }
                        is Overlay.TradeHistory -> {
                            TradeHistoryScreen(
                                holdingId = current.holdingId,
                                onBack = { overlay = null },
                            )
                        }
                        is Overlay.OperationLog -> {
                            OperationLogScreen(onBack = { overlay = null })
                        }
                        }
                    }
                }

                // Tab 栏在覆盖页打开时隐藏：全屏详情页带自己的底部操作栏，
                // 两套底栏叠在一起会互相压住
                if (overlay == null) {
                    GlassTabBar(
                        current = tab,
                        onSelect = { tab = it },
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // 方案 §8.4 要求「距底部 8~12dp」。
                            // 这里的"底部"指**安全区底边**，不是屏幕物理底边——
                            // 实测系统手势导航条占 48dp，直接写 10dp 会让 Tab 栏
                            // 落进手势区，点按与滑动互相打架（违反 VISUAL_ACCEPTANCE R3）。
                            // navigationBarsPadding 抬到手势区之上，再留 10dp 视觉间距。
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                    )
                }
            }
        }
    }
}
