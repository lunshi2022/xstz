package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * 历史净值解析。
 *
 * 两个来源，两套格式，都要支持——组合快照的回填依赖它：
 *
 *   1. **场外基金** 天天基金 `api.fund.eastmoney.com/f10/lsjz`
 *      返回 `{"Data":{"LSJZList":[{"FSRQ":"2026-10-30","DWJZ":"2.8710",...}]}}`
 *      注意必须带 `Referer: https://fundf10.eastmoney.com/`，否则返回空。
 *
 *   2. **场内标的** 腾讯 `web.ifzq.gtimg.cn/appstock/app/fqkline/get`
 *      返回 `{"data":{"sh600519":{"qfqday":[["2026-10-30","1363.000","1392.050",...]]}}}`
 *      数组索引：0 日期 / 1 开 / 2 收 / **3 高** / **4 低** / 5 量。
 *      取索引 2（收盘价）作为当日净值。
 *
 * 为什么不用同一个接口：场内没有"净值"概念，只有成交价；场外没有 K 线。
 * 硬要用一个接口，就得在其中一边造数——那比两个接口都写更危险。
 */
object NavHistoryParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析场外基金历史净值。
     *
     * @param body `lsjz` 的响应体
     * @return 按日期升序的净值点；解析失败或有缺失字段的点被跳过
     */
    fun parseFundHistory(body: String): List<NavPoint> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val list = root["Data"]?.jsonObject?.get("LSJZList")?.jsonArray ?: return emptyList()
        list.mapNotNull { element ->
            val obj = element.jsonObject
            val date = obj["FSRQ"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            // DWJZ = 单位净值；LJJZ = 累计净值（分红后仍能反映真实收益）
            val navText = obj["DWJZ"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val accText = obj["LJJZ"]?.jsonPrimitive?.contentOrNullSafe()
            val epochDay = parseEpochDay(date) ?: return@mapNotNull null
            val nav = navText.toDoubleOrNull() ?: return@mapNotNull null
            if (nav <= 0.0) return@mapNotNull null
            NavPoint(
                epochDay = epochDay,
                nav = Nav.fromDouble(nav),
                accumulatedNav = accText?.toDoubleOrNull()?.let { Nav.fromDouble(it) } ?: Nav.fromDouble(nav),
            )
        }.sortedBy { it.epochDay }
    }.getOrDefault(emptyList())

    /**
     * 解析场内 K 线为净值序列。
     *
     * @param symbol 带交易所前缀的代码（sh600519 / sz159915），用于在 data 里定位数组
     * @param body   `fqkline` 的响应体
     */
    fun parseKlineHistory(symbol: String, body: String): List<NavPoint> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val node = root["data"]?.jsonObject?.get(symbol)?.jsonObject ?: return emptyList()
        // 前复权优先：不复权的价格在除权日会跳空，直接把跳空当成"暴跌"算进收益曲线
        val rows = node["qfqday"]?.jsonArray ?: node["day"]?.jsonArray ?: return emptyList()
        rows.mapNotNull { row ->
            val arr = row.jsonArray
            if (arr.size < 3) return@mapNotNull null
            val date = arr[0].jsonPrimitive.contentOrNullSafe() ?: return@mapNotNull null
            // 索引 2 是收盘价。索引 1 是开盘价，用错了会把每天的涨跌算反
            val close = arr[2].jsonPrimitive.contentOrNullSafe()?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (close <= 0.0) return@mapNotNull null
            val epochDay = parseEpochDay(date) ?: return@mapNotNull null
            NavPoint(epochDay = epochDay, nav = Nav.fromDouble(close))
        }.sortedBy { it.epochDay }
    }.getOrDefault(emptyList())

    /** `yyyy-MM-dd` → epochDay。两种接口都是这个格式。 */
    private fun parseEpochDay(text: String): Long? = runCatching {
        LocalDate.parse(text).toEpochDay()
    }.getOrNull()
}

/** JsonPrimitive 取字符串内容，非字符串类型（数字/布尔）也转成字符串 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
