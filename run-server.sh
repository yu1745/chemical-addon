#!/usr/bin/env bash
# run-server.sh — 启动 dev Forge 服务器，输出原样透传到终端（不做任何过滤）。
#
# 行为：后台启动 `./gradlew runServer`（set -m 独占进程组），前台轮询
# run/logs/latest.log，识别到启动标志 "Done (" 后关闭服务器并退出 0；
# 崩溃 / 超时 / 提前退出时退出 1。
#
# 关闭策略（三级，纯 PID 方案，不做任何进程名/路径匹配，绝不误伤）：
#   1. 由启动命令的 $! 拿到 gradle 启动器 PID，再由其 PGID 对整个进程组
#      （gradle 启动器 + single-use daemon + 服务器 JVM）发 SIGTERM，
#      服务器 JVM 的 shutdown hook 会保存世界
#   2. 轮询：进程组内仍有成员就等待（SHUTDOWN_GRACE_SECONDS 秒）
#   3. 仍存活则对整组 SIGKILL 升级，最后回收 gradle 启动器
#
# 用法：./run-server.sh [gradle 额外参数...]
# 环境变量：WAIT_DONE_TIMEOUT（秒，默认 300）、SHUTDOWN_GRACE_SECONDS（秒，默认 8）
#
# 注意：runServer 永不自行返回，因此不要用 `cmd | script` 或
# `cmd && script` 的形式调用；本脚本自行承担启动与收尾。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

LOG_FILE="$SCRIPT_DIR/run/logs/latest.log"
TIMEOUT="${WAIT_DONE_TIMEOUT:-300}"
GRACE="${SHUTDOWN_GRACE_SECONDS:-8}"
DONE_MARKER="Done ("
CRASH_MARKERS="Failed to load datapacks|Failed to start the minecraft server|Exception in thread \"main\""

GRADLE_PID=""
GRADLE_PGID=""

# 进程组是否还有成员（组 PID 来自启动命令的 $!，纯 PID 方案）
group_alive() {
    if [ -n "$GRADLE_PGID" ]; then
        kill -0 -- "-$GRADLE_PGID" 2>/dev/null
    else
        kill -0 "$GRADLE_PID" 2>/dev/null
    fi
}

kill_group() {
    local sig="$1"
    if [ -n "$GRADLE_PGID" ]; then
        kill "-$sig" -- "-$GRADLE_PGID" 2>/dev/null
    else
        kill "-$sig" "$GRADLE_PID" 2>/dev/null
    fi
}

shutdown_server() {
    local waited=0

    # 1) 普通退出：SIGTERM 整个进程组（服务器 JVM 的 shutdown hook 保存世界）
    kill_group TERM

    # 2) 轮询：给世界保存留时间，等进程组清空
    while [ "$waited" -lt "$GRACE" ]; do
        if ! group_alive; then
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done

    # 3) 升级：组内还有进程就 SIGKILL 整组
    if group_alive; then
        echo "[run-server] server processes still alive after ${waited}s, sending SIGKILL" >&2
        kill_group KILL
    fi

    # 收尾：回收 gradle 启动器
    if [ -n "$GRADLE_PID" ]; then
        wait "$GRADLE_PID" 2>/dev/null || true
    fi
}

on_signal() {
    echo "[run-server] interrupted, shutting down server..." >&2
    shutdown_server
    exit 130
}
trap on_signal INT TERM

# 开启 job control：让后台 gradle 独占一个进程组，整体结束时不误伤终端
set -m
./gradlew runServer --console=plain "$@" &
GRADLE_PID=$!
GRADLE_PGID="$(ps -o pgid= -p "$GRADLE_PID" 2>/dev/null | tr -d ' ')"
set +m

# 服务器的输出由 gradle 直接写入本脚本的 stdout —— 透传，不做任何过滤

start=$(date +%s)
done_ok=0
while :; do
    if [ -f "$LOG_FILE" ] && grep -q -- "$DONE_MARKER" "$LOG_FILE" 2>/dev/null; then
        done_ok=1
        break
    fi
    if [ -f "$LOG_FILE" ] && grep -qE "$CRASH_MARKERS" "$LOG_FILE" 2>/dev/null; then
        echo "[run-server] crash markers detected in $LOG_FILE" >&2
        break
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo "[run-server] server process exited before ready" >&2
        break
    fi
    if [ $(( $(date +%s) - start )) -gt "$TIMEOUT" ]; then
        echo "[run-server] timeout (${TIMEOUT}s) waiting for '$DONE_MARKER'" >&2
        break
    fi
    sleep 2
done

shutdown_server

if [ "$done_ok" -eq 1 ]; then
    echo "[run-server] server ready ('$DONE_MARKER'), terminated cleanly" >&2
    exit 0
fi
echo "[run-server] server did not reach '$DONE_MARKER' (log: $LOG_FILE)" >&2
exit 1
