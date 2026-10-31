# Safe build wrapper (ASCII only - PowerShell 5.1 reads BOM-less files as GBK,
# so any non-ASCII text here will be corrupted at parse time and break the script).
#
# WHY THIS EXISTS (2026-10-31 incident):
#   Running .\gradlew.bat :app:assembleDebug directly spawned Gradle daemon (-Xmx4g),
#   a Kotlin daemon inheriting that heap, and up to 20 parallel workers (one per CPU core).
#   On a 15.7GB machine that exhausted physical RAM and hard-rebooted the host
#   (Kernel-Power 41). This script is now the ONLY sanctioned build entry point.
#
# THREE GATES:
#   1. Free physical RAM below 3500MB -> reclaim daemons; still low -> refuse to build
#   2. Emulator detected -> refuse (emulator keeps 2-4GB resident; stacking kills the box)
#   3. Always --no-daemon and --max-workers=2, never scale with core count
#
# USAGE:
#   .\tools\build.ps1 :app:assembleDebug
#   .\tools\build.ps1 -Force :app:assembleDebug
#   .\tools\build.ps1 -Task testDebugUnitTest

param(
    [switch]$Force,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Task = @(":app:assembleDebug")
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$MinFreeMB = 3500

$env:JAVA_HOME = "C:\Users\21063\toolchains\jdk17"
$env:ANDROID_HOME = "C:\Users\21063\toolchains\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

function Get-FreeMB {
    [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1KB)
}

function Stop-BuildDaemons {
    Write-Host "[guard] reclaiming gradle/kotlin daemons..." -ForegroundColor Yellow
    Push-Location $Root
    try { & .\gradlew.bat --stop 2>&1 | Out-Null } catch { }
    Pop-Location
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.WorkingSet64 -gt 600MB } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

function Test-EmulatorRunning {
    # Judge by resident memory, not by "process exists".
    # A crashed qemu leaves a zombie with a 0-50 MB working set that can never
    # boot again -- and on this host it cannot even be killed ("access denied"),
    # so it lingers forever. Counting that zombie as "emulator running" would
    # block every build with a message that is plainly wrong.
    $emu = Get-Process qemu-system-x86_64, emulator -ErrorAction SilentlyContinue |
        Where-Object { $_.WorkingSet64 -gt 300MB }
    return ($null -ne $emu)
}

# --- gate 1: memory ---
$free = Get-FreeMB
Write-Host "[guard] free physical RAM: $free MB (floor $MinFreeMB MB)" -ForegroundColor Cyan
if ($free -lt $MinFreeMB) {
    Stop-BuildDaemons
    $free = Get-FreeMB
    Write-Host "[guard] after reclaim: $free MB" -ForegroundColor Cyan
    if ($free -lt $MinFreeMB) {
        Write-Host "[guard] still insufficient, aborting. Close browser/IDE/emulator first." -ForegroundColor Red
        exit 90
    }
}

# --- gate 2: emulator ---
if (-not $Force -and (Test-EmulatorRunning)) {
    Write-Host "[guard] emulator is running. Build + emulator will overwhelm this 16GB host." -ForegroundColor Red
    Write-Host "[guard] Close the emulator first, or pass -Force to override." -ForegroundColor Red
    exit 91
}

# --- gate 3: enforce bounded flags ---
$gradleArgs = @(
    "--no-daemon"
    "--max-workers=2"
    "--console=plain"
    "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8"
    "-Dkotlin.daemon.jvmargs=-Xmx1280m -XX:MaxMetaspaceSize=512m"
) + $Task

Push-Location $Root
$logDir = Join-Path $Root "build-logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logFile = Join-Path $logDir ("build-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".log")
$latestLog = Join-Path $logDir "latest.log"

try {
    Write-Host "[guard] building: $($Task -join ' ')" -ForegroundColor Green
    # 2>&1 会把 Gradle 的 stderr（所有编译错误都在这里）并入日志文件，
    # 否则 PowerShell 只回传一句话，真正的报错全丢。
    & .\gradlew.bat @gradleArgs 2>&1 | Tee-Object -FilePath $logFile
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    Copy-Item $logFile $latestLog -Force -ErrorAction SilentlyContinue
    Write-Host "[guard] log: $logFile" -ForegroundColor DarkGray
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.WorkingSet64 -gt 600MB } |
        Stop-Process -Force -ErrorAction SilentlyContinue
}

exit $code
