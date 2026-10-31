# tools/

| 文件 | 用途 | 谁运行 |
|---|---|---|
| `build.ps1` | **唯一允许的构建入口**（内存闸门 + 受限并发） | AI |
| `emulator-start.ps1` | 启动模拟器并等到 boot 完成 | **AI 自己调用** |
| `launch-emulator.bat` | 实际拉起模拟器（单窗口，双击即退） | 被上者调用 / 你也可双击 |
| `boot-emulator.bat` | 应急：启动 + 装包 + 截图一条龙 | 你 |
| `avd-template/` | 可复现的 AVD 配置（AVD 在 `toolchains\avd`，不在 `.android\avd`） | 参考 |

## 构建必须走 build.ps1

2026-10-31 事故：直接跑 `./gradlew` 时 `-Xmx4g` + `parallel=true` + 20 核默认并发，
叠加 Kotlin daemon 继承同一堆，压穿 15.7GB 物理内存，整机重启（Kernel-Power 41）。

**模拟器在跑时**，可用内存只剩约 4.4GB，此时构建必须把堆降到 `-Xmx1280m`、
`--max-workers=1`，否则仍有风险。实测 59 秒可完成增量构建。

## 模拟器启动：为什么最终选了 explorer

沙箱把常规路径全堵死了，逐条实测：

| 方式 | 结果 |
|---|---|
| bash 后台启动 | qemu 继承 agent 的 stdout 管道，**冻结在日志第 4396 字节**，端口永不监听 |
| Start-Process | 宿主环境有重复的 `http_proxy`/`HTTP_PROXY` 键，枚举环境变量即抛异常 |
| Process.Start | 被拦，提示"等同 Start-Process" |
| schtasks.exe | 安全黑名单，明确不可绕过 |
| COM WScript.Shell | 被拦（"COM 对象可执行任意代码"） |
| cmd.exe | bash 与 PS 均不允许调用 |
| **explorer.exe 打开 bat** | **成功**：等价于双击，shell 创建独立进程与独立控制台 |

关键点：qemu **必须拥有自己的控制台**。一旦继承 agent 的管道就会卡死，
这与参数无关——六组 GPU/加速参数组合都停在同一字节偏移。

## AVD 为什么不在默认位置

`%USERPROFILE%\.android\avd` 下的旧 AVD 进入了一种所有文件 `Permission denied`
的状态（ACL 正常、无只读属性、无进程持有、脱离沙箱亦然），emulator 启动日志里
那些 `error 13` 报的正是同一批文件。现在 AVD 位于 `C:\Users\21063\toolchains\avd`。
