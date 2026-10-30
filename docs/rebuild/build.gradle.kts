// 关键：AGP 9.x 内置 Kotlin 固定为 2.2.10，而 Compose BOM 2026.09 的 stdlib 已是 2.4.x，
// 不显式抬高会出现 "Module was compiled with an incompatible version of Kotlin"（metadata 2.4.0）。
// 这是官方文档给出的显式指定 Kotlin 版本的方式。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

// 重建工程根 build.gradle.kts
// 变化：版本号全部移入 gradle/libs.versions.toml（旧工程 4 个插件版本 + 40 个依赖版本全部内联，无法统一升级）
// 复制到 <repo>/build.gradle.kts

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Kotlin 2.0+ 起 Compose 编译器由独立插件提供，不再用 composeOptions.kotlinCompilerExtensionVersion
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
