package com.huaying.xstz.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter

/**
 * 中国节假日数据仓库
 * 用于判断某一天是否为中国法定节假日
 */
object HolidayRepository {
    private val client = OkHttpClient()
    private val CHINA_ZONE = ZoneId.of("GMT+8")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 节假日缓存（年份 -> 该年所有节假日日期的Set）
    private val holidayCache = mutableMapOf<Int, Set<LocalDate>>()
    private val workdayCache = mutableMapOf<Int, Set<LocalDate>>()

    // 使用 holiday-calendar 库的 JSON 数据
    private val HOLIDAY_API_URL = "https://unpkg.com/holiday-calendar/data/CN/{year}.json"

    /**
     * 判断指定日期是否为交易日
     * 交易日 = 非周末 且 非法定节假日（调休日也不交易）
     * 注意：股市交易规则与普通工作日不同，调休日（周末上班）股市也不交易
     */
    suspend fun isTradingDay(context: Context, date: LocalDate = LocalDate.now(CHINA_ZONE)): Boolean {
        val year = date.year

        // 1. 判断是否为周六或周日
        val dayOfWeek = date.dayOfWeek.value // 1=周一, 7=周日
        if (dayOfWeek == 6 || dayOfWeek == 7) {
            // 周末不开市，即使调休也不交易
            return false
        }

        // 2. 工作日判断是否为法定节假日
        val holidays = getHolidays(context, year)
        if (holidays.contains(date)) {
            return false
        }

        return true
    }

    /**
     * 获取指定年份的所有法定节假日
     */
    private suspend fun getHolidays(context: Context, year: Int): Set<LocalDate> = withContext(Dispatchers.IO) {
        // 检查缓存
        holidayCache[year]?.let { return@withContext it }

        val holidays = mutableSetOf<LocalDate>()

        try {
            // 先尝试从网络获取 holiday-calendar 数据
            val request = Request.Builder()
                .url(HOLIDAY_API_URL.replace("{year}", year.toString()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = org.json.JSONObject(jsonStr)

                if (json.has("dates")) {
                    val datesArray = json.getJSONArray("dates")
                    for (i in 0 until datesArray.length()) {
                        val dateObj = datesArray.getJSONObject(i)
                        val dateStr = dateObj.getString("date")
                        val type = dateObj.getString("type")

                        // 只添加法定节假日
                        if (type == "public_holiday") {
                            try {
                                val date = LocalDate.parse(dateStr, dateFormatter)
                                holidays.add(date)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 网络失败时，使用内置的节假日数据
            holidays.addAll(getBuiltinHolidays(year))
        }

        // 保存到缓存
        holidayCache[year] = holidays
        return@withContext holidays
    }

    /**
     * 获取指定年份的所有调休工作日
     */
    private suspend fun getWorkdays(context: Context, year: Int): Set<LocalDate> = withContext(Dispatchers.IO) {
        // 检查缓存
        workdayCache[year]?.let { return@withContext it }

        val workdays = mutableSetOf<LocalDate>()

        try {
            // 先尝试从网络获取 holiday-calendar 数据
            val request = Request.Builder()
                .url(HOLIDAY_API_URL.replace("{year}", year.toString()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = org.json.JSONObject(jsonStr)

                if (json.has("dates")) {
                    val datesArray = json.getJSONArray("dates")
                    for (i in 0 until datesArray.length()) {
                        val dateObj = datesArray.getJSONObject(i)
                        val dateStr = dateObj.getString("date")
                        val type = dateObj.getString("type")

                        // 只添加调休工作日
                        if (type == "transfer_workday") {
                            try {
                                val date = LocalDate.parse(dateStr, dateFormatter)
                                workdays.add(date)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 网络失败时，使用内置的调休数据
            workdays.addAll(getBuiltinWorkdays(year))
        }

        // 保存到缓存
        workdayCache[year] = workdays
        return@withContext workdays
    }

    /**
     * 内置的节假日数据（2026-2027年）
     * 当网络请求失败时使用
     */
    private fun getBuiltinHolidays(year: Int): List<LocalDate> {
        return when (year) {
            2026 -> listOf(
                LocalDate.of(2026, 1, 1), // 元旦
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18), // 春节
                LocalDate.of(2026, 2, 19), LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 21), LocalDate.of(2026, 2, 22), LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6), // 清明节
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5), // 劳动节
                LocalDate.of(2026, 6, 19), // 端午节
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27), // 中秋节
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), // 国庆节
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8)
            )
            2027 -> listOf(
                LocalDate.of(2027, 1, 1), // 元旦
                LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 7), // 春节
                LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9), LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 11), LocalDate.of(2027, 2, 12),
                LocalDate.of(2027, 4, 4), LocalDate.of(2027, 4, 5), LocalDate.of(2027, 4, 6), // 清明节
                LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 4), LocalDate.of(2027, 5, 5), // 劳动节
                LocalDate.of(2027, 6, 9), // 端午节
                LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 16), LocalDate.of(2027, 9, 17), // 中秋节
                LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 2), LocalDate.of(2027, 10, 3), LocalDate.of(2027, 10, 4), // 国庆节
                LocalDate.of(2027, 10, 5), LocalDate.of(2027, 10, 6), LocalDate.of(2027, 10, 7), LocalDate.of(2027, 10, 8)
            )
            else -> emptyList()
        }
    }

    /**
     * 内置的调休工作日数据（2026-2027年）
     * 当网络请求失败时使用
     */
    private fun getBuiltinWorkdays(year: Int): List<LocalDate> {
        return when (year) {
            2026 -> listOf(
                LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 24), // 春节调休
                LocalDate.of(2026, 5, 3), // 清明节/劳动节调休
                LocalDate.of(2026, 9, 24), // 中秋节调休
                LocalDate.of(2026, 9, 26) // 国庆节调休
            )
            2027 -> listOf(
                LocalDate.of(2027, 2, 4), LocalDate.of(2027, 2, 13), // 春节调休
                LocalDate.of(2027, 4, 3), // 清明节调休
                LocalDate.of(2027, 5, 3), // 劳动节调休
                LocalDate.of(2027, 9, 14), // 中秋节调休
                LocalDate.of(2027, 9, 25) // 国庆节调休
            )
            else -> emptyList()
        }
    }

    /**
     * 清除缓存（用于更新节假日数据）
     */
    fun clearCache() {
        holidayCache.clear()
        workdayCache.clear()
    }

    /**
     * 获取内置节假日数据（用于日历显示）
     */
    fun getBuiltinHolidaysForCalendar(year: Int): Set<LocalDate> {
        return getBuiltinHolidays(year).toSet()
    }

    /**
     * 获取内置调休工作日数据（用于日历显示）
     */
    fun getBuiltinWorkdaysForCalendar(year: Int): Set<LocalDate> {
        return getBuiltinWorkdays(year).toSet()
    }
}
