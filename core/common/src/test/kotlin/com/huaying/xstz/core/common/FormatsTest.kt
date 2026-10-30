package com.huaying.xstz.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatsTest {

    @Test
    fun `金额带千分位与货币符号`() {
        assertEquals("¥1,234.56", Formats.currency(1_234.56))
    }

    @Test
    fun `隐私模式返回掩码而不是空串`() {
        assertEquals("¥ ****", Formats.currency(1_234.56, privacy = true))
        assertEquals("**%", Formats.percent(0.05, privacy = true))
    }

    @Test
    fun `正涨跌幅带加号`() {
        assertEquals("+2.50%", Formats.percent(0.025))
    }

    @Test
    fun `负涨跌幅带减号`() {
        assertEquals("-1.30%", Formats.percent(-0.013))
    }

    @Test
    fun `零涨跌幅显示为正号`() {
        assertEquals("+0.00%", Formats.percent(0.0))
    }

    @Test
    fun `负零不显示为负号`() {
        // -0.0000001 乘 100 后约等于 -1e-5，应被归零
        assertEquals("+0.00%", Formats.percent(-1e-9))
    }

    @Test
    fun `占比不带符号`() {
        assertEquals("25.00%", Formats.ratio(0.25))
    }

    @Test
    fun `占比一位小数`() {
        assertEquals("25.0%", Formats.ratio1f(0.25))
    }

    @Test
    fun `净值四位小数四舍五入`() {
        // 1.23456 保留 4 位：第 5 位是 6，进位到 1.2346
        assertEquals("1.2346", Formats.nav(1.23456))
    }

    @Test
    fun `成本价带货币符号四位小数`() {
        assertEquals("¥1.2346", Formats.costPrice(1.23456))
    }

    @Test
    fun `份额两位小数带千分位`() {
        assertEquals("12,345.68", Formats.quantity(12_345.678))
    }

    @Test
    fun `epochDay 转日期`() {
        assertEquals("2026-10-31", Formats.date(LocalDate.of(2026, 10, 31).toEpochDay()))
    }

    @Test
    fun `中文日期`() {
        assertEquals("10月31日", Formats.chineseDate(LocalDate.of(2026, 10, 31).toEpochDay()))
    }

    @Test
    fun `星期中文`() {
        assertEquals("六", Formats.weekday(LocalDate.of(2026, 10, 31).toEpochDay()))
        assertEquals("五", Formats.weekday(LocalDate.of(2026, 10, 30).toEpochDay()))
    }

    @Test
    fun `刚更新的新鲜度`() {
        val now = 1_000_000_000_000L
        assertEquals("刚刚", Formats.freshness(now - 30_000L, now))
    }

    @Test
    fun `分钟级新鲜度`() {
        val now = 1_000_000_000_000L
        assertEquals("5 分钟前", Formats.freshness(now - 5 * 60_000L, now))
    }

    @Test
    fun `小时级新鲜度`() {
        val now = 1_000_000_000_000L
        assertEquals("3 小时前", Formats.freshness(now - 3 * 3600_000L, now))
    }

    @Test
    fun `零时间戳表示未更新`() {
        assertEquals("未更新", Formats.freshness(0L))
    }
}
