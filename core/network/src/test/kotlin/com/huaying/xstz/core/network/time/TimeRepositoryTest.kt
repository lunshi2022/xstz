package com.huaying.xstz.core.network.time

import android.os.SystemClock
import com.huaying.xstz.core.common.TrustedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 可控假探针：返回预设值或失败 */
private class FakeProbe(private val result: Long?) : TimeProbe {
    var calls = 0
        private set
    override suspend fun probe(): Long? {
        calls++
        return result
    }
}

class TimeRepositoryTest {

    @Test
    fun `首级成功即返回-true-且校准`() = runTest {
        val repo = TimeRepositoryImpl(
            clock = TrustedClock(),
            probes = listOf(FakeProbe(1_700_000_000_000L), FakeProbe(0)),
            nowElapsed = { 1_000L },
        )
        val ok = repo.resync()
        assertTrue(ok)
        assertTrue(repo.isCalibrated)
        // 偏移 = serverMs - elapsed = 1_700_000_000_000 - 1_000 = 1_699_999_999_000
        // trustedNow(elapsed=1_000) = 1_000 + 偏移 = 1_700_000_000_000
        assertEquals(1_700_000_000_000L, repo.trustedNowMs())
    }

    @Test
    fun `前级失败-降级到下一级成功`() = runTest {
        val good = FakeProbe(1_600_000_000_000L)
        val repo = TimeRepositoryImpl(
            clock = TrustedClock(),
            probes = listOf(FakeProbe(null), good, FakeProbe(null)),
            nowElapsed = { 0L },
        )
        assertTrue(repo.resync())
        assertEquals(1, good.calls) // 只用到第二级
    }

    @Test
    fun `全部失败返回-false-且不校准`() = runTest {
        val repo = TimeRepositoryImpl(
            clock = TrustedClock(),
            probes = listOf(FakeProbe(null), FakeProbe(null), FakeProbe(null)),
            nowElapsed = { 0L },
        )
        assertFalse(repo.resync())
        assertFalse(repo.isCalibrated)
    }

    @Test
    fun `已校准后断网保留偏移`() = runTest {
        val clock = TrustedClock()
        val repo = TimeRepositoryImpl(
            clock = clock,
            probes = listOf(FakeProbe(1_000_000L)),
            nowElapsed = { 0L },
        )
        repo.resync()
        // 之后所有探针失败：偏移应保留，不退回系统墙钟
        val repo2 = TimeRepositoryImpl(clock = clock, probes = listOf(FakeProbe(null)), nowElapsed = { 0L })
        assertFalse(repo2.resync()) // resync 失败
        assertTrue(repo2.isCalibrated) // 但仍是校准态
    }
}
