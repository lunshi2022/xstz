package com.huaying.xstz.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 代码段分类的**边界与交易所映射**测试。
 *
 * 与 QuoteParserTest.kt 里的 CodeClassifierTest 分工：
 * 那个类钉"某个代码属于哪一类"（含 519xxx 回归、110022 段重叠），
 * 这里钉"每一段的边界值"和"交易所前缀怎么生成"。
 *
 * 之所以单独拎出来，是因为当年 519xxx 的 bug 恰恰是**段边界写宽了**——
 * `^5\d{4,5}$` 把 519 也吞进了场内。段边界必须逐个卡死，只测中间值测不出问题。
 */
class CodeClassifierBoundaryTest {

    // ---------------- 场内基金：逐段卡边界 ----------------

    @Test
    fun `沪市场内基金四段的首尾都命中`() {
        listOf(
            "500001", "506999", // 500~506
            "510300", "518880", // 510~518
            "560010", "563999", // 560~563
            "588000", "589999", // 588~589
        ).forEach { assertTrue("$it 应为场内基金", CodeClassifier.isExchangeTradedFund(it)) }
    }

    @Test
    fun `深市场内基金三段都命中`() {
        listOf("150001", "159915", "160001", "161725", "180101", "189999").forEach {
            assertTrue("$it 应为场内基金", CodeClassifier.isExchangeTradedFund(it))
        }
    }

    @Test
    fun `场内基金的相邻非法段不命中`() {
        // 507 在 500~506 之外；519 在 510~518 之外；564 在 560~563 之外
        listOf("507000", "519066", "564000", "590000", "170000").forEach {
            assertFalse("$it 不应判为场内基金", CodeClassifier.isExchangeTradedFund(it))
        }
    }

    // ---------------- 债券 ----------------

    @Test
    fun `可转债各段首尾都命中`() {
        listOf(
            "110022", "111000", "113050", "118000", // 沪 11[0138]
            "123456", "127000", "128000", // 深 12[378]
        ).forEach { assertTrue("$it 应为可转债", CodeClassifier.isConvertibleBond(it)) }
    }

    @Test
    fun `可转债相邻非法段不命中`() {
        listOf("112000", "114000", "124000", "129000").forEach {
            assertFalse("$it 不应判为可转债", CodeClassifier.isConvertibleBond(it))
        }
    }

    @Test
    fun `国债与地方债都算债券`() {
        listOf("019547", "100303").forEach {
            assertTrue("$it 应为国债/地方债", CodeClassifier.isTreasuryBond(it))
            assertTrue("$it 应归入债券", CodeClassifier.isBond(it))
        }
        assertFalse(CodeClassifier.isTreasuryBond("600519"))
    }

    // ---------------- 交易所前缀 ----------------

    @Test
    fun `北交所股票映射为 bj 前缀`() {
        // 回归防护：stockExchange 曾只处理沪深，北交所返回 null，
        // 而 tencentQuoteSymbol 用 ?.let 串联，null 会静默变成"不查行情"
        assertEquals("bj", CodeClassifier.stockExchange("430047"))
        assertEquals("bj", CodeClassifier.stockExchange("830799"))
        assertEquals("bj", CodeClassifier.stockExchange("870508"))
        assertEquals("bj", CodeClassifier.stockExchange("889999"))
        assertEquals("bj430047", CodeClassifier.tencentQuoteSymbol("430047"))
    }

    @Test
    fun `股票交易所前缀覆盖沪深`() {
        assertEquals("sh", CodeClassifier.stockExchange("600519"))
        assertEquals("sh", CodeClassifier.stockExchange("900901")) // 沪 B
        assertEquals("sz", CodeClassifier.stockExchange("000001"))
        assertEquals("sz", CodeClassifier.stockExchange("300750"))
        assertEquals("sz", CodeClassifier.stockExchange("200011")) // 深 B
        assertNull(CodeClassifier.stockExchange("510300"))
    }

    @Test
    fun `基金与债券各自的交易所前缀`() {
        assertEquals("sh", CodeClassifier.fundExchange("510300"))
        assertEquals("sz", CodeClassifier.fundExchange("159915"))
        assertNull(CodeClassifier.fundExchange("600519"))

        assertEquals("sh", CodeClassifier.bondExchange("019547"))
        assertEquals("sh", CodeClassifier.bondExchange("113050"))
        assertEquals("sz", CodeClassifier.bondExchange("123456"))
        assertEquals("sz", CodeClassifier.bondExchange("100303"))
        assertNull(CodeClassifier.bondExchange("600519"))
    }

    @Test
    fun `各类代码生成正确的行情符号`() {
        assertEquals("sh600519", CodeClassifier.tencentQuoteSymbol("600519"))
        assertEquals("sz000001", CodeClassifier.tencentQuoteSymbol("000001"))
        assertEquals("sh510300", CodeClassifier.tencentQuoteSymbol("510300"))
        assertEquals("sz159915", CodeClassifier.tencentQuoteSymbol("159915"))
        assertEquals("sh113050", CodeClassifier.tencentQuoteSymbol("113050"))
        assertEquals("sz123456", CodeClassifier.tencentQuoteSymbol("123456"))
        assertEquals("sh019547", CodeClassifier.tencentQuoteSymbol("019547"))
    }

    @Test
    fun `已带交易所前缀的代码原样小写返回`() {
        assertEquals("sh600519", CodeClassifier.tencentQuoteSymbol("SH600519"))
        assertEquals("sz000001", CodeClassifier.tencentQuoteSymbol("sz000001"))
    }

    @Test
    fun `无法识别的输入返回 null 而不抛异常`() {
        assertNull(CodeClassifier.tencentQuoteSymbol("ABCDEF"))
        assertNull(CodeClassifier.tencentQuoteSymbol(""))
    }
}
