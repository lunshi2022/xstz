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

                val name = resolveHolidayName(localDate.monthValue, localDate.dayOfMonth)
                holidayNameMap[timestamp] = name
            }

            Pair(holidayDateSet, holidayNameMap)
        } catch (e: Exception) {
            android.util.Log.e("HolidayDateProvider", "getHolidayDatesForCurrentYear failed", e)
            Pair(emptySet(), emptyMap())
        }
    }

    private fun resolveHolidayName(month: Int, day: Int): String {
        return when (month) {
            1 -> if (day == 1) "元旦" else "休"
            2 -> if (day in 16..23) "春节" else "休"
            4 -> if (day in 4..6) "清明" else "休"
            5 -> if (day in 1..5) "劳动节" else "休"
            6 -> if (day in 19..21) "端午" else "休"
            9 -> if (day in 25..27) "中秋" else "休"
            10 -> if (day in 1..8) "国庆" else "休"
            else -> "休"
        }
    }
}
