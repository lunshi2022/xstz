# 先生投资（XSTZ）项目评估与重开发方案

> 评估对象：`com.huaying.xstz` / InvestmentManager
> 评估基线：Git 分支 `workbuddy/main-ed162e29`，HEAD `de5591c`（2026-09-15）
> 评估日期：2026-10-30
> 代码规模：99 个 Kotlin 文件 / 23,068 行（其中 UI 层 51 文件 17,886 行）

---

## 0. 结论摘要

### 0.1 废弃判定

| 维度 | 判定 | 依据 |
|------|------|------|
| 是否被官方标记废弃 | **否** | GitHub API：`archived=false`、`disabled=false`，仓库可正常访问 |
| 是否仍在维护 | **否（事实停更）** | 远程 `main` 最后提交 2026-06-10（距今 4 个月 20 天）；本地分支有 3 个提交从未推送 |
| 是否可继续分发 | **否（已失效）** | `targetSdk=34`，而 Google Play 自 2026-08-31 起要求新应用/更新必须 `targetSdk≥36`；现有应用需 ≥35 才能对新用户可见 |
| Release 是否可用于分发 | **否** | `release` 构建 `signingConfig = signingConfigs.getByName("debug")`，调试签名无法正式发布，且换签名后老用户无法覆盖安装 |
| 社区与协作 | **无** | 0 star / 0 fork / 0 issue / 0 PR / 0 贡献者（仅 `lunshi2022` 一人）；无 CI 配置 |
| 分发渠道 | **近乎为零** | 仅 1 个 Release（1.6.5），APK 累计下载 2 次 |

**综合结论：该项目属于「事实性停更 / 准废弃」（de-facto dormant），不属于被平台标记的正式废弃。**

判断理由分三层：

1. **时间层**：远程仓库最后推送在 2026-06-10，已超 4 个月无任何提交；更关键的是本地领先远程 3 个提交（v1.6.5-Beta 收尾工作、网络解析收口、再平衡计算迁移三项重构），说明连作者本人的收尾工作都没有推送到公开仓库 —— 这是典型的"做到一半放下"。
2. **合规层**：即使今天想恢复维护，`targetSdk=34` 已无法提交 Google Play 更新（2026-08-31 起需 36），也无法让新用户从商店获取。技术上"还能编译"，商业上"已经出不去"。
3. **工程层**：单人维护 + 零 CI + 零 UI/DB 测试 + 发布用调试签名 + 硬编码本机绝对路径（`gradle.properties:18` 指向 `D:/LeStoreSoftstore/...`），意味着换个开发环境连构建都跑不起来。这不符合"可持续维护"的最低标准。

**但代码资产有明确保留价值**：再平衡迭代收敛算法、持仓计算、腾讯行情纯函数解析器、交易时段判定这四块是真正的领域资产（99 个单元测试覆盖），应当提取复用，而不是从零重写。

### 0.2 重建 vs 原地升级

**建议重建（rewrite），不做原地升级。** 理由：

- 原地升级需要同时跨越：AGP 8.2→9.2、Kotlin 1.9→2.2、Compose BOM 2024.02→2026.09、Room 2.6→2.7（KSP2）、导航 API 重写、图表库替换。这一步的改动量已超过重写，且会在旧架构（占位 NavHost + 手动 AnimatedContent 叠加、Activity 承担导入导出业务、列级加密破坏 SQL 聚合）上叠加债务。
- 目标形态是"iOS Liquid Glass 观感"，这要求**从设计令牌层重建**。现有代码有 145 处硬编码 `Color(0xFF...)`、113 处直接写 `.sp`、80 处 `RoundedCornerShape(1x.dp)`，没有材质抽象层，无法在其上长出玻璃材质系统。
- 唯一必须谨慎的是**数据迁移**：老用户（哪怕是作者自己）的本地数据库必须能平滑导入新版本。

### 0.3 关键前提：Liquid Glass 在 Android 上无法 100% 对齐

必须先把预期钉死，否则会做出一个在中低端机上掉帧、在旧机型上糊成一片的产品。

| iOS Liquid Glass 特性 | Android 可达成度 | 依赖 |
|---|---|---|
| 背景实时模糊（Backdrop Blur） | ✅ 90% | Haze（RenderEffect），API 32+ 生效 |
| 折射/透镜畸变（Lensing） | ⚠️ 60% | AGSL `RuntimeShader`，API 33+ |
| 镜面高光随姿态响应（Specular） | ❌ 30% | Android 无系统级统一光场，只能传感器伪实现 |
| 自适应染色（Adaptive Tint） | ✅ 85% | 采样背景亮度计算 |
| 动态投影（Dynamic Shadow） | ✅ 90% | 自行按背景明度插值 |
| 形状融合形变（Morph / Metaball） | ⚠️ 50% | 需 Path 插值近似，无原生 GlassEffectContainer |
| 系统级动效一致性 | ⚠️ 70% | 需自建动效规范 |

**务实目标：观感与交互语言对齐 92~95%（L3）/ 85~88%（L2），不追求像素级复刻。** 已于 2026-10-30 确认为验收口径；`minSdk 34` 后由初始的 85~90% 上调。

>> **该目标的可测量判据见 `docs/VISUAL_ACCEPTANCE.md`**：六维度加权评分（L3 目标 92~95% / L2 78~82% / L1 62~68%）、逐项测量方法、6 条一票否决红线、测试机型矩阵。**阶段 2 的验收以该文件为准，不以主观感受为准。**

---

## 1. 项目事实基线（可核查证据）

### 1.1 Git 与仓库

```
分支：main / workbuddy/main-ed162e29（当前）/ origin/main
远程：https://github.com/lunshi2022/xstz.git
提交历史（10 条）：
  de5591c 2026-09-15 refactor: 网络解析收口为纯解析器，再平衡计算迁移util并补齐核心测试  ← 未推送
  3cfca89 2026-09-15 perf: 自动刷新生命周期感知，Room批量写与Compose重组缓存            ← 未推送
  c901dba 2026-09-15 feat: 升级至v1.6.5-Beta，重构工具层与网络服务分层并固化AI协作基线   ← 未推送
  272cf67 2026-06-10 docs: 添加项目 README
  febda61 2026-06-09 feat: 升级至v1.6.0，引入Hilt依赖注入与项目初始化基线
  5e07bdf 2026-06-06 feat: 发布v1.4.2版本，新增日历分析页面并优化多项功能
  cfca028 2026-04-22 修复更新下载功能：使用应用私有目录避免权限问题
  2164544 2026-04-21 添加 MIT 许可证
  743fd4a 2026-04-21 初始提交：添加检查更新功能和修复设置界面布局
  e25d7de 2026-04-21 初始提交
origin/main 落后本地 3 个提交（0 ahead / 3 behind）
```

- 项目生命周期仅 5 个月（2026-04-21 起），属于个人快速迭代产物。
- 唯一 Release：`1.6.5`（2026-06-10），产物 `xstz-v1.6.5.apk`（6.39 MB），下载 2 次。
- 无 `.github/workflows`，无任何自动化门禁。

### 1.2 构建配置

| 项 | 当前值 | 位置 |
|---|---|---|
| AGP | 8.2.2 | `build.gradle.kts:3` |
| Kotlin | 1.9.22 | `build.gradle.kts:7` |
| KSP | 1.9.22-1.0.17 | `build.gradle.kts:10` |
| Gradle | 8.5（腾讯云镜像） | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / minSdk / targetSdk | 34 / **34** / 34 | `app/build.gradle.kts:10,20,21` |
| versionCode / Name | 166 / 1.6.5-Beta | `app/build.gradle.kts:22,23` |
| Java / JVM Target | 21 | `app/build.gradle.kts:44,49` |
| Compose Compiler | 1.5.8（旧式 `composeOptions`） | `app/build.gradle.kts:57` |
| Compose BOM | 2024.02.00 | `app/build.gradle.kts:87` |
| Release 签名 | **debug 签名** | `app/build.gradle.kts:35` |
| Lint | `abortOnError=false`、`checkReleaseBuilds=false` | `app/build.gradle.kts:67-68` |
| 本地 Maven | 硬编码 `D:/LeStoreSoftstore/.../.m2/repository` | `gradle.properties:18` |

### 1.3 目录职责映射（实测）

```
app/src/main/java/com/huaying/xstz/          文件  行数
├── MainActivity.kt                            1     319   ← Activity 承担导入/导出/Gson/加解密
├── InvestmentApp.kt                           1       6
├── core/init/                                 1      ~180
├── data/                                     26    2,550
│   ├── entity/     Fund / Transaction / NetValueRecord / TargetAllocation / OperationLog
│   ├── dao/        Fund / Transaction / NetValueRecord / TargetAllocation / OperationLog
│   ├── network/    FundNetworkService(接口) + TencentFundNetworkService + TencentApiParser(纯函数)
│   ├── converter/  AssetTypeConverter + EncryptedDoubleConverter
│   ├── repository/ Fund / Transaction / OperationLog / OperationLogger / Time / Holiday / Update
│   ├── security/   CryptoManager（AndroidKeystore AES-256-GCM）
│   └── AppDatabase.kt（v6，5 表，MIGRATION 1→6）
├── di/AppModule.kt                            1       ~55
├── ui/                                       51   17,886
└── util/                                      9    1,230
    ├── HoldingsCalculator / RebalanceCalculations（纯函数，有测试）
    ├── FormatUtils / FormatTemplates / AppConstants
    ├── DownloadUtil / InstallUtil / SntpClient
    └── performance/StartupTracer

app/src/test/                                  5 文件 / 99 个 @Test
app/src/androidTest/                           0
```

---

## 2. 核心功能模块与主要用途

| # | 模块 | 主要用途 | 关键实现 | 规模 | 保留价值 |
|---|------|---------|---------|------|---------|
| 1 | 资产概览 | 总资产/本金/累计收益/收益率看板；持仓列表含市值、占比、偏离度、再平衡状态；下拉刷新；隐私模式（金额打码） | `AssetOverviewScreen` + `AssetOverviewViewModel` + `DailyPnLCards` | 3,204 行 | ★★★★★ |
| 2 | 行情刷新 | 腾讯财经批量拉取；智能/固定频率（3s/5s）双模式；仅交易时段自动刷新；生命周期感知（后台降频） | `TencentFundNetworkService` + `TencentApiParser` | 181 行解析器 | ★★★★★ |
| 3 | 再平衡 | 两种策略（智能买卖 / 强制再平衡）+ 两种阈值模式（百分比 / 百分点）；无现金基金场景用迭代收敛求解；支持追加资金 | `RebalanceCalculations` + `RebalanceScreen` + `RebalanceViewModel` | 306+854+316 行 | ★★★★★ |
| 4 | 目标仓位 | 基金级目标占比配置、合计校验、预设比例 | `TargetAllocationScreen` + `TargetAllocationViewModel` | 543+180 行 | ★★★★☆ |
| 5 | 交易管理 | 买入/卖出/再平衡/追加/取出 五类流水；交易历史；记一笔联动更新份额与成本 | `Transaction` + `TransactionHistoryScreen` | 293 行 | ★★★★★ |
| 6 | 净值记录 | 按日快照总资产/本金/累计收益/收益率/四类资产市值，作为图表与日历的数据源 | `NetValueRecord` | 26 行 | ★★★★★ |
| 7 | 图表分析 | 收益走势 / 资产配置 双维度 × 周/月/年 三区间；采样降噪 | `OptimizedChartsScreen`（MPAndroidChart） | 1,364 行 | ★★★★☆ |
| 8 | 日历视图 | 月历展示每日盈亏；法定节假日/调休标注；当日明细 | `ModernCalendar` + `CalendarDayDetail` | 868+309+188 行 | ★★★★☆ |
| 9 | 交易时段判定 | A 股 9:15-11:30 / 13:00-15:00；周末与节假日休市；5 分钟缓存 | `TradingTimeChecker` + `HolidayRepository` | 223+~150 行 | ★★★★★ |
| 10 | 时间防篡改 | SNTP（阿里云 → pool.ntp.org → 淘宝 HTTP 三级降级）+ `elapsedRealtime` 基准 | `TimeRepository` + `SntpClient` | 194 行 | ★★★★☆ |
| 11 | 数据导入导出 | 全库 JSON 序列化 + AES-GCM 加密落盘；文件选择器导入 | `MainActivity:244-300` | 内嵌 60 行 | ★★★☆☆（逻辑需重做） |
| 12 | 操作日志 | 20+ 种操作类型，35 处埋点；页面浏览、按钮点击、设置变更、数据变更 | `OperationLog` + `OperationLogger` + `OperationLogScreen` | 664 行 | ★★★☆☆ |
| 13 | 在线更新 | GitHub Release API 检查 + APK 下载 + FileProvider 安装 | `UpdateRepository` + `UpdateViewModel` | 248 行 | ★★☆☆☆（渠道失效） |
| 14 | 主题系统 | 自动/浅/深三态；动态取色（Material You）；全色板 500ms 过渡动画 | `Theme.kt` + `Color.kt` | 380 行 | ★★☆☆☆（需重做） |
| 15 | 动效层 | 启动、加载、列表、交互、导航五类动画 | `ui/animation/**` | 2,843 行，**其中 5 文件零引用** | ★☆☆☆☆ |

**应用定位一句话**：一款面向个人投资者（作者自用）的**基金/股票持仓管理 + 目标仓位再平衡**工具，核心价值不在行情展示，而在「按目标仓位自动算出该买多少、该卖多少」。

---

## 3. 技术栈、依赖与代码结构的问题清单

### 3.1 P0 — 阻断级（必须解决，否则无法上线或存在数据风险）

| # | 问题 | 证据 | 后果 |
|---|------|------|------|
| P0-1 | **targetSdk=34 已无法提交 Play** | `app/build.gradle.kts:21` | 2026-08-31 起新应用/更新须 ≥36；现有应用须 ≥35 才对新用户可见。当前配置商店侧直接拒收 |
| P0-2 | **列级加密破坏 SQL 查询能力** | `EncryptedDoubleConverter.kt`（Double→AES→TEXT）+ `FundDao.kt:55-62` 三个 `SUM()` 查询 | `SUM(holdingQuantity * currentPrice)`、`SUM(totalCost)`、`getMarketValueByType()` 在 SQLite 侧对密文做算术，结果必然无效。目前靠 `HoldingsCalculator` 在应用层重算规避，这三个 DAO 方法是**定时炸弹**（任何人一调用就拿到错误金额） |
| P0-3 | **导出加密绑定本机 Keystore，换机必然导入失败** | `CryptoManager` 使用 `AndroidKeyStore` + 固定 alias 且密钥不可导出；`MainActivity:252` 用它加密导出文件 | 「数据备份」功能实际只能在本机导出/本机导入。换手机、重装、恢复出厂后**备份文件永久不可解**。这是备份功能的根本性失效 |
| P0-4 | **`allowBackup=true` 与 Keystore 加密冲突** | `AndroidManifest.xml` + 应用私有 DB 中金额为密文 | 云备份会保存密文，但 Keystore 密钥不参与备份 → 恢复后金额字段全部解不开，用户看到空数据。属于"看起来备份了，其实救不回来" |
| P0-5 | **Release 用调试签名** | `app/build.gradle.kts:35` | 无法正式分发；一旦换成正式签名，已装老版本无法覆盖升级，必须卸载重装（数据丢失风险） |
| P0-6 | **导入非原子 + 无校验 + 明文回退** | `FundRepository.kt:239` `clearAllTables()` 在事务外；`MainActivity:280` 解密失败回退按明文解析 | `clearAllTables` 之后进程被杀 → 数据全丢且无备份。解密失败静默按明文解析 → 任意 JSON 可注入库 |
| P0-7 | **全局允许明文流量** | `AndroidManifest.xml` `android:usesCleartextTraffic="true"` | 行情接口本身走 HTTPS，无此需求；放开后存在中间人风险，且违反应用商店安全基线 |

### 3.2 P1 — 架构级（显著影响可维护性，重建时必须换掉）

| # | 问题 | 证据 | 说明 |
|---|------|------|------|
| P1-1 | **导航架构反模式** | `MainScreen.kt:153-162` 四个 Tab 注册为空 `Box` 占位，真正内容由外层 `AnimatedContent` 叠加渲染 | 为了绕开 NavHost 的 `SizeTransform(clip=true)` 裁剪问题，放弃了 NavHost 的返回栈、状态保存、深层链接能力。代码注释自己也写了"避免 NavHost 内部 AnimatedContent 的 SizeTransform" —— 这是与框架对抗的典型信号 |
| P1-2 | **状态提升过度，Activity 承担业务** | `MainScreen` 单 composable 收 13 个参数（`MainScreen.kt:70-83`）；`MainActivity:244-300` 内联导出/导入/Gson/加解密 | 无法单测、无法复用、改一个功能要动三层 |
| P1-3 | **图表库与 UI 体系割裂** | MPAndroidChart v3.1.0（Java View）+ `AndroidView` 互操作，`OptimizedChartsScreen` 单文件 1,364 行 | View 体系无法继承 Compose 主题/材质/动画；玻璃材质做在 View 上几乎不可能（需自行抢 Canvas）；单文件千行不可维护 |
| P1-4 | **业务模型双轨制** | `Fund.targetRatio`（基金级）+ `TargetAllocation` 表（资产类别级：stock/bond/gold/cash） | 两套"目标仓位"并存，语义重叠；`TargetAllocation` 用 `goldRatio`，`AssetType` 却叫 `COMMODITY`，命名不一致；`NetValueRecord` 也写 `goldValue` |
| P1-5 | **无设计令牌层** | 145 处 `Color(0xFF...)`、113 处裸 `.sp`、80 处 `RoundedCornerShape(1x.dp)` | 无法支撑"Liquid Glass 材质系统"，也无法做主题一致性校验 |
| P1-6 | **文案零国际化** | `stringResource` 使用 **0 处**；22 个 UI 文件硬编码中文；`strings.xml` 内 200+ 条字符串完全闲置 | 无法出海、无法统一文案、无法做多语言测试 |
| P1-7 | **死代码与重复实现** | `ui/animation/**` 2,843 行中 `ModernSplashScreen`、`LoadingAnimations`、`ModernLoadingAnimations`、`InteractionAnimations`、`ListAnimations` **引用数为 0**；`ChartsViewModel` 与 `OptimizedChartsViewModel` 双份 | 约 1,500+ 行无效代码，增加认知负担与编译时间 |
| P1-8 | **构建不可移植** | `gradle.properties:18` 硬编码 `D:/LeStoreSoftstore/...`；`local.properties` 缺失；`JAVA_HOME` 系统变量指向不存在路径 | 换机/换人无法构建，与他人的"可复现构建"要求冲突 |
| P1-9 | **零 CI、零 Lint 门禁** | 无 `.github/workflows`；`lint.abortOnError=false` | 代码质量完全依赖作者当次自觉 |

### 3.3 P2 — 质量级（影响体验与长期成本）

| # | 问题 | 说明 |
|---|------|------|
| P2-1 | minSdk=34（Android 14） | 收窄到极小设备池；且 target=min=34 属"未做兼容设计"的偷懒配置 |
| P2-2 | 测试仅覆盖纯逻辑 | 99 个用例全在 `util`/`data.network`/`TradingTimeChecker`，0 个 DAO 测试、0 个 Compose UI 测试、0 个迁移测试 |
| P2-3 | 节假日依赖第三方 CDN | `HolidayRepository` 从 `unpkg.com/holiday-calendar` 拉数据；内置兜底表仅到 2027 年（`HolidayRepository.kt:148`）→ 2028 年起必然失效 |
| P2-4 | 更新检查禁用代理直连 GitHub | `UpdateRepository.kt:31` `Proxy.NO_PROXY` + `api.github.com` → 中国大陆网络环境下大概率超时失败；且 0 star 仓库本身不是可持续分发渠道 |
| P2-5 | `MIGRATION_1_2` 为空实现 | `AppDatabase.kt` 中 1→2 无任何 SQL，若历史版本有 schema 差异将导致崩溃；`exportSchema=false` 使迁移无法被验证 |
| P2-6 | 加密转换器错误兜底返回 null | `EncryptedDoubleConverter` 加解密失败返回 `null`，而 `Fund.holdingQuantity` 为非空 `Double` → 读取时可能 NPE 或静默变 0（金额显示为 0 的最坏情况） |
| P2-7 | 单文件超千行 | `OptimizedChartsScreen` 1364、`AssetOverviewScreen` 999、`FundDetailScreen` 931、`ModernCalendar` 868、`RebalanceScreen` 854 |
| P2-8 | 无崩溃上报、无分析埋点 | 仅本地 `OperationLog`，线上问题无法定位 |
| P2-9 | **版本比较逻辑必然误判** | `versionCode=166`，而 `UpdateRepository.parseVersionCode("1.6.5")` 按 `×100` 叠加算出 **10605** → `10605 > 166` 恒成立，即使已是最新版也每次启动都弹"发现新版本"（`UpdateRepository.kt:62-66, 135-145`）。两套版本号编码混用所致 |
| P2-10 | `ADD_FUNDS` 显示名错误 | `TransactionType.toDisplayName()` 把"追加资金"映射成"买入"，用户流水页看到错误的交易类型 |

---

## 4. 重开发总体方案

### 4.1 目标定义

| 维度 | 目标 |
|---|---|
| 功能 | 完整保留第 2 章 15 个模块的核心能力，剔除失效的在线更新与闲置动效 |
| 视觉 | iOS 26 Liquid Glass 观感对齐（见第 8 章），三档降级，中端机稳定 60fps |
| 工程 | 模块化、CI 门禁、单元测试覆盖核心算法 ≥80%、DAO/迁移测试全覆盖 |
| 分发 | 签名可正式发布、targetSdk 合规、可自建分发渠道 |
| 数据 | 老版本 v6 数据库可无损迁移到新版本 |

### 4.2 技术选型（2026-10 基线）

| 层 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 构建 | AGP | **9.2.x** | 支持 compileSdk 37；9.2 起 Compose 1.12 必需 |
| 构建 | Gradle | **9.4.1** | AGP 9.2 最低要求 |
| 构建 | JDK | **17** | AGP 9.2 最低/默认（21 亦可，但 17 兼容面更广） |
| 语言 | Kotlin + KGP | **2.2.10** | AGP 9.2 内置 KGP 基线 |
| UI | Compose BOM | **2026.09.00**（Compose 1.12.1） | 需 compileSdk 37 |
| UI | Compose Compiler | `org.jetbrains.kotlin.plugin.compose` | 取代旧式 `composeOptions.kotlinCompilerExtensionVersion` |
| 材质 | **Haze** | **2.0.1** | 目前唯一成熟的后台模糊方案；2.0 起内置 Glass（模糊+折射+染色+高光） |
| 图表 | **自绘 Canvas** | — | 已决策全部自绘（ADR-010）：Vico 的绘制层不接受外部 Shape 裁切，无法与玻璃卡片圆角统一；自绘可完全贴合材质 |
| 数据库 | Room + KSP2 | 2.7.x | 需 KSP2 |
| 数据库加密 | **SQLCipher for Android** | 最新 | 整库加密，保留 SQL 聚合/索引能力（替代列级加密） |
| DI | Hilt | 2.52+ | 保留原方案，学习成本为零 |
| 导航 | Navigation Compose + `@Serializable` | 2.8+ | type-safe routes，替代字符串 `Route` |
| 序列化 | kotlinx.serialization | 1.7+ | 替代 Gson，编译期生成，无反射/ProGuard 问题 |
| 偏好 | DataStore Preferences | 1.1+ | 保留 |
| 异步 | Coroutines + Flow | 1.9+ | `StateFlow` + `collectAsStateWithLifecycle` |
| 测试 | JUnit5 + Turbine + Robolectric + Compose UI Test | — | 补齐 DAO/迁移/UI 测试 |
| CI | GitHub Actions | — | `assembleDebug` + `testDebugUnitTest` + `lint` 三门禁 |

> **版本风险提示（2026-10-30 实测修订）**：两条轨道的 `compileSdk` 实际上都只能是 **37**。原因是 Haze 2.0.1 的 KMP 变体传递引入 `org.jetbrains.compose.foundation:1.12.1`，其映射的 androidx Compose 1.12.1 在 AAR metadata 中要求 `minCompileSdk=37`，Gradle「取最高版本」会覆盖 BOM 声明值。此前写的「退一档到 compileSdk 36」**经实测不可行**，已废弃。最终选定轨 B（AGP 9.1.1 + Compose BOM 2026.05.01 + compileSdk 37 + targetSdk 36 / minSdk 34），实测组合见 `rebuild/VERIFICATION.md` §6。

### 4.3 目标模块结构

```
:app                          仅 Activity + 导航宿主 + Application，无业务逻辑
:core:designsystem            ← Liquid Glass 材质系统、设计令牌、基础组件、动效规范
:core:model                   纯 Kotlin 数据模型（无 Android 依赖）
:core:domain                  纯 Kotlin 业务逻辑（HoldingsCalculator / RebalanceCalculations / 交易时段）
:core:data                    Room + SQLCipher + DataStore + 网络 + Repository
:core:network                 腾讯行情客户端 + 纯函数解析器（可单测）
:feature:overview             资产概览
:feature:charts               图表分析
:feature:rebalance            再平衡
:feature:target               目标仓位
:feature:fund                 基金详情 + 交易历史
:feature:calendar             日历
:feature:settings             设置 + 操作日志 + 关于
:feature:onboarding           引导 + 数据迁移
```

依赖方向严格单向：`feature:* → core:data → core:domain → core:model`；`feature:* → core:designsystem`；`core:* → core:model`。**禁止 `feature` 之间横向依赖**（当前 `ui/charts` 直接 import `ui/assetoverview/CalendarView`，就是这个问题的苗头）。

---

## 5. 需要保留的核心功能清单

### 5.1 P0 必保（缺一不可，首版必须全有）

- [x] **资产概览**：总资产 / 本金 / 累计收益 / 收益率 / 今日盈亏 / 持仓列表（市值·占比·偏离度·状态）
- [x] **行情刷新**：固定频率（3s/5s）+ 仅交易时段自动刷新 + 后台降频 + 手动下拉刷新
- [x] **行情解析**：腾讯 `qt.gtimg.cn` 接口，区分场内基金（`sh/sz` 前缀）、场外基金（`jj` 前缀）、股票（`sh/sz` 规则），保留 `TencentApiParser` 的字段索引与 18 个回归用例
- [x] **再平衡**：智能买卖 / 强制再平衡 双策略；百分比 / 百分点 双阈值模式；无现金基金迭代收敛；追加资金场景
- [x] **目标仓位**：基金级 `targetRatio` 配置、合计校验（100%）、预设比例快捷值
- [x] **交易管理**：五类流水（买入/卖出/再平衡/追加/取出）+ 交易历史 + 记一笔联动份额与成本
- [x] **净值记录**：按日快照（总资产/本金/收益/收益率/四类市值），作为图表与日历唯一数据源
- [x] **图表分析**：收益走势 / 资产配置 双维度 × 周/月/年
- [x] **日历视图**：月历每日盈亏 + 节假日标注 + 当日明细
- [x] **交易时段判定**：9:15-11:30 / 13:00-15:00，周末与法定节假日休市，5 分钟缓存
- [x] **时间防篡改**：SNTP 三级降级 + `elapsedRealtime` 基准（防止用户改系统时间刷收益）
- [x] **数据备份/恢复**：全库导出导入（**必须改为口令派生密钥，可跨设备**）
- [x] **隐私模式**：金额一键打码
- [x] **深色模式**：自动/浅/深
- [x] **数据迁移**：v6 → 新 schema 无损迁移

### 5.2 P1 次版必保

- [x] **操作日志**：保留 20+ 类型枚举与查询能力，但**降级为非核心功能**（当前 35 处埋点侵入 UI 过深，改为在 ViewModel/Repository 层记录，UI 层只记录页面曝光与显式操作）
- [x] **设置项**：刷新频率、主题、动态取色、初始投资日期、隐私模式、数据管理、关于
- [x] **引导页**：首次使用流程
- [x] **基金详情**：持仓明细、成本曲线、交易历史入口、清空现金账户
- [x] **手势与动效**：Tab 切换、页面推入/弹出、列表入场、下拉刷新

### 5.3 建议砍掉 / 替换

| 功能 | 处置 | 理由 |
|---|---|---|
| GitHub Release 在线更新 | **砍掉** | 0 star 仓库不是分发渠道；`Proxy.NO_PROXY` 在国内基本连不上；且应用商店自带更新。改为「检查更新跳转自建发布页」或直接移除 |
| `ui/animation` 中 5 个零引用文件 | **删除** | 1,500+ 行死代码 |
| 动态取色（Material You） | **降级为可选/移除** | 与 Liquid Glass 的自适应染色机制冲突：Material You 会把品牌色染成壁纸色，导致玻璃材质失去统一光感。保留手动主题色选择更可控 |
| `TargetAllocation`（资产类别级目标） | **合并到基金级** | 双轨制语义重叠。统一为「基金级 targetRatio 之和=100%」，资产类别占比由 `AssetType` 聚合派生（只读） |
| MPAndroidChart | **替换为自绘 Canvas** | 见 P1-3；玻璃卡片内需统一圆角裁切，Vico 做不到 |
| Gson | **替换为 kotlinx.serialization** | 见 4.2 |
| 列级加密 | **替换为 SQLCipher 整库加密** | 见 P0-2 |

---

## 6. 需要重构或替换的部分及理由

| 现状 | 目标 | 理由 | 影响面 |
|---|---|---|---|
| `EncryptedDoubleConverter` 列级加密 + 3 个 `SUM()` DAO | SQLCipher 整库加密（密钥由 Keystore 包裹的随机 DEK，或用户口令派生） | 列级加密使 SQL 聚合、索引、ORDER BY 全部失效，且每行每次过 Keystore。整库加密审计成本更低、查询能力完整 | `data/entity`、`data/dao`、`data/security`、`AppDatabase` |
| 导出用设备 Keystore 加密 | AES-256-GCM + **口令派生密钥（Argon2id 或 PBKDF2-HMAC-SHA256，≥60 万次）**，文件头带 `version + kdf + salt + nonce` | 当前方案绑定本机，换机无法恢复，备份功能形同虚设 | `data/backup`、`feature:settings` |
| 占位 NavHost + 外层 AnimatedContent 叠加 | 标准 NavHost + `@Serializable` type-safe routes + `AnimatedContentScope` 转场 | 恢复返回栈/状态保存/深链能力；转场交给 Nav 官方动画系统 | `:app`、所有 `feature:*` |
| `MainActivity` 内联导入导出 + Gson + 加解密 | 下沉到 `core:data/backup/BackupManager`（suspend + Flow 进度） | Activity 只做宿主；备份逻辑要可单测、要有进度与错误态 | `MainActivity`、`feature:settings` |
| MainScreen 单 composable 收 13 个参数 | 每个 feature 自带 `ViewModel` + `UiState`；共享数据用 `@Singleton` Repository 暴露 `Flow`，或 `CompositionLocal` 注入 | 消除参数透传地狱，feature 独立可测 | 全部 UI |
| MPAndroidChart + `AndroidView` | 自绘 Canvas（全部图表） | 继承 Compose 主题/材质；玻璃卡片内的图表才能统一圆角与裁切。Vico 的绘制层不接受外部 Shape，裁切不齐 | `feature:charts`、`feature:overview` |
| 145/113/80 处硬编码颜色/字号/圆角 | `core:designsystem` 设计令牌 + `MaterialTheme` 扩展（`LocalGlassTokens`） | 无令牌层 = 无法做材质系统，也无法保证深浅色一致 | 全部 UI |
| 硬编码中文 22 文件 | 全部 `stringResource`，`zh-rCN` 主 + `en` 副 | 可国际化、可统一文案、可做长度适配测试 | 全部 UI |
| `minSdk=34` / `targetSdk=34` | `minSdk=29` / `targetSdk=36`（轨 A）/ 36（轨 B） | 覆盖 Android 10+ 约 97% 设备；满足 Play 要求 | `app/build.gradle.kts` |
| Release = debug 签名 | 独立 keystore + `signingConfigs.release`（密钥走 CI Secret / 本地 `keystore.properties`，**不入库**） | 可正式分发、可覆盖升级 | `app/build.gradle.kts`、CI |
| `usesCleartextTraffic=true` | 移除；如确有 HTTP 需求用 `network_security_config.xml` 白名单 | 安全基线 | `AndroidManifest.xml` |
| `REQUEST_INSTALL_PACKAGES` + `DOWNLOAD_WITHOUT_NOTIFICATION` 权限 | **删除**（随"在线更新"一并砍掉） | 前者是 Play 敏感权限（个人开发者基本拿不到）、国内商店需书面说明；后者为非公开/已废弃权限。留着会在合规审核被直接卡住 | `AndroidManifest.xml` |
| `allowBackup=true` + 密文 | 若用 SQLCipher：`allowBackup=false` + 自建备份（推荐）；或配置 `dataExtractionRules` 排除 DB | 避免"备份了但恢复不了" | `AndroidManifest.xml` |
| `importAllData` 先 `clearAllTables` 再事务 | 单事务内 `DELETE`+`INSERT`，导入前校验 schema 版本与字段完整性，导入后校验条数 | 原子性；避免进程中断导致全库丢失 | `core:data` |
| `HolidayRepository` 依赖 unpkg CDN | 行情侧改为「交易日历表随版本内置 + 每年小版本更新」；或接入自有后端 | 第三方 CDN 随时变更/下线；内置表已只到 2027 | `core:data` |
| `MIGRATION_1_2` 空实现 + `exportSchema=false` | `exportSchema=true` + schema JSON 入库 + `MigrationTestHelper` 覆盖每条迁移 | 迁移可验证 | `core:data` |
| 无 CI | GitHub Actions：`assembleDebug` / `testDebugUnitTest` / `lintRelease` / `assembleRelease`（签名走 Secret） | 质量门禁 | 仓库根 |

---

## 7. 重新开发步骤与优先级

### 阶段 0 — 立项基线（1~2 天）｜P0

1. 新建仓库（或 `rebuild` 分支），确认 AGP/Kotlin/Compose 版本矩阵可编译（空壳工程跑通 `assembleDebug`）。
2. 建立 `gradle/libs.versions.toml` 版本目录；**禁止任何硬编码绝对路径**（`local.properties` 由本机生成，`.gitignore` 已忽略）。
3. 建立 GitHub Actions 三门禁流水线。
4. 配置 `signingConfigs.release`（密钥不入库），验证 `assembleRelease` 产物可安装。
5. **验收**：CI 绿；release APK 可装可启；`./gradlew lint` 无 error。

### 阶段 1 — 数据与领域层（1.5~2 周）｜P0

1. `:core:model`：迁移 5 个 Entity + 枚举，统一 `TargetAllocation` 为「基金级 targetRatio」单轨制；`AssetType.COMMODITY` 与 `goldValue` 命名统一。
2. `:core:domain`：**原样迁移** `HoldingsCalculator`、`RebalanceCalculations`、`TradingTimeChecker`，附上现有 99 个测试，并按覆盖率目标补到 ≥80%（重点补再平衡边界：无现金、全现金、目标占比为 0、份额非整手）。
3. `:core:data`：Room + SQLCipher 建库；`exportSchema=true`；写 `MIGRATION_V6_TO_V1`（新库版本号从 1 重新起算，但需能读 v6 旧库）。
4. `:core:network`：`TencentApiParser` 原样迁移（18 个用例是回归防线）；`FundNetworkService` 接口保留，实现改为 OkHttp + kotlinx.serialization。
5. 写 DAO 测试（Robolectric）+ 迁移测试（`MigrationTestHelper`）。
6. **验收**：旧库文件能迁移到新库且金额/份额/交易条数完全一致；核心算法测试全绿；SQLCipher 下 `SUM()` 查询结果正确。

> ⚠️ 阶段 1 是**唯一不可跳过、不可并行压缩**的阶段。数据层错了，后面全部返工。

### 阶段 2 — 设计系统与 Liquid Glass 材质（1.5~2 周）｜P0

1. `:core:designsystem`：颜色（语义令牌，双主题）、字体阶梯、间距/圆角/阴影令牌、动效令牌（时长/曲线）。
2. `GlassSurface` 材质组件（Haze 2.0 + AGSL 折射 + 高光层），三档降级（见第 8 章）。
3. 基础组件：`GlassCard`、`GlassButton`、`GlassTabBar`、`GlassNavBar`、`GlassSheet`、`GlassDialog`、`GlassSegmented`、`GlassListRow`。
4. 建立「材质预算」约束（同屏玻璃层数 ≤3、每层模糊半径 ≤24dp）并在 Debug 下加可视化计数。
5. 做 1 个样板页（资产概览）打通全链路，录屏评估帧率。
6. **验收**：中端机（如骁龙 7 系）在概览页滚动稳定 60fps；API 29 设备降级后视觉不塌；深浅色切换无闪烁。

### 阶段 3 — 功能逐页重建（4~5 周）｜P0

按「用户价值 × 依赖顺序」排：

| 序 | 页面 | 依赖 | 优先级 |
|---|---|---|---|
| 1 | 资产概览 + 持仓列表 | 阶段 1、2 | P0 |
| 2 | 添加基金 + 行情查询 | 网络层 | P0 |
| 3 | 交易录入 + 交易历史 | 概览 | P0 |
| 4 | 净值记录 + 图表分析 | 净值 DAO、自绘 Canvas（先静态后交互） | P0 |
| 5 | 再平衡 | 目标仓位、算法 | P0 |
| 6 | 目标仓位 | 概览 | P0 |
| 7 | 日历视图 | 净值 DAO | P1 |
| 8 | 设置 + 备份恢复（口令加密） | 数据层 | P0（备份） |
| 9 | 引导 + 数据迁移向导 | 全部 | P1 |
| 10 | 操作日志 | 概览 | P2 |

每页完成即做：Compose UI 测试（关键交互）+ 深色模式走查 + 小屏（360dp）/ 大屏（600dp）走查。

**验收**：功能对照清单（第 5.1 节）逐项打勾；无功能回归。

### 阶段 4 — 视觉打磨与动效（1~1.5 周）｜P0

1. 转场动效统一（Tab 切换的玻璃形变、页面推入的景深、Sheet 的材质升起）。
2. 交互反馈：按压的玻璃内发光、高光随滚动位移、加载骨架的折射。
3. 无障碍：`Reduce Transparency` / `Reduce Motion` / 高对比度三档适配（用系统设置联动）。
4. 对比度审计：所有文字在玻璃上≥4.5:1（大字号≥3:1）。
5. **验收**：无障碍检查通过；动效可全局关闭；对比度报告全绿。

### 阶段 5 — 发布准备（3~5 天）｜P0

1. 混淆规则重写（AGP 9 的 `-keepattributes` 语义已收紧，旧的 `-keepattributes *Annotation*` 不再保留 runtime-invisible 注解）。
2. `baseline-prof.txt`（Startup Profile + Baseline Profile），冷启动目标 <1.2s。
3. APK 体积审计（目标 <12MB，含 SQLCipher native lib 需注意 16KB page size 合规）。
4. 数据迁移演练：用真机 v1.6.5 库文件验证升级路径。
5. **验收**：release 包在 API 29 / 33 / 36 三档真机跑通全流程。

**总工期估算：约 9~12 周（单人全职）**；若两人并行（一人数据+领域，一人设计系统），可压到 7~8 周，但阶段 1 与阶段 2 必须先完成才可并行开工功能页。

---

## 8. iOS Liquid Glass 视觉对齐方案

### 8.1 设计原则（移植 Apple HIG 的三条硬规则）

1. **玻璃只用于导航层，不用于内容层。** 顶栏、底栏、Tab、悬浮操作、Sheet、对话框用玻璃；列表内容、图表、表单用不透明或半透明底。这条决定了整个 App 的层级感，违反即"廉价玻璃拟态"。
2. **玻璃不叠玻璃。** 玻璃卡片内放玻璃按钮 = 视觉噪音。玻璃上的按钮用实色填充或描边。
3. **同屏主玻璃面 ≤3 个。** 超出即降级为半透明实色。

### 8.2 材质参数矩阵（三档降级）

> **已修订**：`minSdk = 34`（ADR-002）使 `RenderEffect`（API 31+）与 AGSL `RuntimeShader`（API 33+）全设备可用，原本占 18% 的 `L1 Tint` 档彻底消失。矩阵由四档减为三档。

| 档位 | 触发条件 | 背景模糊 | 折射畸变 | 高光 | 染色 | 阴影 | 预期占比 |
|---|---|---|---|---|---|---|---|
| **L3 Full** | GPU 非低端且非低电量 | Haze 24dp + 噪点 | AGSL `RuntimeShader`（径向畸变 0.02~0.05） | 静态 135° 方向渐变 + 滚动联动 | 背景亮度采样 ±8% | 双层（环境+接触） | ~80% |
| **L2 降级** | `isLowRamDevice`、设备降频或低电量 | Haze 18dp | 无 | 静态斜向渐变 | 背景亮度采样 | 单层 | ~18% |
| **L0 Opaque** | 用户开 Reduce Transparency / 省电模式 | 无 | 无 | 无 | 实色 surface | 描边替代 | ~2% |

**必须实现运行时探针**：启动时读取 `ActivityManager.isLowRamDevice` + 首帧渲染耗时，自动选档。`Build.VERSION.SDK_INT` 无需再判断（minSdk 34 恒 ≥34）。设置项允许用户强制降档。

> **高光不使用陀螺仪**：已决策不做姿态响应（ADR-006）。Android 无系统级统一光场，传感器伪实现功耗高、收益微弱，且静态观感中几乎不可见。

### 8.3 设计令牌（示意，`core:designsystem`）

```kotlin
@Immutable
data class GlassTokens(
    val blurRadius: Dp,          // L3:24 / L2:18 / L1:0
    val tintLight: Color,        // 白 12%~18%（浅色主题）
    val tintDark: Color,         // 黑 18%~28%（深色主题）
    val refractionScale: Float,  // 0f / 0.03f
    val specularAlpha: Float,    // 0.0~0.22
    val borderWidth: Dp,         // 0.5dp（iOS 玻璃边缘有极细高光线）
    val borderLight: Color,      // 白 55%
    val borderDark: Color,       // 白 18%
    val shadowAmbient: Dp,       // 8dp
    val shadowSpot: Dp,          // 16dp
    val cornerRadius: Dp,        // 连续圆角：20dp（卡片）/ 28dp（Sheet）/ 999dp（Capsule）
    val noiseFactor: Float       // 0.03~0.06，消除渐变色带
)
```

**圆角必须用「连续曲率」（squircle）**，不是 `RoundedCornerShape` 的圆弧。iOS 的观感差异很大一部分来自这里。Compose 用 `RoundedCornerShape` 会明显"更方"，需自绘 `SquircleShape`（超椭圆采样）。

**指数取值必须按元素类型区分，不可混用**（此条已修正，原表述有误）：

| 元素 | 超椭圆指数 n | 说明 |
|---|---|---|
| UI 元素：卡片、列表容器、Sheet、对话框 | **2.8** | 与 `rebuild/SquircleShape.kt` 默认值一致 |
| 胶囊 / 全圆端控件 | 2.0（退化圆弧） | 高度的一半为半径 |
| App 图标底板 | 4.0~5.0 | **仅图标**，不得用于 UI 圆角 |

若把 4~5 用在卡片上，观感会明显偏方——比用圆弧错得更远。测量与验收方法见 `docs/VISUAL_ACCEPTANCE.md` §2-A。

### 8.4 组件映射表

| iOS 元素 | Android 实现 | 关键细节 |
|---|---|---|
| NavigationBar（顶部大标题） | `LargeTopAppBar` + `GlassSurface` | 滚动时标题缩放 + 玻璃边缘由无到有渐显（scroll edge effect） |
| TabBar（底部胶囊） | 自绘 `GlassTabBar` | **悬浮胶囊，不贴屏幕底边**（iOS 26 关键特征），距底部 8~12dp；选中项背景为一个小胶囊 |
| Toolbar 气泡化 | `GlassPillGroup` | 动作按钮组改为独立气泡，滚动时收起 |
| Sheet | `ModalBottomSheet` + 玻璃材质 | 升起时背景内容模糊 + 缩放 0.98 |
| Alert / Dialog | 自绘 `GlassDialog` | 玻璃面板宽度 ≤ 300dp，按钮区用分隔线 |
| Segmented Control | `GlassSegmentedControl` | 滑块为实色白胶囊在玻璃槽内位移 |
| Switch | 自绘 | iOS 尺寸 51×31dp，玻璃槽 + 白色圆钮 |
| List Row | 实色卡片 + 细描边 | **不用玻璃**（内容层） |
| 数字/金额 | SF Pro 风格数字等宽 | 用 `FontFeatureSetting("tnum")` 等宽数字，避免跳动 |

### 8.5 动效规范

| 场景 | 时长 | 曲线 |
|---|---|---|
| 按压反馈 | 100ms | 线性 |
| Tab 切换（含玻璃形变） | 300ms | `CubicBezierEasing(0.32, 0.72, 0, 1)`（iOS 标准 ease-out） |
| 页面推入 | 350ms | 同上 |
| Sheet 升起 | 400ms | 弹簧（damping 0.85, stiffness 300） |
| 高光随滚动位移 | 跟随 | 无独立时长，绑定 scroll offset |
| 数字变化 | 250ms | `FastOutSlowIn` |

**禁止**超过 400ms 的动效（当前代码 `AnimationConstants` 里存在 500ms 的颜色过渡，重做时收紧）。

### 8.6 必须写进代码的三条技术红线

1. **不用 `Modifier.blur` 做背景模糊。** 它模糊自身内容，把文字一起糊掉。必须用 Haze 的 source/effect 双端模式（`hazeSource` 标背景、`hazeEffect` 标玻璃层）。
2. **AGSL 折射必须 try-catch 兜底。** `RuntimeShader` 在部分 OEM ROM（尤其定制 GPU 驱动）上会编译失败或返回黑块，必须有 `isShaderAvailable()` 探针 + 降级。
3. **玻璃层不参与 `animateContentSize`。** 尺寸动画期间模糊采样区域会错帧，表现为闪烁。改为对 `Shape` 与 `padding` 分别动画。

---

## 9. 风险登记册

### 9.1 技术风险

| # | 风险 | 概率 | 影响 | 应对 |
|---|------|------|------|------|
| T1 | Haze + AGSL 在中低端机掉帧（<45fps） | 高 | 高 | 三档降级 + 材质预算 + 首帧耗时探针 + 低端机强制 L2 |
| T2 | `RuntimeShader` 在部分 OEM ROM 失效 | 中 | 中 | 启动时编译一个空 shader 做探针，失败即降档；不依赖折射做信息传达 |
| T3 | SQLCipher 引入 native lib，16KB page size 不合规 | 中 | 高 | 选 4.7+ 版本并核对其 16KB 支持；CI 加 `check_elf_alignment.sh`；不合规则退回「仅加密备份、DB 明文 + 设备加密兜底」 |
| T4 | Compose 1.12 / AGP 9 生态兼容坑（R8 规则、KSP2、Hilt） | 中 | 中 | 阶段 0 先跑通最小依赖集；锁定版本目录；不在阶段 3 中途升版本 |
| T5 | 图表全自绘工作量大，交互（双指缩放/长按十字线）成本高于预估 | 中 | 中 | 阶段 3 拆两批交付：先静态渲染（走势+饼图），交互后置；每批单独验收，不阻塞后续页面 |
| T6 | 旧库迁移丢数据 | 低 | 极高 | 迁移前强制自动备份到 `getExternalFilesDir`；迁移失败回滚并不删旧库；迁移前后条数与金额校验 |
| T7 | 玻璃材质导致文字对比度不足（WCAG 不达标） | 中 | 中 | 玻璃层最小染色下限（浅色主题≥12% 白，深色≥18% 黑）+ 自动对比度检测 CI 步骤 |

### 9.2 兼容性风险

| # | 风险 | 说明 | 应对 |
|---|------|------|------|
| C1 | `minSdk 34` 后覆盖约 45% 设备 | Android 13 及以下用户无法安装（`INSTALL_FAILED_OLDER_SDK`），本项目自用 + 开源分发，已接受 | 在 README 与 Release 说明中明确标注「需 Android 14+」；如日后要提高覆盖，需重新引入 `L1 Tint` 档并把视觉目标降回 88~92% |
| C2 | 深色模式下玻璃"脏" | 纯黑背景 + 玻璃 → 灰蒙蒙一片，失去通透感 | 深色主题背景用 `#0A0A0C` 而非纯黑，并给背景加极淡的品牌色光晕（模拟 iOS 的壁纸透光） |
| C3 | 折叠屏/平板布局 | 当前代码全按手机竖屏写死（大量 `120dp`/`140dp` 硬编码偏移） | 用 `WindowSizeClass` 三档布局；玻璃组件在宽屏下改为侧边栏形态 |
| C4 | 手势冲突 | 玻璃卡片 + 横向滑动（日历、图表）+ 系统返回手势 | 玻璃容器不拦截边缘 20dp 内的触摸；图表手势用 `nestedScroll` 而非 `pointerInput` 抢占 |
| C5 | 字体 | iOS 观感依赖 SF Pro，Android 无授权 | 用 `Inter` / `MiSans` 做近似（几何骨架接近）；数字用等宽特性；**不要**直接打包 SF Pro（授权风险） |

### 9.3 维护性风险

| # | 风险 | 应对 |
|---|------|------|
| M1 | 单人维护 + 设计系统复杂度高 → 再次烂尾 | 设计系统先行冻结（阶段 2 结束后不再加新组件）；组件数上限 12 个；每个组件必须有 Preview + 截图回归 |
| M2 | 行情接口（腾讯 `qt.gtimg.cn`）为未公开接口，随时可能变更或封锁 | 解析器独立成纯函数模块 + 契约测试（固定样本字符串）；接口层做 Provider 抽象（预留新浪/东财实现）；解析失败上报而非静默 |
| M3 | 交易日历需每年更新 | 内置表 + 每年 1 次小版本；或改为「首次启动拉取 + 失败用内置」 |
| M4 | 依赖大版本升级（AGP 每年 1~2 次） | 版本目录集中管理；每季度一个"只升依赖"的小版本，不夹带功能 |
| M5 | 无用户反馈渠道 → 问题发现慢 | 接入轻量崩溃上报（或自建）；保留操作日志本地导出用于排障 |

### 9.4 合规风险

| # | 风险 | 应对 |
|---|------|------|
| R1 | 金融类应用商店审核 | 定位为「个人记账工具」，**不得**出现"投资建议""推荐买入"等表述；再平衡结果措辞改为"按你设定的目标占比计算出的调整量" |
| R2 | 数据安全 | 备份文件口令加密 + 明示"口令丢失无法恢复"；隐私政策说明数据仅存本地 |
| R3 | 字体/图标授权 | 仅用开源字体（Inter/MiSans）+ 自绘图标或 Material Symbols |
| R4 | 16KB page size（2027-02-01 起 Play 对 64 位 native 库的要求） | 若引入 SQLCipher，必须核对其 ELF 对齐；纯 Kotlin 部分不受影响 |

---

## 10. 注意事项（Do / Don't）

### Do

1. **先冻结数据模型，再动 UI。** 阶段 1 不通过，不要开始阶段 2。
2. **把 99 个现有测试搬过来当回归网。** 尤其 `TencentApiParserTest`（18 例，字段索引一变就挂）和 `RebalanceCalculationsTest`（8 例，收敛逻辑）。
3. **备份功能第一版就必须可跨设备。** 这是老项目最大的隐性 bug，不要继承。
4. **玻璃材质先做降级，再做效果。** 先让 L1/L0 好看，再叠 L3 的折射高光。
5. **每个 feature 模块独立可运行。** 用 `:feature:xxx` 的 `debug` 变体 + 独立 Activity 做单页调试，避免又回到"改一个页面要跑整个 App"。
6. **金额一律用 `BigDecimal` 或整型分。** 当前全 `Double` + AES 字符串化，再平衡累加误差会随时间放大。建议 DB 存 `Long`（分/厘），展示层转 `BigDecimal`。
7. **迁移前自动备份。** 升级到新版首次启动时，把旧库复制一份到私有目录并保留 30 天。
8. **图表自绘分批交付。** 先静态渲染，后交互。复杂度集中在触控命中与坐标映射，要尽早暴露，不要留到最后。

### Don't

1. ❌ 不要在玻璃上再叠玻璃。
2. ❌ 不要把玻璃用在列表项、表单、图表容器上。
3. ❌ 不要用 `Modifier.blur` 做背景模糊。
4. ❌ 不要保留 Material You 动态取色（与玻璃染色机制冲突）。
5. ❌ 不要继续用设备 Keystore 给导出文件加密。
6. ❌ 不要保留 `usesCleartextTraffic=true`。
7. ❌ 不要把 Release 签名指向 debug。
8. ❌ 不要在 `gradle.properties` 写死本机绝对路径。
9. ❌ 不要为了"像 iOS"打包 SF Pro / 使用 Apple 图标资源。
10. ❌ 不要承诺"完全对齐 iOS"。承诺"同源设计语言的高保真实现"，并公开降级矩阵。

---

## 11. 验收清单（交付前逐项打勾）

**功能**
- [ ] 第 5.1 节 P0 功能全部可用，无回归
- [ ] 老版本 v1.6.5 数据库升级后，基金数/交易数/总资产/收益率与升级前一致
- [ ] 备份文件在另一台设备可成功恢复（**跨设备验证**）
- [ ] 断网、接口返回异常、代码不存在、节假日跨年 四类边界均不崩溃

**视觉**
- [ ] 三档降级在 API 34 / 36 真机均符合设计稿（`L3 Full` / `L2 降级` / `L0 Opaque`）
- [ ] 同屏玻璃层 ≤3；滚动稳定 60fps（中端机）
- [ ] 所有文字在玻璃上对比度 ≥4.5:1
- [ ] 圆角为连续曲率（squircle），非圆弧
- [ ] 深浅色切换无闪烁、无中间态跳色

**工程**
- [ ] CI 三门禁绿（build / test / lint）
- [ ] 核心算法测试覆盖率 ≥80%
- [ ] 每条 Room 迁移有 `MigrationTestHelper` 用例
- [ ] `assembleRelease` 产物可安装、可覆盖升级、体积 <12MB
- [ ] 冷启动 <1.2s（Baseline Profile 生效）
- [ ] `minifyEnabled=true` 下无 ClassNotFound / 反射失败

**合规**
- [ ] `targetSdk=36`（或按轨 A 的 37）
- [ ] 无明文流量
- [ ] 无硬编码密钥，签名文件不入库
- [ ] 文案无"投资建议"类表述

---

## 附录 A：版本轨道（已选定轨 B，2026-10-30 实测通过）

| | 轨 A（激进，备查） | **轨 B（已选定，已实测）** |
|---|---|---|
| AGP | 9.2.0 | **9.1.1** |
| Gradle | 9.4.1 | **9.4.1**（镜像无 9.1.1 发行包；AGP 9.1.1 最低要求 9.1，兼容） |
| Kotlin | 2.3.21（classpath 钉住） | **2.3.21**（AGP 9.1.1 内置仍为 2.2.10，须显式抬） |
| KSP | 2.3.12 | **2.3.12** |
| compileSdk | 37 | **37**（由 Haze 传递依赖强制，见 ADR-017） |
| targetSdk / minSdk | 36 / 34 | **36 / 34** |
| Compose BOM | 2026.09.00 | **2026.05.01**（实际解析为 compose-ui 1.12.1） |
| Hilt / Haze / Room | 2.60.1 / 2.0.1 / 2.7.2 | 同左 |
| 实测结果 | BUILD SUCCESSFUL | **BUILD SUCCESSFUL（41 tasks / 41s）** |
| 退轨成本 | — | 改 2 行版本号 |

> **关键修正**：原以为轨 B 可用 `compileSdk 36`，实测编译失败。根因是 Haze 2.0.1 的 KMP 变体传递引入 `org.jetbrains.compose.foundation:1.12.1`，其对 androidx Compose 1.12.1 的映射要求 AAR metadata `minCompileSdk=37`。**因此 compileSdk 37 与轨道选择无关，是 Haze 的硬约束**，除非放弃背景模糊。

## 附录 B：数据迁移映射（v6 → 新 schema）

| 旧表 | 新表 | 处理 |
|---|---|---|
| `funds` | `fund` | 金额字段由「AES 字符串」解密为 `Long`（分）；`preClosePrice` 保留；`targetRatio` 统一为 0~1 小数 |
| `transactions` | `transaction` | `TransactionType` 五值保留；`ADD_FUNDS` 显示名由"买入"改回"追加资金"（原实现有语义错误，`toDisplayName()` 把 ADD_FUNDS 映射成"买入"） |
| `net_value_records` | `net_value_snapshot` | `goldValue` 改 `commodityValue` |
| `target_allocation` | **废弃** | 资产类别目标并入 `fund.targetRatio`；如需保留资产类别视角，改为只读派生 |
| `operation_logs` | `operation_log` | 保留，`OperationType` 枚举去除已废弃值（`VIEW_DETAIL`→`PAGE_VIEW` 等已在 MIGRATION_3_4 处理） |

> 迁移实现要点：先用旧 `CryptoManager`（同一 alias，同一设备）解密旧库金额，再写入新库。若在**另一台设备**上迁移则无解——这也是必须在升级向导里提示"请先在本机完成升级再考虑换机"的原因。

## 附录 C：可直接落地的骨架文件

- `docs/rebuild/libs.versions.toml` — 版本目录（轨 A，含降级注释）
- `docs/rebuild/settings.gradle.kts` — 仓库顺序修正（官方前置）+ 多模块骨架
- `docs/rebuild/gradle.properties` — 内存/配置缓存修正，标注 4 项应删除的旧配置
- `docs/rebuild/build.gradle.kts` — 根构建脚本（插件全部走版本目录）
- `docs/rebuild/app.build.gradle.kts` — 模块构建脚本（修掉 6 个"升级即炸"的配置点）
- `docs/rebuild/GlassSurface.kt` — Liquid Glass 材质组件骨架（含三档降级与 shader 探针；**待按 Haze 2.0 API 重写**）
- `docs/rebuild/SquircleShape.kt` — 连续曲率圆角（iOS 观感关键）

> 构建脚本未在本机编译验证（无 JDK / Android SDK）。落地时先跑 `./gradlew help` 验证脚本可解析，再跑 `assembleDebug`。
> 依赖别名一致性已用脚本比对 `libs.versions.toml`，全部匹配。
