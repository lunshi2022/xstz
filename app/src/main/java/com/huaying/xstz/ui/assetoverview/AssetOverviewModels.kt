package com.huaying.xstz.ui.assetoverview

import com.huaying.xstz.data.entity.Fund
import java.util.Calendar

/**
 * 资产总览模块的数据模型定义
 */

// ==================== 资产摘要 ====================

data class AssetSummary(
    val totalAssets: Double = 0.0,
    val principal: Double = 0.0,
    val totalReturn: Double = 0.0,
    val returnRate: Double = 0.0,
    val todayReturn: Double = 0.0,
    val todayReturnRate: Double = 0.0,
    val stockValue: Double = 0.0,
    val bondValue: Double = 0.0,
    val commodityValue: Double = 0.0,
    val cashValue: Double = 0.0,
    val stockRatio: Double = 0.0,
    val bondRatio: Double = 0.0,
    val commodityRatio: Double = 0.0,
    val cashRatio: Double = 0.0,
    val lastUpdateTime: Long = 0L,
    val isRefreshing: Boolean = false,
    val rebalanceThreshold: Double = 20.0,
    val isPrivacyMode: Boolean = false
)

// ==================== 时间范围 ====================

enum class TimeRange(val displayName: String) {
    WEEK("近1周"),
    MONTH("近1月"),
    YEAR("今年")
}

// ==================== 图表数据类型 ====================

enum class ChartDataType(val displayName: String) {
    RETURN_RATE("收益率"),
    ASSET_RATIO("资产占比"),
    ASSET_COMPARISON("资产对比")
}

// ==================== 趋势图数据 ====================

data class ChartDataPoint(
    val date: Long,
    val value: Double,
    val label: String
)

data class TrendChartData(
    val dataPoints: List<ChartDataPoint>,
    val minValue: Double,
    val maxValue: Double,
    val currentValue: Double,
    val changeValue: Double,
    val changePercent: Double
)

// ==================== 趋势图缓存 ====================

object TrendChartDataCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, TrendChartData>()
    private const val MAX_CACHE_SIZE = 10

    fun generateKey(timeRange: TimeRange, dataType: ChartDataType, records: List<com.huaying.xstz.data.entity.NetValueRecord>): String {
        return "${timeRange.name}_${dataType.name}_${records.size}_${records.firstOrNull()?.createdAt ?: 0}"
    }

    fun get(key: String): TrendChartData? = cache[key]

    fun put(key: String, data: TrendChartData) {
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
        cache[key] = data
    }

    fun clear() = cache.clear()
}

// ==================== 每日盈亏数据 ====================

data class FundDailyPnL(
    val fund: Fund,
    val dailyReturn: Double,
    val dailyReturnRate: Double,
    val currentValue: Double
)

data class DailyPnLData(
    val date: Calendar,
    val totalDailyReturn: Double,
    val totalDailyReturnRate: Double,
    val fundDetails: List<FundDailyPnL>,
    val hasRecord: Boolean
)
