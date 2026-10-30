# 构建验证报告（实测）

> 环境：Windows 10/11 · Temurin JDK 17.0.20.1 · Gradle 9.4.1 · Android SDK 37.0 / 36
> 时间：2026-10-30 23:20
> 结论：**空壳工程编译通过（`assembleDebug` BUILD SUCCESSFUL，41 tasks）**，但过程中发现 **5 个会导致首次落地直接失败的配置错误**，已全部修正并回写至 `docs/rebuild/`。

---

## 1. 环境（本机新建，均在仓库之外）

| 组件 | 路径 | 版本 |
|---|---|---|
| JDK | `C:/Users/21063/toolchains/jdk17` | Temurin 17.0.20.1+1 |
| Android SDK | `C:/Users/21063/toolchains/android-sdk` | platform-tools 37.0.1、platforms **android-37.0 / android-36**、build-tools 37.0.0 / 36.0.0 |
| Gradle | `~/.gradle/wrapper/dists` | 9.4.1（腾讯镜像） |
| 验证工程 | `C:/Users/21063/toolchains/probe`（一次性，非交付物） | — |

**API 37 的平台目录名是 `android-37.0`（带次版本）**，`compileSdk = 37` 可直接解析，无需额外配置。

---

## 2. 实测通过的版本组合（可直接用）

| 组件 | 版本 | 备注 |
|---|---|---|
| AGP | **9.2.0** | 9.3.3 / 9.4.1 / 9.5.0 均已发布 |
| Gradle | **9.4.1** | 腾讯镜像 `gradle-9.4.1-bin.zip` 可用 |
| Kotlin（编译器） | **2.3.21** | ⚠️ 需在根脚本 `buildscript.classpath` **显式钉住**，见 §3.1 |
| KSP | **2.3.12** | 新版本号方案，非 `<kotlin>-<ksp>` 格式 |
| Compose BOM | **2026.09.00** | 存在；其 stdlib 为 2.4.20 |
| Hilt | **2.60.1** | 2.52 在 AGP 9 下直接抛 `Android BaseExtension not found` |
| Haze | **2.0.1** | 制品已拆分，见 §3.4 |
| Vico | 3.3.1 | 可解析（最新为 3.4.0-next.1，未采用） |
| Room | 2.7.2 | KSP 处理正常 |
| JDK / JVM target | 17 | `compileSdk 37` + `targetSdk 36` + `minSdk 29`（**轨 A 当时值**；终选轨 B 已改为 minSdk 34，见 §6） |
| 配置缓存 | 开启 | `org.gradle.configuration-cache=true` 下构建成功 |

---

## 3. 五个阻断性发现（**这是本次验证的核心产出**）

### 3.1 AGP 9 内置 Kotlin，不能再应用 `org.jetbrains.kotlin.android`

```
Failed to apply plugin 'org.jetbrains.kotlin.android'.
> Cannot add extension with name 'kotlin', as there is an extension already registered with that name.
```

AGP 9.0 起内置 Kotlin（官方文档《Migrate to built-in Kotlin》）。必须：
- 删除模块级与根级的 `org.jetbrains.kotlin.android`；
- 删除版本目录里的 `kotlin-android` 别名；
- `android { kotlinOptions { } }` 改 `kotlin { compilerOptions { } }`；
- kapt 与之不兼容（本项目已用 KSP，不受影响）。

### 3.2 AGP 9.x 内置 Kotlin 固定 2.2.10，与 Compose BOM 2026.09 不兼容

```
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.4.0, expected version is 2.2.0.
```

AGP 9.2.0 / 9.3.3 / 9.4.1 / 9.5.0 的 POM 里 `kotlin-gradle-plugin` **全部是 2.2.10**，而 Compose 2026.09 拉入的是 `kotlin-stdlib 2.4.20`。
解决办法（官方指定的显式指定 Kotlin 版本方式）在根脚本加：

```kotlin
buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21") }
}
```

选 2.3.21 而非 2.4.x，是因为 2.3 编译器可读 2.4.0 元数据，且与 KSP 2.3.12 同一代。

### 3.3 KSP 旧版本号（`2.2.10-2.0.2`）在 AGP 9 下不可用

```
Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.
Kotlin source set 'debug' contains: [.../generated/ksp/debug/kotlin]
```

KSP 2.3.x 起改为独立版本号，且使用了 AGP 9 认可的方式挂载生成源码目录。`android.disallowKotlinSourceSets=false` 只是逃生阀，不应作为长期方案。

### 3.4 Haze 2.0 制品已拆分，`haze-materials` 不存在

`haze-materials` 只存在于 1.x（最后版本 1.7.3）。2.0.1 实际制品：

| 制品 | 内容 |
|---|---|
| `dev.chrisbanes.haze:haze` | `HazeState` / `Modifier.hazeSource` / `HazeInput` |
| `dev.chrisbanes.haze:haze-blur` | `Modifier.hazeBlur` / `HazeBlurStyle` / `HazeBlurDefaults` |
| `dev.chrisbanes.haze:haze-blur-materials` | **`CupertinoMaterials`** / `FluentMaterials` / `HazeMaterials` |
| `dev.chrisbanes.haze:haze-blur-material3` | Material3 风格材质 |

**意外收获**：`CupertinoMaterials` 提供的正是 Apple 的四档材质 —— `ultraThin / thin / regular / thick`，与 Liquid Glass 的语义一一对应，比自行调参更接近 iOS。

### 3.5 `hazeEffect` 签名已变，旧写法编译不过

Haze 2.0.1 实际签名（`javap` 反解 + 编译验证）：

```kotlin
// 源层
Modifier.hazeSource(state: HazeState, zIndex: Float = 0f, key: Any? = null)

// 效果层（2.0 新 API，走 blur 模块）
Modifier.hazeBlur(
    input: HazeInput,
    style: HazeBlurStyle,
    performanceMode: HazePerformanceMode = ...,
    enabled: Boolean = ...,
)

// HazeInput 的三种形态
HazeInput.Backdrop(state)        // ← 玻璃卡片用这个
HazeInput.Content                // 内容自身作为源
HazeInput.Sources(state, selection, retention)
```

**已通过编译的最小可用写法**（`GlassProbe.kt`，`compileDebugKotlin` 成功）：

```kotlin
val state = rememberHazeState()
Box(Modifier.fillMaxSize().hazeSource(state)) { /* 背景内容 */ }
Box(Modifier.size(200.dp).hazeBlur(
    input = HazeInput.Backdrop(state),
    style = CupertinoMaterials.regular(),
))
```

> 因此 `docs/rebuild/GlassSurface.kt` 里基于 `hazeEffect(state, style)` 的写法**必须重写**为上面的形态。其余部分（四档降级策略、AGSL 折射探针）设计思路仍成立。

---

## 4. 复现步骤

```bash
export JAVA_HOME="C:\\Users\\21063\\toolchains\\jdk17"
cd C:/Users/21063/toolchains/probe
sh gradlew :app:assembleDebug --no-daemon --console=plain
# → BUILD SUCCESSFUL in 2m 8s / 41 actionable tasks
# → app/build/outputs/apk/debug/xstz-v2.0.0-debug-debug.apk（66MB，debug 含 tooling，非最终体积）
```

仓库侧落地时只需：`local.properties` 写 `sdk.dir`，其余四个脚本按 `docs/rebuild/` 原样复制。

---

## 5. 仍未验证的部分（不要误认为已验证）

| 项 | 原因 | 影响 |
|---|---|---|
| 真实 App 代码编译 | 旧工程 99 个 Kotlin 文件与新配置无兼容关系，需重建后才有意义 | 阶段 1 起才会暴露 |
| release 构建 / R8 / baseline profile | 未建 keystore，未接 ProGuard 规则 | 阶段 5 处理 |
| SQLCipher + 16KB page size | 尚未引入 | 引入时单独验证 |
| 多模块（12 个）+ build-logic 约定插件 | 尚未创建（S-01/S-02） | 阶段 0 待做 |
| 真机性能（60fps / 降级档位） | 无设备 | 阶段 2 验收项 |
| `compileSdk 37` 是否有行为变更影响 | 仅验证能编译 | 需查 API 37 变更日志 |

---

## 6. 轨 B 复测（2026-10-31 00:05，最终采用）

轨 A 验证完成后，用户改选轨 B（更保守）。**轨 B 单独复测，结果推翻了原设计中的两个前提。**

### 6.1 最终可用组合（`assembleDebug` BUILD SUCCESSFUL，41 tasks / 41s）

| 项 | 版本 | 与轨 A 的差异 |
|---|---|---|
| AGP | **9.1.1** | 9.2.0 → 9.1.1（更保守） |
| Gradle | **9.4.1** | 相同（镜像无 9.1.1 发行包，且 AGP 9.1.1 最低要求 9.1，兼容） |
| Kotlin | **2.3.21**（`buildscript classpath` 钉住） | 相同（AGP 9.1.1 内置仍为 2.2.10） |
| KSP | **2.3.12** | 相同 |
| Compose BOM | **2026.05.01**（实际解析 1.12.1） | 2026.09.00 → 2026.05.01 |
| Hilt / Haze / Room | 2.60.1 / 2.0.1 / 2.7.2 | 相同 |
| **compileSdk** | **37** | **与轨 A 相同（无法降）**，见 6.3 |
| **targetSdk / minSdk** | **36 / 34** | minSdk 29 → 34 |
| JDK | 17.0.20.1 | 相同 |

### 6.2 复测暴露的 3 个问题

| # | 现象 | 根因 | 处置 |
|---|---|---|---|
| B-1 | `Unresolved reference 'outputFileName'`（`app/build.gradle.kts:122`） | `VariantOutput.outputFileName` 是 **AGP 9.2 引入**的属性，9.1.1 接口无此成员 | 删除自定义产物改名逻辑，下沉到 CI 打包步骤（`mv`）。不强转 `VariantOutputImpl` 内部类 |
| B-2 | **`compileSdk 36` 编译直接失败**：`ui-android:1.12.1 requires ... compile against version 37 or later` | 见 6.3 | compileSdk 抬至 37 |
| B-3 | `w: -Xjvm-default is deprecated. Use -jvm-default instead` | Kotlin 2.3 变更了编译参数名 | 改为 `-jvm-default`（去掉 `X` 前缀） |

### 6.3 关键根因：Haze 把 Compose 顶到 1.12.1

BOM 2026.05.01 本应给出 `compose-ui 1.11.2`（已核对 POM：244 个模块中 168 个为 1.11.2，**无任何 1.12.x**）。但实际解析为 1.12.1。`dependencyInsight` 定位到唯一来源：

```
androidx.compose.ui:ui:1.12.1
└── org.jetbrains.compose.foundation:foundation-layout-android:1.12.1
    └── (Haze 2.0.1 的 KMP 变体传递引入)
```

**机制**：Haze 2.0.1 是 Kotlin Multiplatform 库，其 android 变体依赖 JetBrains Compose，而 JetBrains Compose 1.12.1 与 androidx Compose 1.12.1 版本对齐映射。Gradle 的「取最高版本」规则使 1.12.1 胜过 BOM 的 1.11.2 约束。

**结论**：

1. **`compileSdk 37` 是 Haze 的硬约束，与轨 A/B 选择无关。** 除非放弃背景模糊（= 放弃液态玻璃核心），否则无法回避。
2. `compileSdk` 只是编译期 API 级别，不影响运行时行为与设备覆盖，抬到 37 **无用户可见副作用**。
3. **不采用 `resolutionStrategy` 强制降回 1.11.2**：Haze 可能调用 1.12 新 API，强制降级会把「编译期报错」换成「运行期崩溃」，是负优化。

### 6.4 新增的维护红线

> **Haze 版本与 Compose 版本强耦合。** 每次调整 `haze` 版本后，必须重新确认 `composeBom` 的实际解析结果与 `compileSdk` 下限——**不能只看 BOM 声明值**，因为传递依赖可能已把它顶掉。
>
> 核查命令：
> ```
> ./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath \
>   --dependency androidx.compose.ui:ui
> ```

### 6.5 顺带确认的两项简化

- `minSdk 34` 后 `androidx.core:core-splashscreen` **可移除**（splash attrs 为 API 31+，34 原生可用），已从配置删除；
- `isLowRamDevice` / `ApplicationExitInfo`（API 30+）在 minSdk 34 下无条件可用。
