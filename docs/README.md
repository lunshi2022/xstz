# 项目重开发方案 · 文档索引

> **当前状态**：全部决策已锁定，构建配置**已实测编译通过**（轨 B）。下一步为阶段 0：铺多模块骨架。
>
> **最后更新**：2026-10-31 00:15

---

## 按你的时间选阅读路径

### 只有 3 分钟 → 读这两段

1. `REBUILD_PLAN.md` **§0 结论摘要**（废弃判定 + Liquid Glass 可行性）；
2. `VISUAL_ACCEPTANCE.md` **§0 一句话结论**（L3 可达 92~95%，L2 85~88%）。

### 有 30 分钟 → 按此顺序

1. `REBUILD_PLAN.md` 全文 —— 主方案；
2. `VISUAL_ACCEPTANCE.md` §1~§3 —— 验收模型与 6 条红线；
3. `ADR.md` —— 17 条决策及连带后果（重点 ADR-017：compileSdk 必须 37）；
4. `rebuild/VERIFICATION.md` §6 —— 轨 B 实测报告。

### 要动手实施 → 再看骨架代码

- `rebuild/libs.versions.toml`、`settings.gradle.kts`、`build.gradle.kts`、`app.build.gradle.kts`、`gradle.properties` —— **已实测可编译**；
- `rebuild/SquircleShape.kt` —— 连续曲率圆角，可直接用；
- `rebuild/GlassSurface.kt` —— ⚠️ 需按 Haze 2.0 API 重写；
- `rebuild/VERIFICATION.md` —— 实测报告，含 8 个踩过的坑。

---

## 文件清单

| 文件 | 作用 | 行数 | 状态 |
|---|---|---|---|
| `REBUILD_PLAN.md` | **主方案**：废弃判定、功能清单、技术选型、实施步骤、风险、验收 | 630 | ✅ 可交付 |
| `VISUAL_ACCEPTANCE.md` | **视觉验收标准**：六维度加权、测量方法、6 条红线 | 253 | ✅ 已按 minSdk 34 上调目标 |
| `ADR.md` | **决策记录**：17 条决策逐条写明理由与负面后果 | 352 | ✅ 已补齐全部条目 |
| `DELIVERY_GAP_ANALYSIS.md` | 交付缺口复核、决策对账、实测进展 | 190 | ✅ 可交付 |
| `rebuild/VERIFICATION.md` | 实测报告（轨 A + 轨 B 两次，真跑过） | 216 | ✅ 可交付 |
| `rebuild/libs.versions.toml` | 版本目录（轨 B，已移除 Vico / splashscreen） | 109 | ✅ 实测通过 |
| `rebuild/settings.gradle.kts` | 仓库配置（官方源前置） | 46 | ✅ 可直接用 |
| `rebuild/build.gradle.kts` | 根脚本（Kotlin classpath 钉版） | 22 | ✅ 实测通过 |
| `rebuild/app.build.gradle.kts` | 模块脚本（签名注入、lint 门禁） | 202 | ✅ 实测通过 |
| `rebuild/gradle.properties` | 堆内存 4G、配置缓存 | 33 | ✅ 可直接用 |
| `rebuild/SquircleShape.kt` | 连续曲率圆角（n=2.8，LRU 缓存） | 171 | ✅ 可直接用 |
| `rebuild/GlassSurface.kt` | 玻璃材质骨架 | 310 | ⚠️ **唯一未完成项**：需按 Haze 2.0 重写 |

> 合计 2,534 行（不含本文）。行数为 2026-10-31 00:22 实测值。

---

## 17 条决策（16 条已锁定 + 1 条待确认，详见 ADR）

### 技术栈

| 决策 | 结论 |
|---|---|
| **版本轨道** | **轨 B**：AGP 9.1.1 + Gradle 9.4.1 + Kotlin 2.3.21 + KSP 2.3.12 + Compose BOM 2026.05.01 |
| **SDK 级别** | **compileSdk 37 / targetSdk 36 / minSdk 34**（Android 14+） |
| **compileSdk 为何是 37** | 不是选择，是 **Haze 2.0.1 传递引入 JetBrains Compose 1.12.1** 带来的硬约束（ADR-017） |
| **实测状态** | `assembleDebug` **BUILD SUCCESSFUL**（41 tasks / 41s） |

### 产品与分发

| 决策 | 结论 |
|---|---|
| 分发渠道 | 开源仓库 + GitHub Release（无需备案/软著） |
| 包名 | 沿用 `com.huaying.xstz`（3 条已知后果见 ADR-007） |
| 存量数据 | 无 → 删除全部迁移逻辑，阶段 1 压到 3~4 天 |
| 语言 | 仅简体中文，资源结构按可国际化设计 |
| 图表 | **全部自绘 Canvas**，已移除 Vico |
| 备份加密 | 口令派生密钥（Argon2id） |
| 崩溃诊断 | **零第三方 SDK**：`ApplicationExitInfo` + 本地日志 + 导出诊断包 |
| 动态取色 | 移除 Material You |
| 数据源 | 保留接口抽象，腾讯为默认实现 |

### 视觉与交付

| 决策 | 结论 |
|---|---|
| **视觉验收** | 观感对齐 **92~95%**（L3）/ 85~88%（L2），六维度加权 + 6 条红线 |
| 玻璃降级 | **三档**（L3 Full / L2 降级 / L0 Opaque），L1 因 minSdk 34 消失 |
| 仓库处置 | 沿用现有仓库，**先本地重建、远程暂不动**，旧代码已打 tag `legacy/pre-rebuild` |
| 开发方式 | **由 AI 全程开发**，分批交付；你负责真机验收、keystore、推送、签字 |
| 排期 | 等效 2~3 周工作量，含验收往返实际 **3~4 周** |

---

## 阶段计划

| 阶段 | 内容 | 工期 | 关键产出 |
|---|---|---|---|
| **0（下一步）** | 13 个模块骨架 + `build-logic` 约定插件 + CI + `.gitignore` | 约 1 天 | 可编译的空骨架 |
| 1 | 数据与领域层：SQLCipher、金额存 Long（分）、搬迁 99 个测试 | 3~4 天 | `core:model` / `core:data` |
| 2 | 设计系统与玻璃材质（含 `GlassSurface` 重写） | 1.5 周 | `core:designsystem`，达验收基线 |
| 3 | 9 个 feature 逐页重建 | 4~5 周 | 全部页面 |
| 4 | 视觉打磨与动效 | 1~1.5 周 | 达 92%+ |
| 5 | 发布准备（签名、隐私页、R8） | 3~5 天 | 可发布 APK |

---

## 三个仍待你回答的小问题（不阻塞开工）

| # | 问题 | 何时需要 |
|---|---|---|
| 1 | 手上有哪几档测试机（现在只剩 L3 / L2）？ | 阶段 2 开始前 |
| 2 | 是否有 iPhone 可用于动效逐帧对照？ | 阶段 2 |
| 3 | 自持 keystore 打算怎么离线备份？ | 阶段 5 签名前 |

---

## 环境位置

```
C:\Users\21063\toolchains\          共 1501 MB（用户级，不在仓库内，所有项目可复用）
├── jdk17\              304 MB   Temurin 17.0.20.1
├── android-sdk\        719 MB   platforms android-36 / android-37.0
├── jdk17.zip           182 MB   ← 可删
├── cmdline-tools.zip   147 MB   ← 可删
└── probe\              150 MB   ← 临时验证工程，可删
```

`local.properties` 的 `sdk.dir` 指向 `C:/Users/21063/toolchains/android-sdk`。

---

## 一条提醒

`rebuild/GlassSurface.kt` 是全部交付物中**唯一未通过编译验证**的文件（其余配置已在轨 B 下跑通 `assembleDebug`）。它的三档降级策略、AGSL 探针、同屏玻璃层预算三块设计仍然成立，但渲染层必须按 Haze 2.0 重写：

```kotlin
Modifier.hazeBlur(
    input = HazeInput.Backdrop(state),
    style = CupertinoMaterials.regular()
)
```

重写时机是阶段 2 第一周，届时配 `VISUAL_ACCEPTANCE.md` 一起做验收。
