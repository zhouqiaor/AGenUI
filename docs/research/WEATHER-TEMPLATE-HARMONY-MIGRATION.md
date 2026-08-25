# 天气/议程/待办 三模板鸿蒙风格迁移分析

> 日期：2026-08-25
> 分析对象：`playground/android/app/src/main/assets/widget_templates/{weather,agenda,todo}.json`
> 代码层：`WidgetRenderActivity.java` + `a2ui_widget_content.xml`
> 对比基准：`docs/harmony/references/design-tokens.json`（HarmonyOS Design 1.2.0）

---

## 1. 三模板当前风格逐项分析

### 1.1 Weather 模板（weather.json）

| 属性 | 当前值 | 鸿蒙等价 | 差异说明 |
|------|--------|---------|----------|
| Card 背景 | `linear-gradient(135deg, #667eea, #764ba2)` | `surface #FFFFFF` | 🔴 紫蓝渐变 → 白底（鸿蒙不用渐变做卡片背景） |
| Card 圆角 | `16` | `radius_lg 16vp` | ✅ 数值一致 |
| Card 内边距 | `20` | `space_lg 24vp` 或 `space_md 16vp` | 🟡 20 非标准（非 4 倍数），应改 16 或 24 |
| 城市名 字号 | `h4` (≈24sp) | `subtitle 18fp Medium` | 🔴 偏大 |
| 城市名 色值 | `#FFFFFF` | `text_primary #181818` | 🔴 白字 → 深色字（底色从渐变换白底） |
| 温度 字号 | `h1` (≈32sp) Bold | `display 28-30fp Bold` | 🟡 接近，鸿蒙 display 略小 |
| 温度 色值 | `#FFFFFF` | `text_primary #181818` 或 `brand #007DFF` | 🔴 白字 → 深色或品牌色 |
| 温度 字重 | `bold` | `Bold` | ✅ 一致 |
| 天气描述 字号 | `body1` (≈16sp) | `body 16fp Regular` | ✅ 一致 |
| 天气描述 色值 | `#E0E0E0` | `text_secondary #666666` | 🔴 浅灰 → 中灰 |
| 湿度/风速 字号 | `caption` (≈12sp) | `overline 12fp` 或 `caption 14fp` | 🟡 鸿蒙 caption=14fp |
| 湿度/风速 色值 | `#E0E0E0` | `text_tertiary #999999` | 🔴 浅灰 → 中灰 |
| Emoji 图标 | `💧` / `🌬` | 应替换为系统图标 | 🟡 鸿蒙推荐系统图标资源 |

**风格定位**：Weather 模板当前是「紫蓝渐变 + 全白文字」的 Material 风格，视觉上像一张天气卡片海报，但与鸿蒙「白底 + 品牌色点缀 + 深浅文字层次」完全不同。

### 1.2 Agenda 模板（agenda.json）

| 属性 | 当前值 | 鸿蒙等价 | 差异说明 |
|------|--------|---------|----------|
| Card 背景 | `#F0F4FF` | `surface #FFFFFF` | 🟡 浅蓝底 → 白底 |
| Card 圆角 | `16` | `radius_lg 16vp` | ✅ 一致 |
| Card 内边距 | `16` | `space_md 16vp` | ✅ 一致 |
| 标题 字号 | `h5` (≈20sp) | `title 22fp Medium` | 🟡 偏小 |
| 标题 色值 | `#333333` Bold | `text_primary #181818` Medium | 🟡 色值偏浅 + 字重应改 Medium |
| 条目文字 字号 | `body2` (≈14sp) | `body 16fp` | 🟡 偏小 |
| 条目文字 色值 | `#555555` | `text_primary #181818` | 🟡 偏浅 |
| 时间 字号 | `caption` (≈12sp) | `overline 12fp` 或 `caption 14fp` | 🟡 鸿蒙 caption=14fp |
| 时间 色值 | `#999999` | `text_tertiary #999999` | ✅ 一致 |
| 条目间无分隔线 | 无 | 鸿蒙列表项有 `divider` 线 | 🔴 缺少分隔线 |

**风格定位**：Agenda 模板是「浅蓝底 + 深灰文字」的 Material 风格，排版已比较克制，但色值偏离鸿蒙规范、缺少列表分隔线、字号偏小。

### 1.3 Todo 模板（todo.json）

| 属性 | 当前值 | 鸿蒙等价 | 差异说明 |
|------|--------|---------|----------|
| Card 背景 | `#F0FFF4` | `surface #FFFFFF` | 🟡 浅绿底 → 白底 |
| Card 圆角 | `16` | `radius_lg 16vp` | ✅ 一致 |
| Card 内边距 | `16` | `space_md 16vp` | ✅ 一致 |
| 标题 字号 | `h5` (≈20sp) | `title 22fp Medium` | 🟡 偏小 |
| 标题 色值 | `#333333` Bold | `text_primary #181818` Medium | 🟡 偏浅 + 字重 |
| 已完成任务色 | `#999999` | `text_tertiary #999999` | ✅ 一致 |
| 未完成任务色 | `#555555` | `text_primary #181818` | 🟡 偏浅 |
| 进度文字 字号 | `caption` (≈12sp) | `overline 12fp` | 🟡 鸿蒙 caption=14fp |
| 进度文字 色值 | `#888888` | `text_tertiary #999999` | 🟡 接近 |
| 条目间无分隔线 | 无 | 应有 divider 线 | 🔴 缺少 |
| CheckBox 勾选色 | 默认 Material | `brand #007DFF` | 🔴 勾选色应改品牌蓝 |

**风格定位**：Todo 模板是「浅绿底 + CheckBox + 灰文字」的 Material 风格，功能完整但视觉上缺少品牌色点缀和层次感。

---

## 2. 三模板共性问题汇总

| # | 问题 | 影响 | 迁移难度 |
|---|------|------|----------|
| 1 | 色值硬编码，无令牌引用 | 全局 | 低（替换值） |
| 2 | Weather 用渐变背景（鸿蒙禁止） | 视觉违和 | 低 |
| 3 | Agenda/Todo 用浅色底而非白底 | 视觉不一致 | 低 |
| 4 | 字号未对齐鸿蒙 6 档阶梯 | 排版 | 低 |
| 5 | 标题字重全 Bold（鸿蒙用 Medium） | 排版 | 低 |
| 6 | 文字色值偏离鸿蒙三级灰 | 层次 | 低 |
| 7 | 缺少列表分隔线 | 列表规范 | 中（需加组件） |
| 8 | 无品牌色点缀 | 品牌一致性 | 低 |
| 9 | padding=20 非标准间距 | 间距 | 低 |

---

## 3. 鸿蒙风格版模板

### 3.1 Weather 模板（鸿蒙版）

核心变化：
- 渐变背景 → `#FFFFFF` 白底
- 白字 → 深色文字 `#181818` + 次级 `#666666`
- 温度用品牌色 `#007DFF` 点缀
- padding 20 → 16
- 字号对齐鸿蒙阶梯
- 增加天气图标区域（品牌浅底 `#E8F3FF`）
- 底部增加预报行

### 3.2 Agenda 模板（鸿蒙版）

核心变化：
- `#F0F4FF` → `#FFFFFF`
- 标题 22fp Medium `#181818`
- 条目间增加 divider 线
- 时间用品牌色 `#007DFF`
- 选中条目用 `brand_surface #E8F3FF` 背景

### 3.3 Todo 模板（鸿蒙版）

核心变化：
- `#F0FFF4` → `#FFFFFF`
- CheckBox 勾选色 → `#007DFF`
- 已完成任务文字加删除线
- 进度文字用品牌色
- 条目间增加 divider 线

---

## 4. WidgetRenderActivity 代码层硬编码迁移

### 4.1 当前硬编码点

| 位置 | 代码 | 鸿蒙等价 | 迁移方式 |
|------|------|---------|----------|
| `drawAndPush()` L199 | `MeasureSpec.makeMeasureSpec(300, ...)` | 应从 Widget 尺寸动态获取 | 从 `AppWidgetManager` 获取 Widget 实际宽高 |
| `drawAndPush()` L205 | `if (h <= 0) h = 200;` | 同上 | 同上 |
| `drawAndPush()` L214 | `canvas.drawColor(Color.WHITE)` | `harmony_surface #FFFFFF` | 引用资源色值 |
| `pushToWidget()` L229 | `"AGenUI · " + template` | 应中文化 | `"AGenUI · 天气/议程/待办"` |
| `pushToWidget()` L271 | `0xFF6200EE` (选中态) | `0xFF007DFF` (harmony_brand) | 替换色值 |
| `pushToWidget()` L271 | `0xFF666666` (未选中) | `0xFF999999` (text_tertiary) | 替换色值 |
| `pushToWidget()` L232 | `800_000` bytes 阈值 | — | 合理，可保留 |

### 4.2 鸿蒙化后关键代码变更

```java
// 颜色令牌引用（替代硬编码）
int colorSelected = context.getColor(R.color.harmony_brand);     // #007DFF
int colorUnselected = context.getColor(R.color.harmony_text_tertiary); // #999999

// Widget 尺寸动态获取（替代固定 300）
Bundle options = awm.getAppWidgetOptions(appWidgetId);
int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
int widthSpec = View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY);

// 画布背景（替代 Color.WHITE）
canvas.drawColor(context.getColor(R.color.harmony_surface)); // #FFFFFF

// 模板中文名
String[] templateNames = {"天气", "议程", "待办"};
views.setTextViewText(R.id.widgetTitle, "AGenUI · " + templateNames[templateIndex]);
```

---

## 5. 迁移优先级

| 优先级 | 任务 | 文件 | 预估 |
|--------|------|------|------|
| P0 | 替换三模板 JSON 为鸿蒙版 | `assets/widget_templates/*.json` | 0.5 天 |
| P0 | WidgetRenderActivity 色值令牌化 | `WidgetRenderActivity.java` | 0.5 天 |
| P0 | a2ui_widget_content.xml 令牌化 | `res/layout/a2ui_widget_content.xml` | 0.5 天 |
| P1 | Widget 尺寸动态获取 | `WidgetRenderActivity.java` | 0.5 天 |
| P1 | 模板中文名称 | `WidgetProtocolTemplates.java` | 0.2 天 |
