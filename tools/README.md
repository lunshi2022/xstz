# tools/

| 文件 | 用途 | 谁运行 |
|---|---|---|
| `build.ps1` | **唯一允许的构建入口**。三道闸门：可用内存 <3500MB 拒绝、模拟器在跑拒绝、强制 `--no-daemon --max-workers=2` | AI / 你 |
| `start-emulator.bat` | 启动模拟器（2048MB / 2 核 / 软件 GPU），等开机完成后自动关动画 | **你双击** |
| `probe-mem.sh` | 只读内存探针，bash 侧快速判断 | AI |

## 为什么构建必须走 build.ps1

2026-10-31 事故：直接跑 `./gradlew` 时 `-Xmx4g` + `parallel=true` + 20 核默认并发，
叠加 Kotlin daemon 继承同一堆，压穿 15.7GB 物理内存，整机重启（Kernel-Power 41）。
`build.ps1` 把峰值锁在 3.6GB 以内。

## 为什么模拟器要你双击

AI 进程挂在 `sandbox-cli.exe` 下。模拟器要创建 Vulkan 设备与桌面窗口，
在沙箱里 qemu 会冻结在 87MB / 0% CPU，端口 5554 永不监听。
六组参数组合（含 `-feature -Vulkan`）都只推进到同一字节偏移。详见 `docs/REBUILD_STATUS.md`。

启动成功后 AI 可以通过 adb 接管（5037 端口跨会话共享）。
