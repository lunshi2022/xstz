import java.util.Properties

plugins {
    alias(libs.plugins.android.test)
}

// ---------------------------------------------------------------------------
// 签名：测试 APK 必须与被测 App 用同一把密钥。
//
// Android 只允许「同签名」的包互相插桩，否则 am instrument 直接报
// Permission Denial: package ... does not have a signature matching the target。
// app 的 benchmark 变体用 release 密钥签名，这里就必须跟着用 release 密钥，
// 不能沿用 debug——这是第一次真正跑起来时唯一卡住的点。
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseKey = releaseStoreFile?.exists() == true

android {
    namespace = "com.huaying.xstz.benchmark"
    compileSdk = 37

    /*
     * 自插桩（官方 baselineprofile 插件内部也是这么干的）。
     *
     * 不设它，AGP 会按 targetProjectPath 把 instrumentation 的 targetPackage 钉成被测 App，
     * 测试于是和 App 挤在同一进程；基准测试第一步就是杀 App 进程测冷启动，
     * 顺手把自己杀掉 —— "Instrumentation run failed due to Process crashed"。
     * 实测：在清单里手写 instrumentation（含 tools:replace）都会被 AGP 覆盖，只能走这个开关。
     */
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 基准配置文件由 BaselineProfile 生成器在真机上产出，再拷贝回 app 模块
        targetProjectPath = ":app"
        // 注意：不要在这里设置 androidx.benchmark.profiling.mode。
        // BaselineProfileRule 自己决定采集方式，额外传 "baselineprofile" 会让
        // benchmark 解析枚举失败并抛 AssertionError（实测）。
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        // 必须有一个与 app 的 benchmark buildType 同名的 buildType，否则变体无法对齐。
        //
        // 两边都不开 R8：AGP 的 checkTestedAppObfuscation 要求被测 App 与测试 APK
        // 混淆状态一致，而 com.android.test 模块在 AGP 9.1 下不应用
        // buildTypes.proguardFiles，无法给测试包提供 keep 规则（实测规则文件
        // 未进入 R8 输入，minify 阶段报缺类失败）。详见 app/build.gradle.kts 同名变体注释。
        create("benchmark") {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("[XSTZ] 测试包缺 release 密钥，回落 debug 签名——插桩会因签名不一致失败")
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro)
}
