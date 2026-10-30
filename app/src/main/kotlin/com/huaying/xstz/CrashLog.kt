package com.huaying.xstz

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃落盘：只保留最近 5 次，避免反复崩溃把用户存储写满。
 * 内容不含任何用户资产数值——只记录线程、异常栈、构建与设备指纹。
 */
object CrashLog {

    private const val DIR = "crash"
    private const val KEEP = 5

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val body = buildString {
            appendLine("time=$stamp")
            appendLine("thread=${thread.name}")
            appendLine("build=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("---")
            appendLine(sw.toString())
        }
        runCatching { File(dir, "crash-$stamp.txt").writeText(body) }

        // 只留最近 KEEP 份
        runCatching {
            dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        }
    }

    fun latest(context: Context): String? {
        val dir = File(context.filesDir, DIR)
        val file = dir.listFiles()?.maxByOrNull { it.name } ?: return null
        return runCatching { file.readText() }.getOrNull()
    }
}
