# 模拟器启动器（Windows PowerShell）
#
# 内存纪律（2026-10-31 事故后硬性规定）：
#   * 模拟器固定 -memory 2048 -cores 2，绝不放任默认值（默认常按物理内存 1/2 分配）
#   * 启动前检查可用物理内存 < 4000MB 直接拒绝
#   * 与 Gradle 构建互斥：构建期间不要开模拟器，模拟器开着不要构建
#   * 用 -no-snapshot-save 避免退出时写快照占用磁盘与内存
#
# 用法：
#   .\tools\emulator.ps1 start      启动并等待 boot 完成
#   .\tools\emulator.ps1 stop       关闭
#   .\tools\emulator.ps1 status     查看状态

param([string]$Action = "status")

$ErrorActionPreference = "Stop"
$Sdk = "C:\Users\21063\toolchains\android-sdk"
$Emu = Join-Path $Sdk "emulator\emulator.exe"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$Avd = "xstz_api36"
$Serial = "emulator-5554"
$MinFreeMB = 4000

function Get-FreeMB { [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1KB) }
function Test-EmuRunning { return ($null -ne (Get-Process qemu-system-x86_64, emulator -ErrorAction SilentlyContinue)) }

switch ($Action) {

    "start" {
        if (Test-EmuRunning) { Write-Host "[emu] already running"; exit 0 }

        $free = Get-FreeMB
        Write-Host "[emu] free RAM: $free MB (floor $MinFreeMB MB)"
        if ($free -lt $MinFreeMB) {
            Write-Host "[emu] refused: not enough RAM. Close browser/IDE first." -ForegroundColor Red
            exit 90
        }

        # 构建守护：有 Gradle/Kotlin daemon 在跑就不启动模拟器
        $busy = Get-Process java, javaw -ErrorAction SilentlyContinue |
                Where-Object { $_.WorkingSet64 -gt 700MB }
        if ($busy) {
            Write-Host "[emu] refused: a JVM build process is holding >700MB. Finish the build first." -ForegroundColor Red
            exit 91
        }

        Write-Host "[emu] starting $Avd (2048MB, 2 cores)..."
        $args = @(
            "-avd", $Avd
            "-memory", "2048"
            "-cores", "2"
            "-no-snapshot-save"
            "-no-boot-anim"
            "-gpu", "auto"
            "-no-audio"
        )
        Start-Process -FilePath $Emu -ArgumentList $args -WindowStyle Minimized

        Write-Host "[emu] waiting for device..."
        & $Adb wait-for-device 2>&1 | Out-Null
        $deadline = (Get-Date).AddMinutes(6)
        do {
            Start-Sleep -Seconds 4
            $booted = (& $Adb -s $Serial shell getprop sys.boot_completed 2>$null) -join ""
            if ($booted.Trim() -eq "1") { break }
        } while ((Get-Date) -lt $deadline)

        if ($booted.Trim() -eq "1") {
            & $Adb -s $Serial shell settings put global window_animation_scale 0 2>&1 | Out-Null
            & $Adb -s $Serial shell settings put global transition_animation_scale 0 2>&1 | Out-Null
            & $Adb -s $Serial shell settings put global animator_duration_scale 0 2>&1 | Out-Null
            Write-Host "[emu] booted and ready (animations off)" -ForegroundColor Green
        } else {
            Write-Host "[emu] boot timeout" -ForegroundColor Red
            exit 1
        }
    }

    "stop" {
        if (-not (Test-EmuRunning)) { Write-Host "[emu] not running"; exit 0 }
        & $Adb -s $Serial emu kill 2>&1 | Out-Null
        Start-Sleep -Seconds 5
        Get-Process qemu-system-x86_64, emulator -ErrorAction SilentlyContinue |
            Stop-Process -Force -ErrorAction SilentlyContinue
        Write-Host "[emu] stopped" -ForegroundColor Green
    }

    "status" {
        if (Test-EmuRunning) {
            $booted = (& $Adb -s $Serial shell getprop sys.boot_completed 2>$null) -join ""
            Write-Host "[emu] running, boot_completed=$($booted.Trim())"
            & $Adb devices
        } else {
            Write-Host "[emu] not running"
        }
    }

    default { Write-Host "usage: .\tools\emulator.ps1 start|stop|status"; exit 1 }
}
