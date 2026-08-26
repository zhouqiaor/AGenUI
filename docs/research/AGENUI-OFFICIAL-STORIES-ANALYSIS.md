# AGenUI 官方案例库全分析 — 26 组件 + 3 层样式体系

> 日期：2026-08-25
> 分析对象：
> - 鸿蒙端案例库：`playground/harmony/entry/src/main/resources/rawfile/stories/A2UI Show/`（26 组件 + 1 Gallery 全景）
> - 安卓端默认样式：`platforms/android/src/main/assets/component_styles.json`
> - 鸿蒙端资源：`playground/harmony/entry/src/main/resources/base/element/{color,float,string}.json` + `dark/element/color.json`
> 对比基准：`docs/harmony/references/design-tokens.json`（HarmonyOS Design 1.2.0）

---

## 1. 三层样式体系发现

AGenUI 存在 **三层样式定义体系**，这是此前分析中未发现的关键架构：

```
┌──────────────────────────────────────────────────────────────────┐
│ 层 1: 平台默认样式 (component_styles.json)                       │
│   Android: 硬编码色值 #2273F7 / #2E82FF / #1A66FF               │
│   Harmony: rawfile 中的单组件 story（同样硬编码）                │
├──────────────────────────────────────────────────────────────────┤
│ 层 2: 组件级 story (stories/A2UI Show/<Component>/)              │
│   每个 updateComponents.json — 单组件 demo，硬编码色值            │
│   示例：Button 用 #000000 边框，Card 用 #FFFFFF + drop-shadow    │
├──────────────────────────────────────────────────────────────────┤
│ 层 3: Gallery 全景 (stories/A2UI Show/Gallery/)                  │
│   使用令牌引用系统 {"call":"token","args":{"name":"Color_BG_L2"}}│
│   这是 AGenUI 设计的「正确用法」— 全令牌化                       │
└──────────────────────────────────────────────────────────────────┘
```

### 关键发现

| 维度 | 单组件 story（层 2） | Gallery（层 3） | 差异 |
|------|---------------------|-----------------|------|
| 色值引用 | 硬编码 `#FFFFFF` | 令牌 `{"call":"token","args":{"name":"Color_BG_L2"}}` | 根本性差异 |
| 间距 | 硬编码 `20px` | 硬编码 `20px`（未令牌化间距） | 一致 |
| 阴影 | `drop-shadow(0px 6px 24px rgba(0,0,0,0.08))` | `drop-shadow(0px 2px 8px rgba(0,0,0,0.06))` | Gallery 更轻 |
| 卡片圆角 | 16px | 16px | 一致 |
| 内卡片圆角 | — | 12px | Gallery 有嵌套 |
| 文字色 | 硬编码 `#000000` / `#000000E6` | 令牌 `Color_Text_L1` / `Color_Text_Body` | 根本性差异 |

---

## 2. AGenUI 令牌系统逆向解析

Gallery 文件中出现的所有令牌名，按类别整理：

### 2.1 背景色令牌（Color_BG_*）

| 令牌名 | 推断用途 | 鸿蒙等价 |
|--------|---------|---------|
| `Color_BG_L1` | 页面背景（最底层） | `surface_muted #F5F6F7` |
| `Color_BG_L2` | 卡片背景（第二层） | `surface #FFFFFF` |
| `Color_BG_L3` | 内嵌卡片/嵌套元素背景 | `surface_muted #F5F6F7` 或更浅 |
| `Color_BG_Brand` | 品牌色背景（主按钮） | `brand #007DFF` |

### 2.2 文字色令牌（Color_Text_*）

| 令牌名 | 推断用途 | 鸿蒙等价 |
|--------|---------|---------|
| `Color_Text_L1` | 主文字 | `text_primary #181818` |
| `Color_Text_L2` | 次级文字 | `text_secondary #666666` |
| `Color_Text_Body` | 正文文字 | `text_primary #181818` 或 `text_secondary` |
| `Color_Text_Highlight` | 高亮文字（按钮上的白字） | `text_inverse #FFFFFF` |

### 2.3 墨色/分隔线令牌（Color_Ink_*）

| 令牌名 | 推断用途 | 鸿蒙等价 |
|--------|---------|---------|
| `Color_Ink_L1` | 深色块（次按钮背景） | 接近 `text_primary #181818` |
| `Color_Ink_L2` | 中深色块 | 接近 `text_secondary` |
| `Color_Ink_L3` | 分隔线/描边 | `divider #E8EAED` |
| `Color_Ink_L5` | 浅分隔线 | `divider` 更浅变体 |

### 2.4 灰阶令牌

| 令牌名 | 推断用途 | 鸿蒙等价 |
|--------|---------|---------|
| `Color_Gray_20` | 边框色（Outline 按钮） | `divider #E8EAED` |

### 2.5 令牌系统 vs 鸿蒙规范映射

```
AGenUI 令牌系统               鸿蒙 design-tokens.json
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Color_BG_L1 (页面底)     →    surface_muted #F5F6F7
Color_BG_L2 (卡片底)     →    surface #FFFFFF
Color_BG_L3 (嵌套底)     →    surface_muted #F5F6F7
Color_BG_Brand (品牌底)  →    brand #007DFF
Color_Text_L1 (主文字)   →    text_primary #181818
Color_Text_L2 (次文字)   →    text_secondary #666666
Color_Text_Body (正文)   →    text_primary #181818
Color_Text_Highlight     →    text_inverse #FFFFFF
Color_Ink_L1 (深色块)    →    text_primary #181818
Color_Ink_L3 (分隔线)    →    divider #E8EAED
Color_Ink_L5 (浅分隔)    →    divider #E8EAED
Color_Gray_20 (边框)     →    divider #E8EAED
```

**结论**：AGenUI 的令牌系统与鸿蒙规范**理念一致**（分层背景 + 分级文字 + 品牌色），但令牌名不同（`Color_BG_L1` vs `surface_muted`），且数量更少（~12 个 vs 鸿蒙 ~20+ 个）。

---

## 3. Android component_styles.json 深度分析

### 3.1 品牌色差异（关键发现）

| 组件 | Android 默认色值 | 鸿蒙 brand #007DFF | 差异 |
|------|-----------------|-------------------|------|
| Tabs 指示器 | `#2273F7` | `#007DFF` | 🔴 不同蓝！ |
| Tabs 选中文字 | `#2273F7` | `#007DFF` | 🔴 |
| CheckBox 选中 | `#2E82FF` | `#007DFF` | 🔴 另一个蓝 |
| ChoicePicker 选中 | `#2E82FF` | `#007DFF` | 🔴 |
| Slider 轨道 | `#1A66FF` | `#007DFF` | 🔴 第三个蓝 |
| AudioPlayer | `#2273F7` | `#007DFF` | 🔴 |
| DateTimeInput | `#2273F7` | `#007DFF` | 🔴 |

**关键发现**：AGenUI Android 端使用了 **至少 3 种不同的蓝色**（`#2273F7` / `#2E82FF` / `#1A66FF`），全部偏离鸿蒙品牌色 `#007DFF`。这说明 AGenUI Android 端没有对齐鸿蒙设计规范，而是用了高德地图自己的蓝色体系。

### 3.2 组件级样式逐项分析

#### Button
- `disabled-opacity: 0.4` — ✅ 与鸿蒙一致（禁用 40% 透明度）

#### CheckBox
- `checkbox-size: 32px` → 鸿蒙无固定值，但 32px 合理
- `checkbox-border-radius: 12px` → 鸿蒙圆角 8vp（偏大）
- `checkbox-border-width: 3px` → 鸿蒙无规范，3px 偏粗
- 选中色 `#2E82FF` → 🔴 应为 `#007DFF`

#### ChoicePicker
- chip `border-radius: 40px` → 胶囊形 ✅
- chip 选中色 `#2E82FF` → 🔴 应为 `#007DFF`
- filter 搜索框有完整样式（placeholder/hint）→ 功能完善

#### Slider
- `track-height: 4px` → 合理
- `thumb-outer-diameter: 48px` → 接近鸿蒙触摸目标 48vp
- `minimum-track-color: #1A66FF` → 🔴 应为 `#007DFF`
- `thumb-inner-color: #1A66FF` → 🔴 应为 `#007DFF`

#### Tabs
- `indicator-color: #2273F7` → 🔴 应为 `#007DFF`
- `indicator-width: 48px, height: 8px, radius: 4px` → 鸿蒙无精确规范但合理
- `tab-font-size: 32px` → 鸿蒙 body 16fp，32px 偏大（但这是 vp 不是 fp）
- `tab-font-weight-selected: bold` → 鸿蒙应 Medium

#### Table
- `header-bg-color: #EEEFF2` → 鸿蒙 `surface_muted #F5F6F7` 接近
- `body-bg-color: ["#FFFFFF", "#F6F7F8"]` → 斑马纹，鸿蒙无此规范但合理
- `header-font-weight: bold` → 鸿蒙应 Medium
- `border-radius: 16px` → 表格圆角偏大，鸿蒙表格通常 8-12vp

#### DateTimeInput
- 三种模式：compact / wheels-2col / wheels-3col / wheels-5col
- `selected-color: #2273F7` → 🔴 应为 `#007DFF`
- `popup-mask-color: #00000066` → 40% 黑色遮罩，鸿蒙 `rgba(0,0,0,0.45)` 接近
- `popup-corner-radius: 12px` → 鸿蒙 `radius_md 12vp` ✅

#### Carousel
- 指示器：active 24px 宽 + inactive 6px 宽 → 合理的差异设计
- `indicator-active-dot-color: #00000099` → 60% 黑，合理
- `image-placeholder-color: #F2F2F7` → 接近鸿蒙 `surface_muted`

#### Modal
- `overlay-color: rgba(0,0,0,0.5)` → 鸿蒙 `rgba(0,0,0,0.45)` 接近
- `show-close-button: false` → 不显示关闭按钮，依赖点击遮罩关闭

---

## 4. 26 个官方案例组件清单

### 4.1 组件分类与案例风格

| # | 组件 | 分类 | 单 story 风格 | Gallery 令牌化 | 关键属性 |
|---|------|------|-------------|---------------|---------|
| 1 | **Text** | 基础展示 | variant h1-h5/body/caption | ✅ | 7 级字体阶梯 |
| 2 | **Image** | 基础展示 | url + styles | ✅ | 12px 圆角 |
| 3 | **Icon** | 基础展示 | name + 48px | ✅ | Material Icons 图标库 |
| 4 | **Lottie** | 基础展示 | url + autoPlay + loop | ✅ | Lottie 动画 |
| 5 | **Column** | 布局 | children + align + gap | ✅ | 垂直布局 |
| 6 | **Row** | 布局 | children + align + justify + gap | ✅ | 水平布局 |
| 7 | **Divider** | 布局 | axis + height + color | ✅ token | 分隔线 |
| 8 | **Card** | 布局 | child + padding + radius + bg + shadow | ✅ token | 16px 圆角 + drop-shadow |
| 9 | **Button** | 输入交互 | child + action + styles | ✅ token | 80px 高 + 16px 圆角 + toast |
| 10 | **TextField** | 输入交互 | label + placeholder + value | ✅ | 输入框 |
| 11 | **CheckBox** | 输入交互 | label + value | ✅ | 复选框 |
| 12 | **Slider** | 输入交互 | value + min + max | ✅ | 滑块 |
| 13 | **ChoicePicker** | 输入交互 | options + variant + value | ✅ | 单选/多选 + chip 模式 |
| 14 | **DateTimeInput** | 输入交互 | enableDate + enableTime + value | ✅ | 日期/时间/日期时间三模式 |
| 15 | **Tabs** | 导航布局 | tabs[] + selectedIndex | ✅ | 标签页 |
| 16 | **List** | 导航布局 | children + align | ✅ token | 列表容器 |
| 17 | **Carousel** | 导航布局 | autoplay + draggable + content[] | ✅ | 轮播图 |
| 18 | **Modal** | 导航布局 | trigger + content + visible | ✅ | 模态弹窗 |
| 19 | **Table** | 数据展示 | columns + rows + styles | ✅ token | 表格 |
| 20 | **Chart** | 数据展示 | chartType + data + chartConfig | ✅ | bar/line/donut 三类型 |
| 21 | **Markdown** | 富内容 | content (markdown string) | ✅ | Markdown 渲染 |
| 22 | **RichText** | 富内容 | text (HTML string) | ✅ | HTML 富文本 |
| 23 | **AudioPlayer** | 媒体 | url | ✅ | 音频播放器（圆形按钮） |
| 24 | **Video** | 媒体 | url + autoPlay + controls | ✅ | 视频播放器 |
| 25 | **Web** | 媒体 | source + styles | ✅ | WebView 嵌入 |
| 26 | **Gallery** | 全景 | — | ✅ 全令牌 | 所有组件展示页 |

### 4.2 组件能力矩阵

```
基础展示 (4):  Text / Image / Icon / Lottie
布局     (4):  Column / Row / Divider / Card
输入交互 (6):  Button / TextField / CheckBox / Slider / ChoicePicker / DateTimeInput
导航布局 (4):  Tabs / List / Carousel / Modal
数据展示 (2):  Table / Chart
富内容   (2):  Markdown / RichText
媒体     (3):  AudioPlayer / Video / Web
全景     (1):  Gallery
```

### 4.3 Text variant 体系

Gallery 中确认的 Text variant 阶梯：

| variant | 用途 | 鸿蒙等价 |
|---------|------|---------|
| h1 | 最大标题 | display 28-30fp Bold |
| h2 | 区块大标题 | title 22fp Medium |
| h3 | 区块标题 | subtitle 18fp Medium |
| h4 | 卡片标题 | h3 16fp Medium |
| h5 | 小标题 | caption 14fp |
| body | 正文 | body 16fp Regular |
| caption | 辅助 | overline 12fp |

**差异**：AGenUI 有 7 级（h1-h5 + body + caption），鸿蒙有 6 级（display/title/subtitle/body/caption/overline）。h4 对应鸿蒙 h3，h5 对应 caption，命名不同但层级相似。

---

## 5. 单组件 story vs Gallery 的样式差异

### 5.1 Card 组件对比

| 属性 | 单 story (Card/updateComponents.json) | Gallery |
|------|--------------------------------------|---------|
| 背景 | `#FFFFFF` 硬编码 | `Color_BG_L2` 令牌 |
| 圆角 | 16px | 16px |
| 内边距 | 24px | 20px |
| 阴影 | `drop-shadow(0px 6px 24px rgba(0,0,0,0.08))` | `drop-shadow(0px 2px 8px rgba(0,0,0,0.06))` |
| 标题字号 | 32px Bold | 32px Bold |
| 正文字号 | 28px | 28px |
| 正文色 | `#000000E6` (90% 黑) | `Color_Text_Body` 令牌 |

**关键差异**：Gallery 阴影更轻（2px 8px vs 6px 24px），内边距更小（20 vs 24），使用了令牌引用。

### 5.2 Button 组件对比

| 属性 | 单 story | Gallery |
|------|---------|---------|
| 宽度 | 670px | 100% |
| 高度 | 80px | 80px |
| 圆角 | 16px | 16px (primary) / 12px (modal trigger) |
| 背景 | 无（透明 + 边框） | `Color_BG_Brand` 令牌 (primary) |
| 边框 | `1px rgba(0,0,0,0.4)` | `Color_Gray_20` 令牌 (outline) |
| 文字色 | 默认黑 | `Color_Text_Highlight` 令牌 (白) |
| action | toast | toast |

**关键差异**：Gallery 有 3 种按钮（primary 品牌底白字 / secondary 深底浅字 / outline 描边），单 story 只有 1 种（边框 + 默认色）。

---

## 6. A2UI Widget 三模板 vs 官方案例对比

### 6.1 Weather 模板 vs 官方 Card 案例

| 属性 | Widget Weather | 官方 Card story | 官方 Gallery |
|------|---------------|----------------|-------------|
| 背景 | 渐变 `#667eea→#764ba2` | `#FFFFFF` | `Color_BG_L2` 令牌 |
| 圆角 | 16 | 16 | 16 |
| 内边距 | 20 | 24 | 20 |
| 阴影 | 无 | `6px 24px 0.08` | `2px 8px 0.06` |
| 文字色 | `#FFFFFF` | `#000000` / `#000000E6` | 令牌 |
| variant | h4/h1/body1/caption | 无 variant | h1-h5/body/caption |

**结论**：Widget 模板完全偏离官方案例风格——用渐变代替白底、用白字代替深色字、用 Emoji 代替 Icon 组件。

### 6.2 Agenda 模板 vs 官方 List 案例

| 属性 | Widget Agenda | 官方 List story | 官方 Gallery |
|------|--------------|----------------|-------------|
| 背景 | `#F0F4FF` | 无背景 | `Color_BG_L2` |
| 条目 | Row + Text | Text 直接子项 | Text + padding |
| 分隔线 | 无 | 无 | `Color_Ink_L3` 描边 |
| 条目内边距 | 无 | 无 | `12px 16px` |
| variant | h5/body2/caption | body | body + padding |

**结论**：Widget Agenda 缺少条目内边距和分隔线，与 Gallery 的 List 案例差距大。

### 6.3 Todo 模板 vs 官方 CheckBox 案例

| 属性 | Widget Todo | 官方 CheckBox story | 官方 Gallery |
|------|------------|---------------------|-------------|
| 背景 | `#F0FFF4` | 无 | `Color_BG_L2` |
| CheckBox 属性 | `checked` | `label` + `value` | `label` + `value` |
| CheckBox 色值 | 无（默认 Material 紫） | 无 | `#2E82FF`（component_styles） |
| variant | h5/body2/caption | 无 | h4 |

**结论**：Widget Todo 的 CheckBox 用 `checked` 而官方用 `value`，API 不一致。CheckBox 选中色官方是 `#2E82FF`（偏离鸿蒙 `#007DFF`）。

---

## 7. 鸿蒙化迁移建议

### 7.1 令牌系统对接

AGenUI 已有令牌系统（`Color_BG_L1` 等），迁移策略：

1. **定义鸿蒙令牌值**：将 AGenUI 令牌映射到鸿蒙色值
   - `Color_BG_L1` → `#F5F6F7` (surface_muted)
   - `Color_BG_L2` → `#FFFFFF` (surface)
   - `Color_BG_Brand` → `#007DFF` (brand)
   - `Color_Text_L1` → `#181818` (text_primary)
   - `Color_Text_Highlight` → `#FFFFFF` (text_inverse)
   - `Color_Ink_L3` → `#E8EAED` (divider)

2. **Widget 模板迁移到令牌引用**：将 weather/agenda/todo 的硬编码色值改为 `{"call":"token","args":{"name":"Color_BG_L2"}}` 格式

3. **component_styles.json 色值统一**：将 `#2273F7` / `#2E82FF` / `#1A66FF` 全部替换为 `#007DFF`

### 7.2 Widget 三模板 API 修正

| 修正项 | 当前 | 官方标准 | 影响 |
|--------|------|---------|------|
| CheckBox `checked` | `checked: true` | `value: true` + `label` | API 不一致 |
| Text variant | h4/h5/body1/body2/caption | h1-h5/body/caption | variant 名不同 |
| 色值引用 | 硬编码 hex | `{"call":"token",...}` | 令牌化 |
| 阴影 | 无 | `drop-shadow(0px 2px 8px rgba(0,0,0,0.06))` | 加阴影 |
| Card padding | 20 / 16 | 20 (Gallery) | 统一为 20 |

### 7.3 官方案例可复用清单

| 官方案例 | 可复用到 A2UI Widget | 迁移方式 |
|---------|---------------------|---------|
| Gallery Card | 天气卡片外壳 | 直接引用 Card + drop-shadow |
| Gallery Button | Widget 底部按钮 | 引用 Button + Color_BG_Brand |
| Gallery List | 议程/待办列表 | 引用 List + Color_Ink_L3 描边 |
| Gallery CheckBox | 待办勾选 | 引用 CheckBox + label + value |
| Gallery Tabs | AI 输入面板 3 tab | 引用 Tabs + selectedIndex |
| Gallery Slider | Widget 设置（音量/亮度） | 直接引用 Slider |
| Gallery Chart | Widget 数据可视化 | 引用 Chart + #007DFF 配色 |
| Gallery Divider | 列表分隔线 | 引用 Divider + Color_Ink_L5 |
| Gallery Carousel | Widget 图片轮播 | 引用 Carousel |
| Gallery Table | Widget 数据表格 | 引用 Table + Color_Ink_L3 |

---

## 8. 关键结论

### 8.1 三层差距

| 层次 | 差距 | 迁移难度 |
|------|------|---------|
| Widget 三模板 vs 官方单 story | 大（渐变/白字/无阴影/variant 不同/API 不同） | 中 |
| 官方单 story vs Gallery | 中（硬编码 vs 令牌、阴影不同） | 低 |
| Gallery vs 鸿蒙规范 | 小（令牌系统理念一致，令牌名不同） | 低 |
| Android component_styles vs 鸿蒙 | 大（3 种蓝色 vs 1 种、Bold vs Medium） | 低（替换值） |

### 8.2 一句话总结

> **AGenUI 已有完整的令牌系统（Gallery 层），但 Widget 三模板完全没用它（全硬编码），Android 默认样式用了 3 种不同的蓝色（`#2273F7`/`#2E82FF`/`#1A66FF`）全部偏离鸿蒙 `#007DFF`。迁移路径：Widget 模板改用令牌引用 + 统一品牌色 + 对齐 variant API + 补阴影。**
