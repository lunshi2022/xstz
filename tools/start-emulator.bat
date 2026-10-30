@echo off
rem ============================================================================
rem  XSTZ emulator launcher - double click this file
rem
rem  WHY A .BAT: the AI agent runs inside a sandbox (parent process
rem  sandbox-cli.exe). The emulator needs to create a Vulkan device, a desktop
rem  window and many low level syscalls. Inside that sandbox qemu freezes at
rem  87MB with 0% CPU and never opens port 5554 - tried 6 parameter combos,
rem  all stuck at the same byte offset. Launched from YOUR desktop session
rem  (this file), it is not sandboxed.
rem
rem  Memory cap: 2048MB guest + 2 cores. Do not raise these on a 16GB laptop,
rem  and do not run a Gradle build while the emulator is up.
rem ============================================================================

setlocal enabledelayedexpansion

set "SDK=C:\Users\21063\toolchains\android-sdk"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_HOME=%SDK%"
set "ANDROID_AVD_HOME=C:\Users\21063\.android\avd"
set "PATH=%SDK%\emulator;%SDK%\platform-tools;%PATH%"
set "ADB=%SDK%\platform-tools\adb.exe"
set "SERIAL=emulator-5554"

echo.
echo  ============================================
echo   XSTZ Emulator Launcher
echo  ============================================
echo.

if not exist "%SDK%\emulator\emulator.exe" (
  echo  [FAIL] emulator.exe not found at %SDK%\emulator
  pause
  exit /b 1
)

echo  [1/4] Restarting adb server...
"%ADB%" kill-server  >nul 2>&1
"%ADB%" start-server >nul 2>&1
timeout /t 2 >nul

echo  [2/4] Launching emulator (2048MB / 2 cores / software GPU)...
echo        A window will open. First boot takes 3-5 minutes.
start "" "%SDK%\emulator\emulator.exe" -avd xstz_api36 -memory 2048 -cores 2 -no-snapshot -no-boot-anim -gpu swiftshader_indirect

echo  [3/4] Waiting for device to register with adb...
set /a TRIES=0
:waitdev
set /a TRIES+=1
if %TRIES% gtr 72 goto timeout_dev
"%ADB%" -s %SERIAL% get-state >nul 2>&1
if errorlevel 1 (
  timeout /t 5 >nul
  goto waitdev
)

echo  [4/4] Device online. Waiting for Android to finish booting...
set /a TRIES=0
:waitboot
set /a TRIES+=1
if %TRIES% gtr 72 goto timeout_boot
set "BOOT="
rem getprop prints just "1" on success. Use tokens=* (no delimiter tricks):
rem an earlier version used "tokens=2 delims=:" which can never match and
rem would loop forever claiming the device never booted.
for /f "usebackq tokens=*" %%L in (`"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul`) do set "BOOT=%%L"
if not "!BOOT!"=="1" (
  timeout /t 5 >nul
  goto waitboot
)

"%ADB%" -s %SERIAL% shell settings put global window_animation_scale 0 >nul 2>&1
"%ADB%" -s %SERIAL% shell settings put global transition_animation_scale 0 >nul 2>&1
"%ADB%" -s %SERIAL% shell settings put global animator_duration_scale 0 >nul 2>&1

echo.
echo  ============================================
echo   READY - emulator is up, animations disabled.
echo   Tell the AI "emulator is up"; it will take
echo   over via adb: install APK and screenshots.
echo  ============================================
echo.
"%ADB%" devices
pause
exit /b 0

:timeout_dev
echo.
echo  [FAIL] Emulator never registered with adb within 6 minutes.
echo.
echo  Fallback 1 - add this flag to the emulator line above:
echo      -feature -Vulkan
echo  (that was the ONLY thing that moved the boot forward past the
echo   Vulkan device creation hang when tested from the sandbox)
echo.
echo  Fallback 2 - if it still hangs, try:
echo      -no-window
echo.
pause
exit /b 2

:timeout_boot
echo.
echo  [WARN] Device online but boot not completed in 6 minutes.
echo         Software GPU boot can be slow; it may still be progressing.
echo         Check with:  adb -s %SERIAL% shell getprop sys.boot_completed
echo.
pause
exit /b 3
