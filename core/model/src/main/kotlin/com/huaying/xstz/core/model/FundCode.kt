package com.huaying.xstz.core.model

/** 基金/股票代码的规范化与校验，集中在模型层，避免各层各写一套正则 */
object FundCode {

    private val OTC_FUND = Regex("^\\d{6}$")
    private val EXCHANGE_FUND = Regex("^(sh|sz|SH|SZ)\\d{6}$")
    private val STOCK = Regex("^\\d{6}$")

    /** 纯 6 位数字：可能是场外基金，也可能是股票，需要靠类型区分 */
    fun isSixDigits(code: String): Boolean = OTC_FUND.matches(code.trim())

    /** 带交易所前缀：sh000001 / sz399001 这类 */
    fun isExchangePrefixed(code: String): Boolean = EXCHANGE_FUND.matches(code.trim())

    /** 规范化为大写、去空格 */
    fun normalize(code: String): String = code.trim().uppercase()

    /**
     * 是否像基金代码。
     * 场外基金通常是 0/1/2/3/5 开头，但这条规则有例外（如 4/5 开头的新基金），
     * 所以这里只做"六位数字"的宽松判断，真正的类型由用户选择决定，
     * 不靠前缀猜——猜错会让整只基金的行情全部取不到。
     */
    fun looksLikeFund(code: String): Boolean = isSixDigits(code) || isExchangePrefixed(code)
}

/** 数据来源，用于在 UI 上标注"离线样例"，避免用户把演示数据当真 */
enum class DataSource {
    LIVE,
    CACHE,
    SAMPLE;

    val displayName: String
        get() = when (this) {
            LIVE -> "实时"
            CACHE -> "缓存"
            SAMPLE -> "离线样例"
        }
}

/** 带来源标记的值。所有行情读取都返回它，让 UI 永远能说清数据新鲜度。 */
data class Sourced<T>(val value: T, val source: DataSource, val fetchedAt: Long = 0L)
