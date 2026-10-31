#!/usr/bin/env bash
# 校验 APK 内所有 64 位 native 库满足 16KB 页对齐。
#
# 为什么需要这个脚本：
#   Google Play 自 2027-02-01 起，要求所有面向 Android 15+ 的应用
#   其 64 位 native 库必须按 16KB 页边界对齐，否则拒绝上架。
#   Android 16 起更严格。
#
#   本项目引入 SQLCipher 时踩过一次：方案原文写的
#   net.zetetic:android-database-sqlcipher:4.7.2 这个组合不存在，
#   而真正存在的旧坐标 android-database-sqlcipher:4.5.4 的
#   libsqlcipher.so 是 0x1000 对齐 —— 不合规。
#   换成 sqlcipher-android:4.7.2 才是 0x4000。
#
#   这类问题编译期完全无感，运行时也只在 16KB 设备上崩。
#   所以必须用脚本在 CI 里卡住，不能靠人记。
#
# 判据：ELF 的每个 PT_LOAD 段 p_align 必须 >= 0x4000（16384）。
#
# 用法：tools/check-elf-alignment.sh <apk 路径>
# 退出码：0 合规；1 不合规或无法检查。

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "用法: $0 <apk 路径>" >&2
    exit 1
fi

APK="$1"
if [ ! -f "$APK" ]; then
    echo "错误：找不到 APK：$APK" >&2
    exit 1
fi

WORK="$(mktemp -d)"
# shellcheck disable=SC2064
trap "rm -rf '$WORK'" EXIT

# 只解 64 位架构的 .so：32 位库不受 16KB 页要求约束
unzip -oq "$APK" "lib/arm64-v8a/*" "lib/x86_64/*" -d "$WORK" 2>/dev/null || true

SO_LIST="$(find "$WORK" -name '*.so' -type f 2>/dev/null || true)"
if [ -z "$SO_LIST" ]; then
    echo "未在 APK 中找到 64 位 native 库，视为通过（纯 Kotlin 应用不受此约束）"
    exit 0
fi

FAIL=0
while IFS= read -r so; do
    # 优先用 readelf（Linux CI 标配，最快）。
    # 没有就退回内联 Python —— Windows/Git Bash 环境通常没有 binutils，
    # 而 CI 与本机必须得到同样的结论，否则"本地过、CI 挂"会浪费大量时间。
    ALIGNS=""
    if command -v readelf >/dev/null 2>&1; then
        ALIGNS="$(readelf -l "$so" 2>/dev/null | awk '$1=="LOAD" {print $NF}')"
    elif command -v python >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1; then
        PY=python
        command -v python >/dev/null 2>&1 || PY=python3
        ALIGNS="$("$PY" - "$so" <<'PYEOF'
import struct, sys
path = sys.argv[1]
data = open(path, 'rb').read()
# 仅支持 ELF64（32 位库不在 16KB 要求范围内）
if data[:4] != b'\x7fELF' or data[4] != 2:
    sys.exit(0)
e_phoff = struct.unpack_from('<Q', data, 0x20)[0]
e_phentsize = struct.unpack_from('<H', data, 0x36)[0]
e_phnum = struct.unpack_from('<H', data, 0x38)[0]
out = []
for i in range(e_phnum):
    off = e_phoff + i * e_phentsize
    p_type = struct.unpack_from('<I', data, off)[0]
    if p_type == 1:  # PT_LOAD
        out.append(hex(struct.unpack_from('<Q', data, off + 0x30)[0]))
print('\n'.join(out))
PYEOF
)"
    else
        echo "跳过 $(basename "$so")：环境既无 readelf 也无 python，无法校验" >&2
        FAIL=1
        continue
    fi

    if [ -z "$ALIGNS" ]; then
        echo "跳过 $(basename "$so")：读不到 PT_LOAD 段（非 ELF64？）" >&2
        FAIL=1
        continue
    fi

    BAD=0
    while IFS= read -r a; do
        [ -z "$a" ] && continue
        # 剥掉可能存在的 \r：Windows 上 python 的 stdout 是 CRLF，
        # 不清掉会让 "4000\r" 无法通过十六进制校验，导致全部误判为不合规。
        a="${a%$'\r'}"
        # 十进制比较：把 0x4000 形式换成十进制。
        # 不能直接 $((a))：Git Bash 的算术展开对 "0x4000" 报 syntax error，
        # 且失败时 DEC 为空，比较被跳过 —— 会变成假阳性（误判通过）。
        HEX="${a#0x}"
        if ! [[ "$HEX" =~ ^[0-9a-fA-F]+$ ]]; then
            echo "  无法解析 $(basename "$so") 的对齐值：[$a]" >&2
            BAD=1
            continue
        fi
        DEC=$((16#$HEX))
        if [ "$DEC" -lt 16384 ]; then
            BAD=1
        fi
    done <<< "$(printf '%s\n' "$ALIGNS" | tr -d '\r')"

    if [ "$BAD" -eq 0 ]; then
        echo "  OK   $(basename "$so")  aligns=$(echo "$ALIGNS" | tr '\n' ' ')"
    else
        echo "  FAIL $(basename "$so")  aligns=$(echo "$ALIGNS" | tr '\n' ' ')" >&2
        echo "       ^ 存在小于 0x4000 的 PT_LOAD 对齐，在 16KB 页设备上会加载失败" >&2
        FAIL=1
    fi
done <<< "$SO_LIST"

if [ "$FAIL" -ne 0 ]; then
    echo "" >&2
    echo "16KB 页对齐校验未通过。常见原因：" >&2
    echo "  * 引入了不支持 16KB 的 native 库（如旧版 SQLCipher 4.5.x）" >&2
    echo "  * 依赖库版本回退" >&2
    exit 1
fi

echo "16KB 页对齐校验通过"
exit 0
