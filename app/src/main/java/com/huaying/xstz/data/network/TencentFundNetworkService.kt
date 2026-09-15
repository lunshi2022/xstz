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
 * 响应解析与代码分类逻辑见 [TencentApiParser]（纯函数，可单元测试）
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
                    TencentApiParser.isStockCode(trimmedCode) || isAmbiguousCode -> {
                        val exchange = TencentApiParser.determineStockExchange(trimmedCode)
                        if (exchange != null) {
                            stockResult = fetchFundInfoFromUrl("https://qt.gtimg.cn/q=${exchange}${trimmedCode}")
                        }
                    }
                    TencentApiParser.is场内基金Code(trimmedCode) -> {
                        val exchange = TencentApiParser.determineFundExchange(trimmedCode)
                        if (exchange != null) {
                            stockResult = fetchFundInfoFromUrl("https://qt.gtimg.cn/q=${exchange}${trimmedCode}")
                        }
                    }
                }

                // 2. 尝试场外基金接口
                if (TencentApiParser.is场外基金Code(trimmedCode) || isAmbiguousCode) {
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
                    TencentApiParser.parseRealtimeData(trimmedCode, response.body!!.string())
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

    // ========== 私有辅助方法：URL构建与请求 ==========

    private fun buildApiUrl(code: String): String? {
        return when {
            TencentApiParser.isStockCode(code) -> {
                val exchange = TencentApiParser.determineStockExchange(code)
                if (exchange != null) "https://qt.gtimg.cn/q=${exchange}${code}" else null
            }
            TencentApiParser.is场内基金Code(code) -> {
                val exchange = TencentApiParser.determineFundExchange(code)
                if (exchange != null) "https://qt.gtimg.cn/q=${exchange}${code}" else null
            }
            TencentApiParser.is场外基金Code(code) -> "https://qt.gtimg.cn/q=jj${code}"
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

    private fun fetchFundInfoFromUrl(url: String): Pair<String?, String> {
        val request = buildRequest(url)
        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful && response.body != null) {
            val content = response.body!!.string()
            return TencentApiParser.parseFundName(content)
        } else {
            return Pair(null, "请求失败: ${response.code}")
        }
    }

    companion object {
        private const val TAG = "TencentFundNetworkSvc"
    }
}
