package com.huaying.xstz.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrustedClockTest {

    @Test
    fun `未校准时退回系统墙钟`() {
        val clock = TrustedClock()
        assertFalse(clock.isCalibrated)
        // 校准前 trustedNowMs 应≈ System.currentTimeMillis()，差应在一秒内
        val a = clock.trustedNowMs(elapsedRealtimeMs = 0)
        val b = System.currentTimeMillis()
        assertTrue("未校准应退回系统墙钟，差异 ${abs(a - b)}ms 过大", abs(a - b) < 2000)
    }

    @Test
    fun `校准后基于 elapsedRealtime 推算`() {
        // 校准时刻：elapsed=1000ms 时，服务器时间=1_000_000ms
        // ⇒ offset = 1_000_000 - 1000 = 999_000
        val clock = TrustedClock()
        clock.setOffset(1_000_000L - 1000L)
        assertTrue(clock.isCalibrated)

        // 之后 elapsed 走到 5000ms：可信时间应 = 5000 + 999_000 = 1_004_000
        assertEquals(1_004_000L, clock.trustedNowMs(elapsedRealtimeMs = 5000))
    }

    @Test
    fun `改系统时间不影响可信时间`() {
        // 关键场景：offset 在校准时固定。用户把系统时间往后拨 30 天，
        // System.currentTimeMillis() 会跳变，但 elapsedRealtime 不变，
        // 因此可信时间只随真实流逝推进，不受篡改影响。
        val clock = TrustedClock()
        clock.setOffset(10_000L - 1000L) // elapsed=1000 时 server=10_000
        val before = clock.trustedNowMs(elapsedRealtimeMs = 2000) // = 11_000
        // 假设系统时间被拨乱，但 elapsedRealtime 只正常推进了 1000ms
        val after = clock.trustedNowMs(elapsedRealtimeMs = 3000) // = 12_000
        assertEquals(1000L, after - before)
    }
}
