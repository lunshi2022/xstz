package com.huaying.xstz.core.network

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历史净值解析测试。
 *
 * 锁三件事：
 *   1. 场外 `lsjz` 的字段（FSRQ / DWJZ / LJJZ）取对，索引不能错
 *   2. 场内 K 线取**收盘价**（索引 2），不是开盘价（索引 1）——取错会把每天涨跌算反
 *   3. 脏数据（缺字段、净值 <= 0、日期非法）跳过而不是造出 0 值点
 */
class NavHistoryParserTest {

    private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

    @Test
    fun `解析场外基金历史净值`() {
        val body = """
            {"Data":{"LSJZList":[
              {"FSRQ":"2026-10-30","DWJZ":"2.8710","LJJZ":"2.8710","JZZZL":"2.35"},
              {"FSRQ":"2026-10-29","DWJZ":"2.8050","LJJZ":"2.8050","JZZZL":"1.37"}
            ]},"ErrCode":0}
        """.trimIndent()

        val points = NavHistoryParser.parseFundHistory(body)

        assertEquals(2, points.size)
        // 接口是倒序返回的，解析后必须升序——回放器依赖升序
        assertEquals(day("2026-10-29"), points[0].epochDay)
        assertEquals(day("2026-10-30"), points[1].epochDay)
        assertEquals(2.871, points[1].nav.toDouble(), 1e-6)
    }

    @Test
    fun `场外历史取累计净值作为 accumulatedNav`() {
        val body = """
            {"Data":{"LSJZList":[{"FSRQ":"2026-10-30","DWJZ":"1.2340","LJJZ":"3.4560"}]}}
        """.trimIndent()

        val points = NavHistoryParser.parseFundHistory(body)

        assertEquals(1.234, points[0].nav.toDouble(), 1e-6)
        // 分红过的基金，单位净值与累计净值不同，两者都要留住
        assertEquals(3.456, points[0].accumulatedNav.toDouble(), 1e-6)
    }

    @Test
    fun `解析场内K线取收盘价而不是开盘价`() {
        // 索引：0 日期 / 1 开 / 2 收 / 3 高 / 4 低 / 5 量
        val body = """
            {"code":0,"data":{"sh600519":{"qfqday":[
              ["2026-10-29","1349.990","1354.000","1356.000","1330.010","42992.000"],
              ["2026-10-30","1363.000","1392.050","1393.370","1352.980","59927"]
            ]}}}
        """.trimIndent()

        val points = NavHistoryParser.parseKlineHistory("sh600519", body)

        assertEquals(2, points.size)
        // 收盘价 1392.05，不是开盘价 1363.00
        assertEquals(1392.05, points[1].nav.toDouble(), 1e-6)
    }

    @Test
    fun `没有前复权数据时退回不复权`() {
        val body = """
            {"data":{"sz159915":{"day":[["2026-10-30","2.100","2.200","2.250","2.050","1000"]]}}}
        """.trimIndent()

        val points = NavHistoryParser.parseKlineHistory("sz159915", body)

        assertEquals(1, points.size)
        assertEquals(2.200, points[0].nav.toDouble(), 1e-6)
    }

    @Test
    fun `净值非正数或字段缺失时跳过该点`() {
        val body = """
            {"Data":{"LSJZList":[
              {"FSRQ":"2026-10-30","DWJZ":"0.0000"},
              {"FSRQ":"2026-10-29","DWJZ":""},
              {"FSRQ":"","DWJZ":"1.5000"},
              {"FSRQ":"2026-10-28","DWJZ":"1.5000"}
            ]}}
        """.trimIndent()

        val points = NavHistoryParser.parseFundHistory(body)

        // 只有最后一条是完整的有效记录
        assertEquals(1, points.size)
        assertEquals(day("2026-10-28"), points[0].epochDay)
    }

    @Test
    fun `空体与畸形JSON都返回空列表而不是抛异常`() {
        assertTrue(NavHistoryParser.parseFundHistory("").isEmpty())
        assertTrue(NavHistoryParser.parseFundHistory("not json").isEmpty())
        assertTrue(NavHistoryParser.parseFundHistory("{}").isEmpty())
        assertTrue(NavHistoryParser.parseKlineHistory("sh600519", "").isEmpty())
        assertTrue(NavHistoryParser.parseKlineHistory("sh600519", """{"data":{}}""").isEmpty())
    }
}
