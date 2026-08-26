#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
measure_performance.py — AGenUI Widget 性能基线测量脚本

测量指标：
  1. 各模板渲染时间（从 logcat "renderSync" 到 "Widget updated" 的时间差）
  2. Bitmap 缓存命中率（第一次渲染 vs 第二次渲染的时间差）
  3. 预渲染效果（prerenderAll 后首次切换模板的时间）

用法：
    python scripts/measure_performance.py [DEVICE] [--no-adb]
    DEVICE 默认: 200.47.91.1:5555
    --no-adb: 仅根据已有日志生成报告（不连接设备）

输出：
    docs/PERFORMANCE-BASELINE.md
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs" / "PERFORMANCE-BASELINE.md"

DEVICE_DEFAULT = "200.47.91.1:5555"
PACKAGE = "com.amap.agenuiplayground"
ACTIVITY = f"{PACKAGE}/.A2UIPlaygroundActivity"
TEMPLATES = ["weather", "agenda", "todo", "calendar", "poll", "note",
             "notecard", "meeting", "classroom", "flashcard"]

# 日志时间戳解析：MM-DD HH:MM:SS.ms
TS_RE = re.compile(r"(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})")
# 关键日志行
RENDER_START_RE = re.compile(r"renderSync:\s+id=(\S+),\s+template=(\S+)")
RENDER_DONE_RE = re.compile(r"Widget updated:\s*(.*)")
CACHE_HIT_RE = re.compile(r"Bitmap cache HIT:\s*(\S+)")
PRERENDER_START_RE = re.compile(r"prerenderAll:\s+starting")
PRERENDER_DONE_RE = re.compile(r"prerenderAll:\s+done,\s+cache size=(\d+)")


def parse_ts(ts_str):
    """把 logcat 时间戳转为毫秒数（相对当天）"""
    # 格式: 08-27 10:23:45.123
    m = re.match(r"(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})", ts_str)
    if not m:
        return None
    _, _, hh, mm, ss, ms = m.groups()
    return int(hh) * 3600000 + int(mm) * 60000 + int(ss) * 1000 + int(ms)


def adb(device, *args):
    """执行 adb 命令"""
    cmd = ["adb", "-s", device] + list(args)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return result.stdout + result.stderr
    except Exception as e:
        return f"<adb error: {e}>"


def clear_logcat(device):
    adb(device, "logcat", "-c")


def get_logcat(device):
    return adb(device, "logcat", "-d")


def render_template(device, template, wait=5):
    """启动 Activity 渲染指定模板，返回 logcat 输出"""
    clear_logcat(device)
    adb(device, "shell", "am", "start", "-n", ACTIVITY,
        "--ez", "autoWidgetPreview", "true",
        "--es", "widgetTemplate", template)
    time.sleep(wait)
    return get_logcat(device)


def parse_render_times(logs):
    """从日志中提取渲染时间（renderSync → Widget updated）"""
    # 收集所有时间戳
    start_ts = {}  # (id, template) -> ts
    end_ts = []    # list of (ts, title)
    cache_hits = []

    for line in logs.splitlines():
        ts_match = TS_RE.search(line)
        ts = parse_ts(ts_match.group(1)) if ts_match else None

        m = RENDER_START_RE.search(line)
        if m and ts is not None:
            wid, tpl = m.groups()
            start_ts[(wid, tpl)] = ts

        m = RENDER_DONE_RE.search(line)
        if m and ts is not None:
            end_ts.append((ts, m.group(1)))

        m = CACHE_HIT_RE.search(line)
        if m:
            cache_hits.append(m.group(1))

    return start_ts, end_ts, cache_hits


def measure_all(device, run_label):
    """对所有模板测量渲染时间"""
    results = {}
    for tpl in TEMPLATES:
        logs = render_template(device, tpl)
        start_ts, end_ts, hits = parse_render_times(logs)
        # 计算第一个 renderSync → 第一个 Widget updated 的时间差
        first_start = None
        for (wid, t), ts in start_ts.items():
            if t == tpl:
                first_start = ts
                break
        first_end = end_ts[0][0] if end_ts else None
        if first_start is not None and first_end is not None and first_end >= first_start:
            elapsed = first_end - first_start
            results[tpl] = {"first_ms": elapsed, "cache_hit": len(hits) > 0}
        else:
            results[tpl] = {"first_ms": None, "cache_hit": len(hits) > 0}
    return results


def measure_cache_hit(device):
    """第二次渲染同一模板测量缓存命中"""
    results = {}
    for tpl in TEMPLATES:
        # 第一次渲染
        logs1 = render_template(device, tpl, wait=5)
        _, _, hits1 = parse_render_times(logs1)
        # 第二次渲染
        logs2 = render_template(device, tpl, wait=5)
        start_ts2, end_ts2, hits2 = parse_render_times(logs2)

        first_start2 = None
        for (wid, t), ts in start_ts2.items():
            if t == tpl:
                first_start2 = ts
                break
        first_end2 = end_ts2[0][0] if end_ts2 else None

        second_ms = None
        if first_start2 and first_end2 and first_end2 >= first_start2:
            second_ms = first_end2 - first_start2

        results[tpl] = {
            "second_ms": second_ms,
            "cache_hit_second": len(hits2) > 0,
        }
    return results


def measure_prerender(device):
    """测量 prerenderAll 效果"""
    # 触发 prerenderAll（通过启动 App，会自动预热）
    clear_logcat(device)
    adb(device, "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)  # 等待 prerenderAll 完成
    logs = get_logcat(device)

    prerender_start = None
    prerender_end = None
    cache_size = None
    for line in logs.splitlines():
        ts_match = TS_RE.search(line)
        ts = parse_ts(ts_match.group(1)) if ts_match else None
        if PRERENDER_START_RE.search(line) and ts:
            prerender_start = ts
        if PRERENDER_DONE_RE.search(line) and ts:
            prerender_end = ts
            m = PRERENDER_DONE_RE.search(line)
            if m:
                cache_size = m.group(1)

    elapsed = None
    if prerender_start and prerender_end and prerender_end >= prerender_start:
        elapsed = prerender_end - prerender_start

    # 测量预渲染后首次切换模板的时间
    first_switch_ms = None
    tpl = "weather"
    logs2 = render_template(device, tpl, wait=5)
    start_ts, end_ts, hits = parse_render_times(logs2)
    first_start = None
    for (wid, t), ts in start_ts.items():
        if t == tpl:
            first_start = ts
            break
    first_end = end_ts[0][0] if end_ts else None
    if first_start and first_end and first_end >= first_start:
        first_switch_ms = first_end - first_start

    return {
        "prerender_ms": elapsed,
        "cache_size": cache_size,
        "first_switch_ms": first_switch_ms,
        "cache_hit": len(hits) > 0,
    }


def generate_report(first_run, second_run, prerender_result, adb_used):
    """生成 Markdown 报告"""
    lines = []
    lines.append("# AGenUI Widget 性能基线报告")
    lines.append("")
    lines.append(f"> 生成时间：{time.strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"> 测试设备：{DEVICE_DEFAULT if adb_used else '未连接设备（基于估算）'}")
    lines.append(f"> 模板数量：{len(TEMPLATES)}")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 1. 模板渲染时间（首次渲染）")
    lines.append("")
    lines.append("| 模板 | 首次渲染时间 (ms) | 缓存命中 |")
    lines.append("|------|------------------|---------|")
    for tpl in TEMPLATES:
        r = first_run.get(tpl, {})
        ms = r.get("first_ms")
        hit = r.get("cache_hit")
        ms_str = str(ms) if ms is not None else "N/A"
        hit_str = "✅ 是" if hit else "❌ 否"
        lines.append(f"| {tpl} | {ms_str} | {hit_str} |")
    lines.append("")

    # 汇总统计
    valid_times = [r.get("first_ms") for r in first_run.values() if r.get("first_ms") is not None]
    if valid_times:
        avg = sum(valid_times) / len(valid_times)
        max_t = max(valid_times)
        min_t = min(valid_times)
        lines.append(f"- **平均**：{avg:.1f} ms")
        lines.append(f"- **最快**：{min_t} ms")
        lines.append(f"- **最慢**：{max_t} ms")
    else:
        lines.append("- 无有效数据（设备未连接或日志未捕获）")
    lines.append("")

    lines.append("## 2. Bitmap 缓存效果（第二次渲染 vs 第一次）")
    lines.append("")
    lines.append("| 模板 | 第二次渲染 (ms) | 第二次缓存命中 | 加速比 |")
    lines.append("|------|---------------|---------------|--------|")
    for tpl in TEMPLATES:
        r1 = first_run.get(tpl, {})
        r2 = second_run.get(tpl, {})
        ms1 = r1.get("first_ms")
        ms2 = r2.get("second_ms")
        hit2 = r2.get("cache_hit_second")
        ms2_str = str(ms2) if ms2 is not None else "N/A"
        hit2_str = "✅ 是" if hit2 else "❌ 否"
        ratio = ""
        if ms1 and ms2 and ms2 > 0:
            ratio = f"{ms1/ms2:.1f}x"
        lines.append(f"| {tpl} | {ms2_str} | {hit2_str} | {ratio} |")
    lines.append("")

    lines.append("## 3. 预渲染（prerenderAll）效果")
    lines.append("")
    pr = prerender_result
    pr_ms = pr.get("prerender_ms")
    cs = pr.get("cache_size")
    sw_ms = pr.get("first_switch_ms")
    sw_hit = pr.get("cache_hit")
    lines.append(f"- **prerenderAll 总耗时**：{pr_ms if pr_ms else 'N/A'} ms")
    lines.append(f"- **预渲染后缓存大小**：{cs if cs else 'N/A'}")
    lines.append(f"- **预渲染后首次切换模板时间**：{sw_ms if sw_ms else 'N/A'} ms")
    lines.append(f"- **首次切换是否缓存命中**：{'✅ 是' if sw_hit else '❌ 否'}")
    lines.append("")

    lines.append("## 4. 性能基线目标与达成情况")
    lines.append("")
    lines.append("| 指标 | 目标 | 实测/估算 | 状态 |")
    lines.append("|------|------|----------|------|")
    avg_first = (sum(valid_times) / len(valid_times)) if valid_times else None
    lines.append(f"| 首次渲染平均时间 | ≤ 500 ms | {f'{avg_first:.0f} ms' if avg_first else 'N/A'} | {'✅' if avg_first and avg_first <= 500 else '⚠️'} |")
    lines.append(f"| 缓存命中后渲染时间 | ≤ 50 ms | {f'{sw_ms} ms' if sw_ms else 'N/A'} | {'✅' if sw_ms and sw_ms <= 50 else '⚠️'} |")
    lines.append(f"| prerenderAll 总耗时 | ≤ 3000 ms | {f'{pr_ms} ms' if pr_ms else 'N/A'} | {'✅' if pr_ms and pr_ms <= 3000 else '⚠️'} |")
    lines.append("")

    lines.append("## 5. 测量方法说明")
    lines.append("")
    lines.append("### 5.1 渲染时间测量")
    lines.append("从 logcat 提取 `renderSync: id=...,template=...` 时间戳作为渲染开始，")
    lines.append("`Widget updated: ...` 时间戳作为渲染完成，两者差值即为单次渲染时间。")
    lines.append("")
    lines.append("### 5.2 缓存命中率测量")
    lines.append("对每个模板连续渲染两次：")
    lines.append("- 第一次：冷渲染（无缓存），记录耗时")
    lines.append("- 第二次：热渲染（有缓存），记录耗时")
    lines.append("- 若第二次出现 `Bitmap cache HIT` 日志，则缓存命中")
    lines.append("")
    lines.append("### 5.3 预渲染效果测量")
    lines.append("启动 App 时自动触发 `prerenderAll`，预渲染所有模板到 Bitmap 缓存。")
    lines.append("测量 prerenderAll 总耗时，以及预渲染完成后首次切换模板的时间。")
    lines.append("")

    if not adb_used:
        lines.append("## ⚠️ 数据说明")
        lines.append("")
        lines.append("本次报告基于未连接设备模式生成，所有数据为 N/A。")
        lines.append("连接设备后运行 `python scripts/measure_performance.py` 获取真实数据。")
        lines.append("")

    return "\n".join(lines)


def main():
    device = DEVICE_DEFAULT
    no_adb = "--no-adb" in sys.argv

    if not no_adb:
        # 尝试连接设备
        adb(device, "connect")
        time.sleep(1)
        state = adb(device, "get-state").strip()
        if "device" not in state:
            print(f"设备 {device} 未在线，切换到 --no-adb 模式")
            no_adb = True

    if no_adb:
        # 无设备模式：生成空数据报告
        first_run = {tpl: {"first_ms": None, "cache_hit": False} for tpl in TEMPLATES}
        second_run = {tpl: {"second_ms": None, "cache_hit_second": False} for tpl in TEMPLATES}
        prerender_result = {"prerender_ms": None, "cache_size": None,
                             "first_switch_ms": None, "cache_hit": False}
    else:
        print("测量首次渲染时间...")
        first_run = measure_all(device, "first")
        print("测量缓存命中效果...")
        second_run = measure_cache_hit(device)
        print("测量预渲染效果...")
        prerender_result = measure_prerender(device)

    report = generate_report(first_run, second_run, prerender_result, not no_adb)

    DOCS.parent.mkdir(parents=True, exist_ok=True)
    with open(DOCS, "w", encoding="utf-8") as f:
        f.write(report)

    print(f"性能基线报告已生成：{DOCS}")
    if no_adb:
        print("（未连接设备，报告为空数据模板）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
