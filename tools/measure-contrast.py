#!/usr/bin/env python3
"""测量界面元素在实际背景上的对比度。

为什么需要它：设计稿的对比度是按"纯色背景"算的，而本项目的玻璃面板是半透明的，
真实对比度取决于**底下那层壁纸**。同一套配色在深色壁纸和浅色壁纸上，
对比度可以差好几倍。所以必须拿真机截图实测，不能只看色值表。

用法：
    python tools/measure-contrast.py                  # 用当前壁纸测一组关键文案
    python tools/measure-contrast.py --label 白色壁纸   # 给本次测量打标

前提：
    * 设备已连接（adb devices 能看到）
    * 应用停在前台、且停在"概览"页
    * 需要 ffmpeg 在 PATH 里（用于裁剪和解码）

判据（WCAG 2.1 AA）：
    * 正文（<18pt）≥ 4.5:1
    * 大字（≥18pt 或 ≥14pt 加粗）≥ 3.0:1

原理：
    在目标文字的包围盒内取亮度分布的 P95 与 P5 当"文字色"和"背景色"，
    再按 sRGB 转线性光后套 WCAG 公式。这是工程近似——比逐像素分类稳，
    因为它不依赖文字与背景一定是"最亮/最暗"的那一小撮像素。
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile

ADB = os.environ.get(
    "ADB",
    r"D:\LeStoreSoftstore\Cache\WorkBuddy\android-sdk\platform-tools\adb.exe",
)
SERIAL = os.environ.get("ANDROID_SERIAL", "")

# 概览页上最能代表"可读性"的几处文案。用文本内容定位，不写死坐标。
TARGETS = ["总资产", "更新于", "股票", "本金"]


def adb(*args, binary=False):
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(args)
    if binary:
        return subprocess.run(cmd, stdout=subprocess.PIPE, check=True).stdout
    return subprocess.run(cmd, stdout=subprocess.PIPE, check=True).stdout.decode(
        "utf-8", "replace"
    )


def dump_ui():
    """取 uiautomator 的 XML。"""
    adb("shell", "uiautomator dump /sdcard/_contrast.xml >/dev/null 2>&1")
    return adb("shell", "cat /sdcard/_contrast.xml")


def find_bounds(xml, needle):
    """在 XML 里找 text 包含 needle 的第一个节点的 bounds，返回 (x, y, w, h)。"""
    import re

    for m in re.finditer(r'<node[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        text = m.group(1)
        if needle in text:
            x1, y1, x2, y2 = (int(m.group(i)) for i in (2, 3, 4, 5))
            w, h = x2 - x1, y2 - y1
            if w > 0 and h > 0:
                return x1, y1, w, h
    return None


def grab_gray(box, png_path):
    """裁剪指定区域并解码成 8bit 灰度数组（0-255）。"""
    x, y, w, h = box
    with tempfile.NamedTemporaryFile(suffix=".gray", delete=False) as f:
        raw_path = f.name
    try:
        subprocess.run(
            [
                "ffmpeg", "-v", "error", "-y",
                "-i", png_path,
                "-vf", f"crop={w}:{h}:{x}:{y},format=gray",
                "-f", "rawvideo",
                raw_path,
            ],
            check=True,
        )
        with open(raw_path, "rb") as f:
            return list(f.read())
    finally:
        try:
            os.unlink(raw_path)
        except OSError:
            pass


def srgb_to_linear(c8):
    c = c8 / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def contrast_from_percentiles(pixels):
    """估前景/背景亮度并算 WCAG 对比度。

    踩过的坑（2026-11-01）：最初用 P95 当文字色、P5 当背景色，结果把
    「股票」这一行测成 1.82:1，而实际截图里是白字压深底、非常清楚。
    原因是**文字像素通常只占整块区域不到 5%**，P95 取到的仍然是背景色。

    现在的做法：
      * 背景 = 中位数（区域内绝大多数像素是背景）
      * 前景 = P99.5 与 P0.5 中**离背景更远**的那个
        —— 这样深字浅底、浅字深底两种情形都成立，不需要预先知道是哪种主题。
    """
    if not pixels:
        return None
    s = sorted(pixels)
    n = len(s)
    bg = s[n // 2]
    hi = s[min(n - 1, int(n * 0.995))]
    lo = s[max(0, int(n * 0.005))]
    # 取离背景更远的一端作为前景
    fg = hi if (hi - bg) >= (bg - lo) else lo
    l_fg = srgb_to_linear(fg)
    l_bg = srgb_to_linear(bg)
    if l_fg < l_bg:
        l_fg, l_bg = l_bg, l_fg
    return (l_fg + 0.05) / (l_bg + 0.05), bg, fg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", default="当前壁纸", help="本次测量的场景名，例如 白色壁纸")
    ap.add_argument("--targets", default=",".join(TARGETS))
    args = ap.parse_args()

    if not os.path.exists(ADB):
        print(f"找不到 adb：{ADB}", file=sys.stderr)
        return 2

    shot = os.path.join(tempfile.gettempdir(), "contrast-shot.png")
    with open(shot, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))

    xml = dump_ui()
    results = []
    for name in [t for t in args.targets.split(",") if t]:
        box = find_bounds(xml, name)
        if not box:
            results.append((name, None, f"未找到「{name}」"))
            continue
        px = grab_gray(box, shot)
        got = contrast_from_percentiles(px)
        if not got:
            results.append((name, None, "区域为空"))
        else:
            ratio, lo, hi = got
            results.append((name, ratio, f"背景={lo} 前景={hi}"))

    print(f"\n场景：{args.label}")
    print(f"{'元素':<10}{'对比度':>10}   判定      明细")
    print("-" * 62)
    all_ok = True
    for name, ratio, detail in results:
        if ratio is None:
            print(f"{name:<10}{'--':>10}   跳过      {detail}")
            continue
        # 这里的元素都按"正文"标准 4.5 判；大字号（总资产数字）按 3.0
        threshold = 3.0 if name == "总资产" else 4.5
        ok = ratio >= threshold
        all_ok = all_ok and ok
        print(f"{name:<10}{ratio:>8.2f}:1 {'通过' if ok else '未达标':<6}  {detail} (门槛 {threshold})")
    print("-" * 62)
    print("结论：" + ("全部达标" if all_ok else "存在未达标项"))
    print(json.dumps({"label": args.label,
                      "items": [{"name": n, "ratio": r} for n, r, _ in results]},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
