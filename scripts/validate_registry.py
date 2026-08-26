#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
validate_registry.py — AGenUI WidgetTemplateRegistry consistency checker

Verifies that:
  1. Every template registered in WidgetTemplateRegistry.java has a corresponding
     .json file in assets/widget_templates/
  2. Every template .json file in assets/widget_templates/ is registered
  3. Every template with a non-zero buttonId has a corresponding R.id.* entry
     referenced in the layout XML
  4. Every template has a corresponding string resource in strings.xml

Usage:
    python scripts/validate_registry.py
Exit code:
    0 = all checks pass
    1 = inconsistencies found
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = ROOT / "playground" / "android" / "app" / "src" / "main" / "assets" / "widget_templates"
REGISTRY_FILE = ROOT / "playground" / "android" / "app" / "src" / "main" / "java" / "com" / "amap" / "agenuiplayground" / "widget" / "WidgetTemplateRegistry.java"
LAYOUT_FILE = ROOT / "playground" / "android" / "app" / "src" / "main" / "res" / "layout" / "a2ui_widget_content.xml"
STRINGS_FILE = ROOT / "playground" / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"

errors = []
warnings = []

def main():
    print("=" * 70)
    print("AGenUI WidgetTemplateRegistry Consistency Check")
    print("=" * 70)

    # 1. Parse registry entries
    registry_content = REGISTRY_FILE.read_text(encoding='utf-8')
    # Pattern: new TemplateEntry("name", R.id.xxx or 0, R.string.xxx, Category.XXX)
    pattern = r'new TemplateEntry\(\s*"(\w+)"\s*,\s*(R\.id\.\w+|0)\s*,\s*(R\.string\.\w+)\s*,\s*Category\.(\w+)\s*\)'
    entries = re.findall(pattern, registry_content)

    if not entries:
        errors.append("No TemplateEntry registrations found in WidgetTemplateRegistry.java")
        print_results()
        return

    print(f"\nRegistry entries: {len(entries)}")

    registered_names = set()
    for name, button_id, string_res, category in entries:
        registered_names.add(name)

        # Check JSON file exists
        json_file = TEMPLATES_DIR / f"{name}.json"
        if not json_file.exists():
            errors.append(f"Template '{name}': JSON file not found at {json_file}")

        # Check string resource exists
        string_name = string_res.replace("R.string.", "")
        strings_content = STRINGS_FILE.read_text(encoding='utf-8')
        if f'name="{string_name}"' not in strings_content:
            errors.append(f"Template '{name}': string resource '{string_name}' not found in strings.xml")

        # Check button ID exists in layout (if non-zero)
        if button_id != "0":
            button_name = button_id.replace("R.id.", "")
            layout_content = LAYOUT_FILE.read_text(encoding='utf-8')
            if f'@+id/{button_name}' not in layout_content and f'@id/{button_name}' not in layout_content:
                errors.append(f"Template '{name}': button ID '{button_name}' not found in layout XML")

    # 2. Check for orphan JSON files (in assets but not registered)
    json_files = {f.stem for f in TEMPLATES_DIR.glob("*.json") if f.is_file()}
    orphans = json_files - registered_names
    for orphan in sorted(orphans):
        warnings.append(f"Template JSON '{orphan}.json' exists but is not registered in WidgetTemplateRegistry")

    # 3. Print entry details
    print(f"\n{'Name':<15} {'Button':<25} {'String':<30} {'Category':<15} {'JSON':<5}")
    print("-" * 95)
    for name, button_id, string_res, category in entries:
        json_ok = "OK" if (TEMPLATES_DIR / f"{name}.json").exists() else "MISS"
        print(f"{name:<15} {button_id:<25} {string_res:<30} {category:<15} {json_ok}")

    print_results()

def print_results():
    print("\n" + "=" * 70)
    if warnings:
        print(f"\nWarnings ({len(warnings)}):")
        for w in warnings:
            print(f"  [WARN] {w}")
    if errors:
        print(f"\nErrors ({len(errors)}):")
        for e in errors:
            print(f"  [FAIL] {e}")
        print(f"\n{'=' * 70}")
        print(f"FAILED: {len(errors)} error(s), {len(warnings)} warning(s)")
        sys.exit(1)
    else:
        print(f"\n{'=' * 70}")
        print(f"PASSED: 0 errors, {len(warnings)} warning(s)")
        sys.exit(0)

if __name__ == "__main__":
    main()
