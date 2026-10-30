package com.huaying.xstz.core.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 金额，单位 1e-4 元（万分之一元）。
 *
 * 为什么不用 Double：
 *   旧版把金额存成 Double，既能出现 0.1+0.2=0.30000000000000004 的漂移，
 *   又因为列级加密把 Double 变成 TEXT，让 `SUM(holdingQuantity * currentPrice)`
 *   在 SQL 里直接失效，只能退回应用层重算——这是旧架构最大的数据缺陷。
 *
 * 改成 Long 定点后：SQL 聚合精确、加密不再是障碍（加密整数不破坏运算语义，但我们
 * 干脆不再做列级加密）、跨进程序列化也不丢精度。
 *
 * 4 位小数足够覆盖基金净值（4 位）与金额累加（1 万分之一的误差可忽略）。
 */
@JvmInline
value class Money(val micros: Long) : Comparable<Money> {

    val isZero: Boolean get() = micros == 0L
    val isNegative: Boolean get() = micros < 0L

    operator fun plus(other: Money) = Money(micros + other.micros)
    operator fun minus(other: Money) = Money(micros - other.micros)
    operator fun times(factor: Double) = Money((micros * factor).roundToLong())
    operator fun times(factor: Int) = Money(micros * factor)
    operator fun div(divisor: Int) = Money(micros / divisor)
    operator fun unaryMinus() = Money(-micros)

    override fun compareTo(other: Money) = micros.compareTo(other.micros)

    /** 转成元（仅用于展示与图表坐标，不参与累计） */
    fun toYuan(): Double = micros / MICROS_PER_YUAN

    /** 绝对值 */
    fun abs(): Money = if (micros < 0) Money(-micros) else this

    override fun toString(): String = "Money(${toYuan()})"

    companion object {
        const val MICROS_PER_YUAN = 10_000.0

        val ZERO = Money(0L)

        /** 从元构造。Double 只在这一层出现，进入模型即转定点。 */
        fun fromYuan(yuan: Double): Money {
            if (yuan.isNaN() || yuan.isInfinite()) return ZERO
            return Money((yuan * MICROS_PER_YUAN).roundToLong())
        }

        fun fromMicros(micros: Long) = Money(micros)
    }
}

/**
 * 份额，单位 1e-4 份。
 * 与 Money 分开是因为量纲不同：把「份」当「元」写过一次加法，排查了两小时。
 */
@JvmInline
value class Shares(val micros: Long) : Comparable<Shares> {

    val isZero: Boolean get() = micros == 0L
    val isNegative: Boolean get() = micros < 0L

    operator fun plus(other: Shares) = Shares(micros + other.micros)
    operator fun minus(other: Shares) = Shares(micros - other.micros)
    operator fun times(factor: Double) = Shares((micros * factor).roundToLong())
    operator fun unaryMinus() = Shares(-micros)

    override fun compareTo(other: Shares) = micros.compareTo(other.micros)

    fun toDouble(): Double = micros / MICROS_PER_UNIT
    fun abs(): Shares = if (micros < 0) Shares(-micros) else this

    override fun toString(): String = "Shares(${toDouble()})"

    companion object {
        const val MICROS_PER_UNIT = 10_000.0
        val ZERO = Shares(0L)

        fun fromDouble(value: Double): Shares {
            if (value.isNaN() || value.isInfinite()) return ZERO
            return Shares((value * MICROS_PER_UNIT).roundToLong())
        }
    }
}

/**
 * 基金净值，单位 1e-4。
 * 单列一个类型是为了在编译期挡住「净值 × 份额 = 金额」里把净值当金额传错。
 */
@JvmInline
value class Nav(val micros: Long) : Comparable<Nav> {

    val isZero: Boolean get() = micros == 0L

    override fun compareTo(other: Nav) = micros.compareTo(other.micros)

    fun toDouble(): Double = micros / MICROS_PER_UNIT

    /** 净值 × 份额 = 市值。这是本项目最核心的一次乘法，单独收在这里便于审计。 */
    operator fun times(shares: Shares): Money =
        Money((micros.toDouble() * shares.micros / MICROS_PER_UNIT).roundToLong())

    /** 净值差（今日 - 昨日），结果仍是净值量纲 */
    operator fun minus(other: Nav) = Nav(micros - other.micros)

    override fun toString(): String = "Nav(${toDouble()})"

    companion object {
        const val MICROS_PER_UNIT = 10_000.0
        val ZERO = Nav(0L)

        fun fromDouble(value: Double): Nav {
            if (value.isNaN() || value.isInfinite()) return ZERO
            return Nav((value * MICROS_PER_UNIT).roundToLong())
        }
    }
}

/** 两个 Money 的相对差（用于偏离度），安全处理除零 */
fun deviationRatio(actual: Money, target: Money): Double {
    if (target.micros == 0L) return if (actual.micros == 0L) 0.0 else Double.MAX_VALUE
    return (actual.micros - target.micros).toDouble() / abs(target.micros)
}
