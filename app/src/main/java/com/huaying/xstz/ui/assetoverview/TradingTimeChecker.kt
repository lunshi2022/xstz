package com.huaying.xstz.ui.assetoverview

import android.app.Application
import com.huaying.xstz.data.repository.HolidayRepository
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 交易时间检查器
 * 负责判断当前是否为交易时间，管理交易日缓存
 */
class TradingTimeChecker(private val application: Application) {

    companion object {
        private const val CHINA_TIMEZONE = "GMT+8"
        private const val TRADING_DAY_CACHE_DURATION = AppConstants.MILLISECONDS_PER_MINUTE * 5 // 5分钟

        private const val MORNING_START_HOUR = 9
        private const val MORNING_START_MINUTE = 15
        private const val MORNING_END_HOUR = 11
        private const val MORNING_END_MINUTE = 30
        private const val AFTERNOON_START_HOUR = 13
        private const val AFTERNOON_END_HOUR = 15
        private const val MINUTES_PER_HOUR = 60
    }

    private var cachedTradingDay: org.threeten.bp.LocalDate? = null
    private var cachedIsTradingDay: Boolean? = null
    private var lastTradingDayCheckTime: Long = 0

    fun getMarketStatus(): String {
        val calendar = getChinaCalendar()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return "周末休市"
        }

        val timeInMinutes = getTimeInMinutes(calendar)
        val morningStart = MORNING_START_HOUR * MINUTES_PER_HOUR + MORNING_START_MINUTE
        val morningEnd = MORNING_END_HOUR * MINUTES_PER_HOUR + MORNING_END_MINUTE
        val afternoonStart = AFTERNOON_START_HOUR * MINUTES_PER_HOUR
        val afternoonEnd = AFTERNOON_END_HOUR * MINUTES_PER_HOUR

        return when {
            timeInMinutes < morningStart -> "未开盘"
            timeInMinutes in morningStart..morningEnd -> "交易中"
            timeInMinutes in morningEnd + 1..afternoonStart - 1 -> "午间休盘"
            timeInMinutes in afternoonStart..afternoonEnd -> "交易中"
            else -> "已收盘"
        }
    }

    fun isTradingTime(): Boolean {
        val calendar = getChinaCalendar()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val currentDate = org.threeten.bp.LocalDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val isTradingDay = checkIsTradingDay(currentDate, dayOfWeek)
        if (!isTradingDay) return false

        val timeInMinutes = getTimeInMinutes(calendar)
        val morningStart = MORNING_START_HOUR * MINUTES_PER_HOUR + MORNING_START_MINUTE
        val morningEnd = MORNING_END_HOUR * MINUTES_PER_HOUR + MORNING_END_MINUTE
        val afternoonStart = AFTERNOON_START_HOUR * MINUTES_PER_HOUR
        val afternoonEnd = AFTERNOON_END_HOUR * MINUTES_PER_HOUR

        return (timeInMinutes in morningStart..morningEnd) ||
               (timeInMinutes in afternoonStart..afternoonEnd)
    }

    fun checkIsTradingDay(
        date: org.threeten.bp.LocalDate,
        dayOfWeek: Int,
        scope: CoroutineScope? = null
    ): Boolean {
        val now = TimeRepository.getCurrentTimeMillis()

        if (cachedTradingDay == date && cachedIsTradingDay != null &&
            (now - lastTradingDayCheckTime) < TRADING_DAY_CACHE_DURATION
        ) {
            return cachedIsTradingDay!!
        }

        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            cachedTradingDay = date
            cachedIsTradingDay = false
            lastTradingDayCheckTime = now
            return false
        }

        scope?.launch {
            try {
                val result = HolidayRepository.isTradingDay(application, date)
                cachedTradingDay = date
                cachedIsTradingDay = result
                lastTradingDayCheckTime = now
            } catch (e: Exception) {
                // 忽略错误，保持当前缓存状态
                android.util.Log.w("TradingTimeChecker", "isTradingDay check failed", e)
            }
        }

        return cachedIsTradingDay ?: true
    }

    private fun getChinaCalendar(): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA).apply {
            timeInMillis = TimeRepository.getCurrentTimeMillis()
        }
    }

    private fun getTimeInMinutes(calendar: Calendar): Int {
        return calendar.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + calendar.get(Calendar.MINUTE)
    }
}
