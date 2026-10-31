package com.huaying.xstz.core.common

/**
 * 可信时间锚。
 *
 * 方案 §5.1「时间防篡改」的核心：用 SNTP 校时得到的偏移量，
 * 把"本机墙钟"替换成"可信墙钟"。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么能防篡改
 *
 * 偏移量定义为 `serverTimeMs − elapsedRealtimeMs`。
 * `elapsedRealtime` 是开机后经过的毫秒，**不受用户改系统时间影响**；
 * 用户把手机时间往后拨一个月，它照常走。所以只要偏移量在校准时
 * 确定下来，之后 `trustedNow = elapsedRealtimeNow + offset` 永远正确，
 * 改系统时间无法让"可信时间"跟着跳——这正是刷收益/造未来快照类
 * 攻击失效的原因。
 *
 * 未校准时（`offset == null`）退回 `System.currentTimeMillis()`：
 * 连一次网都失败的极端情况下，至少不比原生更差。
 * ══════════════════════════════════════════════════════════════════
 */
class TrustedClock(
    private var offsetMs: Long? = null,
) {

    /** 记录一次校时结果，写入新的偏移量 */
    fun setOffset(offsetMs: Long) {
        this.offsetMs = offsetMs
    }

    /** 是否已至少成功校时一次 */
    val isCalibrated: Boolean get() = offsetMs != null

    /**
     * 取可信墙钟毫秒。
     *
     * @param elapsedRealtimeMs 调用方传入的 [android.os.SystemClock.elapsedRealtime]，
     *                          注入以便测试，不在此处直接读 Android API。
     */
    fun trustedNowMs(elapsedRealtimeMs: Long): Long {
        val off = offsetMs ?: return System.currentTimeMillis()
        return elapsedRealtimeMs + off
    }
}
