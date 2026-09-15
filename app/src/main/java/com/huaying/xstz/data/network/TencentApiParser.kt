package com.huaying.xstz.data.network

/**
 * 腾讯财经接口响应解析器
 *
 * 纯函数集合（无 Android 框架依赖），供 [TencentFundNetworkService] 调用，可被 JVM 单元测试覆盖。
 * 字段索引依据腾讯接口实测约定；若上游接口字段变更，只需修正本文件常量与对应测试样本，
 * 不会波及网络请求与仓库层逻辑。
 */
internal object TencentApiParser {

    // ========== 通用字段索引（股票/场内基金/场外基金 共用） ==========

    /** 基金/股票名称 */
    const val INDEX_NAME = 1

    // ========== 场外基金字段索引（q=jj<code> 接口） ==========

    /** 单位净值（作为 currentPrice） */
    const val FUND_INDEX_UNIT_NAV = 5

    /** 累计净值（不可作为当前价，否则对有分红/QDII 类基金会产生 4~5 倍误差） */
    const val FUND_INDEX_ACCUM_NAV = 6

    /** 日增长率(%) */
    const val FUND_INDEX_DAY_CHANGE_PERCENT = 7

    /** 场外基金解析所需最小字段数 */
    const val FUND_MIN_FIELDS = 8

    // ========== 股票/场内基金字段索引 ==========

    const val STOCK_INDEX_CURRENT_PRICE = 3
    const val STOCK_INDEX_PRE_CLOSE_PRICE = 4
    const val STOCK_INDEX_CHANGE = 31
    const val STOCK_INDEX_CHANGE_PERCENT = 32

    /** 股票/场内基金解析所需最小字段数 */
    const val STOCK_MIN_FIELDS = 33

    // ========== 代码分类 ==========

    /** 判断是否为股票代码（根据中国市场规则） */
    fun isStockCode(code: String): Boolean {
        // 股票代码规则：
        // 上海主板：600、601、603、605
        // 上海科创板：688
        // 上海B股：900
        // 深圳主板（含中小板）：000、001、002、003、004
        // 深圳创业板：300、301
        // 深圳B股：200
        // 北京证券交易所：43、83、87、88
        return code.matches(Regex("^(60[0135]|688|900|00[0-4]|30[01]|200|43|8[378])\\d{3}$"))
    }

    /** 判断是否为场内基金代码（根据中国市场规则） */
    fun is场内基金Code(code: String): Boolean {
        // 场内基金代码规则：
        // 上海证券交易所：5、50、51、52
        // 深圳证券交易所：15、16、18
        return code.matches(Regex("^(5|50|51|52|15|16|18)\\d{4,5}$"))
    }

    /** 判断是否为场外基金代码（根据中国市场规则） */
    fun is场外基金Code(code: String): Boolean {
        // 除上述股票和场内基金开头之外的数字，尤其是0、00开头
        return code.matches(Regex("^\\d{6}$")) && !isStockCode(code) && !is场内基金Code(code)
    }

    // ========== 交易所判断 ==========

    fun determineStockExchange(code: String): String? {
        return when {
            code.startsWith("6") || code.startsWith("9") -> "sh"
            code.startsWith("0") || code.startsWith("3") || code.startsWith("2") -> "sz"
            else -> null
        }
    }

    fun determineFundExchange(code: String): String? {
        return when {
            code.startsWith("5") -> "sh"
            code.startsWith("1") -> "sz"
            else -> null
        }
    }

    // ========== 数据解析 ==========

    /** 从响应文本中解析基金/股票名称，失败时返回 Pair(null, 错误描述) */
    fun parseFundName(content: String): Pair<String?, String> {
        if (content.isEmpty() || content.trim().isEmpty()) {
            return Pair(null, "响应为空")
        }

        val startIndex = content.indexOf('"')
        val endIndex = content.lastIndexOf('"')

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return Pair(null, "数据格式错误。响应: $content")
        }

        val data = content.substring(startIndex + 1, endIndex)

        if (data.isEmpty()) {
            return Pair(null, "数据为空。响应: $content")
        }

        val fields = data.split("~")

        return if (fields.size > INDEX_NAME && fields[INDEX_NAME].isNotEmpty()) {
            Pair(fields[INDEX_NAME], "")
        } else {
            Pair(null, "字段数量: ${fields.size}。数据: $data")
        }
    }

    /** 按代码类型分发实时数据解析；无法识别的代码返回 null */
    fun parseRealtimeData(code: String, content: String): Map<String, Any>? {
        val startIndex = content.indexOf('"')
        val endIndex = content.lastIndexOf('"')

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return null
        }

        val data = content.substring(startIndex + 1, endIndex)
        val fields = data.split("~")

        return if (is场外基金Code(code)) {
            parseFundRealtimeData(fields)
        } else if (is场内基金Code(code) || isStockCode(code)) {
            parseStockRealtimeData(fields)
        } else {
            null
        }
    }

    /** 场外基金数据解析
     *
     * 腾讯场外基金接口（q=jj<code>）返回字段（以 `~` 分隔）实测：
     *   fields[0] = 基金代码
     *   fields[1] = 基金名称
     *   fields[2..4] = 占位/历史字段（常为 "0.0000" 或空）
     *   fields[5] = 单位净值  ← 作为 currentPrice
     *   fields[6] = 累计净值  （不可作为当前价，否则对有分红/QDII 类基金会产生 4~5 倍误差）
     *   fields[7] = 日增长率(%)  ← 作为 changePercent
     *   fields[8] = 净值日期字符串（如 "2026-08-07"）
     *
     * 场外基金按日收盘净值更新，无实时"昨收"价；preClosePrice 保留 0.0，
     * 由 HoldingsCalculator.calculateTodayReturn 走 currentPrice * changePercent 分支计算今日收益。
     */
    private fun parseFundRealtimeData(fields: List<String>): Map<String, Any>? {
        return if (fields.size >= FUND_MIN_FIELDS) {
            mapOf(
                "name" to fields[INDEX_NAME],
                "currentPrice" to (fields[FUND_INDEX_UNIT_NAV].toDoubleOrNull() ?: 0.0),
                "preClosePrice" to 0.0,
                "change" to 0.0,
                "changePercent" to (fields[FUND_INDEX_DAY_CHANGE_PERCENT].toDoubleOrNull() ?: 0.0)
            )
        } else {
            null
        }
    }

    /** 场内基金和股票数据解析 */
    private fun parseStockRealtimeData(fields: List<String>): Map<String, Any>? {
        return if (fields.size >= STOCK_MIN_FIELDS) {
            mapOf(
                "name" to fields[INDEX_NAME],
                "currentPrice" to (fields[STOCK_INDEX_CURRENT_PRICE].toDoubleOrNull() ?: 0.0),
                "preClosePrice" to (fields[STOCK_INDEX_PRE_CLOSE_PRICE].toDoubleOrNull() ?: 0.0),
                "change" to (fields[STOCK_INDEX_CHANGE].toDoubleOrNull() ?: 0.0),
                "changePercent" to (fields[STOCK_INDEX_CHANGE_PERCENT].toDoubleOrNull() ?: 0.0)
            )
        } else {
            null
        }
    }
}
