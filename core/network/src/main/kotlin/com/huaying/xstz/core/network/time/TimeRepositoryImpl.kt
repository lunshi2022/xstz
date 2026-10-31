package com.huaying.xstz.core.network.time

import android.os.SystemClock
import com.huaying.xstz.core.common.TrustedClock
import com.huaying.xstz.core.domain.repository.TimeRepository

/**
 * 可信时间仓库实现。
 *
 * 三级降级链（方案 §5.1，硬性要求）：
 *   1. 阿里云 NTP（`ntp.aliyun.com`）—— 国内延迟最低、最稳
 *   2. `pool.ntp.org` —— 国际兜底，前一级彻底不通时还能试
 *   3. 淘宝 HTTP 时间戳 —— SNTP 在部分网络被 UDP 屏蔽时的最终退路
 *
 * [TrustedClock] 的偏移一旦写入，之后取值都基于 `elapsedRealtime`，
 * 因此**即使之后完全断网**，已校准的可信时间也不会被用户改系统钟带偏。
 */
class TimeRepositoryImpl(
    private val clock: TrustedClock = TrustedClock(),
    private val probes: List<TimeProbe> = defaultProbes(),
    private val nowElapsed: () -> Long = { SystemClock.elapsedRealtime() },
) : TimeRepository {

    override val isCalibrated: Boolean get() = clock.isCalibrated

    override fun trustedNowMs(): Long = clock.trustedNowMs(nowElapsed())

    override suspend fun resync(): Boolean {
        for (probe in probes) {
            val serverMs = probe.probe() ?: continue
            // 偏移 = 服务器时间 − 本地开机后经过毫秒。
            // elapsedRealtime 不被用户改系统时间影响，这是防篡改的关键。
            clock.setOffset(serverMs - nowElapsed())
            return true
        }
        return false
    }

    companion object {
        /** 三级降级顺序：阿里云 → pool.ntp.org → 淘宝 HTTP */
        fun defaultProbes(): List<TimeProbe> = listOf(
            SntpProbe("ntp.aliyun.com"),
            SntpProbe("pool.ntp.org"),
            TaobaoTimestampProbe(),
        )
    }
}
