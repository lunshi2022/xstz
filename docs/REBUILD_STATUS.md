# 重建进度（2026-10-31）

## 已完成并验证

| 阶段 | 内容 | 验证方式 | 状态 |
|---|---|---|---|
| 0 | 14 模块骨架、Gradle 9.4.1 + AGP 9.1.1 + Kotlin 2.3.21 | `:app:assembleDebug` BUILD SUCCESSFUL | ✅ |
| 1 | model / common / domain / database / network / data | 98 个单测全通过 | ✅ |
| 2 | 设计系统：超椭圆、玻璃材质、色彩/字体令牌 | 编译通过；`AllocationRing` 自绘 | ✅ 部分 |
| 3 | 概览页 + 应用外壳 + 玻璃 Tab 栏 | APK 可构建、可安装结构完整 | 🟡 1/6 页面 |

## 产物
- `artifacts/apk/xstz-2.0.0-debug.apk`（65MB，debug 含 tooling）
- package `com.huaying.xstz.debug`，targetSdk 36，label「行时投资」

## 未完成
- 持仓 / 走势 / 再平衡 / 设置 四个页面（当前为占位）
- 净值历史抓取与图表交互
- release 签名构建验证、R8 规则实测、baseline profile
- 模拟器验收截图（环境限制，见下）

## 模拟器：已装好，但沙箱内无法启动（2026-10-31 深挖结论）

### 组件状态：完整可用
- emulator 37.2.13（`-version` 正常、`-list-avds` 返回 `xstz_api36`）
- 387 个文件、qemu-system-x86_64 就位、系统镜像 4.2G
- AEHD 2.2 加速驱动已装且 `-accel-check` 返回可用
- **这不是"安装"问题**，是"启动"被环境挡住

### 六组对照实验，全部卡死在同一字节偏移（4393~4395B）

| 实验 | 参数 | 结果 |
|---|---|---|
| A | `-gpu off -accel off -no-window` | 卡在 VkEmulation |
| B | `-gpu guest` | 同上 |
| C | `-gpu swiftshader_indirect -no-window` | 同上 |
| D | 去掉 `-no-window`（真开窗口） | 同上，排除无头模式 |
| E | `-feature -Vulkan` | **推进到 bootconfig**，但仍卡住 |
| F | `-feature -Vulkan -accel off` | 仍卡住，内存恒定 87MB |

### 决定性证据
- CPU 增量恒为 0.00~0.03s（进程空转，非计算）
- 工作集恒定 87MB，远低于 2048MB guest 内存 → **卡在分配 guest 内存之前**
- 端口 5554/5555 始终不监听
- 进程父链：`powershell.exe ← sandbox-cli.exe ← WorkBuddy.exe` → **运行在沙箱中**

### 根因判断
模拟器初始化需要创建 Vulkan 设备、桌面 QPA 窗口与大量底层系统调用，
在沙箱内被阻塞。旁证：连 `avdmanager`（纯 Java CLI）都出现"返回 exit=0 但文件未创建"
的静默失败，说明进程/文件语义在此环境被改写。

结论：**加任何参数都无法解决**，必须脱离沙箱启动。

### 解决路径
用户双击 `tools/start-emulator.bat`（在用户桌面会话中运行，不受沙箱限制）。
模拟器起来后，AI 侧可通过 adb（5037 端口跨会话共享）接管：安装 APK、截图、验收。

> 该脚本**未能在沙箱内实测**（沙箱同时禁止 Bash 与 PowerShell 调用 cmd.exe）。
> 语法已逐行复核，修掉了两处必然失败的写法：
> `for /f "tokens=2 delims=:"` 取不到 getprop 输出（无冒号分隔符）→ 改 `tokens=*`；
> `findstr /r "^emulator-.*device$"` 匹配不到 adb devices 的制表符格式 → 改 `get-state` 判断。


## 已知技术债
- `GlassTabBar` 用悬浮胶囊而非 iOS 贴底栏（Android 手势区冲突）
- 高光不随设备姿态流动（Android 无统一光场，已放弃伪实现）
- `effectiveThreshold` 的相对模式未接入 UI
