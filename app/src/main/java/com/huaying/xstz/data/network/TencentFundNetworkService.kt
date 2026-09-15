package com.huaying.xstz.data.network

import android.util.Log
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.repository.TimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 腾讯财经接口实现的基金网络服务
 * 负责从腾讯财经API获取基金/股票的实时数据和名称信息
 */
class TencentFundNetworkService : FundNetworkService {

    private val okHttpClient = OkHttpClient()

    // ========== 公共接口实现 ==========

    override suspend fun fetchFundInfoWithOptions(code: String): FundNetworkService.FundQueryResult =
        withContext(Dispatchers.IO) {
            try {
                val trimmedCode = code.trim()

                if (trimmedCode.length != 6) {
                    return@withContext FundNetworkService.FundQueryResult(
                        stockError = "代码长度错误",
                        fundError = "代码长度错误"
                    )
                }

                val isAmbiguousCode = trimmedCode.matches(Regex("^00[0-4]\\d{3}$"))

                var stockResult: Pair<String?, String>? = null
                var fundResult: Pair<String?, String>? = null

                // 1. 尝试股票/场内基金接口
                when {
                    isStockCode(trimmedCode) || isAmbiguousCode -> {
                        val exchange = determineStockExchange(trimmedCode)
                        if (exchange != null) {
                            stockResult = fetchFundInfoFromUrl("https://qt.gtimg.cn/q=${exchange}${trimmedCode}")
                        }
                    }
                    is场内基金Code(trimmedCode) -> {
                        val exchange = determineFundExchange(trimmedCode)
                        if (exchange != null) {
                            stockResult = fetchFundInfoFromUrl("https://qt.gtimg.cn/q=${exchange}${trimmedCode}")
                        }
                    }
                }

                // 2. 尝试场外基金接口
                if (is场外基金Code(trimmedCode) || isAmbiguousCode) {
                    fundResult = fetchFundInfoFromUrl("https://qt.gtimg.cn/q=jj${trimmedCode}")
                }

                FundNetworkService.FundQueryResult(
                    stockName = stockResult?.first,
                    fundName = fundResult?.first,
                    stockError = stockResult?.second,
                    fundError = fundResult?.second
                )
            } catch (e: Exception) {
                Log.w(TAG, "fetchFundInfoWithOptions failed", e)
                FundNetworkService.FundQueryResult(stockError = "查询失败", fundError = "查询失败")
            }
        }

    override suspend fun fetchFundInfo(code: String): Pair<String?, String> =
        withContext(Dispatchers.IO) {
            val result = fetchFundInfoWithOptions(code)
            when {
                result.fundName != null -> Pair(result.fundName, "")
                result.stockName != null -> Pair(result.stockName, "")
                else -> Pair(null, result.fundError ?: result.stockError ?: "无法获取基金信息")
            }
        }

    override suspend fun fetchStockRealtimeData(code: String): Map<String, Any>? =
        withContext(Dispatchers.IO) {
            try {
                val trimmedCode = code.trim()

                if (trimmedCode.length != 6) {
                    return@withContext null
                }

                val url = buildApiUrl(trimmedCode) ?: return@withContext null

                val request = buildRequest(url)
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    parseRealtimeData(trimmedCode, response.body!!.string())
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchStockRealtimeData failed", e)
                null
            }
        }

    override suspend fun updateAllFundsRealtimeData(funds: List<Fund>): List<Fund> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                funds.map { fund ->
                    async {
                        try {
                            val stockData = fetchStockRealtimeData(fund.code)
                            if (stockData != null) {
                                fund.copy(
                                    currentPrice = (stockData["currentPrice"] as? Double) ?: fund.currentPrice,
                                    preClosePrice = (stockData["preClosePrice"] as? Double) ?: fund.preClosePrice,
                                    changePercent = (stockData["changePercent"] as? Double) ?: fund.changePercent,
                                    updatedAt = TimeRepository.getCurrentTimeMillis()
                                )
                            } else {
                                fund
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "update fund ${fund.code} failed", e)
                            fund
                        }
                    }
                }.awaitAll()
            }
        }

    // ========== 私有辅助方法：代码分类 ==========

    /** 判断是否为股票代码（根据中国市场规则） */
    private fun isStockCode(code: String): Boolean {
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
    private fun is场内基金Code(code: String): Boolean {
        // 场内基金代码规则：
        // 上海证券交易所：5、50、51、52
        // 深圳证券交易所：15、16、18
        return code.matches(Regex("^(5|50|51|52|15|16|18)\\d{4,5}$"))
    }

    /** 判断是否为场外基金代码（根据中国市场规则） */
    private fun is场外基金Code(code: String): Boolean {
        // 场外基金代码规则：
        // 除上述股票和场内基金开头之外的数字，尤其是0、00开头
        return code.matches(Regex("^\\d{6}$")) && !isStockCode(code) && !is场内基金Code(code)
    }

    // ========== 私有辅助方法：交易所判断 ==========

    private fun determineStockExchange(code: String): String? {
        return when {
            code.startsWith("6") || code.startsWith("9") -> "sh"
            code.startsWith("0") || code.startsWith("3") || code.startsWith("2") -> "sz"
            else -> null
        }
    }

    private fun determineFundExchange(code: String): String? {
        return when {
            code.startsWith("5") -> "sh"
            code.startsWith("1") -> "sz"
            else -> null
        }
    }

    // ========== 私有辅助方法：URL构建与请求 ==========

    private fun buildApiUrl(code: String): String? {
        return when {
            isStockCode(code) -> {
                val exchange = determineStockExchange(code)
                if (exchange != null) "https://qt.gtimg.cn/q=${exchange}${code}" else null
            }
            is场内基金Code(code) -> {
                val exchange = determineFundExchange(code)
                if (exchange != null) "https://qt.gtimg.cn/q=${exchange}${code}" else null
            }
            is场外基金Code(code) -> "https://qt.gtimg.cn/q=jj${code}"
            else -> null
        }
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .header("Referer", "https://gu.qq.com/")
            .build()
    }

    // ========== 私有辅助方法：数据解析 ==========

    private fun fetchFundInfoFromUrl(url: String): Pair<String?, String> {
        val request = buildRequest(url)
        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful && response.body != null) {
            val content = response.body!!.string()
            return parseFundName(content)
        } else {
            return Pair(null, "请求失败: ${response.code}")
        }
    }

    private fun parseFundName(content: String): Pair<String?, String> {
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

        return if (fields.size > 2 && fields[1].isNotEmpty()) {
            Pair(fields[1], "")
        } else {
            Pair(null, "字段数量: ${fields.size}。数据: $data")
        }
    }

    private fun parseRealtimeData(code: String, content: String): Map<String, Any>? {
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
        return if (fields.size > 7) {
            mapOf(
                "name" to fields[1],
                "currentPrice" to (fields[5].toDoubleOrNull() ?: 0.0),
                "preClosePrice" to 0.0,
                "change" to 0.0,
                "changePercent" to (fields[7].toDoubleOrNull() ?: 0.0)
            )
        } else {
            null
        }
    }

    /** 场内基金和股票数据解析 */
    private fun parseStockRealtimeData(fields: List<String>): Map<String, Any>? {
        return if (fields.size > 32) {
            mapOf(
                "name" to fields[1],
                "currentPrice" to (fields[3].toDoubleOrNull() ?: 0.0),
                "preClosePrice" to (fields[4].toDoubleOrNull() ?: 0.0),
                "change" to (fields[31].toDoubleOrNull() ?: 0.0),
                "changePercent" to (fields[32].toDoubleOrNull() ?: 0.0)
            )
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "TencentFundNetworkSvc"
    }
}
