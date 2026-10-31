plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.huaying.xstz.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 基准配置文件由 BaselineProfile 生成器在真机上产出，再拷贝回 app 模块
        targetProjectPath = ":app"
        // 与 app 的 benchmark buildType 对齐
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "baselineprofile"
    }

    buildTypes {
        // 必须有一个与 app 的 benchmark buildType 同名的 buildType，否则变体无法对齐
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro)
}
