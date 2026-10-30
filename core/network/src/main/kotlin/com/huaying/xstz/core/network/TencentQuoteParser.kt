package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.Nav

/**
 * 腾讯行情响应解析器（纯函数，无网络、无 Android 依赖）。
 *
 * 为什么要拆成纯解析器：旧版把「发请求」和「解析字符串」写在一起，
 * 导致 17 个字段索引的回归测试必须依赖 mock 网络。拆开后，
 * 解析器可以拿历史响应样本直接测，字段错位会立刻被测试抓到。
 *
 * 腾讯返回格式（v 格式）：
 *   v_sh600519="1~贵州茅台~600519~1680.00~1675.00~..."
 * 字段按 ~ 分割，索引含义随品种不同而不同，见下方常量。
 */
object TencentQuoteParser {

    // --- 场外基金实时估值（fundgz 接口，JSONP 风格）---
    // jsonpgz({"fundcode":"000001","name":"华夏成长","jzrq":"2026-10-30","dwjz":"1.2345","gsz":"1.2400","gszzl":"0.45","gztime":"2026-10-31 14:30"});

    // --- 场内品种（股票/ETF/LOF）---
    private const val IDX_NAME = 1
    private const val IDX_CODE = 2
    private const val IDX_PRICE = 3
    private const val IDX_PREV_CLOSE = 4

    data class Quote(
        val code: String,
        val name: String,
        val nav: Nav,
        val prevNav: Nav,
    ) {
        val changeRate: Double
            get() = if (prevNav.isZero) 0.0 else (nav.micros - prevNav.micros).toDouble() / prevNav.micros
    }

    data class Estimate(
        val code: String,
        val name: String,
        /** 单位净值（上一交易日确认值） */
        val nav: Nav,
        /** 实时估算净值 */
        val estimatedNav: Nav,
        /** 估算涨跌幅，百分数（0.45 表示 0.45%） */
        val estimatedChangePercent: Double,
        /** 净值日期，yyyy-MM-dd */
        val navDate: String,
        /** 估值时间，yyyy-MM-dd HH:mm */
        val estimateTime: String,
    )

    /**
     * 解析场内行情。返回 null 表示响应不可用（空串、格式错、字段不足）。
     *
     * 这里刻意不抛异常：行情抓取是高频、易失败的操作，
     * 用 null + 上层降级（缓存 → 离线样例）比 try/catch 满天飞更可控。
     */
    fun parseQuote(expectedCode: String, raw: String): Quote? {
        val body = extractQuotedPayload(raw) ?: return null
        val fields = body.split('~')
        if (fields.size <= IDX_PREV_CLOSE) return null

        val name = fields[IDX_NAME].ifBlank { return null }
        val price = fields[IDX_PRICE].toDoubleOrNull() ?: return null
        val prevClose = fields[IDX_PREV_CLOSE].toDoubleOrNull() ?: price

        // 停牌时价格会是 0，用昨收兜底，避免界面上出现"市值归零"
        val effectivePrice = if (price <= 0.0) prevClose else price

        return Quote(
            code = expectedCode,
            name = name,
            nav = Nav.fromDouble(effectivePrice),
            prevNav = Nav.fromDouble(if (prevClose <= 0.0) effectivePrice else prevClose),
        )
    }

    /**
     * 解析场外基金估值。字段是 JSON，但两端包了 `jsonpgz(...)`，不是严格 JSON。
     * 不引入 JSON 解析库，用正则取键值——这个接口十年没变过格式，
     * 为它拉一个 parser 不值得，且正则版更容易在字段缺失时降级。
     */
    fun parseEstimate(expectedCode: String, raw: String): Estimate? {
        val body = extractJsonpPayload(raw) ?: return null

        val name = field(body, "name") ?: return null
        val navValue = field(body, "dwjz")?.toDoubleOrNull() ?: return null
        val gsz = field(body, "gsz")?.toDoubleOrNull() ?: navValue
        val gszzl = field(body, "gszzl")?.toDoubleOrNull() ?: 0.0
        val navDate = field(body, "jzrq") ?: ""
        val gztime = field(body, "gztime") ?: ""

        return Estimate(
            code = expectedCode,
            name = name,
            nav = Nav.fromDouble(navValue),
            estimatedNav = Nav.fromDouble(gsz),
            estimatedChangePercent = gszzl,
            navDate = navDate,
            estimateTime = gztime,
        )
    }

    /** 从 `v_sh600519="..."` 里取出引号内的内容 */
    internal fun extractQuotedPayload(raw: String): String? {
        if (raw.isBlank()) return null
        val start = raw.indexOf('"')
        val end = raw.lastIndexOf('"')
        if (start == -1 || end == -1 || start >= end) return null
        val body = raw.substring(start + 1, end)
        return body.ifBlank { null }
    }

    /** 从 `jsonpgz({...})` 里取出 JSON 主体 */
    internal fun extractJsonpPayload(raw: String): String? {
        if (raw.isBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || start >= end) return null
        return raw.substring(start, end + 1)
    }

    /** 取 `"key":"value"` 或 `"key":123.4` 的值，统一按字符串返回 */
    internal fun field(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"?([^\",}]*)")
        return regex.find(json)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
