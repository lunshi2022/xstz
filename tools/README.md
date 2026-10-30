# tools/

| 文件 | 用途 | 谁运行 |
|---|---|---|
| `build.ps1` | **唯一允许的构建入口**。可用内存 <3500MB 拒绝、模拟器在跑拒绝、强制 `--no-daemon --max-workers=2` | AI / 你 |
| `boot-emulator.bat` | 启动模拟器 → 自动装 APK → 自动启动 App → 自动截图到 `shot-home.png` | **你双击** |
| `probe-mem.sh` | 只读内存探针 | AI |

## 为什么构建必须走 build.ps1

2026-10-31 事故：直接跑 `./gradlew` 时 `-Xmx4g` + `parallel=true` + 20 核默认并发，
叠加 Kotlin daemon 继承同一堆，压穿 15.7GB 物理内存，整机重启（Kernel-Power 41）。
`build.ps1` 把峰值锁在 3.6GB 以内。

## 为什么模拟器要你双击

AI 进程挂在 `sandbox-cli.exe` 下。模拟器要创建 Vulkan 设备与桌面窗口，
在沙箱里 qemu 冻结在 87MB / 0% CPU，端口 5554 永不监听。

## 黑屏问题（API 36 + 软件 GPU）

现象：窗口黑屏、adb 恒 offline、`userdata-qemu.img` 停在 6.1MB。
但 qemu 内存已填到 1726MB、CPU 从 60% 降到 16% —— 说明系统在跑，
卡在**显示子系统**（SurfaceFlinger 起不来 → system_server 永久等待）。

`boot-emulator.bat` 依次尝试三个渲染后端，第一个成功即停：

| 尝试 | 参数 | 思路 |
|---|---|---|
| A | `-gpu guest` | guest 内软件渲染，host 完全不介入图形 ← 最可能成功 |
| B | `-gpu swiftshader_indirect -feature -Vulkan` | 关掉 Vulkan（沙箱内唯一能推进启动的参数） |
| C | `-gpu swiftshader_indirect -no-window` | 无窗口，减少渲染负担 |

每次最多等 5 分钟。日志：`tools/emu-try-*.log`。
