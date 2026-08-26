# Playground 控件风格 × 鸿蒙设计规范 差异对比与 A2UI 自定义控件方案

> 生成日期：2026-08-24
> 分析对象：`C:\Code\zhouqiaor-AGenUI\playground\android\app` 全量资源文件
> 对比基准：`docs/harmony/references/design-tokens.json` + `docs/harmony/SKILL.md`（HarmonyOS Design 1.2.0）
> 关联文档：`WIDGET-TEMPLATE-UX-ANALYSIS.md`（Widget/模板切换 UX 规范）、`XIAOYI-STYLE-OPENSOURCE-ANALYSIS.md`（小艺风格开源复用）

---

## 目录

1. [Playground 控件风格全景分析](#1-playground-控件风格全景分析)
2. [Playground × 鸿蒙 逐项差异矩阵](#2-playground--鸿蒙-逐项差异矩阵)
3. [关键差异深度剖析](#3-关键差异深度剖析)
4. [A2UI 自定义控件设计方案](#4-a2ui-自定义控件设计方案)
5. [迁移路线图与优先级](#5-迁移路线图与优先级)
6. [自检清单](#6-自检清单)

---

## 1. Playground 控件风格全景分析

### 1.1 设计体系定位

| 维度 | 结论 |
|------|------|
| **设计语言** | **Material Design 2**（非 Material 3，非 HarmonyOS） |
| **主题继承** | `Theme.MaterialComponents.DayNight.DarkActionBar` |
| **品牌色** | `#6200EE`（Material Purple 500）+ `#03DAC5`（Teal 200 辅助） |
| **暗色策略** | DayNight 双主题，`values/` + `values-night/` 两套色值 |
| **令牌化程度** | **极低** — colors.xml 有语义命名但 Widget 布局大量硬编码 |
| **间距体系** | 3 档（8/16/24dp），无 4dp 基数约束 |
| **圆角体系** | 2 档（4dp/8dp），Widget 用 16dp 但无系统定义 |
| **字体阶梯** | 3 档（20sp/16sp/12sp），标题全 Bold |
| **组件风格** | Material 标准组件（Button/OutlinedButton/TextButton/TabLayout/NavigationView/DrawerLayout） |

### 1.2 色彩系统现状

#### 浅色模式（values/colors.xml）

| 语义名 | 色值 | 鸿蒙等价令牌 | 差异说明 |
|--------|------|-------------|----------|
| `text_primary` | `#333333` | `text_primary #181818` | Playground 偏灰，鸿蒙更黑（对比度更高） |
| `text_secondary` | `#666666` | `text_secondary #666666` | **一致** |
| `text_hint` | `#999999` | `text_tertiary #999999` | **一致**（命名不同） |
| `divider` | `#E0E0E0` | `divider #E8EAED` | Playground 偏深，鸿蒙更浅更柔和 |
| `render_background` | `#FFFFFF` | `surface #FFFFFF` | **一致** |
| `logs_background` | `#FAFAFA` | `surface_muted #F5F6F7` | 接近，鸿蒙略偏冷调 |
| `card_background` | `#FFFFFF` | `surface #FFFFFF` | **一致** |
| `colorPrimary` | `#6200EE` | `brand #007DFF` | **核心差异**：紫色 vs 蓝色 |
| `colorPrimaryVariant` | `#370B3` | `brand_pressed #0069D6` | 紫色深色 vs 蓝色深色 |
| `colorSecondary` | `#03DAC5` | 无直接对应 | 鸿蒙无 Teal 辅助色概念 |

#### 深色模式（values-night/colors.xml）

| 语义名 | 色值 | 鸿蒙等价令牌 | 差异说明 |
|--------|------|-------------|----------|
| `text_primary` | `#FFFFFF` | `text_primary #E6E6E6` | Playground 纯白刺眼，鸿蒙用深灰白更舒适 |
| `text_secondary` | `#E0E0E0` | `text_secondary #B0B3B8` | Playground 偏亮，鸿蒙层次更分明 |
| `text_hint` | `#B0B0B0` | `text_tertiary #8A8D93` | Playground 偏亮 |
| `render_background` | `#121212` | `surface #1F1F22` | Playground 纯黑（MD2 标准），鸿蒙用深灰避免纯黑 |
| `component_row_background` | `#1E1E1E` | `surface_muted #2A2A2E` | 接近，鸿蒙略亮 |
| `divider` | `#2C2C2C` | `divider #3A3A40` | 接近 |
| `card_background` | `#2C2C2C` | 无直接对应 | 鸿蒙卡片在深色下仍用 surface |

### 1.3 尺寸/间距/圆角现状

| 令牌 | Playground 值 | 鸿蒙等价 | 差异说明 |
|------|--------------|---------|----------|
| `toolbar_height` | 56dp | 无固定值（标题 22fp，导航栏自适应） | 鸿蒙不强制固定高度 |
| `component_row_height` | 48dp | 列表项 48-64vp | **一致**下限 |
| `drawer_width` | 280dp | 半模态面板 max 400vp | Playground 偏窄 |
| `spacing_small` | 8dp | `space_xs 8vp` | **一致** |
| `spacing_medium` | 16dp | `space_md 16vp` | **一致** |
| `spacing_large` | 24dp | `space_lg 24vp` | **一致** |
| —（缺失） | 无 4dp | `space_2xs 4vp` | **缺失最小间距档** |
| —（缺失） | 无 12dp | `space_sm 12vp` | **缺失常规间距档** |
| —（缺失） | 无 32dp | `space_xl 32vp` | **缺失大间距档** |
| `corner_radius_small` | 4dp | `radius_sm 8vp` | Playground 偏小 |
| `corner_radius_medium` | 8dp | `radius_md 12vp` | Playground 偏小 |
| —（缺失） | 无 16dp 令牌 | `radius_lg 16vp` | Widget 硬编码 16dp 但未定义为令牌 |
| —（缺失） | 无胶囊圆角 | `radius_full 999vp` | **完全缺失**胶囊形态 |

### 1.4 字体阶梯现状

| 令牌 | Playground 值 | 鸿蒙等价 | 差异说明 |
|------|--------------|---------|----------|
| `text_size_title` | 20sp **Bold** | `title 22fp Medium` | Playground 偏小 + 过粗 |
| `text_size_body` | 16sp | `body 16fp Regular` | **一致**字号 |
| `text_size_caption` | 12sp | `caption 14fp Regular` | Playground 偏小 |
| —（缺失） | 无 | `display 28-32fp Bold` | **缺失最大标题** |
| —（缺失） | 无 | `subtitle 18fp Medium` | **缺失次级标题** |
| —（缺失） | 无 | `overline 12fp Regular` | 部分场景用 12sp 但未系统化 |

### 1.5 Widget 布局特殊问题

Widget（`a2ui_widget_content.xml`）存在 **最严重的令牌违规**：

| 元素 | 当前硬编码值 | 鸿蒙规范要求 |
|------|-------------|-------------|
| 标题栏背景 | `#F5F5F5`（硬编码） | 应引用 `surface_muted #F5F6F7` |
| 标题文字色 | `#333333`（硬编码） | 应引用 `text_primary` |
| 标题文字号 | `13sp`（非标准号） | 应取 `body 16fp` 或 `caption 14fp` |
| 按钮图标 | `28dp`（非标准尺寸） | 应取 32vp（触摸最小尺寸） |
| 按钮内边距 | `4dp`（非 4dp 基数倍）→ 实际是 4dp 倍数 ✓ | — |
| 底部按钮文字 | `11sp`（非标准号） | 应取 `overline 12fp` |
| 底部按钮高度 | `32dp`（低于触摸最小 48vp） | **违反触摸可达性** |
| 底部按钮文字色 | `#666666`（硬编码） | 应引用 `text_secondary` |
| 卡片圆角 | `16dp`（drawable 硬编码） | 引用 `radius_lg 16vp` |
| 卡片描边 | `1dp #E0E0E0`（硬编码） | 应引用 `divider` + 1vp |

---

## 2. Playground × 鸿蒙 逐项差异矩阵

### 2.1 核心设计令牌差异总表

| # | 维度 | Playground（Material 2） | 鸿蒙（HarmonyOS Design 1.2） | 差异等级 | 迁移难度 |
|---|------|--------------------------|-------------------------------|----------|----------|
| 1 | **品牌色** | `#6200EE` 紫色 | `#007DFF` 蓝色 | 🔴 高 | 低（替换色值） |
| 2 | **品牌按压色** | `#370B3` 深紫 | `#0069D6` 深蓝 | 🔴 高 | 低 |
| 3 | **品牌浅底** | 无 | `#E8F3FF` | 🔴 高 | 低（新增令牌） |
| 4 | **文字主色（浅）** | `#333333` | `#181818` | 🟡 中 | 低 |
| 5 | **文字主色（深）** | `#FFFFFF` 纯白 | `#E6E6E6` 深灰白 | 🟡 中 | 低 |
| 6 | **分隔线** | `#E0E0E0` | `#E8EAED` | 🟢 低 | 低 |
| 7 | **深色背景** | `#121212` 纯黑系 | `#1F1F22` 深灰系 | 🟡 中 | 低 |
| 8 | **间距基数** | 3 档（8/16/24） | 4vp 基数 × 6 档 | 🔴 高 | 中 |
| 9 | **圆角** | 2 档（4/8dp） | 4 档（8/12/16/999） | 🔴 高 | 中 |
| 10 | **字体阶梯** | 3 档（20/16/12sp） | 6 档（28/22/18/16/14/12fp） | 🔴 高 | 中 |
| 11 | **字体字重** | 标题全 Bold | 标题 Medium（仅 Display Bold） | 🟡 中 | 低 |
| 12 | **阴影** | Material 标准阴影 | 多层柔和 ARGB 阴影 | 🟡 中 | 中 |
| 13 | **玻璃拟态** | 无 | glass_bg/glass_blur/glass_border | 🔴 高 | 高 |
| 14 | **动效** | Material 标准动画 | 4 场景 × 精确时长 + press_scale 0.95 | 🟡 中 | 中 |
| 15 | **响应式断点** | 无 | xs/sm/md/lg 4 档 | 🟡 中 | 高 |
| 16 | **按钮类型** | Button/OutlinedButton/TextButton | EMPHASIZE/NORMAL/TEXT/CAPSULE | 🟡 中 | 中 |
| 17 | **半模态面板** | DrawerLayout 280dp | Sheet 16vp 顶圆角 + 指示条 | 🔴 高 | 中 |
| 18 | **列表项** | 48dp 固定 + drawableBg | 48-64vp + divider 左缩进 16vp | 🟢 低 | 低 |
| 19 | **Widget 令牌化** | 大量硬编码 | 全令牌引用 | 🔴 高 | 中 |
| 20 | **主题体系** | MaterialComponents.DayNight | HarmonyOS Design（沉浸光感） | 🔴 高 | 高 |

### 2.2 组件级差异明细

#### 2.2.1 顶部导航栏（Toolbar vs 顶部导航）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 组件 | `MaterialToolbar` | 原生导航栏 | — |
| 背景色 | `colorPrimary #6200EE` | `surface #FFFFFF` + 半透明模糊 | 鸿蒙不用品牌色做导航底 |
| 标题色 | `#FFFFFF` | `text_primary #181818` | 鸿蒙浅底深字 |
| 标题字号 | `20sp Bold` | `title 22fp Medium` | 字号 + 字重差异 |
| 标题位置 | 居左 | **居中** | 布局差异 |
| 高度 | `56dp` 固定 | 自适应 | — |
| 右侧操作 | `theme_switch_action_view` | 图标按钮 | — |

#### 2.2.2 按钮（Material Button vs 鸿蒙按钮）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 主按钮 | `Button` + `backgroundTint=colorPrimary` | EMPHASIZE：品牌色底 + 白字 | 色值不同 |
| 次按钮 | `OutlinedButton` | NORMAL：`surface` 底 + 品牌色描边 | 鸿蒙用浅底非透明 |
| 文字按钮 | `TextButton` | TEXT：透明底 + 品牌色字 | **一致**概念 |
| 胶囊按钮 | 无 | CAPSULE：圆角=高/2 | **完全缺失** |
| 高度 | 默认 36dp | 40vp 手机 / 36vp 桌面 | 接近 |
| 圆角 | `4dp`（默认 Material） | 8vp 手机 / 6vp 平板 | Playground 偏小 |
| 最大宽度 | 无限制 | 448vp | Playground 无约束 |
| 按压态 | Material ripple | 品牌色加深 20% + scale 0.95 | 机制不同 |
| 禁用态 | 40% 透明度 | 40% 透明度 | **一致** |

#### 2.2.3 抽屉/半模态面板（Drawer vs Sheet）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 组件 | `DrawerLayout` 左右抽屉 | 半模态 Sheet | 完全不同模式 |
| 宽度 | `280dp` 固定 | 最大 `400vp`，大屏限宽 | — |
| 顶圆角 | 0（直角侧滑） | 16vp 顶部圆角 | — |
| 指示条 | 无 | 36×4vp 居中灰色圆角 | — |
| 关闭方式 | 点击外部/滑动 | 下拉/点遮罩/侧滑返回 | — |
| 遮罩 | `dim 0.6` 标准暗化 | `rgba(0,0,0,0.45)` 半透明 | 接近 |
| 头部 | `colorPrimary` 背景白字 | `surface` 背景 + `text_primary` | 色彩方案相反 |

#### 2.2.4 列表项（item_component_row/menu/parent）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 高度 | `48dp` 固定 | 48-64vp 范围 | 下限一致 |
| 背景色 | `component_row_background` | `surface #FFFFFF` | 基本一致 |
| 分割线 | 无显式分割线 | 底部 `divider` 线，左缩进 16vp | Playground 缺分割线 |
| 内边距 | `16dp` 左右 | 16vp 左右 | **一致** |
| 标签字号 | `16sp Bold` | `body 16fp Regular` | 字重差异 |
| 描述字号 | `12sp` text_secondary | `caption 14fp Regular` | 字号偏小 |
| 触摸反馈 | `selectableItemBackground` | 按压品牌色浅底 8% | 机制不同 |
| 展开图标 | `▶` 字符 12sp | 图标组件 | — |

#### 2.2.5 输入框（EditText vs 鸿蒙输入框）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 背景 | 无显式背景 | `surface_muted` 底 | Playground 缺底色 |
| 圆角 | 默认 Material | `radius_sm 8vp` | — |
| 占位符色 | `text_hint #999999` | `text_tertiary #999999` | **一致** |
| 高度 | 无固定值 | 最小 40vp | — |
| 聚焦态 | Material 下划线高亮 | 品牌色描边 | 机制不同 |

#### 2.2.6 TabLayout（鸿蒙 Tab）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 模式 | `fixed` + `fill` | 自适应 | — |
| 指示条 | 2dp `colorPrimary` | 品牌色下划线 | 色值不同 |
| 标签字号 | 默认 14sp | `body 16fp` | 接近 |
| 选中态色 | `colorPrimary` | `brand` | 色值不同 |

#### 2.2.7 Widget 内容（a2ui_widget_content）

| 属性 | Playground | 鸿蒙规范 | 差异 |
|------|-----------|---------|------|
| 圆角 | 16dp drawable 硬编码 | `radius_lg 16vp` 令牌 | 值一致但未令牌化 |
| 描边 | 1dp `#E0E0E0` 硬编码 | 1vp `divider` 令牌 | 值接近但未令牌化 |
| 背景 | `#FFFFFF` drawable | `surface` 令牌 | 值一致但未令牌化 |
| 标题栏底 | `#F5F5F5` 硬编码 | `surface_muted` 令牌 | 值接近但未令牌化 |
| 按钮尺寸 | 28dp | 32vp（最小触摸尺寸） | **偏小** |
| 按钮间距 | 4dp | `space_xs 8vp` | 偏小 |
| 文字号 | 13sp/11sp 非标准 | 14fp/12fp 标准阶梯 | 非标准 |
| 文字色 | 硬编码 hex | 令牌引用 | 完全未令牌化 |

---

## 3. 关键差异深度剖析

### 3.1 设计哲学差异

```
Material Design 2                    HarmonyOS Design
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
理念：Material is the metaphor        理念：和谐美学 · 沉浸光感
色彩：品牌色饱和度高，对比强烈        色彩：品牌色温和，层次靠透明度
层次：Z 轴 elevation 阴影             层次：多层柔和阴影 + 玻璃拟态
形状：强调几何感，小圆角(4dp)          形状：大圆角(8-16vp)，胶囊形态
字体：Roboto，Bold 标题                字体：HarmonyOS Sans，Medium 标题
间距：8dp 基数                          间距：4vp 基数，更细粒度
动效：Material motion                  动效：克制，精确时长(100/250/300ms)
暗色：#121212 纯黑系                   暗色：#1F1F22 深灰系，避免纯黑
```

### 3.2 最核心的 5 个差距（影响全局）

| 排序 | 差距 | 影响范围 | 迁移策略 |
|------|------|---------|---------|
| **1** | 品牌色 `#6200EE` → `#007DFF` | 全局（所有品牌色引用） | 替换 `colorPrimary` + `colorPrimaryVariant` 色值 + 添加 `brand_surface` |
| **2** | 间距 3 档 → 4vp 基数 6 档 | 全局布局 | 新增 `space_2xs(4)` + `space_sm(12)` + `space_xl(32)` 令牌 |
| **3** | 圆角 2 档 → 4 档 | 全局形状 | 新增 `radius_md(12)` + `radius_lg(16)` + `radius_full` 令牌 |
| **4** | 字体 3 档 → 6 档 | 全局文字 | 新增 `display(28)` + `subtitle(18)` + `overline(12)` 令牌 |
| **5** | Widget 硬编码 → 全令牌化 | Widget 布局 | 重写 `a2ui_widget_content.xml` + drawable 引用令牌 |

### 3.3 差距分类

- **色值替换即可**（低难度）：品牌色、文字色、分隔线、深色背景 → 改 colors.xml 色值
- **需新增令牌**（中难度）：间距 4/12/32dp、圆角 12/16/999、字体 18/28fp → 扩展 dimens.xml
- **需组件改造**（高难度）：Drawer→Sheet、Toolbar→鸿蒙导航、按钮类型扩展 → 自定义 View 或 Compose
- **需新增能力**（最高难度）：玻璃拟态、响应式断点、半模态面板 → 需自定义控件或引入三方库

---

## 4. A2UI 自定义控件设计方案

### 4.1 设计原则

基于鸿蒙五条铁律 + A2UI 实际场景：

1. **令牌唯一源**：所有控件属性引用 `design-tokens.json` 映射的 Android 资源令牌，零硬编码
2. **鸿蒙视觉皮肤**：色彩/圆角/间距/阴影/字体完全遵循鸿蒙规范
3. **Material 兼容降级**：在非鸿蒙设备上保持可用性，用 Material 组件做底座 + 鸿蒙样式覆盖
4. **Widget RemoteViews 适配**：Widget 受 RemoteViews 限制（无自定义 View），靠 drawable + 令牌化资源实现
5. **Compose 优先**：App 内控件优先用 Jetpack Compose 实现，更接近声明式 UI 范式

### 4.2 自定义控件清单

按优先级排序（P0 = Phase 1 必做，P1 = Phase 2，P2 = Phase 3）：

| # | 控件名 | 优先级 | 替代对象 | 鸿蒙规范要点 | 实现方式 |
|---|--------|--------|---------|-------------|---------|
| 1 | **HarmonyColorTokens** | P0 | colors.xml | 全套鸿蒙色值令牌 | 重写 `values/colors.xml` + `values-night/colors.xml` |
| 2 | **HarmonyDimenTokens** | P0 | dimens.xml | 间距/圆角/字号令牌 | 扩展 `values/dimens.xml` |
| 3 | **HarmonyButton** | P0 | Material Button | 4 类型(EMPHASIZE/NORMAL/TEXT/CAPSULE) + 品牌色 + press_scale 0.95 | Compose 自定义 Button 或 Material Button 样式覆盖 |
| 4 | **HarmonyCard** | P0 | Material CardView | 12vp 圆角 + 多层柔和阴影 + 16vp 内边距 | Compose Surface / Material CardView 样式 |
| 5 | **HarmonyListItem** | P0 | item_component_row | 48-64vp + divider 左缩进 16vp + 品牌浅底按压 | Compose ListItem / 自定义 LinearLayout |
| 6 | **HarmonySheet** | P1 | DrawerLayout | 16vp 顶圆角 + 36×4vp 指示条 + 下拉关闭 + 遮罩 45% | BottomSheetDialog 自定义或 ModalBottomSheet |
| 7 | **HarmonyTextField** | P1 | EditText | surface_muted 底 + 8vp 圆角 + 品牌色描边聚焦 | Compose TextField / TextInputLayout 样式 |
| 8 | **HarmonyTabRow** | P1 | TabLayout | 品牌色下划线 + 16fp 标签 | Compose TabRow / TabLayout 样式 |
| 9 | **HarmonyTopBar** | P1 | MaterialToolbar | surface 半透明模糊底 + 标题居中 + 22fp Medium | Compose TopAppBar / 自定义 Toolbar |
| 10 | **HarmonyGlassPanel** | P2 | 无 | glass_bg 25% + blur 12vp + glass_border 30% | BlurView 库 + Compose Box |
| 11 | **HarmonyCapsuleChip** | P2 | 无 | radius_full + 品牌浅底 + 品牌色文字 | Compose FilterChip / 自定义 TextView |
| 12 | **HarmonyWidgetCard** | P0 | a2ui_widget_bg | 16vp 圆角 + divider 描边 + surface 底（令牌化 drawable） | 重写 drawable XML 引用 color 令牌 |

### 4.3 令牌映射表（Android 资源）

#### values/colors.xml（鸿蒙浅色）

```xml
<!-- 鸿蒙品牌与语义色 -->
<color name="harmony_brand">#007DFF</color>
<color name="harmony_brand_pressed">#0069D6</color>
<color name="harmony_brand_surface">#E8F3FF</color>
<color name="harmony_success">#2E7D32</color>
<color name="harmony_warning">#F5A623</color>
<color name="harmony_danger">#FF3B30</color>

<!-- 鸿蒙中性色 -->
<color name="harmony_text_primary">#181818</color>
<color name="harmony_text_secondary">#666666</color>
<color name="harmony_text_tertiary">#999999</color>
<color name="harmony_text_inverse">#FFFFFF</color>
<color name="harmony_surface">#FFFFFF</color>
<color name="harmony_surface_muted">#F5F6F7</color>
<color name="harmony_divider">#E8EAED</color>
<color name="harmony_mask">#66000000</color> <!-- 40% black -->
```

#### values-night/colors.xml（鸿蒙深色）

```xml
<color name="harmony_brand">#3D9BFF</color>
<color name="harmony_brand_pressed">#2B82E6</color>
<color name="harmony_brand_surface">#123A5C</color>
<color name="harmony_success">#4CAF50</color>
<color name="harmony_warning">#FFB84D</color>
<color name="harmony_danger">#FF6B61</color>
<color name="harmony_text_primary">#E6E6E6</color>
<color name="harmony_text_secondary">#B0B3B8</color>
<color name="harmony_text_tertiary">#8A8D93</color>
<color name="harmony_text_inverse">#1A1A1A</color>
<color name="harmony_surface">#1F1F22</color>
<color name="harmony_surface_muted">#2A2A2E</color>
<color name="harmony_divider">#3A3A40</color>
<color name="harmony_mask">#66000000</color>
```

#### values/dimens.xml（鸿蒙尺寸令牌）

```xml
<!-- 间距 (4vp 基数) -->
<dimen name="harmony_space_2xs">4dp</dimen>
<dimen name="harmony_space_xs">8dp</dimen>
<dimen name="harmony_space_sm">12dp</dimen>
<dimen name="harmony_space_md">16dp</dimen>
<dimen name="harmony_space_lg">24dp</dimen>
<dimen name="harmony_space_xl">32dp</dimen>

<!-- 圆角 -->
<dimen name="harmony_radius_sm">8dp</dimen>
<dimen name="harmony_radius_md">12dp</dimen>
<dimen name="harmony_radius_lg">16dp</dimen>
<!-- radius_full 使用代码设置 (高度/2 或 999) -->

<!-- 字号 -->
<dimen name="harmony_text_display">28sp</dimen>
<dimen name="harmony_text_title">22sp</dimen>
<dimen name="harmony_text_subtitle">18sp</dimen>
<dimen name="harmony_text_body">16sp</dimen>
<dimen name="harmony_text_caption">14sp</dimen>
<dimen name="harmony_text_overline">12sp</dimen>

<!-- 按钮高度 -->
<dimen name="harmony_button_height">40dp</dimen>
<dimen name="harmony_button_height_desktop">36dp</dimen>
<dimen name="harmony_button_max_width">448dp</dimen>

<!-- 列表项 -->
<dimen name="harmony_list_item_min_height">48dp</dimen>
<dimen name="harmony_list_item_max_height">64dp</dimen>

<!-- Sheet -->
<dimen name="harmony_sheet_top_radius">16dp</dimen>
<dimen name="harmony_sheet_indicator_width">36dp</dimen>
<dimen name="harmony_sheet_indicator_height">4dp</dimen>
<dimen name="harmony_sheet_max_width">400dp</dimen>

<!-- Widget -->
<dimen name="harmony_widget_radius">16dp</dimen>
<dimen name="harmony_widget_button_size">32dp</dimen>
```

### 4.4 Widget 令牌化 Drawable 示例

```xml
<!-- a2ui_widget_bg.xml (令牌化版本) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/harmony_surface" />
    <corners android:radius="@dimen/harmony_widget_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/harmony_divider" />
</shape>
```

### 4.5 自定义控件核心实现方向

#### HarmonyButton（Compose 方向）

```kotlin
// 概念伪代码 — 不含完整实现
@Composable
fun HarmonyButton(
    text: String,
    onClick: () -> Unit,
    type: HarmonyButtonType = HarmonyButtonType.EMPHASIZE,
    modifier: Modifier = Modifier
) {
    val colors = when (type) {
        HarmonyButtonType.EMPHASIZE -> ButtonDefaults.buttonColors(
            containerColor = HarmonyColorTokens.brand,
            contentColor = HarmonyColorTokens.text_inverse
        )
        HarmonyButtonType.NORMAL -> ButtonDefaults.outlinedButtonColors(
            containerColor = HarmonyColorTokens.surface,
            contentColor = HarmonyColorTokens.brand
        )
        HarmonyButtonType.TEXT -> TextButton // 透明底 + 品牌色文字
        HarmonyButtonType.CAPSULE -> // radius = height/2
    }
    // press_scale 0.95 动效
    // max width 448vp 约束
}
```

---

## 5. 迁移路线图与优先级

### Phase 0：令牌层迁移（P0，1-2 天）

```
┌─────────────────────────────────────────────┐
│ 1. 重写 colors.xml → 鸿蒙色值              │
│ 2. 扩展 dimens.xml → 鸿蒙间距/圆角/字号   │
│ 3. 主题 parent 切换 → 去掉 colorPrimary 引用│
│ 4. grep 检查：硬编码 #xxxxxx = 0（除令牌）  │
└─────────────────────────────────────────────┘
```

### Phase 1：核心控件迁移（P0，3-5 天）

```
┌─────────────────────────────────────────────┐
│ 5. HarmonyButton（4 类型 + press_scale）   │
│ 6. HarmonyCard（12vp 圆角 + 柔和阴影）      │
│ 7. HarmonyListItem（divider 左缩进）        │
│ 8. HarmonyWidgetCard（令牌化 drawable）     │
│ 9. 全局替换：grep + sed 批量替换旧引用     │
└─────────────────────────────────────────────┘
```

### Phase 2：面板与输入控件（P1，5-7 天）

```
┌─────────────────────────────────────────────┐
│ 10. HarmonySheet（替代 DrawerLayout）       │
│ 11. HarmonyTextField（surface_muted 底）    │
│ 12. HarmonyTabRow（品牌色下划线）            │
│ 13. HarmonyTopBar（标题居中 + 半透明底）    │
└─────────────────────────────────────────────┘
```

### Phase 3：高级材质与响应式（P2，7-10 天）

```
┌─────────────────────────────────────────────┐
│ 14. HarmonyGlassPanel（BlurView 玻璃拟态）  │
│ 15. HarmonyCapsuleChip（胶囊标签）          │
│ 16. 响应式断点适配（xs/sm/md/lg）           │
│ 17. 动效规范落地（100/250/300ms + scale）   │
└─────────────────────────────────────────────┘
```

---

## 6. 自检清单

### 鸿蒙五条铁律自检

- [x] **一致性**：所有颜色/字体/间距/圆角已映射为令牌，无硬编码散值（迁移后）
- [x] **层级分明**：主按钮(EMPHASIZE) > 次按钮(NORMAL) > 文字按钮(TEXT)，字号 6 档分级
- [x] **呼吸感**：间距 4vp 基数 6 档（4/8/12/16/24/32），页面 16vp 安全边距
- [x] **质感优先**：多层柔和阴影替代硬边，玻璃拟态用于浮层
- [x] **多端连续**：响应式断点 xs/sm/md/lg，一次设计多端适配

### 令牌化自检

- [ ] `grep -rn "#[0-9A-Fa-f]\{6\}" res/layout/` → 应为 0（迁移后）
- [ ] `grep -rn "android:color=\"@" res/layout/` → 全部引用 `@color/harmony_*`
- [ ] `grep -rn "[0-9]\+dp" res/layout/` → 仅 dimens 引用
- [ ] Widget 布局所有色值/尺寸引用 `@color` / `@dimen`
- [ ] 深色模式 `values-night/` 令牌完整覆盖

### 组件覆盖自检

- [ ] 4 种按钮类型全部实现
- [ ] 卡片 12vp 圆角 + 阴影
- [ ] 列表项 divider 左缩进
- [ ] 半模态 Sheet 替代 Drawer
- [ ] 输入框 surface_muted 底
- [ ] 玻璃拟态面板可选项就绪
- [ ] Widget 全令牌化

---

## 附录：差异总结一句话

> **Playground 当前是「Material Design 2 紫色体系 + 3 档间距/圆角/字体 + 大量硬编码 Widget」，鸿蒙规范要求「HarmonyOS Design 蓝色体系 + 4vp 基数 6 档间距 + 4 档圆角 + 6 档字体 + 全令牌化 + 沉浸光感材质」。迁移路径为：Phase 0 令牌层 → Phase 1 核心控件 → Phase 2 面板/输入 → Phase 3 高级材质/响应式，共 12 个自定义控件，整体可控。**
