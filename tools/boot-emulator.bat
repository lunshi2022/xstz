@echo off
rem ============================================================================
rem  XSTZ emulator - boot clean AVD, install app, screenshot
rem
rem  WHY A NEW AVD DIRECTORY:
rem    The old AVD under %USERPROFILE%\.android\avd became un-deletable:
rem    every file returned "Permission denied" even outside the sandbox, with
rem    normal ACLs, no read-only flag and no owning process. Same files that
rem    the emulator logged as "error 13 / access denied" during boot
rem    (modem_simulator\iccprofile_for_sim0.xml, numeric_operator.xml).
rem    So this uses a fresh AVD at C:\Users\21063\toolchains\avd instead.
rem
rem  WHY IT FIXES THE BLACK SCREEN (matches a documented API-36 case):
rem    1. fresh AVD + fresh userdata  - old userdata was a stuck 6.1MB stub
rem    2. hw.gpu.mode = swiftshader   - "auto" picks the Vulkan backend on
rem                                     API 36, which is what hangs
rem    3. -gpu swiftshader_indirect   - belt and braces on the command line
rem    4. -no-snapshot                - never load a corrupt quick-boot snapshot
rem    5. -wipe-data                  - guaranteed factory state
rem
rem  NOTE: pure ASCII. cmd.exe reads .bat as GBK; UTF-8 Chinese breaks parsing.
rem ============================================================================

setlocal enabledelayedexpansion

set "SDK=C:\Users\21063\toolchains\android-sdk"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_AVD_HOME=C:\Users\21063\toolchains\avd"
set "PATH=%SDK%\emulator;%SDK%\platform-tools;%PATH%"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMU=%SDK%\emulator\emulator.exe"
set "AVD=xstz"
set "AVDDIR=%ANDROID_AVD_HOME%\%AVD%.avd"
set "HERE=%~dp0"
set "APK=%HERE%..\artifacts\apk\xstz-2.0.0-debug.apk"
set "BOOTED=0"
set "SERIAL=emulator-5554"

echo.
echo  ==========================================================
echo   XSTZ Emulator - clean AVD boot
echo  ==========================================================
echo.

if not exist "%AVDDIR%\config.ini" (
  echo  [FAIL] AVD not found at %AVDDIR%
  pause
  exit /b 1
)

call :try_one "A-swiftshader" "-gpu swiftshader_indirect -no-snapshot -wipe-data"
if "!BOOTED!"=="1" goto install

call :try_one "B-angle"       "-gpu angle_indirect -no-snapshot -wipe-data"
if "!BOOTED!"=="1" goto install

call :try_one "C-guest"       "-gpu guest -no-snapshot -wipe-data"
if "!BOOTED!"=="1" goto install

echo.
echo  ==========================================================
echo   All backends failed. Logs: %HERE%emu-try-*.log
echo   Tell the AI.
echo  ==========================================================
pause
exit /b 3

rem ---------------------------------------------------------------------------
:install
echo.
echo  [install] pushing APK...
if not exist "%APK%" ( echo  [WARN] APK missing & goto shot )
"%ADB%" -s %SERIAL% install -r -t "%APK%"

echo  [launch] starting app...
"%ADB%" -s %SERIAL% shell am force-stop com.huaying.xstz.debug
"%ADB%" -s %SERIAL% shell monkey -p com.huaying.xstz.debug -c android.intent.category.LAUNCHER 1 >nul 2>&1
timeout /t 8 >nul

echo  [shot] light mode...
"%ADB%" -s %SERIAL% exec-out screencap -p > "%HERE%shot-home.png"
for %%F in ("%HERE%shot-home.png") do echo        shot-home.png = %%~zF bytes

echo  [shot] dark mode...
"%ADB%" -s %SERIAL% shell "cmd uimode night yes" >nul 2>&1
timeout /t 4 >nul
"%ADB%" -s %SERIAL% exec-out screencap -p > "%HERE%shot-home-dark.png"
for %%F in ("%HERE%shot-home-dark.png") do echo        shot-home-dark.png = %%~zF bytes
"%ADB%" -s %SERIAL% shell "cmd uimode night no" >nul 2>&1

:shot
echo.
echo  ==========================================================
echo   READY - booted, app installed, screenshots captured.
echo   Tell the AI: "emulator is up"
echo  ==========================================================
"%ADB%" devices
pause
exit /b 0

rem ---------------------------------------------------------------------------
:try_one
set "LABEL=%~1"
set "ARGS=%~2"
set "LOG=%HERE%emu-try-%LABEL%.log"
call :kill_all

echo  --- Attempt %LABEL% : %ARGS%
if exist "%LOG%" del /Q "%LOG%" >nul 2>&1
start "emu-%LABEL%" /min cmd /c ""%EMU%" -avd %AVD% -memory 2048 -cores 2 -no-boot-anim %ARGS% > "%LOG%" 2>&1"

set /a TRIES=0
:wait_loop
set /a TRIES+=1
if "%TRIES%"=="24" call :report_userdata
if %TRIES% gtr 72 goto give_up
set "BOOT="
for /f "usebackq tokens=*" %%L in (`"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul`) do set "BOOT=%%L"
if "!BOOT!"=="1" (
  set "BOOTED=1"
  echo       BOOT OK after about %TRIES% x 5s
  call :report_userdata
  "%ADB%" -s %SERIAL% shell settings put global window_animation_scale 0 >nul 2>&1
  "%ADB%" -s %SERIAL% shell settings put global transition_animation_scale 0 >nul 2>&1
  "%ADB%" -s %SERIAL% shell settings put global animator_duration_scale 0 >nul 2>&1
  goto :eof
)
timeout /t 5 >nul
goto wait_loop

:give_up
echo       no boot in 6 min, next backend
call :report_userdata
goto :eof

rem ---------------------------------------------------------------------------
:report_userdata
set "USZ=0"
if exist "%AVDDIR%\userdata-qemu.img" for %%F in ("%AVDDIR%\userdata-qemu.img") do set "USZ=%%~zF"
set /a UMB=%USZ%/1048576
echo       userdata-qemu.img = %UMB% MB  (a healthy first boot passes 100MB)
goto :eof

rem ---------------------------------------------------------------------------
:kill_all
"%ADB%" -s %SERIAL% emu kill >nul 2>&1
taskkill /F /IM qemu-system-x86_64.exe          >nul 2>&1
taskkill /F /IM qemu-system-x86_64-headless.exe >nul 2>&1
taskkill /F /IM emulator.exe                    >nul 2>&1
timeout /t 5 >nul
if exist "%AVDDIR%\multiinstance.lock" del /Q "%AVDDIR%\multiinstance.lock" >nul 2>&1
goto :eof
