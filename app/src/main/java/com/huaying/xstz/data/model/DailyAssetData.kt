package com.huaying.xstz.data.model

import org.threeten.bp.LocalDate

data class DailyAssetData(
    val date: LocalDate,
    val stockValue: Double,
    val bondValue: Double,
    val goldValue: Double,
    val cashValue: Double,
    val principal: Double // 本金
) {
    val totalAsset: Double
        get() = stockValue + bondValue + goldValue + cashValue
    
    val returnRate: Double
        get() = if (principal > 0) (totalAsset - principal) / principal else 0.0
}
