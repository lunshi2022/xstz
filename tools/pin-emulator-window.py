#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把模拟器窗口钉在固定的位置与尺寸上。

WHY THIS EXISTS
---------------
模拟器窗口默认是"自动缩放到能放进屏幕"，每次启动位置和大小都不一样：
2026-10-31 实测窗口落在 (50, 98) 尺寸 543x1214，屏幕逻辑分辨率只有 1707x1067，
窗口比屏幕还高，底部一截跑到屏幕外面去了（外接显示器上更明显）。

这带来两个具体的麻烦：
  1. 截图比对时窗口内的内容比例每次不同，没法用像素对比判断"有没有变化"
  2. 拿模拟器窗口截图发给别人时，窗口大小忽大忽小

EMULATOR 自己的参数做不到这件事
-------------------------------
  * `-scale` 已被标记 deprecated，且只影响初始缩放，不锁位置
  * `-no-window` 直接没有窗口
  * AVD 的 emulator-user.ini 里有 window.x / window.y / window.scale，
    但 window.scale = -1 表示"自动适应"，写死正数只锁缩放不锁窗口大小，
    而且模拟器退出时会把这个文件重写（把用户的拖动结果存下来）

所以这里直接用 Win32 SetWindowPos 改窗口矩形。实测有效：
  请求 430x956 -> 得到 430x956；请求 500x1050 -> 得到 500x1050。
模拟器窗口没有最小尺寸约束（外层那个控制台窗口才有 476 的下限）。

WHY PYTHON AND NOT POWERSHELL
-----------------------------
PowerShell 的 Add-Type 会在运行时编译并加载 .NET 代码，被安全策略直接拦截
（本项目已实测："Add-Type compiles and loads .NET code at runtime"）。
ctypes 不需要编译，Python 自带。

USAGE
-----
    python tools/pin-emulator-window.py                  # 用默认位置尺寸
    python tools/pin-emulator-window.py 40 60 460 1020   # 自定义 x y w h
    python tools/pin-emulator-window.py --list           # 只看当前窗口状态
"""

import ctypes
import ctypes.wintypes as win
import sys
import time

# 目标窗口标题前缀：模拟器主窗口（不含那个控制台窗口）
TITLE_PREFIX = "Android Emulator"

# 默认位置与尺寸（逻辑像素）
# 40,60  离屏幕左上角一点，方便同时看到 IDE
# 460x1020 足够显示 1080x2400 模拟器的完整内容（缩放 0.42）
DEFAULT = (40, 60, 460, 1020)


def find_window():
    """按标题找到模拟器主窗口，返回 hwnd"""
    user32 = ctypes.windll.user32
    found = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, win.HWND, win.LPARAM)
    def callback(hwnd, _lparam):
        length = user32.GetWindowTextLengthW(hwnd)
        if length:
            buf = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buf, length + 1)
            if buf.value.startswith(TITLE_PREFIX):
                found.append(hwnd)
                return False
        return True

    user32.EnumWindows(callback, 0)
    return found[0] if found else None


def rect_of(hwnd):
    user32 = ctypes.windll.user32
    r = win.RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(r))
    return r.left, r.top, r.right - r.left, r.bottom - r.top


def set_rect(hwnd, x, y, w, h):
    user32 = ctypes.windll.user32
    # SWP_NOZORDER | SWP_NOACTIVATE：只改位置尺寸，不抢焦点、不改层级
    return bool(user32.SetWindowPos(hwnd, 0, x, y, w, h, 0x0004 | 0x0010))


def main(argv):
    user32 = ctypes.windll.user32
    # 必须在做任何窗口计算之前声明进程是 DPI aware，
    # 否则拿到的坐标是虚拟化后的，和 SetWindowPos 用的坐标系对不上
    try:
        user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except AttributeError:
        user32.SetProcessDPIAware()

    if "--list" in argv:
        hwnd = find_window()
        if not hwnd:
            print("emulator window not found")
            return 1
        print("hwnd=%d rect(x, y, w, h)=%s" % (hwnd, rect_of(hwnd)))
        return 0

    args = [a for a in argv[1:] if not a.startswith("-")]
    x, y, w, h = DEFAULT
    if len(args) >= 4:
        x, y, w, h = (int(v) for v in args[:4])

    hwnd = find_window()
    if not hwnd:
        print("emulator window not found (is it running?)")
        return 1

    before = rect_of(hwnd)
    ok = set_rect(hwnd, x, y, w, h)
    time.sleep(0.35)
    after = rect_of(hwnd)

    print("hwnd      = %d" % hwnd)
    print("before    = x=%d y=%d w=%d h=%d" % before)
    print("after     = x=%d y=%d w=%d h=%d" % after)
    if not ok or after[:2] != (x, y):
        print("FAILED to pin window")
        return 1
    print("pinned to x=%d y=%d w=%d h=%d" % after)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
