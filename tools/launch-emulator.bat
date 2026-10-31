@echo off
rem ============================================================================
rem  XSTZ Emulator - single click launcher
rem
rem  2026-10-31 三次崩溃（0xc0000005 ACCESS_VIOLATION，模块 unknown）都是
rem  qemu 自身在 swiftshader 软件光栅化时越界。触发点集中在安装 APK 之后，
rem  因为装包会引起整屏重绘。
rem
rem  因此两处降低渲染压力：
rem    1. -gpu angle_indirect —— 交给 D3D11/硬件（RTX 3060），不再用 CPU 光栅化
rem    2. AVD 分辨率已降到 720x1600（原 1080x2400），像素量减少 55%%
rem  angle 若不可用，请把 -gpu 参数换回 swiftshader_indirect。
rem
rem  双击后本窗口立即关闭，只留模拟器窗口。
rem ============================================================================

set "SDK=C:\Users\21063\toolchains\android-sdk"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_AVD_HOME=C:\Users\21063\toolchains\avd"
set "PATH=%SDK%\emulator;%SDK%\platform-tools;%PATH%"

start "XSTZ Emulator" "%SDK%\emulator\emulator.exe" -avd xstz -memory 2048 -cores 2 -no-boot-anim -no-snapshot-save -gpu angle_indirect

exit
