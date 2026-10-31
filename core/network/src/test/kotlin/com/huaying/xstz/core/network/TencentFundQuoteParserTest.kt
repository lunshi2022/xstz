package com.huaying.xstz.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TencentQuoteParser] 的场外基金解析与防御分支。
 *
 * 与 QuoteParserTest 的分工：那个文件覆盖场内行情与估值的主路径，
 * 这里专测 **jj 前缀的场外基金格式**（parseFundQuote 此前完全没有测试）
 * 以及各条 `return null` 的降级分支。
 *
 * 之所以值得单独测：这些分支决定了"取不到数据时界面怎么表现"。
 * 一旦这里悄悄返回了半成品（比如净值为 0 的对象而不是 null），
 * 上层就不会降级到缓存/样例，界面会显示 0.0000——比"暂无数据"更误导。
 */
class TencentFundQuoteParserTest {

    // v_jj 前缀：0 代码 / 1 名称 / 5 单位净值 / 6 累计净值 / 7 日涨跌% / 8 净值日期
    private val jjRaw =
        """v_jj000001="000001~华夏成长混合~0.0000~0.0000~~0.9190~3.4920~-5.0620~2026-10-30~";"""

    @Test
    fun `解析场外基金行情`() {
        val e = TencentQuoteParser.parseFundQuote("000001", jjRaw)
        assertNotNull(e)
        e!!
        assertEquals("000001", e.code)
        assertEquals("华夏成长混合", e.name)
        assertEquals(0.9190, e.estimatedNav.toDouble(), 1e-6)
        assertEquals(-5.0620, e.estimatedChangePercent, 1e-6)
        assertEquals("2026-10-30", e.navDate)
        // 昨净值由「净值 /(1+涨跌%)」反推：0.9190 / 0.94938 ≈ 0.96801
        assertEquals(0.96801, e.nav.toDouble(), 1e-4)
    }

    @Test
    fun `涨跌幅字段非法时按零处理而不是丢弃整条数据`() {
        val raw = """v_jj000001="000001~某基金~0~0~~1.2340~1.2340~~2026-10-30~";"""
        val e = TencentQuoteParser.parseFundQuote("000001", raw)
        assertNotNull(e)
        assertEquals(0.0, e!!.estimatedChangePercent, 1e-9)
        // 涨跌幅为 0 时昨净值应等于净值本身
        assertEquals(1.2340, e.nav.toDouble(), 1e-6)
    }

    @Test
    fun `响应为空返回 null`() {
        assertNull(TencentQuoteParser.parseFundQuote("000001", ""))
        assertNull(TencentQuoteParser.parseFundQuote("000001", "   "))
        assertNull(TencentQuoteParser.parseFundQuote("000001", """v_jj000001="";"""))
    }

    @Test
    fun `字段不足九段返回 null`() {
        val raw = """v_jj000001="000001~某基金~0~0~~0.9190~3.4920";"""
        assertNull(TencentQuoteParser.parseFundQuote("000001", raw))
    }

    @Test
    fun `净值为零或负数返回 null`() {
        // 新发基金募集期就是 0，没有净值可展示，必须交给上层降级
        val zero = """v_jj000001="000001~某基金~0~0~~0.0000~0.0000~1.0000~2026-10-30~";"""
        assertNull(TencentQuoteParser.parseFundQuote("000001", zero))

        val negative = """v_jj000001="000001~某基金~0~0~~-0.5000~0.0000~1.0000~2026-10-30~";"""
        assertNull(TencentQuoteParser.parseFundQuote("000001", negative))
    }

    @Test
    fun `净值字段非数字返回 null`() {
        val raw = """v_jj000001="000001~某基金~0~0~~abc~3.4920~1.0000~2026-10-30~";"""
        assertNull(TencentQuoteParser.parseFundQuote("000001", raw))
    }

    // ---------------- 场内行情的其余降级分支 ----------------

    @Test
    fun `名称为空返回 null`() {
        val raw = """v_sh600519="1~~600519~10.00~9.00";"""
        assertNull(TencentQuoteParser.parseQuote("600519", raw))
    }

    @Test
    fun `价格非数字返回 null`() {
        val raw = """v_sh600519="1~贵州茅台~600519~abc~9.00";"""
        assertNull(TencentQuoteParser.parseQuote("600519", raw))
    }

    @Test
    fun `昨收非数字时用现价兜底而不是丢弃数据`() {
        val raw = """v_sh600519="1~贵州茅台~600519~10.00~abc";"""
        val q = TencentQuoteParser.parseQuote("600519", raw)
        assertNotNull(q)
        assertEquals(10.00, q!!.nav.toDouble(), 1e-6)
        assertEquals(10.00, q.prevNav.toDouble(), 1e-6)
    }

    @Test
    fun `昨收为零时涨跌幅为零而不是除零`() {
        val raw = """v_sh600519="1~贵州茅台~600519~10.00~0.00";"""
        val q = TencentQuoteParser.parseQuote("600519", raw)
        assertNotNull(q)
        assertEquals(0.0, q!!.changeRate, 1e-9)
    }

    @Test
    fun `没有引号包裹的响应返回 null`() {
        assertNull(TencentQuoteParser.parseQuote("600519", "v_sh600519=;"))
        assertNull(TencentQuoteParser.extractQuotedPayload("没有任何标记"))
    }
}
