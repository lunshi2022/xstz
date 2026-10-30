#!/usr/bin/env bash
# 只读的内存探针，供 bash 侧快速判断（不启动任何进程）
awk '/MemTotal|MemAvailable/ {printf "%s %.0f MB\n", $1, $2/1024}' /proc/meminfo 2>/dev/null || echo "no /proc/meminfo"
