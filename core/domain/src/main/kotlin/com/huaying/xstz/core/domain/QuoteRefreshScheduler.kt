package com.huaying.xstz.core.domain

import com.huaying.xstz.core.common.AutoRefreshPolicy
import com.huaying.xstz.core.common.MarketClock
import com.huaying.xstz.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 行情自动刷新调度器。
 *
 * 职责边界：它**只决定何时调用刷新**，不关心刷新本身怎么做——
 * 真正的拉取交给 [onRefresh]（通常是 `holdingRepository.refreshQuotes()`）。
 * 这样网络、解析、落库都不用进这个文件，也便于把它从 ViewModel 里挪走。
 *
 * 循环结构：1 秒一次轻量轮询节拍，每次用 [AutoRefreshPolicy.shouldRefresh]
 * 判一次，命中才真正刷。用「轮询 + 决策」而不是「delay(间隔)」直接等，
 * 是因为前后台、交易状态、刷新频率都是会变的：1 秒一判能即时响应这些变化，
 * 而一次性 delay 到时间才醒，期间状态变了也发现不了。
 *
 * 前台/后台、交易时段、频率三件事都交给 policy，这里只把输入凑齐。
 */
class QuoteRefreshScheduler(
    private val marketClock: MarketClock,
    private val foreground: StateFlow<Boolean>,
    private val refreshIntervalMs: StateFlow<Long>,
    private val onRefresh: suspend () -> Unit,
    /**
     * 后台刷新间隔（响应式）。
     *
     * 用 StateFlow 而不是固定 Long：用户在设置里拨动「后台降频」后，
     * 下一次决策必须立刻生效。若构造时读一次快照，用户要杀进程重启 App
     * 才会生效——这种"开关拨了没反应"最难自查。
     */
    private val backgroundIntervalMs: StateFlow<Long>,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * 在 [scope] 内启动循环。调用方用 `viewModelScope` 即可——
     * ViewModel 销毁时 scope 取消，循环随之停止。
     *
     * [onRefresh] 自身应做好并发防护（OverviewViewModel.refresh 已有
     * `refreshing` 互斥锁），这里不做额外节流，避免和 policy 的间隔判定重复。
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var lastAttempt = 0L
            while (isActive) {
                delay(TICK_MS)
                val status = marketClock.currentStatus()
                val hit = AutoRefreshPolicy.shouldRefresh(
                    status = status,
                    foreground = foreground.value,
                    nowMs = nowMs(),
                    lastAttemptMs = lastAttempt,
                    foregroundIntervalMs = refreshIntervalMs.value,
                    backgroundIntervalMs = backgroundIntervalMs.value,
                )
                if (hit) {
                    lastAttempt = nowMs()
                    onRefresh()
                }
            }
        }
    }

    companion object {
        /** 轮询节拍。1s 足够灵敏，又不会空转过多 */
        private const val TICK_MS = 1_000L
    }
}
