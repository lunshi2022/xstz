plugins {
    // 升级到 8.2.2 以支持 JDK 21
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false

    // 建议同时将 Kotlin 升级到 1.9.22 以匹配 AGP 8.2
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false

    // KSP 也要对应 Kotlin 版本 (1.9.22 对应 1.0.17)
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}