# Ensure the emulator is online. Start it if needed, then wait for boot.
#
# Why this script exists:
#   qemu 37.2.13 crashes with 0xc0000005 under heavy load (Gradle builds).
#   Workflow: tolerate crashes during builds, call this script before using the device.
#
# NOTE: keep this file pure ASCII. Windows PowerShell 5.1 reads BOM-less files
# as GBK; non-ASCII comments corrupt the parse and produce bogus syntax errors.
#
# Usage: run this first, then adb.

param([int]$TimeoutSec = 300)

$ConfirmPreference = "None"
$ErrorActionPreference = "Continue"

$adb = "C:\Users\21063\toolchains\android-sdk\platform-tools\adb.exe"
$bat = "C:\Users\21063\WorkBuddy\Worktrees\20260205124830\main-ed162e29\tools\launch-emulator.bat"
$pin = "C:\Users\21063\WorkBuddy\Worktrees\20260205124830\main-ed162e29\tools\pin-emulator-window.py"
$avd = "C:\Users\21063\toolchains\avd\xstz.avd"
$serial = "emulator-5554"

# Pin the emulator window to a fixed rect so screenshots are comparable
# between runs. Best effort: if python is missing or the window is gone,
# continue without it (a wrong window size is not worth failing the boot).
function Set-PinnedWindow {
    if (-not (Test-Path $pin)) { return }
    & python $pin 2>&1 | Out-Null
}

# "Process exists" is not enough: a crashed qemu leaves a zombie process with
# a working set of 0-50 MB that will never boot. Judge by memory instead
# (a healthy emulator sits above 1.5 GB once the guest is up).
function Test-Qemu {
    $p = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue
    if ($null -eq $p) { return $false }
    return ($p.WorkingSet64 -gt 300MB)
}

if (Test-Qemu) {
    $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ""
    if ($boot.Trim() -eq "1") {
        "already online"
        exit 0
    }
    "qemu present but not booted, waiting..."
} else {
    "qemu absent or zombie, starting..."
    Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Get-ChildItem $avd -Filter "*.lock" -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
    # explorer.exe on a .bat equals a double-click: it hands the emulator its own
    # console. Starting it from the agent's shell freezes qemu at a fixed log byte.
    & explorer.exe $bat
    Start-Sleep -Seconds 20
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
        # The window only exists once the Qt UI is up; pin it after boot.
        Set-PinnedWindow
        "ONLINE"
        exit 0
    }
    Start-Sleep -Seconds 6
}

"TIMEOUT"
exit 2
