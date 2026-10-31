package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.InstrumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索解析器测试。
 *
 * 用真实响应样本，不用构造的假数据——
 * 这类接口最坑的是"字段顺序变了但返回 200"，
 * 只有拿真实样本钉住，才能在接口改版时立刻发现。
 */
class TencentSuggestParserTest {

    /** 2026-10-31 真实响应：搜"贵州茅台" */
    private val maotai = """v_hint="sh~600519~\u8d35\u5dde\u8305\u53f0~gzmt~GP-A";"""

    /** 搜"五粮液" */
    private val wuliangye = """v_hint="sz~000858~\u4e94\u7cae\u6db2~wly~GP-A";"""

    /**
     * 搜"沪深300"：同时返回指数、ETF、LOF。
     * 指数（ZS）必须被丢掉——用户看到"沪深300 000300"会以为能买。
     */
    private val hs300 = """v_hint="sh~000300~\u6caa\u6df1300~hs300~ZS^sz~399300~\u6caa\u6df1300~hs300~ZS^sh~510300~\u6caa\u6df1300ETF\u534e\u6cf0\u67cf\u745e~hs300etfhtbr~ETF^sz~160706~\u6caa\u6df1300LOF~hs300lof~LOF";"""

    /** 搜"易方达消费行业"：场外开放式基金，前缀 jj */
    private val fund = """v_hint="jj~110022~\u6613\u65b9\u8fbe\u6d88\u8d39\u884c\u4e1a\u80a1\u7968~yfdxfhygp~KJ";"""

    /** 无结果 */
    private val none = """v_hint="N";"""

    /** 港股与美股：本应用不做境外持仓，必须丢掉 */
    private val overseas = """v_hint="hk~03489~\u6613\u65b9\u8fbeai~yfdai~GP^us~aapx.am~etf opportunities tr t rex~*~GP";"""

    @Test
    fun `解析A股名称与代码`() {
        val hits = TencentSuggestParser.parse(maotai)
        assertEquals(1, hits.size)
        assertEquals("600519", hits[0].code)
        assertEquals("贵州茅台", hits[0].name)
        assertEquals(InstrumentKind.STOCK, hits[0].kind)
    }

    @Test
    fun `解析深市股票`() {
        val hits = TencentSuggestParser.parse(wuliangye)
        assertEquals(1, hits.size)
        assertEquals("000858", hits[0].code)
        assertEquals("五粮液", hits[0].name)
        assertEquals(InstrumentKind.STOCK, hits[0].kind)
    }

    @Test
    fun `指数被丢弃只保留可持有的`() {
        val hits = TencentSuggestParser.parse(hs300)
        assertTrue("指数不应出现", hits.none { it.code == "000300" })
        assertTrue("指数不应出现", hits.none { it.code == "399300" })
        assertEquals(2, hits.size)
        assertEquals(InstrumentKind.ETF, hits[0].kind)
        assertEquals("510300", hits[0].code)
    }

    @Test
    fun `场外基金解析为FUND`() {
        val hits = TencentSuggestParser.parse(fund)
        assertEquals(1, hits.size)
        assertEquals("110022", hits[0].code)
        assertEquals("易方达消费行业股票", hits[0].name)
        assertEquals(InstrumentKind.FUND, hits[0].kind)
    }

    @Test
    fun `无结果返回空列表`() {
        assertTrue(TencentSuggestParser.parse(none).isEmpty())
    }

    @Test
    fun `港股与美股被丢弃`() {
        assertTrue(TencentSuggestParser.parse(overseas).isEmpty())
    }

    @Test
    fun `空响应与畸形响应都不崩`() {
        assertTrue(TencentSuggestParser.parse("").isEmpty())
        assertTrue(TencentSuggestParser.parse("   ").isEmpty())
        assertTrue(TencentSuggestParser.parse("not a json").isEmpty())
        // 字段不足
        assertTrue(TencentSuggestParser.parse("""v_hint="sh~600519~贵州茅台";""").isEmpty())
    }

    @Test
    fun `名称里的Unicode转义被解码`() {
        assertEquals("贵州茅台", TencentSuggestParser.decodeUnicode("\\u8d35\\u5dde\\u8305\\u53f0"))
    }

    @Test
    fun `畸形转义原样保留而不是吞字符`() {
        // \uZZZZ 不是合法转义：整段当作普通文本，不能把后面的字吃掉
        assertEquals("\\uZZZZabc", TencentSuggestParser.decodeUnicode("\\uZZZZabc"))
        // 结尾处的截断转义同样不能丢字符
        assertEquals("ab\\u12", TencentSuggestParser.decodeUnicode("ab\\u12"))
    }

    @Test
    fun `结果条数不超过上限`() {
        // 同一响应里塞 40 条
        val many = (1..40).joinToString("^") { "sh~60000$it~标的$it~bd$it~GP-A" }
        val raw = """v_hint="$many";"""
        assertTrue(TencentSuggestParser.parse(raw).size <= TencentSuggestParser.MAX_HITS)
    }
}
