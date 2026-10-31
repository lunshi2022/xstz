package com.huaying.xstz.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [Formats] 的补充测试。
 *
 * 与 FormatsTest 分开的原因：那边覆盖"主路径"（金额、涨跌幅、净值、新鲜度），
 * 这里专门钉住三类容易出错的地方——
 *   1. 隐私分支（每个函数都有一条 `if (privacy)`，漏测就会在真机上露数字）
 *   2. 时区相关的输出（时间/日期时间/距今天数，写死 Asia/Shanghai）
 *   3. 兜底路径（safe 的 fallback、百分点与百分比的单位差异）
 */
class FormatsPrivacyAndTimeTest {

    // 固定时区，避免测试机时区不同导致期望值漂移
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun millisAt(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // ---------------- 隐私分支 ----------------

    @Test
    fun `所有金额类函数在隐私模式下都返回掩码`() {
        assertEquals("****", Formats.nav(1.2345, privacy = true))
        assertEquals("****", Formats.costPrice(1.2345, privacy = true))
        assertEquals("****", Formats.quantity(1234.5, privacy = true))
        assertEquals("¥ ****", Formats.signedCurrency(-1234.5, privacy = true))
    }

    @Test
    fun `占比类函数隐私模式带单位掩码`() {
        assertEquals("**%", Formats.ratio(0.25, privacy = true))
        assertEquals("**pp", Formats.percentagePoints(2.73, privacy = true))
    }

    // ---------------- 符号与单位 ----------------

    @Test
    fun `带符号金额负数用减号且金额取绝对值`() {
        assertEquals("-¥123.45", Formats.signedCurrency(-123.45))
        assertEquals("+¥123.45", Formats.signedCurrency(123.45))
    }

    @Test
    fun `不带货币符号的金额仍带千分位`() {
        assertEquals("1,234.56", Formats.currencyValue(1234.56))
    }

    @Test
    fun `百分点与百分比是两种单位`() {
        // 百分点入参本身就是"点"的数值，不再乘 100
        assertEquals("+2.73pp", Formats.percentagePoints(2.73))
        assertEquals("-2.73pp", Formats.percentagePoints(-2.73))
        // 同一数值走 percent 会变成 273%
        assertEquals("+273.00%", Formats.percent(2.73))
    }

    @Test
    fun `百分点的负零也归为正号`() {
        assertEquals("+0.00pp", Formats.percentagePoints(-1e-9))
    }

    // ---------------- 兜底 ----------------

    @Test
    fun `safe 正常格式化`() {
        assertEquals("1,234.57", Formats.safe(1234.567, "#,##0.00"))
    }

    @Test
    fun `safe 在模式非法时返回兜底值而不抛异常`() {
        // 非法 pattern：未闭合的引号
        assertEquals("0.00", Formats.safe(1.0, "0.0'"))
    }

    @Test
    fun `safe 支持自定义兜底值`() {
        assertEquals("—", Formats.safe(1.0, "0.0'", fallback = "—"))
    }

    // ---------------- 日期与时间 ----------------

    @Test
    fun `短日期省略年份`() {
        assertEquals("10-31", Formats.shortDate(LocalDate.of(2026, 10, 31).toEpochDay()))
    }

    @Test
    fun `时间按东八区输出并补零`() {
        assertEquals("14:35", Formats.time(millisAt(2026, 10, 31, 14, 35)))
        assertEquals("09:05", Formats.time(millisAt(2026, 10, 31, 9, 5)))
    }

    @Test
    fun `日期时间带月日与时分`() {
        assertEquals("10-31 14:35", Formats.dateTime(millisAt(2026, 10, 31, 14, 35)))
    }

    @Test
    fun `周七天的中文都覆盖`() {
        // 2026-10-26 是周一
        val monday = LocalDate.of(2026, 10, 26)
        val expected = listOf("一", "二", "三", "四", "五", "六", "日")
        expected.forEachIndexed { i, want ->
            assertEquals(want, Formats.weekday(monday.plusDays(i.toLong()).toEpochDay()))
        }
    }

    // ---------------- 距今天数 ----------------

    @Test
    fun `距今天数过去为正未来为负当天为零`() {
        val now = millisAt(2026, 10, 31, 12, 0)
        assertEquals(0, Formats.daysSince(now, now))
        assertEquals(3, Formats.daysSince(millisAt(2026, 10, 28, 12, 0), now))
        assertEquals(-2, Formats.daysSince(millisAt(2026, 11, 2, 12, 0), now))
    }

    @Test
    fun `同一天不同时刻不算跨天`() {
        val now = millisAt(2026, 10, 31, 23, 30)
        assertEquals(0, Formats.daysSince(millisAt(2026, 10, 31, 0, 10), now))
    }

    // ---------------- 新鲜度：跨天分支 ----------------

    @Test
    fun `超过一天显示天数`() {
        val now = 1_000_000_000_000L
        assertEquals("2 天前", Formats.freshness(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `负时间戳同样按未更新处理`() {
        assertEquals("未更新", Formats.freshness(-1L))
    }

    // ---------------- 图表轴 ----------------

    @Test
    fun `轴格式按小数位固定且带千分位`() {
        val zeroDecimals = Formats.axisNumberFormat(0)
        assertEquals("1,235", zeroDecimals.format(1234.6))

        val oneDecimal = Formats.axisNumberFormat(1)
        assertEquals("1,234.6", oneDecimal.format(1234.56))
        // 固定小数位：整数也要补 .0
        assertEquals("1,000.0", oneDecimal.format(1000.0))
    }

    @Test
    fun `轴格式常量 PRIVACY_MASK 与隐私输出一致`() {
        assertTrue(Formats.currency(1.0, privacy = true).contains(Formats.PRIVACY_MASK))
    }
}
