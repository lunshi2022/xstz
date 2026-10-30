// 重建工程 settings.gradle.kts
// 关键变更：官方仓库前置、镜像降级为 fallback（解决新版 AGP/Compose 在镜像上解析不到的问题）
// 复制到 <repo>/settings.gradle.kts

pluginManagement {
    repositories {
        // 顺序很重要：镜像对 AGP 9.x / Compose 2026.x 同步普遍滞后，
        // 放在 google() 之前会造成 "Could not find com.android.tools.build:gradle:9.x" 的假故障。
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

dependencyResolutionManagement {
    // 保持严格模式：子模块不得私自声明仓库，避免仓库漂移
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 镜像仅作兜底；若内网必须用镜像，请把镜像放前面并接受版本滞后风险
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        // JitPack 仅剩 MPAndroidChart 等历史依赖；重建后应移除
        maven("https://jitpack.io")
    }
}

rootProject.name = "InvestmentManager"

include(":app")
// 目标多模块结构（阶段 1 起逐步启用）
// include(":core:model")
// include(":core:domain")
// include(":core:data")
// include(":core:designsystem")
// include(":core:ui")
// include(":feature:overview")
// include(":feature:holdings")
// include(":feature:charts")
// include(":feature:rebalance")
// include(":feature:settings")
