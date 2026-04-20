package com.huaying.xstz.util.performance

import android.util.Log

/**
 * 启动性能追踪器
 * 用于监控和分析应用启动时间，帮助识别性能瓶颈
 */
object StartupTracer {
    private const val TAG = "StartupTracer"
    private var appStartTime: Long = 0
    private val milestones = mutableMapOf<String, Long>()

    /**
     * 标记应用启动时间点
     */
    fun markAppStart() {
        appStartTime = System.currentTimeMillis()
        milestones["app_start"] = appStartTime
        Log.d(TAG, "App start marked")
    }

    /**
     * 标记启动过程中的里程碑
     * @param name 里程碑名称
     */
    fun markMilestone(name: String) {
        val time = System.currentTimeMillis()
        milestones[name] = time
        val elapsed = time - appStartTime
        Log.d(TAG, "Milestone '$name' reached at ${elapsed}ms")
    }

    /**
     * 生成启动性能报告
     * @return 格式化的性能报告字符串
     */
    fun getStartupReport(): String {
        val totalTime = System.currentTimeMillis() - appStartTime
        val sb = StringBuilder()
        sb.appendLine("=== 启动性能报告 ===")
        sb.appendLine("总启动时间: ${totalTime}ms")
        milestones.toSortedMap().forEach { (name, time) ->
            val elapsed = time - appStartTime
            sb.appendLine("  $name: ${elapsed}ms")
        }
        return sb.toString()
    }
}
