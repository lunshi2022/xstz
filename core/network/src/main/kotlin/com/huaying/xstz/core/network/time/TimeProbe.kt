package com.huaying.xstz.core.network.time

/**
 * 单一时间探针：尝试从某一个时间源拿到服务器当前毫秒时间。
 *
 * 返回 `null` 表示这次尝试失败（超时 / 解析失败 / 网络不可达）。
 * 失败是常态——移动网络下任何单一时间源都可能抽风，
 * 所以 [TimeRepository] 才做成"按顺序试、第一个成功就用"的降级链。
 */
interface TimeProbe {
    suspend fun probe(): Long?
}
