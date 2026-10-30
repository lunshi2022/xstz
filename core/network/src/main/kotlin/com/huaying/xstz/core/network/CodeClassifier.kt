package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.FundCode

/**
 * 代码分类与交易所判定。
 *
 * 移植自旧版 TencentApiParser 的分类逻辑（17 个测试锁死字段索引与正则），
 * 但把中文函数名改成英文——旧代码里 `is场内基金Code` 这种命名会让 IDE 自动补全
 * 和 diff 都变得难以阅读，是长期维护成本。
 *
 * 规则本身一个字没改：A 股的代码段划分是有历史包袱的，改了就会漏掉某类标的。
 */
object CodeClassifier {

    /** 股票：沪主板 600/601/603/605、科创 688、沪B 900、深主板 000~004、创业板 300/301、深B 200、北交所 43/83/87/88 */
    private val STOCK = Regex("^(60[0135]|688|900|00[0-4]|30[01]|200|43|8[378])\\d{3}$")

    /**
     * 场内基金（ETF/LOF）。
     *
     * 旧版规则是 `^5\d{4,5}$`，把 519xxx 这类**场外**基金也吞进了场内分支，
     * 后果是这些基金走错接口、永远取不到净值（静默失败，界面上只是"暂无数据"）。
     *
     * 按真实代码段收紧：
     *   沪市 500~506 / 510~518 / 560~563 / 588~589
     *   深市 15xxxx / 16xxxx / 18xxxx
     * 519xxx、110xxx、000xxx 等都属于场外，必须落到 isOpenEndFund。
     */
    private val EXCHANGE_FUND = Regex("^(50[0-6]\\d{3}|51[0-8]\\d{3}|56[0-3]\\d{3}|58[89]\\d{3}|1[568]\\d{4})$")

    private val SIX_DIGITS = Regex("^\\d{6}$")

    fun isStock(code: String): Boolean = STOCK.matches(code)

    fun isExchangeTradedFund(code: String): Boolean = EXCHANGE_FUND.matches(code)

    /** 场外基金 = 六位数字，且不是股票、不是场内基金 */
    fun isOpenEndFund(code: String): Boolean =
        SIX_DIGITS.matches(code) && !isStock(code) && !isExchangeTradedFund(code)

    fun stockExchange(code: String): String? = when {
        code.startsWith("6") || code.startsWith("9") -> "sh"
        code.startsWith("0") || code.startsWith("3") || code.startsWith("2") -> "sz"
        else -> null
    }

    fun fundExchange(code: String): String? = when {
        code.startsWith("5") -> "sh"
        code.startsWith("1") -> "sz"
        else -> null
    }

    /**
     * 给出腾讯行情接口需要的带前缀代码。
     * 场外基金走另一套接口（fundgz），这里返回 null 表示"不该用行情接口"。
     */
    fun tencentQuoteSymbol(code: String): String? = when {
        isOpenEndFund(code) -> null
        isStock(code) -> stockExchange(code)?.let { "$it$code" }
        isExchangeTradedFund(code) -> fundExchange(code)?.let { "$it$code" }
        FundCode.isExchangePrefixed(code) -> code.lowercase()
        else -> null
    }
}
