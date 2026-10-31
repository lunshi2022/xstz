# 重建进度（2026-10-31 18:23 更新）

## 已完成并验证

| 阶段 | 内容 | 验证方式 | 状态 |
|---|---|---|---|
| 0 | 14 模块骨架、Gradle 9.4.1 + AGP 9.1.1 + Kotlin 2.3.21 | `:app:assembleDebug` BUILD SUCCESSFUL | ✅ |
| 1 | model / common / domain / database / network / data | **137 个单测全通过** | ✅ |
| 2 | 设计系统：超椭圆、玻璃材质、色彩/字体令牌、GlassCapability | 模拟器实测截图 | ✅ |
| 3 | **五个页面全部完成**：概览 / 持仓 / 走势 / 再平衡 / 设置 | 每页截图 + 数值复算 | ✅ |
| 3.5 | **组合级快照 + 收益走势 / 资产配置 / 盈亏日历 / 单只标的四维度** | 137 单测 + 模拟器截图 | ✅ |
| 4 | 视觉打磨与无障碍 | WCAG 对比度审计全绿 + 系统减弱动效/透明度联动 + 转场动效门控 + contentDescription | ✅ |
| 5 | 发布准备（R8 / baseline profile / 签名 release） | baseline profile 生成模块 + 种子 baseline-prof.txt + CI YAML | 🟡 |

## 本轮（G1 + S5 + S6）改动

### G1 组合级每日快照表（原审计中"卡死两个页面"的缺口）

| 项 | 内容 |
|---|---|
| 表 | `net_value_snapshot`（v3 迁移 `MIGRATION_2_3`） |
| 字段 | `epoch_day` / `total_value` / `principal` / `day_profit` / `stock_value` / `bond_value` / `commodity_value` / `cash_value` |
| 生成 | `SnapshotBuilder` 从 `trades` + `nav_history` **逐日重放**份额与成本，不用总资产差值 |
| 回填 | `SnapshotWriterImpl` 首次进入组合维度时从网络拉最近 **365 天**净值 → 落 `nav_history` → 重放 |
| 数据源 | 场外 `api.fund.eastmoney.com/f10/lsjz`（分页 20 条）；场内 `web.ifzq.gtimg.cn/appstock/app/fqkline/get`（`qfqday[2]` = 收盘价） |

### S5 玻璃层级（`VISUAL_ACCEPTANCE.md` §4）

- Tab 栏距**安全区底边** 10dp（实测 y=2311，导航栏 inset 63px=21dp）
- Tab 选中项新增小胶囊：实测 40×31dp，alpha 0.14 的 accent 底
- Tab 栏本身仍是玻璃（导航层），符合"玻璃仅用于导航层"

### S6 内容层去玻璃（红线 R3）

新增 `core/ui/SolidCard.kt`，把 **13+5+3+3+3+2+2=31 处** `GlassPanel` 换成不透明实色卡：

| 文件 | 替换数 |
|---|---|
| `ChartsScreen.kt` | 13 |
| `HoldingDetailScreen.kt` | 5 |
| `OverviewScreen.kt` / `RebalanceScreen.kt` / `SettingsScreen.kt` | 各 3 |
| `AddHoldingScreen.kt` | 2 |
| `HoldingsScreen.kt` | 2（汇总卡 + 持仓行） |

判定依据：**会跟着内容滚动的是内容层**（列表行、图表、统计面板），不滚动的是导航层（Tab、顶栏、Sheet）。顺带移除了 6 个页面已无用的 `hazeState` 参数。

### 日历可读性修复（截图反推发现的真实缺陷）

初版日格 alpha = `0.10 + ratio*0.45`（封顶 0.55）。截图反推实际渲染 alpha 仅 **0.076~0.152**，在 `#F7F9FC` 浅卡片上只差 2 个色阶，"日历"退化成淡灰数字。
改为 `0.20 + ratio*0.52`（封顶 0.72）。

### #12 对比度 / 无障碍 / 转场（验收缺口补齐）

| 项 | 内容 | 位置 |
|---|---|---|
| WCAG 对比度审计单测 | `core/designsystem/src/test/.../ContrastAuditTest.kt`，6 用例（浅/深两套 palette × 三档文字色 × 实卡涨跌色 × 白字 Accent），全绿 | `core:designsystem:testDebugUnitTest` |
| 色板修复（审计抓出的真实缺陷） | `Accent` `#3A6FF7`→`#3568EA`（白字 4.38→4.86）；浅色 `textTertiary` `#6B7688`→`#65707F`（4.27→4.77）；`Down` `#12A67B`→`#0F9E73`（浅卡 2.94→3.23 大字达标） | `XstzColors.kt` |
| 系统级「减弱动效」真正联动 | `SettingsRepository.observeReducedMotion()` 监听 `ANIMATOR_DURATION_SCALE==0`（开发者选项/无障碍「移除动画」），`ShellViewModel` 不再把透明度当动效源 | `SettingsRepositoryImpl.kt` / `MainActivity.kt` |
| 动效全局关闭门控 | `XstzApp` 用 `AnimatedContent`（Tab 切换）+ `AnimatedVisibility`（覆盖层）包裹，二者均受 `motion.animationsEnabled` 门控；`OverviewScreen` 的 `AllocationRing` 入环动画从空操作 `1f else 1f` 修正为 `0f→1f`，时长收到 Motion.TAB_MS | `XstzApp.kt` / `OverviewScreen.kt` |
| contentDescription | `AddHoldingScreen` 选中行 `Check` 图标补 `contentDescription="已选中"` | `AddHoldingScreen.kt` |
| 系统级「高对比度」联动 | `SettingsRepository.observeHighContrast()` 监听 `Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST`（无公开常量，用字符串 `"high_text_contrast"`）；开启时 `ProvideXstzTheme` 把最弱文字（textTertiary/flat）在浅色加深到 secondary、深色提亮到 primary，对比度再推一档 | `SettingsRepositoryImpl.kt` / `XstzTheme.kt` / `MainActivity.kt` / `XstzApp.kt` |

### #13 baseline profile / CI（验收缺口补齐）

| 项 | 内容 | 位置 |
|---|---|---|
| 生成模块 | `benchmark`（`com.android.test` 插件），`BaselineProfileGenerator` 启动 App → 等待首屏 → 滑动 → 切 Tab 收集规则；`app` 与所有库模块补同名 `benchmark` buildType（`matchingFallbacks`，避免变体不匹配） | `benchmark/`、`app/build.gradle.kts`、`build.gradle.kts` |
| 种子 profile | `app/src/main/baseline-prof.txt` 内置 `MainActivity`/`XstzTheme`/`XstzType`/`XstzColors` 的 HSPL/HPL 规则，注释说明完整 profile 需设备生成 | `app/src/main/baseline-prof.txt` |
| 依赖 | `libs.versions.toml` 加 `android-test` / `benchmark-macro` / `uiautomator` / `lifecycle-process`；`profileinstaller` 此前已依赖 | `gradle/libs.versions.toml` |
| CI | YAML 已写（lint + 单测 + assembleRelease + 可选 baseline profile），未启用 | 仓库根 CI 配置 |

## 产物

- `artifacts/apk/xstz-2.0.0-debug.apk`
- `tools/v2-overview.png` / `v2-charts-profit.png` / `v2-charts-calendar.png`：本轮三个维度截图
- package `com.huaying.xstz.debug`，label「行时投资」

## 数值正确性（可复算）

- 快照表 180 条，覆盖 2026-05-05 ~ 2026-10-31
- BALANCED 策略：买入 8,202.25 + 7,925.01 = 16,127.26 = 卖出额
- 环形图股票 51.28% = 沪深300 37.73% + 中证500 13.56%
- 涨跌色遵循 A 股习惯（红涨绿跌）

## 已知偏差与限制

1. **模拟器无实时模糊**：软件渲染下 hazeBlur 输出不透明近似色，降级为半透明渐变。**真机才走 RenderEffect 真模糊**。S6 之后内容层已不需要模糊，此限制只剩导航层。
2. **模拟器不稳定**：qemu 37.2.13 高负载下 `0xc0000005` 崩溃（本轮又 3 次，恒为 pid 9388 的 0MB 僵尸，无权限终止）。`uiautomator dump` 是最强触发源，已改为纯 `screencap` + 像素分析。
3. **日历格子的像素级验收未完成**：alpha 已确认提升，但因模拟器崩溃，未取到"改进后"的对照截图。
4. **阶段 5 部分完成**：R8 已在 release buildType 开启；`benchmark` 模块（com.android.test）已可编译，能产出 baseline profile（需真机/模拟器跑 `generateBaselineProfile`）；`app/src/main/baseline-prof.txt` 已内置种子规则；CI YAML 已写但未启用。签名 release 仍依赖用户提供密钥（无密钥时回退 debug）。

## 环境限制

- JDK：`JAVA_HOME` 必须是 `C:\Users\21063\.jdks\jbr-17.0.14`（JBR 17.0.14；`local.properties` 里没有，Gradle 默认值指向已不存在的 Android Studio JBR）。所有 gradle 命令均加 `-Xmx2g --max-workers=2` 内存上限，避免触达重启红线。
- 启动模拟器：`explorer.exe` 打开 bat
- 截图：`exec-out screencap` 可用，但 `uiautomator dump` 会杀 qemu
- 数据库检查：`run-as ... cat databases/xstz.db` 导到项目 `build/dbcopy/` 再查（`/tmp` 在 Git Bash 下不可写）
