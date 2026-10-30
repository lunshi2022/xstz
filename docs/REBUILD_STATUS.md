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

## 环境限制（重要）
模拟器无法启动：`emulator.exe` 在 gfxstream/Vulkan 初始化阶段反复卡死，
swiftshader 与 host GPU 两条路径都停在同一行日志，qemu 进程常驻 224MB 不再推进。
已排除：组件缺失（重装后 387 文件完整）、AEHD 加速不可用（accel-check 返回可用）、
adb 注册顺序（先起 adb 再起模拟器仍失败）。
结论：当前执行环境无法创建图形上下文，本机截图验收暂不可行。

## 已知技术债
- `GlassTabBar` 用悬浮胶囊而非 iOS 贴底栏（Android 手势区冲突）
- 高光不随设备姿态流动（Android 无统一光场，已放弃伪实现）
- `effectiveThreshold` 的相对模式未接入 UI
