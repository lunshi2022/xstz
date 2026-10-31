import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// 签名：密钥信息来自仓库根目录的 keystore.properties（已 gitignore）
// 缺文件时 release 不再回落到 debug 签名，而是直接出 unsigned 包并在构建日志里告警——
// 旧工程 signingConfig = debug 是"能装不能发"的隐形炸弹，这里彻底断掉。
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseKey = releaseStoreFile?.exists() == true

if (!hasReleaseKey) {
    logger.warn(
        "[XSTZ] 未找到 keystore.properties 或其指向的密钥文件，release 将输出 unsigned APK。" +
            "这不会影响 debug 与日常开发。"
    )
}

android {
    namespace = "com.huaying.xstz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.huaying.xstz"
        minSdk = 34
        targetSdk = 36
        versionCode = 200
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false // minSdk 34，v2/v3 足够
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
                "proguard-rules.pro",
            )
        }
        // 基准生成器（benchmark 模块）按同名 buildType 对齐变体：
        // 与生产 release 同源（同样开启 R8），便于在真机上产出与生产一致的基线 profile。
        // matchingFallbacks 让库依赖（core:*）无需各自声明 benchmark 变体，回落到 release 即可。
        create("benchmark") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            matchingFallbacks += listOf("release")
            signingConfig =
                if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    /*
     * ABI 拆分：只留 arm64-v8a。
     *
     * SQLCipher 的 libsqlcipher.so 四个架构合计 18.5MB，全打进一个包会让
     * release APK 从 2.97MB 涨到 21.54MB，超出方案第 389 行的 <12MB 目标。
     *
     * 为什么只留 arm64：
     *   * Play 自 2019 年起要求 64 位支持，2020 年后上市的设备几乎全为 arm64
     *   * 32 位 arm 设备已不在 Android 14（本项目 minSdk 34）的支持范围内
     *   * x86/x86_64 只用于模拟器——它们由 universalApk 覆盖，见下
     *
     * universalApk = true 保留一个包含全部架构的包：
     *   模拟器是 x86_64，装不了 arm64-only 包。没有这个 fallback，
     *   本地跑 connectedAndroidTest 会直接失败。
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(projects.feature.overview)
    implementation(projects.feature.transactions)
    implementation(projects.feature.charts)
    implementation(projects.feature.rebalance)
    implementation(projects.feature.settings)
    implementation(projects.feature.onboarding)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.profileinstaller)

    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
}
