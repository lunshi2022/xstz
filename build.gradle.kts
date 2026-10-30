// 根构建脚本
//
// 【为什么不用 build-logic / 预编译约定插件】
// AGP 9 自带内置 Kotlin 支持，预编译脚本插件与它共用 classpath 时容易撞 kotlin-gradle-plugin
// 版本（AGP 内置 2.2.10 vs 工程需要 2.3.21）。这里改用 subprojects 统一配置：
// 类型安全、零额外编译单元、改一处生效于 13 个模块。
// 已验证：KotlinAndroidProjectExtension 在 AGP 9.1.1 下的实际实现为 *_Decorated，可直接 configure。

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

buildscript {
    dependencies {
        // AGP 9.x 内置 Kotlin 固定 2.2.10，而 Compose BOM 2026.05 的 stdlib 是 2.3.x/2.4.x。
        // 不抬这个 classpath 会出现 "Module was compiled with an incompatible version of Kotlin"。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Kotlin 2.0+ 起 Compose 编译器由独立插件提供，不再用 composeOptions.kotlinCompilerExtensionVersion
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// 在 subprojects 之外取一次，避免在子项目上下文里重复解析版本目录
val junit4Dependency = libs.junit4

// ---------------------------------------------------------------------------
// 全模块统一基线：SDK 版本、Java/Kotlin 目标、单测资源、lint 门禁
// ---------------------------------------------------------------------------
subprojects {
    // --- 库模块 ---
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileSdk = 37 // 硬约束：Compose 1.12.1 的 AAR metadata 要求 minCompileSdk=37
            namespace = "com.huaying.xstz" + project.path.replace(":", ".")
            defaultConfig.minSdk = 34
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures.buildConfig = false
            testOptions.unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
            lint {
                abortOnError = true
                checkReleaseBuilds = true
                warningsAsErrors = false
                disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        dependencies.add("testImplementation", junit4Dependency)
    }

    // --- Compose 开关：只对真正应用了 Compose 编译器的库模块开启 ---
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        if (plugins.hasPlugin("com.android.library")) {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                buildFeatures.compose = true
            }
        }
    }
}
