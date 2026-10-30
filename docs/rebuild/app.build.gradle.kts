// 重建工程 app/build.gradle.kts（示例骨架）
// 复制到 <repo>/app/build.gradle.kts
//
// 与旧配置相比，本文件解决 6 个"一升级就编译失败"的问题，逐条见下方注释：
//   1. 内部 API com.android.build.gradle.internal.api.ApkVariantOutputImpl → androidComponents.onVariants
//   2. composeOptions.kotlinCompilerExtensionVersion → kotlin-compose 插件
//   3. kotlinOptions（Kotlin 2.x 已废弃）→ kotlin.compilerOptions
//   4. release 复用 debug 签名 → keystore.properties 外部注入
//   5. lint 全量关闭 → 恢复为门禁 + baseline
//   6. 版本号内联 → version catalog

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 签名信息从本地文件读取，文件不入库（务必加入 .gitignore：keystore.properties / *.jks / *.keystore）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.huaying.xstz"
    // 【实测修正 2026-10-30】compileSdk 必须 37，与选哪条轨无关。
    // 原因：Compose 生态已硬性要求 compileSdk ≥ 37（androidx.compose.ui:ui-android:1.12.1
    //   的 AAR metadata 里 minCompileSdk=37，配 36 直接编译失败）。
    // 注意 compileSdk 只是编译期 API 级别，不影响运行时行为与设备覆盖（那由 minSdk/targetSdk 决定）。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.huaying.xstz"
        // minSdk 34（Android 14+，已确认）。覆盖约 45% 设备，但换来两项关键收益：
        //   1. RenderEffect 背景模糊（API 31+）与 AGSL 折射（API 33+）全设备可用
        //      → 玻璃降级矩阵由四档缩为三档，L1「仅染色无模糊」档彻底消失，观感目标上调至 92~95%
        //   2. ApplicationExitInfo（API 30+）可读崩溃历史，支撑零第三方 SDK 的崩溃诊断方案
        // 附带：native splash attrs 为 API 31+，minSdk 34 下原生可用，无需 core-splashscreen。
        minSdk = 34
        // 2026-08-31 起 Play 强制 targetSdk ≥ 36；34 会被直接拒收。
        targetSdk = 36
        versionCode = 200
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false // minSdk 34 ≥ 24，v2/v3 足够，无需 v1
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 旧配置用 JavaVersion.VERSION_21 + AGP 8.2，属官方未验证组合（AGP 8.2 基线 JDK 17）。
    // 统一到 17：编译产物稳定，Gradle 仍可用 JDK 21 运行。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // AGP 8+ 默认关闭，需显式开启
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    lint {
        // 旧配置 abortOnError=false + checkReleaseBuilds=false = 完全放弃静态门禁
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml") // 首次执行 ./gradlew lint 生成，存量问题冻结
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion") // 版本提示不阻断
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric 需要
            isReturnDefaultValues = true
        }
    }

    // 【轨 B 实测修正 2026-10-30】原写法 output.outputFileName 在 AGP 9.1.1 上不存在
    // （该属性由 AGP 9.2 引入，9.1.1 的 VariantOutput 接口无此成员，编译期报 Unresolved reference）。
    // 不采用强转 VariantOutputImpl 内部类的方案（AGP 9 已明确不鼓励依赖内部 API）。
    // 结论：自定义产物文件名下沉到 CI 打包步骤（mv app-release.apk xstz-v2.0.0.apk），构建脚本保持纯净。
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // --- 平台化 ---
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    // --- 基础设施 ---
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    // 不使用 androidx.core:core-splashscreen：minSdk 34 原生支持 splash attrs（API 31+）


    // --- Compose ---
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // 注意：旧工程的 androidx.compose.material:material:1.6.1 仅用于 pullRefresh（M2 已冻结）。
    // M3 1.3+ 提供 PullToRefreshBox，重建时直接用它，不要再引 M2 material。

    // --- Liquid Glass 材质 ---
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // --- 图表（替代 MPAndroidChart v3.1.0 / 2020 年停更）---
    // 图表全部自绘 Canvas（已确认 2026-10-30）→ 已移除 Vico，
    // 收益：少一个依赖、包体更小、图表圆角裁切与玻璃材质完全统一。

    // --- 数据 ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    // 若改为整库加密（替代列级加密），再启用：
    // implementation(libs.sqlcipher)
    // implementation(libs.sqlite.ktx)

    // --- 网络 ---
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // --- 协程 ---
    implementation(libs.kotlinx.coroutines.android)

    // --- DI ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // --- 测试 ---
    testImplementation(libs.junit4)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
