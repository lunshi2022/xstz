# XSTZ 项目长期记忆（curated）

## 目标
Android 14 模块化资产管理 App（A股/ETF/债券/基金），Compose + Material3。
包名 `com.huaying.xstz`；debug 构建带 `.debug` 后缀 → 运行时包名 `com.huaying.xstz.debug`。
入口 Activity：`com.huaying.xstz.MainActivity`。

## 如何打开模拟器（务必记住，可复现）
- SDK：`C:\Users\21063\toolchains\android-sdk`；AVD：`xstz_api36`（API36 / x86_64 / google_apis）；adb 串口 `emulator-5554`。
- 沙箱禁掉所有常规启动方式（bash 后台 / `Start-Process` / `Process.Start` / `schtasks` / COM `WScript.Shell` 全被拦或报错）。
- **唯一可行**：`explorer.exe` 打开 `tools/launch-emulator.bat`（等同双击，由 Windows shell 给 qemu 独立进程+独立控制台）。
- 已固化 `tools/emulator-start.ps1`：explorer 拉起 → 等 qemu 进程 → 等 `adb -s emulator-5554 get-state`=device → 等 `getprop sys.boot_completed`=1。AI 自己就能跑，不必用户点鼠标。
- **真因（别再误判）**：① qemu 必须拥有独立控制台，继承 agent 的 stdout 管道会冻结在日志第 4396 字节；② `Start-Process` 失败是宿主环境 `http_proxy` 与 `HTTP_PROXY` 两键重复，PowerShell 枚举一抛异常——与"能否启动进程"无关。两条坑都被 explorer 双击绕开。
- 模拟器上玻璃降级（`GlassCapability.hasRealBlur=false` → 半透明材质）；真机才走 RenderEffect 真模糊。差异已写进 `docs/REBUILD_STATUS.md`，勿拿降级效果冒充最终效果。

## 构建安全（绝不重启机器——最高优先级）
- 唯一构建入口 `tools/build.ps1`（ASCII-only，PowerShell 5.1 读无 BOM 文件会按 GBK 乱码，故脚本内禁中文）。
- 强制：`--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.daemon.jvmargs=-Xmx1280m`。
- `JAVA_HOME=C:\Users/21063\toolchains\jdk17`；`ANDROID_HOME=C:\Users\21063\toolchains\android-sdk`。
- 模拟器运行时构建需 `-Force` 覆盖"模拟器运行中拒绝"闸门；内存上限仍生效。
- 教训：曾 `-Xmx4g + parallel + 20 worker` 压穿 15.7G 物理内存 → Kernel-Power 41 整机重启。

## 验证循环
- AI 写码；编译/装/截图由用户或 AI 经 adb 完成（`android-sdk/platform-tools/adb` 在 Git Bash 可用，`emulator-5554` 已连）。
- `uiautomator dump` 会杀 qemu，截图只用 `adb exec-out screencap -p > file.png`。

## ⚠️ 本目录是 git worktree，主仓库在别处（删错等于删历史）

- 工作区：`C:/Users/21063/WorkBuddy/Worktrees/20260205124830/main-ed162e29`
- 这里的 `.git` 是一个**文件**（不是目录），内容 `gitdir: E:/AI/Project/20260205124830/.git/worktrees/main-ed162e29`
- **主仓库 = `E:/AI/Project/20260205124830/.git`**。删掉这个目录会**连本项目的全部提交历史一起消失**（2026-10-31 真实发生过一次）。
- 事故经过：用户清理"旧项目目录"时删了 `E:\AI\Project\20260205124830`，导致本 worktree 的 git 全部失效（21 个提交 + `legacy/pre-rebuild` tag）。最终从 E 盘回收站 `$Recycle.Bin/S-1-5-21-.../$RCB9Y45/20260205124830/.git` 拷回原位恢复。
- 教训：① 清理磁盘前先确认是不是别处的 git 主仓库；② 回收站能救，第一时间去 `$Recycle.Bin` 找；③ 验证 git 是否可用要用 `git rev-parse --show-toplevel`，**不能只看 `git status` 空输出就判定安全**（命令失败时输出也是空的，会造成假阳性）。

## 发布签名（务必记住）

- **签名配置在仓库根 `keystore.properties`（已被 .gitignore 忽略）。口令只存在于该文件，禁止写进任何被 git 跟踪的文件**——`.workbuddy/memory/` 是被跟踪的，往里写等于提交私钥口令。
- 当前使用的密钥：alias `xstz`，证书 `CN=XSTZ, OU=InvestmentManager, O=huaying`，指纹 `eec40e0e...`。
  同一把密钥，MD5 `9c4dc3d97fb76612965ab3ae936f39b9`，keystore.properties 里 `storeFile=xstz-release.jks`（相对路径，解析到仓库根）：
  - `<仓库根>/xstz-release.jks` ← **keystore.properties 指向这里**（已被 .gitignore 第 41 行 `*.jks` 忽略）
  - `C:/Users/21063/.keystores/xstz-release.jks` ← 项目外安全副本（旧项目目录那一份已被用户删除）
  口令仍是 `keystore.properties` 里那组，**不写在任何被 git 跟踪的文件里**（`.workbuddy/` 是被跟踪的）。
- **路径必须纯 ASCII**：`app/build.gradle.kts` 用 `Properties().load(InputStream)`，按 ISO-8859-1 解码，中文路径会乱码导致签名失败。
- **旧密钥已作废并销毁（2026-11-01 决策）**：原 `E:\AI\Project\密钥`（证书 `L=Donglizhen`，v1.6.5 用的那把）口令从未留存，已确认无法恢复（详见 `docs/TEST_PLAN.md` T-002 取证经过）。**旧密钥的全部副本已删除/覆写销毁，不要再去找**——找也找不到了。装过 v1.6.5 的设备需卸载重装一次，此后统一用 `CN=XSTZ` 新密钥覆盖升级。
- ⚠️ **密钥删不得**：它是签名身份，删了再也签不出能升级的包。当前唯一在用的 `xstz-release.jks`（4.4KB）务必两处都留（仓库根 + `C:\Users\21063\.keystores\`）。
- release 包已验证：同签名下**可覆盖升级且数据保留**。

## 已修的真实缺陷（备查）
- 519xxx 场外基金被旧正则误判为场内 ETF → 走错接口取不到净值。
- `sharesToTrade` 多乘 1e4（1000 元算成 300 万份）。
- 备份恢复用自增 id 关联流水 → 改按 code 映射。
- 数据来源角标曾写"实时"但系离线样例 → 改橙色「离线样例」（SAMPLE），诚实标注。
- 底部悬浮 Tab 栏压住末位持仓 → 内容区 bottom 留白 170dp（走势）/190dp（再平衡、设置）。
