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
    alias(libs.plugins.android.test) apply false
    // Kotlin 2.0+ 起 Compose 编译器由独立插件提供，不再用 composeOptions.kotlinCompilerExtensionVersion
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // 覆盖率统计。不 apply 到具体模块，由下面的 subprojects 统一挂载——
    // 13 个模块逐个写一遍既啰嗦又容易漏。
    alias(libs.plugins.kover) apply false
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
            // 基准生成器（benchmark 模块）需要 :app:benchmark 变体，而 :app:benchmark 又要求
            // 所有库依赖也暴露 benchmark 变体。给每个库模块补同名 buildType，使变体精确对齐，
            // 比依赖 matchingFallbacks 跨模块级联更稳（级联在实测中未生效）。
            buildTypes {
                create("benchmark") {
                    isMinifyEnabled = false
                    matchingFallbacks += listOf("release")
                }
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        dependencies.add("testImplementation", junit4Dependency)

        // --- 覆盖率统计（方案阶段 1 验收：补覆盖率）---
        // 只对库模块挂载：:app 本身几乎无业务代码，统计它只会稀释数字。
        // 用 Kover 而非 JaCoCo：Kover 原生理解 Kotlin inline/value class，
        // 本项目 Money/Shares/Nav 全是 @JvmInline value class，
        // JaCoCo 会把它们算成一堆无法覆盖的合成方法，数字严重失真。
        pluginManager.apply("org.jetbrains.kotlinx.kover")
    }

    // --- Compose 开关：只对真正应用了 Compose 编译器的库模块开启 ---
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        if (plugins.hasPlugin("com.android.library")) {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                buildFeatures.compose = true
            }
        }
    }

    // --- 单测任务 ---
    // Gradle 9 默认对"有测试源目录但没发现用例"的模块判失败。
    // 多模块项目里，先建目录后写测试是常态，这条默认值会让空模块直接卡住构建。
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
