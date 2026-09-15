package com.huaying.xstz.data.network

import com.huaying.xstz.data.entity.Fund

/**
 * 基金网络服务接口
 * 负责从网络获取基金相关数据，与数据持久化层解耦
 */
interface FundNetworkService {

    /**
     * 基金查询结果数据类
     */
    data class FundQueryResult(
        val stockName: String? = null,
        val fundName: String? = null,
        val stockError: String? = null,
        val fundError: String? = null
    ) {
        val hasStockResult: Boolean get() = stockName != null
        val hasFundResult: Boolean get() = fundName != null
        val hasBothResults: Boolean get() = hasStockResult && hasFundResult
        val hasAnyResult: Boolean get() = hasStockResult || hasFundResult
    }

    /**
     * 从网络获取基金信息（同时查询股票和基金接口）
     */
    suspend fun fetchFundInfoWithOptions(code: String): FundQueryResult

    /**
     * 从网络获取基金名称（兼容旧版本，优先返回基金结果）
     */
    suspend fun fetchFundInfo(code: String): Pair<String?, String>

    /**
     * 从腾讯接口获取完整实时数据
     */
    suspend fun fetchStockRealtimeData(code: String): Map<String, Any>?

    /**
     * 批量更新基金实时数据，返回更新后的基金列表
     */
    suspend fun updateAllFundsRealtimeData(funds: List<Fund>): List<Fund>
}
