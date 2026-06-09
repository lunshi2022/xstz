package com.huaying.xstz.ui.assetoverview

import com.huaying.xstz.util.AppConstants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 格式化工具类
 * 负责日期、货币、百分比等格式化
 */
object FormatUtils {

    private const val CHINA_TIMEZONE = "GMT+8"
    private const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
    private const val DATE_DISPLAY_FORMAT = "%02d月%02d日"

    private val weekdays = arrayOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    fun formatCurrency(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "¥ ****"
        return "¥%,.2f".format(Locale.CHINA, value)
    }

    fun formatPercent(value: Double, isPrivacy: Boolean = false): String {
        if (isPrivacy) return "****%"
        return if (value >= 0) "+%.2f%%".format(value) else "%.2f%%".format(value)
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat(DATETIME_FORMAT, Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone(CHINA_TIMEZONE)
        return sdf.format(java.util.Date(timestamp))
    }

    fun formatShortDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone(CHINA_TIMEZONE)
        return sdf.format(java.util.Date(timestamp))
    }

    fun formatSelectedDate(calendar: Calendar): String {
        return DATE_DISPLAY_FORMAT.format(
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun getWeekdayString(calendar: Calendar): String {
        return weekdays[calendar.get(Calendar.DAY_OF_WEEK)]
    }

    fun getChinaCalendar(): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA)
    }

    fun getDayStart(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun getDayEnd(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    fun getInvestmentDays(initialInvestmentDate: Long): Int {
        if (initialInvestmentDate == 0L) return 0

        val startCalendar = getChinaCalendar().apply {
            timeInMillis = initialInvestmentDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentCalendar = getChinaCalendar().apply {
            timeInMillis = com.huaying.xstz.data.repository.TimeRepository.getCurrentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffInMillis = currentCalendar.timeInMillis - startCalendar.timeInMillis
        return (diffInMillis / AppConstants.MILLISECONDS_PER_DAY).toInt() + 1
    }
}
