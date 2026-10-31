# 确保模拟器在线：不在就启动并等到 boot 完成
#
# 背景：qemu 37.2.13 在高负载（尤其是 Gradle 构建）下会 0xc0000005 崩溃。
# 2026-10-31 一天内崩了 4 次，其中 2 次发生在构建期间。
# 因此工作流调整为：构建时容忍它挂 → 需要用设备前调本脚本恢复。
#
# 用法：先 ensure，再 adb。

param([int]$TimeoutSec = 300)

$ErrorActionPreference = "Continue"
$adb = "C:\Users\21063\toolchains\android-sdk\platform-tools\adb.exe"
$bat = "C:\Users\21063\WorkBuddy\Worktrees\20260205124830\main-ed162e29\tools\launch-emulator.bat"
$serial = "emulator-5554"

function Test-Qemu { return ($null -ne (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)) }

if (Test-Qemu) {
    $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ""
    if ($boot.Trim() -eq "1") {
        "already online"
        exit 0
    }
    "qemu present but not booted, waiting..."
} else {
    "qemu absent, starting..."
    # 清掉可能残留的锁，否则启动会失败
    $avd = "C:\Users\21063\toolchains\avd\xstz.avd"
    Get-ChildItem $avd -Filter "*.lock" -ErrorAction SilentlyContinue | Remove-Item -Force -Confirm:$false -ErrorAction SilentlyContinue
    & explorer.exe $bat
    Start-Sleep -Seconds 15
    if (-not (Test-Qemu)) {
        "FAILED to start qemu"
        exit 1
    }
    "qemu up"
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ""
    if ($boot.Trim() -eq "1") {
        "ONLINE"
        exit 0
    }
    Start-Sleep -Seconds 6
}

"TIMEOUT"
exit 2
