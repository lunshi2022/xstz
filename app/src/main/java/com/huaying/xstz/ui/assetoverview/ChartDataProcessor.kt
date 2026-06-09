package com.huaying.xstz.ui.assetoverview

import com.huaying.xstz.data.entity.NetValueRecord
import com.huaying.xstz.util.AppConstants
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale

/**
 * 图表数据处理器
 * 负责趋势图数据的计算、采样和转换
 */
object ChartDataProcessor {

    private const val MAX_SAMPLE_SIZE = AppConstants.MAX_CHART_SAMPLE_SIZE
    private const val CHINA_TIMEZONE = "GMT+8"

    fun sampleRecords(records: List<NetValueRecord>, targetSize: Int = MAX_SAMPLE_SIZE): List<NetValueRecord> {
        if (records.size <= targetSize) return records

        val step = records.size.toFloat() / targetSize
        val result = ArrayList<NetValueRecord>(targetSize)

        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceIn(0, records.size - 1)
            result.add(records[index])
        }

        if (result.lastOrNull() != records.last()) {
            result.add(records.last())
        }

        return result
    }

    fun calculateReturnRateData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }
        val basePrincipal = sortedRecords.first().principal

        val dataPoints = sortedRecords.map { record ->
            val returnRate = if (basePrincipal > 0) {
                (record.totalAssets - basePrincipal) / basePrincipal * AppConstants.PERCENTAGE_BASE
            } else AppConstants.ZERO_DOUBLE

            ChartDataPoint(
                date = record.createdAt,
                value = returnRate,
                label = formatDateLabel(record.createdAt)
            )
        }

        return buildTrendChartData(dataPoints)
    }

    fun calculateAssetRatioData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }

        val dataPoints = sortedRecords.map { record ->
            val totalValue = record.stockValue + record.bondValue + record.goldValue + record.cashValue
            val stockRatio = if (totalValue > 0) record.stockValue / totalValue * AppConstants.PERCENTAGE_BASE else AppConstants.ZERO_DOUBLE

            ChartDataPoint(
                date = record.createdAt,
                value = stockRatio,
                label = formatDateLabel(record.createdAt)
            )
        }

        val values = dataPoints.map { it.value }
        return TrendChartData(
            dataPoints = dataPoints,
            minValue = values.minOrNull() ?: 0.0,
            maxValue = values.maxOrNull() ?: 0.0,
            currentValue = values.lastOrNull() ?: 0.0,
            changeValue = (values.lastOrNull() ?: 0.0) - (values.firstOrNull() ?: 0.0),
            changePercent = 0.0
        )
    }

    fun calculateAssetComparisonData(records: List<NetValueRecord>): TrendChartData {
        if (records.isEmpty()) {
            return TrendChartData(emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sortedRecords = records.sortedBy { it.createdAt }

        val dataPoints = sortedRecords.map { record ->
            ChartDataPoint(
                date = record.createdAt,
                value = record.totalAssets,
                label = formatDateLabel(record.createdAt)
            )
        }

        return buildTrendChartData(dataPoints)
    }

    private fun buildTrendChartData(dataPoints: List<ChartDataPoint>): TrendChartData {
        val values = dataPoints.map { it.value }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 0.0
        val currentValue = values.lastOrNull() ?: 0.0
        val firstValue = values.firstOrNull() ?: 0.0
        val changeValue = currentValue - firstValue
        val changePercent = if (firstValue != AppConstants.ZERO_DOUBLE) (changeValue / kotlin.math.abs(firstValue)) * AppConstants.PERCENTAGE_BASE else AppConstants.ZERO_DOUBLE

        return TrendChartData(
            dataPoints = dataPoints,
            minValue = minValue,
            maxValue = maxValue,
            currentValue = currentValue,
            changeValue = changeValue,
            changePercent = changePercent
        )
    }

    private fun formatDateLabel(timestamp: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA)
        cal.timeInMillis = timestamp
        return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}
