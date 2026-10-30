@echo off
rem ============================================================================
rem  XSTZ emulator boot - with GPU fallback, auto install, auto screenshot
rem
rem  PROBLEM: API 36 boots to a black screen forever. Evidence:
rem    - qemu alive, guest RAM filled to 1726MB   (it IS running)
rem    - CPU drops 60%% -> 16%%                     (waiting, not working)
rem    - userdata-qemu.img stays at 6.1MB          (userspace never writes)
rem    - adb stays "offline"                       (adbd never starts)
rem  Diagnosis: the display stack (SurfaceFlinger) never comes up, so
rem  system_server waits forever and the screen stays black. Most likely the
rem  software GPU backend cannot satisfy what API 36 asks for.
rem
rem  WHAT THIS DOES:
rem    1. kills any stuck instance
rem    2. tries 3 GPU backends, 5 min each, stops at first successful boot
rem    3. installs the built APK
rem    4. launches it and takes a screenshot into tools\shot-home.png
rem
rem  NOTE: pure ASCII only. cmd.exe reads .bat as GBK; UTF-8 Chinese comments
rem  corrupt the parser.
rem ============================================================================

setlocal enabledelayedexpansion

set "SDK=C:\Users\21063\toolchains\android-sdk"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_AVD_HOME=C:\Users\21063\.android\avd"
set "PATH=%SDK%\emulator;%SDK%\platform-tools;%PATH%"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMU=%SDK%\emulator\emulator.exe"
set "AVD=xstz_api36"
set "HERE=%~dp0"
set "APK=%HERE%..\artifacts\apk\xstz-2.0.0-debug.apk"
set "BOOTED=0"
set "SERIAL=emulator-5554"

echo.
echo  ==========================================================
echo   XSTZ Emulator - boot, install, screenshot
echo  ==========================================================
echo.

call :try_one "A-guest"    "-gpu guest"
if "!BOOTED!"=="1" goto install

call :try_one "B-novulkan" "-gpu swiftshader_indirect -feature -Vulkan"
if "!BOOTED!"=="1" goto install

call :try_one "C-headless" "-gpu swiftshader_indirect -no-window"
if "!BOOTED!"=="1" goto install

echo.
echo  ==========================================================
echo   All backends failed to complete boot.
echo   Logs: %HERE%emu-try-*.log
echo   Tell the AI - it will read them and change approach.
echo  ==========================================================
"%ADB%" devices
pause
exit /b 3

rem ---------------------------------------------------------------------------
:install
echo.
echo  [install] pushing APK...
if not exist "%APK%" (
  echo  [WARN] APK not found at %APK%
  goto shot
)
"%ADB%" -s %SERIAL% install -r -t "%APK%"

echo  [launch] starting app...
"%ADB%" -s %SERIAL% shell am force-stop com.huaying.xstz.debug
"%ADB%" -s %SERIAL% shell monkey -p com.huaying.xstz.debug -c android.intent.category.LAUNCHER 1 >nul 2>&1
timeout /t 6 >nul

echo  [shot] capturing screenshot...
"%ADB%" -s %SERIAL% exec-out screencap -p > "%HERE%shot-home.png"
if exist "%HERE%shot-home.png" (
  for %%F in ("%HERE%shot-home.png") do echo        saved %%~zF bytes to %HERE%shot-home.png
) else (
  echo        [WARN] screenshot failed
)

:shot
echo.
echo  ==========================================================
echo   READY. Emulator booted, app installed.
echo   Screenshot: %HERE%shot-home.png
echo   Tell the AI: "emulator is up"
echo  ==========================================================
"%ADB%" devices
pause
exit /b 0

rem ---------------------------------------------------------------------------
rem  :try_one  <label> <emulator args>
rem ---------------------------------------------------------------------------
:try_one
set "LABEL=%~1"
set "ARGS=%~2"
set "LOG=%HERE%emu-try-%LABEL%.log"

call :kill_all

echo  --- Attempt %LABEL% : %ARGS%
if exist "%LOG%" del /Q "%LOG%" >nul 2>&1
start "emu-%LABEL%" /min cmd /c ""%EMU%" -avd %AVD% -memory 2048 -cores 2 -no-snapshot -no-boot-anim %ARGS% > "%LOG%" 2>&1"

set /a TRIES=0
:wait_loop
set /a TRIES+=1
if %TRIES% gtr 60 goto give_up
set "BOOT="
for /f "usebackq tokens=*" %%L in (`"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul`) do set "BOOT=%%L"
if "!BOOT!"=="1" (
  set "BOOTED=1"
  echo       BOOT OK after about %TRIES% x 5s
  "%ADB%" -s %SERIAL% shell settings put global window_animation_scale 0 >nul 2>&1
  "%ADB%" -s %SERIAL% shell settings put global transition_animation_scale 0 >nul 2>&1
  "%ADB%" -s %SERIAL% shell settings put global animator_duration_scale 0 >nul 2>&1
  goto :eof
)
timeout /t 5 >nul
goto wait_loop

:give_up
echo       no boot in 5 min, next backend
goto :eof

rem ---------------------------------------------------------------------------
:kill_all
"%ADB%" -s %SERIAL% emu kill >nul 2>&1
taskkill /F /IM qemu-system-x86_64.exe          >nul 2>&1
taskkill /F /IM qemu-system-x86_64-headless.exe >nul 2>&1
taskkill /F /IM emulator.exe                    >nul 2>&1
timeout /t 4 >nul
if exist "%ANDROID_AVD_HOME%\%AVD%.avd\multiinstance.lock" del /Q "%ANDROID_AVD_HOME%\%AVD%.avd\multiinstance.lock" >nul 2>&1
goto :eof
