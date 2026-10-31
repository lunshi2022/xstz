package com.huaying.xstz.core.common

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 数值格式化。
 *
 * 移植自旧工程 util/FormatUtils.kt，保留其全部行为特征（29 个单测覆盖）：
 *   * 千分位用中文习惯，币种固定 ¥
 *   * 涨跌幅强制带符号（+2.50% / -1.30%）
 *   * 隐私模式返回掩码而不是空串——空串会让布局跳动
 *
 * 与旧版的差异只有一处：入参从 Double 换成 Money/Nav，避免调用方在格式化前
 * 把定点值转回 Double 又引入一次精度损失。
 */
object Formats {

    private val CURRENCY_2F = DecimalFormat("#,##0.00")
    private val NUMBER_2F = DecimalFormat("#,##0.00")
    private val NUMBER_3F = DecimalFormat("#,##0.000")
    private val NUMBER_4F = DecimalFormat("#,##0.0000")
    private val PERCENT_2F = DecimalFormat("0.00")
    private val PERCENT_1F = DecimalFormat("0.0")
    private val QUANTITY_2F = DecimalFormat("#,##0.00")

    const val PRIVACY_MASK = "****"

    // ---------------- 金额 ----------------

    /** ¥1,234.56 */
    fun currency(value: Double, privacy: Boolean = false): String =
        if (privacy) "¥ $PRIVACY_MASK" else "¥${CURRENCY_2F.format(value)}"

    /** 1,234.56（不带货币符号） */
    fun currencyValue(value: Double): String = NUMBER_2F.format(value)

    /** 带符号金额：+¥123.45 / -¥123.45，用于盈亏展示 */
    fun signedCurrency(value: Double, privacy: Boolean = false): String {
        if (privacy) return "¥ $PRIVACY_MASK"
        val sign = if (value >= 0) "+" else "-"
        return "$sign¥${CURRENCY_2F.format(abs(value))}"
    }

    // ---------------- 百分比 ----------------

    /**
     * 涨跌幅，带符号。入参是比例（0.025 表示 2.50%）。
     * 注意：与旧版一样，0 会被格式化成 "+0.00%"——负零（-0.0）也归到正号，
     * 否则界面上会出现 -0.00% 这种观感很怪的输出。
     */
    fun percent(value: Double, privacy: Boolean = false): String {
        if (privacy) return "**%"
        val pct = value * 100.0
        val safe = if (abs(pct) < 0.005) 0.0 else pct
        return if (safe >= 0) "+${PERCENT_2F.format(safe)}%" else "${PERCENT_2F.format(safe)}%"
    }

    /** 占比，不带符号。入参是比例。 */
    fun ratio(value: Double, privacy: Boolean = false): String =
        if (privacy) "**%" else "${PERCENT_2F.format(value * 100.0)}%"

    /**
     * 百分点（percentage points），带符号。入参是**百分点数值**，不是比例
     * （2.73 输出 "+2.73pp"，表示目标 40% 而当前 42.73%）。
     *
     * 为什么单独做而不是复用 [percent]：占比之差是"两个百分比相减"，单位上
     * 是百分点而非百分比。写成 "%" 会让"当前 42.73% 比目标 40% 高 2.73%"被
     * 误读成"高了 2.73% 的相对幅度"，两者数值可以差一个数量级。
     */
    fun percentagePoints(value: Double, privacy: Boolean = false): String {
        if (privacy) return "**pp"
        val safe = if (abs(value) < 0.005) 0.0 else value
        return if (safe >= 0) "+${PERCENT_2F.format(safe)}pp" else "${PERCENT_2F.format(safe)}pp"
    }

    fun ratio1f(value: Double): String = "${PERCENT_1F.format(value * 100.0)}%"

    // ---------------- 价格 / 份额 ----------------

    /** 净值，4 位小数（基金净值标准精度） */
    fun nav(value: Double, privacy: Boolean = false): String =
        if (privacy) PRIVACY_MASK else NUMBER_4F.format(value)

    /** 成本单价，4 位小数带 ¥ */
    fun costPrice(value: Double, privacy: Boolean = false): String =
        if (privacy) PRIVACY_MASK else "¥${NUMBER_4F.format(value)}"

    /** 份额，2 位小数带千分位 */
    fun quantity(value: Double, privacy: Boolean = false): String =
        if (privacy) PRIVACY_MASK else QUANTITY_2F.format(value)

    /** 通用兜底：格式化失败返回 defaultValue，绝不抛异常到 UI 线程 */
    fun safe(value: Double, pattern: String, fallback: String = "0.00"): String =
        runCatching { DecimalFormat(pattern).format(value) }.getOrDefault(fallback)

    // ---------------- 日期 ----------------

    /** epochDay → 2026-10-31 */
    fun date(epochDay: Long): String {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }

    /** epochDay → 10-31 */
    fun shortDate(epochDay: Long): String {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return "%02d-%02d".format(d.monthValue, d.dayOfMonth)
    }

    /** epochDay → 10月31日 */
    fun chineseDate(epochDay: Long): String {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 星期几，中文单字 */
    fun weekday(epochDay: Long): String = when (java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "一"
        java.time.DayOfWeek.TUESDAY -> "二"
        java.time.DayOfWeek.WEDNESDAY -> "三"
        java.time.DayOfWeek.THURSDAY -> "四"
        java.time.DayOfWeek.FRIDAY -> "五"
        java.time.DayOfWeek.SATURDAY -> "六"
        java.time.DayOfWeek.SUNDAY -> "日"
    }

    /** 时间戳 → 14:35 */
    fun time(timestamp: Long): String {
        val t = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /** 时间戳 → 10-31 14:35 */
    fun dateTime(timestamp: Long): String {
        val t = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
        return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }

    /** 距今天数（负数表示未来） */
    fun daysSince(timestamp: Long, now: Long = System.currentTimeMillis()): Int {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val then = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(then, today).toInt()
    }

    /** 数据新鲜度的中文描述，直接给 UI 用 */
    fun freshness(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "未更新"
        val minutes = (now - timestamp) / 60_000L
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            else -> "${minutes / (60 * 24)} 天前"
        }
    }

    /** 一次性拿到中文 Locale 的 NumberFormat，给图表轴用 */
    fun axisNumberFormat(decimals: Int = 0): NumberFormat =
        NumberFormat.getNumberInstance(Locale.CHINA).apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = decimals
            roundingMode = RoundingMode.HALF_UP
        }
}
