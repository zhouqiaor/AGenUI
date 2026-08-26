#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
validate_templates.py — AGenUI Widget 模板完整性校验脚本

扫描 playground/android/app/src/main/assets/widget_templates/*.json，
对每个模板验证：
  1. JSON 格式有效
  2. 有 createSurface / updateComponents / updateDataModel 三段
  3. 所有组件 id 不重复
  4. 数据绑定 path 引用的字段在 dataModel 中存在
  5. 没有引用不存在的子组件 id

用法：
    python scripts/validate_templates.py
退出码：
    0 = 全部通过
    1 = 发现问题
"""
import json
import os
import re
import sys
from pathlib import Path

# 项目根目录（脚本位于 <root>/scripts/ 下）
ROOT = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = ROOT / "playground" / "android" / "app" / "src" / "main" / "assets" / "widget_templates"

REQUIRED_SECTIONS = ("createSurface", "updateComponents", "updateDataModel")
# 仅校验这三个段，updateDataModel 在部分早期模板中可能缺失，视为 warn


class Color:
    """简易 ANSI 颜色（Windows 10+ 支持）"""
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    BOLD = "\033[1m"
    RESET = "\033[0m"


def c(text, color):
    return f"{color}{text}{Color.RESET}"


def collect_component_ids(node, acc):
    """递归收集组件树中所有 id（包括 children）"""
    if isinstance(node, dict):
        cid = node.get("id")
        if cid:
            acc.append(cid)
        for child in node.get("children", []) or []:
            collect_component_ids(child, acc)
        # components 数组
        for comp in node.get("components", []) or []:
            collect_component_ids(comp, acc)
    elif isinstance(node, list):
        for item in node:
            collect_component_ids(item, acc)


def find_duplicate_ids(ids):
    """返回重复的 id 列表"""
    seen = {}
    dups = []
    for cid in ids:
        seen[cid] = seen.get(cid, 0) + 1
    for cid, cnt in seen.items():
        if cnt > 1:
            dups.append((cid, cnt))
    return dups


def extract_binding_paths(obj):
    """从组件树中提取数据绑定 path（支持 ${...} 插值与 dataPath/dataBinding 字段）"""
    paths = []
    if isinstance(obj, dict):
        for key, val in obj.items():
            if key in ("dataPath", "dataBinding", "binding"):
                if isinstance(val, str):
                    paths.append(val)
                elif isinstance(val, dict) and "path" in val:
                    paths.append(val["path"])
            elif isinstance(val, str):
                # ${expr} 形式插值
                for m in re.finditer(r"\$\{([^}]+)\}", val):
                    paths.append(m.group(1).strip())
            else:
                paths.extend(extract_binding_paths(val))
    elif isinstance(obj, list):
        for item in obj:
            paths.extend(extract_binding_paths(item))
    return paths


def parse_path(path_str):
    """把 a.b.c 拆成段；支持过滤器 | （取第一段表达式）"""
    # 去掉过滤器
    base = path_str.split("|")[0].strip()
    # 去掉函数调用等
    base = base.split("(")[0].strip()
    return [seg.strip() for seg in base.split(".") if seg.strip()]


def path_exists_in_model(path_str, model):
    """检查 path 是否存在于 dataModel 中（支持 a.b.c 嵌套 + 数组索引）"""
    if not path_str or not isinstance(model, dict):
        return True  # 空路径或无模型时不强校验
    segs = parse_path(path_str)
    cur = model
    for seg in segs:
        if seg.isdigit():
            idx = int(seg)
            if isinstance(cur, list) and 0 <= idx < len(cur):
                cur = cur[idx]
            else:
                return False
        elif isinstance(cur, dict) and seg in cur:
            cur = cur[seg]
        elif isinstance(cur, list) and cur:
            # 数组字段，取第一个元素继续
            if isinstance(cur[0], dict) and seg in cur[0]:
                cur = cur[0][seg]
            else:
                return False
        else:
            return False
    return True


def validate_template(file_path):
    """校验单个模板文件，返回 (ok, errors, warnings)"""
    errors = []
    warnings = []
    rel = file_path.relative_to(ROOT)

    # 1. JSON 格式
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            raw = f.read()
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        errors.append(f"{rel}: JSON 解析失败 — {e}")
        return False, errors, warnings
    except Exception as e:
        errors.append(f"{rel}: 文件读取失败 — {e}")
        return False, errors, warnings

    if not isinstance(data, list):
        errors.append(f"{rel}: 顶层应为数组")
        return False, errors, warnings

    # 2. 三段结构
    types_found = {}
    for idx, entry in enumerate(data):
        if isinstance(entry, dict) and "type" in entry:
            t = entry["type"]
            types_found[t] = idx

    for sec in REQUIRED_SECTIONS:
        if sec not in types_found:
            if sec == "updateDataModel":
                warnings.append(f"{rel}: 缺少 updateDataModel 段（静态模板可豁免）")
            else:
                errors.append(f"{rel}: 缺少 {sec} 段")

    # 校验 surfaceId 一致性
    surface_ids = set()
    for entry in data:
        if isinstance(entry, dict) and "surfaceId" in entry:
            surface_ids.add(entry["surfaceId"])
    if len(surface_ids) > 1:
        errors.append(f"{rel}: 多个 surfaceId 不一致 — {surface_ids}")

    # 3. 组件 id 唯一性
    all_ids = []
    if "updateComponents" in types_found:
        uc = data[types_found["updateComponents"]]
        components = uc.get("components", []) if isinstance(uc, dict) else []
        for comp in components:
            collect_component_ids(comp, all_ids)
    dups = find_duplicate_ids(all_ids)
    for cid, cnt in dups:
        errors.append(f"{rel}: 组件 id 重复 — '{cid}' 出现 {cnt} 次")

    # 4. 数据绑定 path 校验
    model = None
    if "updateDataModel" in types_found:
        udm = data[types_found["updateDataModel"]]
        model = udm.get("value", {}) if isinstance(udm, dict) else {}

    if model and "updateComponents" in types_found:
        uc = data[types_found["updateComponents"]]
        components = uc.get("components", []) if isinstance(uc, dict) else []
        paths = extract_binding_paths(components)
        for p in paths:
            if not path_exists_in_model(p, model):
                # 绑定 path 找不到对应字段（仅 warn，因为可能是动态计算字段）
                warnings.append(f"{rel}: 数据绑定 path '{p}' 在 dataModel 中找不到")

    # 5. 子组件引用完整性（检查 components 树中没有悬空引用）
    # AGenUI 模板用 children 数组组织，不会引用外部 id；
    # 这里校验 children 中所有 id 都在 all_ids 中（恒成立），
    # 以及没有 "componentId"/"ref" 引用不存在的 id
    ref_fields = ("componentId", "ref", "targetId", "forId")
    if "updateComponents" in types_found:
        uc = data[types_found["updateComponents"]]
        components = uc.get("components", []) if isinstance(uc, dict) else []
        id_set = set(all_ids)
        refs = []
        for comp in components:
            refs.extend(_collect_refs(comp, ref_fields))
        for r in refs:
            if r not in id_set:
                errors.append(f"{rel}: 引用了不存在的组件 id — '{r}'")

    ok = len(errors) == 0
    return ok, errors, warnings


def _collect_refs(obj, fields):
    """收集 ref/componentId/targetId 等字段值"""
    refs = []
    if isinstance(obj, dict):
        for key, val in obj.items():
            if key in fields and isinstance(val, str):
                refs.append(val)
            else:
                refs.extend(_collect_refs(val, fields))
    elif isinstance(obj, list):
        for item in obj:
            refs.extend(_collect_refs(item, fields))
    return refs


def main():
    if not TEMPLATES_DIR.exists():
        print(c(f"错误: 模板目录不存在 — {TEMPLATES_DIR}", Color.RED))
        return 1

    template_files = sorted(TEMPLATES_DIR.glob("*.json"))
    if not template_files:
        print(c("错误: 未找到任何模板文件", Color.RED))
        return 1

    print(c("=" * 70, Color.BLUE))
    print(c("AGenUI Widget 模板完整性校验报告", Color.BOLD))
    print(c("=" * 70, Color.BLUE))
    print(f"模板目录: {TEMPLATES_DIR}")
    print(f"模板数量: {len(template_files)}")
    print()

    total_ok = 0
    total_err = 0
    total_warn = 0
    failed_files = []

    for tf in template_files:
        ok, errors, warnings = validate_template(tf)
        name = tf.name
        if ok:
            status = c("PASS", Color.GREEN)
            total_ok += 1
        else:
            status = c("FAIL", Color.RED)
            total_err += len(errors)
            failed_files.append(name)
        warn_tag = c(f" {len(warnings)} warn", Color.YELLOW) if warnings else ""
        print(f"  [{status}{warn_tag}] {name}")

        for e in errors:
            print(f"      " + c("ERROR: ", Color.RED) + e)
            total_err += 0  # 已计入
        for w in warnings:
            print(f"      " + c("WARN:  ", Color.YELLOW) + w)
            total_warn += 1

    print()
    print(c("-" * 70, Color.BLUE))
    summary_color = Color.GREEN if total_err == 0 else Color.RED
    print(c(f"总计: {len(template_files)} 模板 | {total_ok} 通过 | {total_err} 错误 | {total_warn} 警告",
            summary_color))
    if failed_files:
        print(c("失败文件: " + ", ".join(failed_files), Color.RED))

    if total_err > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
