# AGENTS.md

> 项目：InvestmentManager（Android 投资管理应用）
> 初始化日期：2026-06-06
> 最近更新：2026-06-09
> 语言：中文

---

## 加载指引

后续 AI agent 接手本项目时，按以下顺序阅读：

1. **本文件（AGENTS.md）** — 项目级执行规则与协作基线
2. **`prototype/code.html`** — 原型设计稿，理解 UI 交互意图
3. **`app/build.gradle.kts`** — 依赖版本与构建配置
4. **`app/src/main/AndroidManifest.xml`** — 权限与组件声明

---

## 1. 项目一句话画像

这是一个基于 **Jetpack Compose + Kotlin** 的 Android 投资管理应用，包名为 `com.huaying.xstz`。应用核心功能包括基金/资产概览、净值记录、交易管理、目标仓位再平衡、图表分析、操作日志、数据加密导入导出与自动更新。

---

## 2. 仓库拓扑

| 类别 | 路径 | 说明 |
|------|------|------|
| 项目根目录 | `d:\LeStoreSoftstore\Install\Android_Projram\Project\20260205124830` | 单仓库，无嵌套子仓库 |
| 主代码模块 | `app/` | 唯一 Android 应用模块 |
| Gradle 包装器 | `gradle/wrapper/` | 使用 `gradlew.bat` 构建 |
| 原型/设计稿 | `prototype/` | 包含 `code.html` 与 `screen.png`，非构建产物 |
| 许可证 | `LICENSE` | MIT License，版权所有 2026 lunshi2022 |

- **Git 状态**：已初始化 Git 仓库，`.git` 位于项目根目录。
- **项目类型**：`single-repo`（单仓库 Android 项目）
- **应用版本**：versionName `1.6.0`，versionCode `160`

---

## 2.1 Git 规则

- **默认 Git 工作目录**：`d:\LeStoreSoftstore\Install\Android_Projram\Project\20260205124830`
- **Git 根路由**：已初始化，`.git` 位于项目根目录
- **主分支推断**：`main`（Android 项目惯例）
- **分支与收尾约定**：功能分支命名建议 `feature/<描述>`，修复分支 `fix/<描述>`
- **多仓库暂停点**：无（单仓库项目）

---

## 3. 技术栈与关键版本

| 技术 | 版本 |
|------|------|
| Android Gradle Plugin (AGP) | 8.2.2 |
| Kotlin | 1.9.22 |
| KSP | 1.9.22-1.0.17 |
| Compile SDK | 34 |
| Min SDK | 24 |
| Target SDK | 34 |
| Java / JVM Target | 21 |
| Compose BOM | 2024.02.00 |
| Compose Compiler | 1.5.8 |
| Room | 2.6.1 |
| Hilt | 2.50 |

---

## 4. 目录职责

```
app/src/main/java/com/huaying/xstz/
├── core/init/           # 应用初始化（AppInitializer.kt）
├── data/
│   ├── api/             # Retrofit 网络接口（GitHubApi.kt）
│   ├── converter/       # Room 类型转换器
│   ├── dao/             # Room 数据访问对象
│   ├── entity/          # Room 实体类
│   ├── model/           # 业务数据模型
│   ├── repository/      # 仓库层（数据操作、日志、时间、更新检查）
│   ├── security/        # 加密管理（CryptoManager.kt）
│   ├── AppDatabase.kt   # Room 数据库入口
│   ├── PreferenceManager.kt # DataStore 偏好设置
│   └── ThemeMode.kt     # 主题模式枚举（AUTO/LIGHT/DARK）
├── di/                  # Hilt 依赖注入模块（AppModule.kt）
├── ui/                  # Jetpack Compose UI 层
│   ├── addfund/         # 添加基金
│   ├── animation/       # 动画常量与工具
│   │   ├── interaction/ # 交互动画
│   │   ├── list/        # 列表动画
│   │   ├── loading/     # 加载动画
│   │   ├── navigation/  # 导航动画
│   │   └── splash/      # 启动画面动画
│   ├── assetdetail/     # 资产详情
│   ├── assetoverview/   # 资产总览（含日历、趋势图）
│   ├── calendar/        # 日历视图
│   ├── charts/          # 图表分析（MPAndroidChart）
│   ├── component/       # 通用组件（底部导航、加载状态等）
│   ├── fund/            # 基金详情与交易历史
│   ├── mainscreen/      # 主屏幕
│   ├── navigation/      # 导航扩展
│   ├── rebalance/       # 再平衡
│   ├── settings/        # 设置、关于、操作日志、引导
│   ├── targetallocation/# 目标仓位
│   ├── theme/           # 主题、颜色、字体
│   └── update/          # 更新对话框
├── util/                # 工具类（下载、安装、SNTP、性能追踪）
├── InvestmentApp.kt     # Application 类（@HiltAndroidApp）
└── MainActivity.kt      # 主 Activity（Compose 入口）
```

---

## 5. 构建与运行入口

- **本地构建命令**：
  ```powershell
  $env:JAVA_HOME="D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr"; .\gradlew.bat assembleDebug
  ```
- **安装到设备**：
  ```powershell
  $env:JAVA_HOME="D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr"; .\gradlew.bat installDebug
  ```
- **清理**：
  ```powershell
  $env:JAVA_HOME="D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr"; .\gradlew.bat clean
  ```
- **JAVA_HOME**：`D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr`（Android Studio 内置 JBR，OpenJDK 21.0.10）。系统环境变量 `JAVA_HOME` 指向了不存在的路径，构建时需显式设置。
- **Gradle 配置**：`gradle.properties` 中已启用并行构建、配置按需、缓存，并指定了本地 Maven 仓库镜像路径 `D:/LeStoreSoftstore/Install/Android_Projram/Project/20260205124830/.m2/repository`。

---

## 6. 关键架构特征

- **UI 框架**：100% Jetpack Compose，使用 Material3。
- **依赖注入**：Hilt（`@HiltAndroidApp` + `@Module` + `@Inject`），`AppModule` 提供 Database、Repository、PreferenceManager 的单例绑定。
- **状态管理**：Compose 状态 + `produceState` / `collectAsState`，ViewModel 用于部分页面（`AssetOverviewViewModel`、`ChartsViewModel`、`UpdateViewModel`、`AddFundViewModel`、`RebalanceViewModel`、`TargetAllocationViewModel`、`AssetDetailViewModel`）。
- **本地存储**：Room 数据库（`AppDatabase`）+ DataStore 偏好设置（`PreferenceManager`）。
- **网络**：Retrofit + OkHttp，通过 GitHub raw 检查更新。
- **安全**：使用 `androidx.security:security-crypto` 进行数据加密，导入导出使用 AES 加密。
- **图表**：MPAndroidChart（`com.github.PhilJay:MPAndroidChart`）。
- **时间处理**：ThreeTenABP 提供 `java.time` 向后兼容。

---

## 7. 常见误判提醒

1. **JAVA_HOME 配置**：系统环境变量 `JAVA_HOME` 指向 `C:\Program Files\Java\jdk-21`（不存在），构建时必须显式设置为 `D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr`。
2. **单模块项目**：`settings.gradle.kts` 仅 `include(":app")`，没有多模块结构。
3. **本地 Maven 镜像**：`gradle.properties` 中 `systemProp.maven.repo.local` 指向了项目内 `.m2/repository`，构建时可能依赖本地缓存。
4. **更新检查源**：`UpdateRepository` 通过 GitHub 仓库 `lunshi2022/xstz` 检查更新并下载 APK。
5. **Hilt 入口**：`InvestmentApp` 标注 `@HiltAndroidApp`，所有需要注入的 Activity 需标注 `@AndroidEntryPoint`。
6. **导航入口**：`MainScreen.kt` 是 Compose 导航的根节点，所有页面路由在此定义。

---

## 8. 协作热点

| 热点 | 路径 | 说明 |
|------|------|------|
| 导航路由 | `ui/mainscreen/MainScreen.kt` | Compose NavHost 定义所有页面路由 |
| 数据库入口 | `data/AppDatabase.kt` | Room 数据库单例，所有 DAO 从此获取 |
| 依赖注入 | `di/AppModule.kt` | Hilt Module，提供全局单例绑定 |
| 偏好设置 | `data/PreferenceManager.kt` | DataStore 偏好读写，主题/引导状态等 |
| 更新检查 | `data/repository/UpdateRepository.kt` | GitHub 版本检查与 APK 下载逻辑 |
| 加密管理 | `data/security/CryptoManager.kt` | AES 加密/解密，导入导出数据时使用 |

---

## 9. 契约与证据

- **数据库变更**：修改 Entity 或新增 DAO 方法时，必须同步更新 `AppDatabase.kt` 的 version 和 Migration。
- **依赖变更**：修改 `app/build.gradle.kts` 中的依赖版本时，必须在 AGENTS.md 技术栈表格中同步更新。
- **导航变更**：新增或删除页面路由时，必须同步更新 `MainScreen.kt` 中的 NavHost。
- **Hilt 绑定变更**：新增 Repository 或全局单例时，必须同步更新 `AppModule.kt`。

---

## 10. 任务收尾规范

所有 AI 协作任务结束时，必须在回复中按以下结构给出总结：

1. **本轮完成度**：说明已完成的工作内容。
2. **主线目标是否完成**：明确回答“是”或“否”。
3. **已完成的关键改动**：列出具体文件或功能变更。
4. **已执行的验证**：说明是否编译通过、是否运行测试、是否手动验证。
5. **未完成 / 阻塞项**：如有，说明原因及性质（如“外部阻塞”“验证缺口”“可选优化”）。
6. **下一刀建议**：给出后续最合理的行动建议。

---

## 11. 待确认事项

- [x] **Git 根目录**：已在项目根目录初始化 Git，`.git` 位于 `d:\LeStoreSoftstore\Install\Android_Projram\Project\20260205124830\.git`。
- [x] **开发环境**：已安装 Android Studio / JDK 21 / Android SDK 34，使用真机调试。
- [x] **本地 Maven 缓存**：保留 `gradle.properties` 中的本地 `.m2/repository` 缓存配置，同时保留 `settings.gradle.kts` 中的阿里云公共镜像，两者并存。
