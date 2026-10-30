package com.huaying.xstz.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器回归测试。
 *
 * 这些用例锁死的是「字段索引」——腾讯接口按 ~ 分割，索引错了不会报错，
 * 只会静默把昨收当净值展示，这类 bug 靠肉眼极难发现，必须靠测试守住。
 */
class TencentQuoteParserTest {

    @Test
    fun `解析场内行情基本字段`() {
        val raw = """v_sh600519="1~贵州茅台~600519~1680.00~1675.00~0.30~0.02~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00~0.00";"""
        val q = TencentQuoteParser.parseQuote("600519", raw)
        assertNotNull(q)
        assertEquals("贵州茅台", q!!.name)
        assertEquals(1680.00, q.nav.toDouble(), 1e-6)
        assertEquals(1675.00, q.prevNav.toDouble(), 1e-6)
    }

    @Test
    fun `解析涨跌幅`() {
        val raw = """v_sz000001="1~平安银行~000001~10.00~9.50~0.00~0.00";"""
        val q = TencentQuoteParser.parseQuote("000001", raw)!!
        // (10 - 9.5) / 9.5 = 5.26%
        assertEquals(0.0526, q.changeRate, 1e-4)
    }

    @Test
    fun `字段不足返回 null 而不是崩溃`() {
        val raw = """v_sz000001="1~平安银行~000001";"""
        assertNull(TencentQuoteParser.parseQuote("000001", raw))
    }

    @Test
    fun `空响应返回 null`() {
        assertNull(TencentQuoteParser.parseQuote("000001", ""))
        assertNull(TencentQuoteParser.parseQuote("000001", "   "))
    }

    @Test
    fun `响应用 v_pv_none_match 时应返回 null`() {
        assertNull(TencentQuoteParser.parseQuote("999999", "v_pv_none_match=\"1\";"))
    }

    @Test
    fun `停牌时价格为零用昨收兜底`() {
        val raw = """v_sz000001="1~平安银行~000001~0.00~9.50~0.00~0.00";"""
        val q = TencentQuoteParser.parseQuote("000001", raw)!!
        // 价格取昨收，市值不会突然归零
        assertEquals(9.50, q.nav.toDouble(), 1e-6)
        assertEquals(0.0, q.changeRate, 1e-9)
    }

    @Test
    fun `解析场外基金估值`() {
        val raw =
            """jsonpgz({"fundcode":"000001","name":"华夏成长","jzrq":"2026-10-30","dwjz":"1.2345","gsz":"1.2400","gszzl":"0.45","gztime":"2026-10-31 14:30"});"""
        val e = TencentQuoteParser.parseEstimate("000001", raw)
        assertNotNull(e)
        assertEquals("华夏成长", e!!.name)
        assertEquals(1.2345, e.nav.toDouble(), 1e-6)
        assertEquals(1.2400, e.estimatedNav.toDouble(), 1e-6)
        assertEquals(0.45, e.estimatedChangePercent, 1e-6)
        assertEquals("2026-10-30", e.navDate)
        assertEquals("2026-10-31 14:30", e.estimateTime)
    }

    @Test
    fun `估值缺少字段返回 null`() {
        val raw = """jsonpgz({"fundcode":"000001"});"""
        assertNull(TencentQuoteParser.parseEstimate("000001", raw))
    }

    @Test
    fun `估值响应为空返回 null`() {
        assertNull(TencentQuoteParser.parseEstimate("000001", "jsonpgz();"))
    }

    @Test
    fun `负数估值涨跌可解析`() {
        val raw =
            """jsonpgz({"name":"X","dwjz":"1.0000","gsz":"0.9900","gszzl":"-1.00","jzrq":"2026-10-30","gztime":"t"});"""
        val e = TencentQuoteParser.parseEstimate("X", raw)!!
        assertEquals(-1.00, e.estimatedChangePercent, 1e-6)
    }
}

class CodeClassifierTest {

    @Test
    fun `识别沪市主板股票`() {
        assertTrue(CodeClassifier.isStock("600519"))
        assertTrue(CodeClassifier.isStock("601318"))
        assertTrue(CodeClassifier.isStock("603288"))
        assertTrue(CodeClassifier.isStock("605117"))
    }

    @Test
    fun `识别科创板与创业板`() {
        assertTrue(CodeClassifier.isStock("688981"))
        assertTrue(CodeClassifier.isStock("300750"))
        assertTrue(CodeClassifier.isStock("301029"))
    }

    @Test
    fun `识别深市主板`() {
        assertTrue(CodeClassifier.isStock("000001"))
        assertTrue(CodeClassifier.isStock("002594"))
    }

    @Test
    fun `识别场内基金`() {
        assertTrue(CodeClassifier.isExchangeTradedFund("510300"))
        assertTrue(CodeClassifier.isExchangeTradedFund("159915"))
        assertTrue(CodeClassifier.isExchangeTradedFund("588000"))
        assertTrue(CodeClassifier.isExchangeTradedFund("161725"))
    }

    @Test
    fun `519开头是场外基金不是场内`() {
        // 回归防护：旧规则 ^5\d{4,5}$ 把 519xxx 误判成场内 ETF，
        // 导致这批基金走错行情接口、净值永远取不到
        assertFalse(CodeClassifier.isExchangeTradedFund("519674"))
        assertTrue(CodeClassifier.isOpenEndFund("519674"))
    }

    @Test
    fun `场外基金是六位数字且非股票非场内`() {
        assertFalse(CodeClassifier.isOpenEndFund("000001")) // 深市股票
        assertTrue(CodeClassifier.isOpenEndFund("110022"))
        assertTrue(CodeClassifier.isOpenEndFund("519674"))
        assertTrue(CodeClassifier.isOpenEndFund("008888"))
        // 001186 属于深市主板 001xxx 股票段，不应判为场外基金
        assertFalse(CodeClassifier.isOpenEndFund("001186"))
    }

    @Test
    fun `沪市股票交易所为 sh`() {
        assertEquals("sh", CodeClassifier.stockExchange("600519"))
    }

    @Test
    fun `深市股票交易所为 sz`() {
        assertEquals("sz", CodeClassifier.stockExchange("000001"))
        assertEquals("sz", CodeClassifier.stockExchange("300750"))
    }

    @Test
    fun `场外基金不给行情符号`() {
        assertNull(CodeClassifier.tencentQuoteSymbol("110022"))
    }

    @Test
    fun `场内股票给出带交易所前缀的符号`() {
        assertEquals("sh600519", CodeClassifier.tencentQuoteSymbol("600519"))
        assertEquals("sz159915", CodeClassifier.tencentQuoteSymbol("159915"))
    }

    @Test
    fun `非数字代码不误判`() {
        assertFalse(CodeClassifier.isStock("ABCDEF"))
        assertFalse(CodeClassifier.isOpenEndFund("ABCDEF"))
    }
}
