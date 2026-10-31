package com.huaying.xstz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.ui.GlassTabBar
import com.huaying.xstz.core.ui.MainTab
import com.huaying.xstz.core.ui.MeshBackdrop
import com.huaying.xstz.feature.overview.OverviewScreen
import com.huaying.xstz.feature.overview.OverviewViewModel
import com.huaying.xstz.feature.charts.ChartsScreen
import com.huaying.xstz.feature.charts.ChartsViewModel
import com.huaying.xstz.feature.rebalance.RebalanceScreen
import com.huaying.xstz.feature.rebalance.RebalanceViewModel
import com.huaying.xstz.feature.transactions.HoldingsScreen
import com.huaying.xstz.feature.settings.SettingsScreen
import com.huaying.xstz.feature.settings.SettingsViewModel
import com.huaying.xstz.feature.transactions.HoldingsViewModel
import dev.chrisbanes.haze.rememberHazeState

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
) {
    ProvideXstzTheme(
        dark = darkTheme,
        reduceMotion = reduceMotion,
        reduceTransparency = reduceTransparency,
    ) {
        val hazeState = rememberHazeState()
        var tab by remember { mutableStateOf(MainTab.Overview) }

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
                    when (tab) {
                        MainTab.Overview -> {
                            val vm: OverviewViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            OverviewScreen(
                                state = state,
                                hazeState = hazeState,
                                onRefresh = vm::refresh,
                                onTogglePrivacy = vm::togglePrivacy,
                                onOpenHolding = { /* 详情页在阶段 3 后续批次接入 */ },
                                onSeedSamples = vm::seedSamples,
                            )
                        }
                        MainTab.Holdings -> {
                            val vm: HoldingsViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            HoldingsScreen(
                                state = state,
                                hazeState = hazeState,
                                onAdd = vm::addHolding,
                                onTrade = vm::recordTrade,
                                onDelete = vm::deleteHolding,
                                onDismissMessage = vm::dismissMessage,
                            )
                        }
                        MainTab.Charts -> {
                            val vm: ChartsViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            ChartsScreen(
                                state = state,
                                hazeState = hazeState,
                                onSelect = vm::select,
                                onRangeChange = vm::setRange,
                            )
                        }
                        MainTab.Rebalance -> {
                            val vm: RebalanceViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            RebalanceScreen(
                                state = state,
                                hazeState = hazeState,
                                onStrategyChange = vm::setStrategy,
                                onThresholdChange = vm::setThreshold,
                            )
                        }
                        MainTab.Settings -> {
                            val vm: SettingsViewModel = hiltViewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                hazeState = hazeState,
                                onThemeChange = vm::setThemeMode,
                                onPrivacyChange = vm::setPrivacy,
                                onReduceTransparency = vm::setReduceTransparency,
                                onExport = vm::export,
                                onImport = vm::import,
                                onDismissExport = vm::dismissExport,
                                onDismissMessage = vm::dismissMessage,
                            )
                        }
                    }
                }

                GlassTabBar(
                    current = tab,
                    onSelect = { tab = it },
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderTab(tab: MainTab) {
    val palette = com.huaying.xstz.core.designsystem.theme.XstzTheme.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text = "${tab.label} · 构建中",
            style = XstzType.title3,
            color = palette.textTertiary,
        )
    }
}
