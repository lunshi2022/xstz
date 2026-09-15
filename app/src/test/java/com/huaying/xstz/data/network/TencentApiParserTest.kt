package com.huaying.xstz.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 腾讯财经接口解析器单元测试
 * 样本基于接口实测字段约定；若上游字段变更，先修常量再修样本
 */
class TencentApiParserTest {

    // ==================== 代码分类 ====================

    @Test
    fun isStockCode_shanghaiMainBoard() {
        assertTrue(TencentApiParser.isStockCode("600519"))
        assertTrue(TencentApiParser.isStockCode("601318"))
        assertTrue(TencentApiParser.isStockCode("603259"))
        assertTrue(TencentApiParser.isStockCode("605111"))
    }

    @Test
    fun isStockCode_starMarketAndShenzhen() {
        assertTrue(TencentApiParser.isStockCode("688111"))
        assertTrue(TencentApiParser.isStockCode("000001"))
        assertTrue(TencentApiParser.isStockCode("300750"))
        assertTrue(TencentApiParser.isStockCode("301236"))
        assertTrue(TencentApiParser.isStockCode("200012"))
    }

    @Test
    fun isStockCode_rejectsNonStock() {
        assertTrue(!TencentApiParser.isStockCode("510300"))   // 场内基金
        assertTrue(!TencentApiParser.isStockCode("159915"))   // 场内基金
        assertTrue(!TencentApiParser.isStockCode("110022"))   // 场外基金
        assertTrue(!TencentApiParser.isStockCode("123456"))   // 无效
    }

    @Test
    fun is场内基金Code_etfCodes() {
        assertTrue(TencentApiParser.is场内基金Code("510300"))  // 沪
        assertTrue(TencentApiParser.is场内基金Code("588000"))  // 沪
        assertTrue(TencentApiParser.is场内基金Code("159915"))  // 深
        assertTrue(TencentApiParser.is场内基金Code("160323"))  // 深
    }

    @Test
    fun is场外基金Code_offMarketFunds() {
        // "11"/"27" 前缀：不匹配股票与场内基金规则，归为场外
        assertTrue(TencentApiParser.is场外基金Code("110022"))
        assertTrue(TencentApiParser.is场外基金Code("270042"))
        assertTrue(!TencentApiParser.is场外基金Code("000001")) // 股票
        assertTrue(!TencentApiParser.is场外基金Code("510300")) // 场内
        // "16" 前缀（深交所 LOF）按规则归为场内基金
        assertTrue(!TencentApiParser.is场外基金Code("161725"))
    }

    @Test
    fun determineExchange_codes() {
        assertEquals("sh", TencentApiParser.determineStockExchange("600519"))
        assertEquals("sz", TencentApiParser.determineStockExchange("000001"))
        assertEquals("sh", TencentApiParser.determineFundExchange("510300"))
        assertEquals("sz", TencentApiParser.determineFundExchange("159915"))
        assertNull(TencentApiParser.determineStockExchange("110022"))
    }

    // ==================== parseFundName ====================

    @Test
    fun parseFundName_validResponse_returnsName() {
        val content = "v_jj110022=\"1~易方达优质精选混合~110022~0.0000~0.0000~3.1415~3.2000~1.23~2026-08-07~\";"
        val (name, error) = TencentApiParser.parseFundName(content)
        assertEquals("易方达优质精选混合", name)
        assertEquals("", error)
    }

    @Test
    fun parseFundName_emptyContent_returnsError() {
        val (name, error) = TencentApiParser.parseFundName("")
        assertNull(name)
        assertEquals("响应为空", error)
    }

    @Test
    fun parseFundName_noQuotes_returnsError() {
        val (name, _) = TencentApiParser.parseFundName("invalid response")
        assertNull(name)
    }

    @Test
    fun parseFundName_tooFewFields_returnsError() {
        val (name, _) = TencentApiParser.parseFundName("v_jj110022=\"1~\";")
        assertNull(name)
    }

    // ==================== parseRealtimeData：场外基金 ====================

    @Test
    fun parseRealtimeData_offMarketFund_usesUnitNavAsPrice() {
        // fields[5]=单位净值 → currentPrice；fields[7]=日增长率 → changePercent；preClosePrice 恒为 0
        val content = "v_jj110022=\"1~易方达优质精选混合~110022~0.0000~0.0000~3.1415~3.2000~1.23~2026-08-07~\";"
        val result = TencentApiParser.parseRealtimeData("110022", content)

        assertEquals("易方达优质精选混合", result!!["name"])
        assertEquals(3.1415, result["currentPrice"] as Double, 0.0001)
        assertEquals(0.0, result["preClosePrice"] as Double, 0.0001)
        assertEquals(0.0, result["change"] as Double, 0.0001)
        assertEquals(1.23, result["changePercent"] as Double, 0.0001)
    }

    @Test
    fun parseRealtimeData_offMarketFund_nonNumericNav_fallsBackToZero() {
        val content = "v_jj110022=\"1~某基金~110022~0.0000~0.0000~~3.2000~1.23~2026-08-07~\";"
        val result = TencentApiParser.parseRealtimeData("110022", content)
        assertEquals(0.0, result!!["currentPrice"] as Double, 0.0001)
    }

    // ==================== parseRealtimeData：股票/场内基金 ====================

    @Test
    fun parseRealtimeData_stock_extractsPriceFields() {
        // 构造含 33+ 字段的股票响应：[1]名称 [3]现价 [4]昨收 [31]涨跌额 [32]涨跌幅
        val fields = MutableList(40) { i -> "f$i" }
        fields[0] = "1"; fields[1] = "贵州茅台"; fields[2] = "600519"
        fields[3] = "1700.00"; fields[4] = "1690.00"
        fields[31] = "10.00"; fields[32] = "0.59"
        val content = "v_sh600519=\"${fields.joinToString("~")}\";"

        val result = TencentApiParser.parseRealtimeData("600519", content)

        assertEquals("贵州茅台", result!!["name"])
        assertEquals(1700.00, result["currentPrice"] as Double, 0.0001)
        assertEquals(1690.00, result["preClosePrice"] as Double, 0.0001)
        assertEquals(10.00, result["change"] as Double, 0.0001)
        assertEquals(0.59, result["changePercent"] as Double, 0.0001)
    }

    @Test
    fun parseRealtimeData_indexFundSameRuleAsStock() {
        val fields = MutableList(40) { i -> "f$i" }
        fields[0] = "1"; fields[1] = "沪深300ETF"; fields[2] = "510300"
        fields[3] = "4.100"; fields[4] = "4.080"
        fields[31] = "0.020"; fields[32] = "0.49"
        val content = "v_sh510300=\"${fields.joinToString("~")}\";"

        val result = TencentApiParser.parseRealtimeData("510300", content)
        assertEquals(4.100, result!!["currentPrice"] as Double, 0.0001)
        assertEquals(4.080, result["preClosePrice"] as Double, 0.0001)
    }

    @Test
    fun parseRealtimeData_insufficientFields_returnsNull() {
        val content = "v_sh600519=\"1~贵州茅台~600519~1700.00~\";"
        assertNull(TencentApiParser.parseRealtimeData("600519", content))
    }

    @Test
    fun parseRealtimeData_noQuotes_returnsNull() {
        assertNull(TencentApiParser.parseRealtimeData("600519", "pv_none=1;"))
    }

    @Test
    fun parseRealtimeData_unrecognizedCode_returnsNull() {
        assertNull(TencentApiParser.parseRealtimeData("999999", "v_x=\"1~x~999999~1~2~\";"))
    }
}
