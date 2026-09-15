package com.huaying.xstz.util

import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import java.util.Locale

/**
 * 统一格式化工具类
 * 整合所有界面的数值格式化逻辑，使用 FormatTemplates 作为格式模板
 */
object FormatUtils {

    // ==================== 货币格式化 ====================

    /** 格式化货币（带千分位），如 ¥1,234.56 */
    fun formatCurrency(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "¥ ****"
        return FormatTemplates.CURRENCY_2F.format(Locale.CHINA, value)
    }

    /** 格式化货币（不带符号），如 1,234.56 */
    fun formatCurrencyValue(value: Double): String {
        return FormatTemplates.NUMBER_COMMA_2F.format(Locale.CHINA, value)
    }

    // ==================== 百分比格式化 ====================

    /** 格式化涨跌幅百分比（带正负号），如 +2.50% 或 -1.30% */
    fun formatPercent(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****%"
        return if (value >= 0) "+${FormatTemplates.PERCENT_2F.format(Locale.CHINA, value)}"
        else FormatTemplates.PERCENT_2F.format(Locale.CHINA, value)
    }

    /** 格式化占比百分比（不带正负号），如 25.00% */
    fun formatRatio(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "**%"
        return FormatTemplates.PERCENT_2F.format(Locale.CHINA, value)
    }

    /** 格式化占比百分比（1位小数），如 25.0% */
    fun formatRatio1F(value: Double): String {
        return FormatTemplates.PERCENT_1F.format(Locale.CHINA, value)
    }

    // ==================== 价格格式化 ====================

    /** 格式化价格（3位小数），如 1.234 */
    fun formatPrice(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****"
        return FormatTemplates.NUMBER_3F.format(Locale.CHINA, value)
    }

    /** 格式化成本价（3位小数，带¥符号），如 ¥1.234 */
    fun formatCostPrice(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****"
        return "¥${FormatTemplates.NUMBER_3F.format(Locale.CHINA, value)}"
    }

    // ==================== 份额格式化 ====================

    /** 格式化份额（整数），如 1000 */
    fun formatQuantity(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****"
        return FormatTemplates.NUMBER_0F.format(Locale.CHINA, value)
    }

    /** 格式化现金金额，如 ¥1,234.56 */
    fun formatCashAmount(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****"
        return "¥${FormatTemplates.NUMBER_COMMA_2F.format(Locale.CHINA, value)}"
    }

    // ==================== 收益率格式化 ====================

    /** 格式化收益率（带颜色标识），如 +25.00% 或 -10.50% */
    fun formatReturnRate(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****%"
        return if (value >= 0) "+${FormatTemplates.PERCENT_2F.format(Locale.CHINA, value)}"
        else FormatTemplates.PERCENT_2F.format(Locale.CHINA, value)
    }

    // ==================== 通用数值格式化 ====================

    /** 安全格式化 Double 值，处理 NaN 和 Infinity */
    fun safeFormat(value: Double, format: String, defaultValue: String = "0.00"): String {
        return try {
            if (value.isNaN() || value.isInfinite()) defaultValue
            else String.format(Locale.CHINA, format, value)
        } catch (e: Exception) {
            defaultValue
        }
    }

    // ==================== 日期时间格式化 ====================

    private const val CHINA_TIMEZONE = "GMT+8"

    fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        sdf.timeZone = java.util.TimeZone.getTimeZone(CHINA_TIMEZONE)
        return sdf.format(java.util.Date(timestamp))
    }

    fun formatShortDateTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        sdf.timeZone = java.util.TimeZone.getTimeZone(CHINA_TIMEZONE)
        return sdf.format(java.util.Date(timestamp))
    }

    fun formatSelectedDate(calendar: java.util.Calendar): String {
        return "%02d月%02d日".format(
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    fun getWeekdayString(calendar: java.util.Calendar): String {
        val weekdays = arrayOf("", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
        return weekdays[calendar.get(java.util.Calendar.DAY_OF_WEEK)]
    }

    fun getChinaCalendar(): java.util.Calendar {
        return java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA
        )
    }

    fun getDayStart(calendar: java.util.Calendar): java.util.Calendar {
        return (calendar.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
    }

    fun getDayEnd(calendar: java.util.Calendar): java.util.Calendar {
        return (calendar.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
    }

    fun isSameDay(cal1: java.util.Calendar, cal2: java.util.Calendar): Boolean {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH) &&
               cal1.get(java.util.Calendar.DAY_OF_MONTH) == cal2.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun getInvestmentDays(initialInvestmentDate: Long): Int {
        if (initialInvestmentDate == 0L) return 0

        val startCalendar = getChinaCalendar().apply {
            timeInMillis = initialInvestmentDate
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val currentCalendar = getChinaCalendar().apply {
            timeInMillis = com.huaying.xstz.data.repository.TimeRepository.getCurrentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val diffInMillis = currentCalendar.timeInMillis - startCalendar.timeInMillis
        return (diffInMillis / AppConstants.MILLISECONDS_PER_DAY).toInt() + 1
    }
}
