package com.huaying.xstz.ui.assetoverview

import com.huaying.xstz.data.repository.HolidayRepository
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 节假日日期提供者
 * 负责获取当前年度的节假日信息
 */
object HolidayDateProvider {

    private const val CHINA_TIMEZONE = "GMT+8"

    suspend fun getHolidayDatesForCurrentYear(): Pair<Set<Long>, Map<Long, String>> {
        return try {
            val currentYear = Calendar.getInstance(TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA)
                .get(Calendar.YEAR)
            val holidays = HolidayRepository.getBuiltinHolidaysForCalendar(currentYear)

            val holidayDateSet = mutableSetOf<Long>()
            val holidayNameMap = mutableMapOf<Long, String>()

            holidays.forEach { localDate ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone(CHINA_TIMEZONE), Locale.CHINA)
                cal.set(localDate.year, localDate.monthValue - 1, localDate.dayOfMonth, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val timestamp = cal.timeInMillis
                holidayDateSet.add(timestamp)

                val name = resolveHolidayName(localDate.year, localDate.monthValue, localDate.dayOfMonth)
                holidayNameMap[timestamp] = name
            }

            Pair(holidayDateSet, holidayNameMap)
        } catch (e: Exception) {
            android.util.Log.e("HolidayDateProvider", "getHolidayDatesForCurrentYear failed", e)
            Pair(emptySet(), emptyMap())
        }
    }

    private fun resolveHolidayName(year: Int, month: Int, day: Int): String {
        // 各年份节假日：只有每个假期的第一天显示名称，其余显示"休"
        val holidayFirstDay: Map<Pair<Int, Int>, String> = when (year) {
            2026 -> mapOf(
                (1 to 1) to "元旦",
                (2 to 16) to "春节",
                (4 to 4) to "清明",
                (5 to 1) to "劳动节",
                (6 to 19) to "端午",
                (9 to 25) to "中秋",
                (10 to 1) to "国庆"
            )
            2027 -> mapOf(
                (1 to 1) to "元旦",
                (2 to 5) to "春节",
                (4 to 4) to "清明",
                (5 to 1) to "劳动节",
                (6 to 9) to "端午",
                (9 to 15) to "中秋",
                (10 to 1) to "国庆"
            )
            else -> emptyMap()
        }

        return holidayFirstDay[month to day] ?: "休"
    }
}
