package com.huaying.xstz

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。
 *
 * 刻意保持极薄：这里只做 Hilt 装配与进程级诊断挂钩，
 * 任何业务初始化都下沉到各自模块的 EntryPoint，避免又一次长成"上帝 Application"。
 */
@HiltAndroidApp
class XstzApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashDiagnostics()
    }

    /**
     * 零第三方 SDK 的崩溃诊断：
     * 1. UncaughtExceptionHandler 兜住当场崩溃栈，写到 files/crash/last-crash.txt
     * 2. ApplicationExitInfo（API 30+）在下次启动时回收上一次进程的死因
     * 3. 用户在设置页可一键导出诊断包（脱敏后的设备信息 + 崩溃栈）
     *
     * 不接 Firebase Crashlytics / Bugly：一是隐私合规成本，二是这类 SDK 会拖慢冷启动。
     */
    private fun installCrashDiagnostics() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { CrashLog.write(this, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
