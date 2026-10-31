// InvestmentManager / XSTZ 重建工程 —— 设置文件
//
// 三条不可动的约束（踩过坑，勿改）：
//   1. 官方仓库必须排在阿里云镜像之前。AGP 9.x / Compose 2026.x 在镜像上同步滞后，
//      镜像前置会产生 "Could not find com.android.tools.build:gradle:9.1.1" 的假故障。
//   2. repositoriesMode 必须是 FAIL_ON_PROJECT_REPOS，子模块不得私自声明仓库。
//   3. include 的模块路径即 namespace 后缀（见根 build.gradle.kts 的派生规则）。

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "InvestmentManager"

// ---------- 应用层 ----------
include(":app")

// ---------- 核心层（8 个）----------
include(":core:model")        // 纯数据模型，无 Android 依赖
include(":core:common")       // 格式化、交易日历、调度器、结果类型
include(":core:domain")       // 仓储接口 + 计算内核 + 用例
include(":core:database")     // Room 实体、DAO、迁移
include(":core:network")      // 行情抓取与解析、离线样例
include(":core:data")         // 仓储实现、备份加密、设置存储
include(":core:designsystem") // Liquid Glass 材质与设计令牌
include(":core:ui")           // 跨页面共享组件

// ---------- 功能层（5 个）----------
include(":feature:overview")
include(":feature:transactions")
include(":feature:charts")
include(":feature:rebalance")
include(":feature:settings")
include(":feature:onboarding")
include(":benchmark")

// 类型安全项目访问器：让 projects.core.model 在编译期就能发现模块名写错，
// 而不是等到运行时才抛 "Project with path ':core:modle' could not be found"。
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
