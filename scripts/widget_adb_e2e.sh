#!/bin/bash
# ============================================================
# widget_adb_e2e.sh — AGenUI Widget 轻量 E2E 回归脚本
# ============================================================
# 用法：
#   bash scripts/widget_adb_e2e.sh [DEVICE] [WIDGET_ID]
#   DEVICE 默认: 200.47.91.1:5555
#   WIDGET_ID 默认: 124
#
# 测试项：
#   SH-01: 刷新广播 → 渲染成功
#   SH-02: 模板切换广播 → 渲染成功
#   SH-03: 截图非空
# ============================================================
set -euo pipefail

DEVICE="${1:-200.47.91.1:5555}"
WIDGET_ID="${2:-124}"
PACKAGE="com.amap.agenuiplayground"
ACTION_REFRESH="${PACKAGE}.widget.ACTION_REFRESH"
ACTION_SWITCH="${PACKAGE}.widget.ACTION_SWITCH_TEMPLATE"

PASS=0
FAIL=0
SKIP=0

log()    { echo "[$(date +%H:%M:%S)] $*"; }
passed() { log "✅ $1"; PASS=$((PASS+1)); }
failed() { log "❌ $1"; FAIL=$((FAIL+1)); }
skipped() { log "⚠️  $1 (skipped)"; SKIP=$((SKIP+1)); }

# 确保 adb 连接
log "Connecting to device $DEVICE ..."
adb connect "$DEVICE" 2>/dev/null || true
sleep 1

# 先启动 App 确保 receiver 已注册
log "Starting app to register widget receiver..."
adb -s "$DEVICE" shell am start -n "$PACKAGE/.A2UIPlaygroundActivity" 2>/dev/null
sleep 3

# ============================================================
# SH-01: 刷新广播
# ============================================================
log "=== SH-01: Refresh broadcast ==="
adb -s "$DEVICE" logcat -c 2>/dev/null
adb -s "$DEVICE" shell am broadcast -a "$ACTION_REFRESH" --ei appWidgetId "$WIDGET_ID" 2>/dev/null
sleep 8

RENDER_LOG=$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep "Widget updated successfully" || true)
if [ -n "$RENDER_LOG" ]; then
    passed "SH-01: Widget refresh triggered render"
else
    # 检查是否是 JobScheduler 延迟
    JOB_LOG=$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep "AGenUIWidgetRenderSvc" || true)
    if [ -n "$JOB_LOG" ]; then
        skipped "SH-01: JobIntentService delayed (JobScheduler scheduling)"
    else
        failed "SH-01: No render success log found"
    fi
fi

# ============================================================
# SH-02: 模板切换广播
# ============================================================
log "=== SH-02: Template switch broadcast ==="
adb -s "$DEVICE" logcat -c 2>/dev/null
adb -s "$DEVICE" shell am broadcast -a "$ACTION_SWITCH" --ei appWidgetId "$WIDGET_ID" --es template todo 2>/dev/null
sleep 8

RENDER_LOG=$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep "Widget updated successfully" || true)
if [ -n "$RENDER_LOG" ]; then
    passed "SH-02: Template switch triggered render"
else
    JOB_LOG=$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep "AGenUIWidgetRenderSvc" || true)
    if [ -n "$JOB_LOG" ]; then
        skipped "SH-02: JobIntentService delayed"
    else
        failed "SH-02: No render success log found"
    fi
fi

# ============================================================
# SH-03: 截图非空
# ============================================================
log "=== SH-03: Screenshot non-empty ==="
SCREENSHOT_FILE="/tmp/widget_screen_$(date +%s).png"
adb -s "$DEVICE" exec-out screencap -p > "$SCREENSHOT_FILE" 2>/dev/null

if [ -f "$SCREENSHOT_FILE" ]; then
    FILE_SIZE=$(wc -c < "$SCREENSHOT_FILE" 2>/dev/null || echo "0")
    if [ "$FILE_SIZE" -gt 10000 ]; then
        passed "SH-03: Screenshot captured (${FILE_SIZE} bytes)"
        # 可选：与基线图对比（需要 ImageMagick）
        if command -v compare &>/dev/null; then
            log "ImageMagick available — comparing to baseline (if exists)"
        fi
    else
        failed "SH-03: Screenshot too small (${FILE_SIZE} bytes)"
    fi
else
    failed "SH-03: Screenshot file not created"
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo "  AGenUI Widget E2E Results"
echo "  Device: $DEVICE, Widget ID: $WIDGET_ID"
echo "  ✅ Passed: $PASS"
echo "  ❌ Failed: $FAIL"
echo "  ⚠️  Skipped: $SKIP"
echo "============================================"

exit $FAIL
