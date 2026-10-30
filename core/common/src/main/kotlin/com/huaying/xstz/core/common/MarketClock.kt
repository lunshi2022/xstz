package com.huaying.xstz.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A 股交易时钟。
 *
 * 旧版把「现在是几点」「今天是不是节假日」「该显示什么状态」揉在一个有内部缓存的类里，
 * 导致这三点无法单独测试，也没法在不改代码的前提下模拟节假日。
 *
 * 新版拆成三块：
 *   * [MarketSession] —— 纯时间决策，无依赖、无状态、可完全穷举测试
 *   * [TradingCalendar] —— 只回答"这一天是否交易日"
 *   * [MarketClock] —— 把两者拼起来，注入时钟与日历，便于测试与替换
 *
 * 所有时间统一按 Asia/Shanghai 计算。设备时区是干扰项：用户在纽约也应该看到 A 股的开收盘状态。
 */
object MarketSession {

    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 集合竞价开始 */
    val MORNING_OPEN: LocalTime = LocalTime.of(9, 15)
    val MORNING_CLOSE: LocalTime = LocalTime.of(11, 30)
    val AFTERNOON_OPEN: LocalTime = LocalTime.of(13, 0)
    val AFTERNOON_CLOSE: LocalTime = LocalTime.of(15, 0)

    /**
     * 按当日分钟数给出市场状态。
     * 边界归属：9:15 算"交易中"（进入集合竞价），11:30 仍算"交易中"（含收盘那一分钟），
     * 11:31~12:59 是午休，15:00 之后是已收盘。这套归属与旧版一致，测试用例锁死。
     */
    fun statusAt(minuteOfDay: Int): MarketStatus = when {
        minuteOfDay < MORNING_OPEN.toMinute() -> MarketStatus.PRE_OPEN
        minuteOfDay <= MORNING_CLOSE.toMinute() -> MarketStatus.TRADING
        minuteOfDay < AFTERNOON_OPEN.toMinute() -> MarketStatus.LUNCH_BREAK
        minuteOfDay <= AFTERNOON_CLOSE.toMinute() -> MarketStatus.TRADING
        else -> MarketStatus.CLOSED
    }

    fun statusAt(time: LocalTime): MarketStatus = statusAt(time.toMinute())

    /** 是否处于连续竞价时段 */
    fun isTrading(minuteOfDay: Int): Boolean = statusAt(minuteOfDay) == MarketStatus.TRADING

    fun isTrading(time: LocalTime): Boolean = isTrading(time.toMinute())

    private fun LocalTime.toMinute(): Int = hour * 60 + minute
}

/** 市场状态，直接对应界面上那行小字 */
enum class MarketStatus(val displayName: String) {
    PRE_OPEN("未开盘"),
    TRADING("交易中"),
    LUNCH_BREAK("午间休盘"),
    CLOSED("已收盘"),
    WEEKEND("周末休市"),
    HOLIDAY("节假日休市"),
}

/** 交易日判定。实现可替换：默认周末规则，联网时叠加法定节假日表。 */
fun interface TradingCalendar {
    fun isTradingDay(date: LocalDate): Boolean

    companion object {
        /** 兜底实现：只看周末。没有节假日数据时至少不会把周六当交易日。 */
        val WEEKEND_ONLY = TradingCalendar { date ->
            date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        }
    }
}

/**
 * 组合时钟。注入 now() 与 calendar，让"当前是几点"和"今天休不休市"都可控。
 */
class MarketClock(
    private val calendar: TradingCalendar = TradingCalendar.WEEKEND_ONLY,
    private val now: () -> Instant = { Instant.now() },
) {

    fun today(): LocalDate = now().atZone(MarketSession.ZONE).toLocalDate()

    fun currentStatus(): MarketStatus {
        val local = now().atZone(MarketSession.ZONE)
        val date = local.toLocalDate()
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return MarketStatus.WEEKEND
        }
        if (!calendar.isTradingDay(date)) return MarketStatus.HOLIDAY
        return MarketSession.statusAt(local.toLocalTime())
    }

    fun isTradingNow(): Boolean = currentStatus() == MarketStatus.TRADING

    /**
     * 数据新鲜度容忍窗口：收盘后 2 小时内仍认为当日净值可能更新中，
     * 超过这个窗口才在 UI 上提示"数据可能延迟"。
     */
    fun isDataLikelyStale(lastUpdate: Instant): Boolean {
        if (lastUpdate.isAfter(now())) return false
        val hours = java.time.Duration.between(lastUpdate, now()).toHours()
        return hours > 2 && isTradingNow().not()
    }
}
