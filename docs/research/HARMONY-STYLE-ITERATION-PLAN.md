# AGenUI 鸿蒙风格化迭代计划

> **Worktree**: `C:/Code/AGenUI-harmony-migration` · 分支 `harmony-style-migration`（基于 main@2233580）
> **创建日期**: 2026-08-25
> **目标**: 将 AGenUI 全部基础控件和官方案例从 Material 2 / 硬编码风格迁移到 HarmonyOS Design 1.2.0 规范

---

## 第一章 · 业界开源项目调研

### 1.1 调研范围

| # | 项目 | 平台 | 许可证 | 核心价值 |
|---|------|------|--------|----------|
| 1 | **Omni-UI** (58安居客/wuba) | HarmonyOS ArkUI | Apache 2.0 | 25+ 鸿蒙原生组件，OHPM 安装 `@wuba/omni-ui` |
| 2 | **Material Design 3 Expressive** (Google) | Android/Web/iOS | Apache 2.0 | 动效物理引擎、35 种 Shape 体系、动态色彩提取 |
| 3 | **Telefonica Mística** | Android/iOS/Web | Apache 2.0 | GitHub Actions 自动化令牌管线：Figma → JSON → 3 平台代码 |
| 4 | **Backbase Design System** | Android/iOS/Web | 商业 | Figma → defaultTokens.json → Gradle 可视化插件 → XML theme |
| 5 | **Style Dictionary** (Amazon) | 全平台 | Apache 2.0 | 令牌转换管线标准：JSON → CSS/Swift/Kotlin/XML/Compose |
| 6 | **shadcn/ui** | React Web | MIT | "复制粘贴"模式：源码归你、CLI 安装、CVA 变体管理、设计-实现分离 |

### 1.2 可借鉴的关键模式

#### 模式 A：令牌驱动管线（Style Dictionary + Mística + Backbase）

```
Figma Variables → Tokens Studio → tokens.json (W3C DTCG)
  → Style Dictionary config
    → android/colors.xml + font_dimens.xml
    → compose/ColorTokens.kt + SizeTokens.kt
    → harmony/color.json + float.json
    → css/variables.css
```

**AGenUI 应用**：当前 AGenUI 的 `component_styles.json` 是硬编码 JSON，Gallery 已有令牌引用机制（`{"call":"token","args":{"name":"Color_BG_L1"}}`）。可在此基础上建立正式的 Style Dictionary 管线，将 HarmonyOS design-tokens.json 作为 single source of truth，自动生成多平台资源文件。

#### 模式 B：组件代码所有权（shadcn/ui）

shadcn/ui 的核心理念是"组件不是依赖，是你的代码"。通过 CLI 将组件源码复制到项目内部，开发者完全拥有和控制样式。

**AGenUI 应用**：A2UI 自定义控件（HarmonyButton/HarmonyCard 等 12 个）应采用此模式——不作为外部 AAR 依赖，而是将源码直接放入 AGenUI 项目 `platforms/android/src/main/java/.../harmony/` 目录，让组件代码完全可见、可改、可扩展。

#### 模式 C：变体管理系统（shadcn/ui CVA + Material 3 Shapes）

shadcn 用 `class-variance-authority` (CVA) 声明组件变体（variant + size），Material 3 用 35 种 Shape 系统定义圆角变体。

**AGenUI 应用**：HarmonyOS 有 4 种圆角（8/12/16/999）+ 6 级字号 + 4 种按钮类型（FILLED/OUTLINED/TEXT/CAPSULE），需在 A2UI 组件 JSON schema 中建立统一的变体声明系统，替代当前 Widget 模板中的 h4/h5/body1/body2 非标变体名。

#### 模式 D：鸿蒙原生组件参考（Omni-UI）

Omni-UI 提供 25+ 鸿蒙原生 ArkUI 组件（`ohpm install @wuba/omni-ui`），可直接参考其组件分类、API 设计、属性命名规范。

**AGenUI 应用**：对照 Omni-UI 的组件清单验证 A2UI 12 个自定义控件的覆盖度和命名合理性。Omni-UI 已有的组件（如 `Card`、`Tag`、`Image`、`Grid`）可作为鸿蒙风格实现的行为参考。

### 1.3 调研结论矩阵

| 维度 | 最佳参考 | A2UI 落地方案 |
|------|----------|---------------|
| 令牌管线 | Style Dictionary + Mística | 建立 tokens.json → 自动生成 colors.xml + Compose Object + harmony color.json |
| 组件所有权 | shadcn/ui | 12 个 Harmony 控件源码直接放入 AGenUI 项目 |
| 变体管理 | shadcn CVA + Material 3 Shapes | 在 component_styles.json 中建立 variant 声明体系 |
| 鸿蒙原生参考 | Omni-UI | 对照组件清单、参考 ArkUI 实现模式 |
| 动效规范 | Material 3 Expressive + HarmonyOS Motion | 按鸿蒙 100ms/250ms/300ms + press_scale 0.95 实现 |
| 色彩双主题 | Mística + HarmonyOS Design | light/dark 双套令牌，运行时切换 |

---

## 第二章 · 迁移范围全景

### 2.1 迁移对象清单

| 类别 | 对象 | 数量 | 当前状态 | 目标状态 |
|------|------|------|----------|----------|
| **A2UI 自定义控件** | HarmonyButton/Card/ListItem/Sheet/TextField/TabRow/TopBar/GlassPanel/CapsuleChip/WidgetCard/ColorTokens/DimenTokens | 12 | 设计方案已定，未实现 | 全部实现并通过 Checker 审核 |
| **官方案例 Stories** | Text/Image/Icon/Lottie/Column/Row/Divider/Card/Button/TextField/CheckBox/Slider/ChoicePicker/DateTimeInput/Tabs/List/Carousel/Modal/Table/Chart/Markdown/RichText/AudioPlayer/Video/Web/Gallery | 26 | Material 2 风格、硬编码 hex | HarmonyOS 令牌化、双主题 |
| **Widget 模板** | weather/agenda/todo | 3 | 硬编码渐变色/非标变体名 | HarmonyOS 风格模板（已有设计稿） |
| **Android 平台默认** | component_styles.json | 1 | 3 种不同蓝色 (#2273F7/#2E82FF/#1A66FF) | 统一 #007DFF |
| **渲染管线** | WidgetRenderActivity | 1 | 硬编码 0xFF6200EE/300px/200px/Color.WHITE | 动态尺寸 + 令牌引用 |

### 2.2 令牌映射表（AGenUI → HarmonyOS）

| AGenUI 令牌 | 用途 | HarmonyOS 映射 | 值(Light) | 值(Dark) |
|-------------|------|----------------|-----------|----------|
| `Color_BG_L1` | 页面背景 | `surface_muted` | `#F5F6F7` | `#2A2A2E` |
| `Color_BG_L2` | 卡片背景 | `surface` | `#FFFFFF` | `#1F1F22` |
| `Color_BG_L3` | 嵌套背景 | `surface_muted` | `#F5F6F7` | `#2A2A2E` |
| `Color_BG_Brand` | 品牌背景 | `brand_surface` | `#E8F3FF` | `#123A5C` |
| `Color_Text_L1` | 主要文字 | `text_primary` | `#181818` | `#E6E6E6` |
| `Color_Text_L2` | 次要文字 | `text_secondary` | `#666666` | `#B0B3B8` |
| `Color_Text_Body` | 正文文字 | `text_primary` | `#181818` | `#E6E6E6` |
| `Color_Text_Highlight` | 高亮文字 | `brand` | `#007DFF` | `#3D9BFF` |
| `Color_Ink_L1` | 深墨色 | `text_primary` | `#181818` | `#E6E6E6` |
| `Color_Ink_L3` | 分割线 | `divider` | `#E8EAED` | `#3A3A40` |
| `Color_Ink_L5` | 浅墨色 | `text_tertiary` | `#999999` | `#8A8D93` |
| `Color_Gray_20` | 边框色 | `divider` | `#E8EAED` | `#3A3A40` |

### 2.3 变体名统一映射

| 当前 Widget 变体名 | 当前 Stories 变体名 | HarmonyOS 统一名 | 字号 | 字重 |
|--------------------|---------------------|------------------|------|------|
| h1 | h1 | `display` | 30fp | Bold |
| h2 | h2 | `title` | 22fp | Medium |
| h3 | h3 | `subtitle` | 18fp | Medium |
| h4 | h4 | `body` | 16fp | Regular |
| h5 | h5 | `caption` | 14fp | Regular |
| body1 | body | `body` | 16fp | Regular |
| body2 | — | `body` | 16fp | Regular |
| caption | caption | `overline` | 12fp | Regular |

---

## 第三章 · 分阶段迭代计划

### Phase 0：令牌基础设施（P0 阻塞项）

**目标**：建立 HarmonyOS 令牌管线，所有后续工作依赖此项。

**参考模式**：Style Dictionary + Mística GitHub Actions 自动化

| 任务 | 产出物 | 验收标准 |
|------|--------|----------|
| 0.1 创建 `tokens/harmony-tokens.json` | W3C DTCG 格式令牌源文件 | 包含 light/dark 双套色彩 + 6 级字号 + 6 级间距 + 4 级圆角 + 阴影 + 动效 |
| 0.2 配置 Style Dictionary | `style-dictionary.config.mjs` | 能从 tokens.json 生成 `android/colors.xml` + `android/font_dimens.xml` + `compose/HarmonyTokens.kt` |
| 0.3 创建 Android 资源 | `res/values/harmony_colors.xml` + `res/values/harmony_dimens.xml` + `res/values-night/harmony_colors.xml` | `grep -ri "6200EE\|2273F7\|2E82FF\|1A66FF\|667eea\|764ba2" platforms/android/` 返回 0 行 |
| 0.4 创建 HarmonyOS 资源 | `entry/src/main/resources/base/element/color.json` + `float.json` + `dark/element/color.json` | 与 design-tokens.json 值一致 |
| 0.5 建立 CI hook（可选） | GitHub Action workflow | push tokens/ 时自动 build 并 commit 生成文件 |

**依赖**：无  
**阻塞**：Phase 1-3 全部  
**预计文件变更**：新增 ~5 文件，修改 `component_styles.json` 1 文件

### Phase 1：核心 A2UI 自定义控件（P0）

**目标**：实现 12 个 Harmony 风格自定义控件，作为所有 Stories 和 Widget 的构建基块。

**参考模式**：shadcn/ui 源码所有权 + Omni-UI 组件参考

| 优先级 | 控件 | 关键属性 | 参考来源 |
|--------|------|----------|----------|
| P0-1 | **HarmonyColorTokens** | light/dark 双套 12 色 | design-tokens.json |
| P0-2 | **HarmonyDimenTokens** | 6 级间距 + 4 级圆角 + 6 级字号 + 阴影 | design-tokens.json |
| P0-3 | **HarmonyButton** | 4 类型 (FILLED/OUTLINED/TEXT/CAPSULE) + press_scale 0.95 + 100ms 动效 | shadcn CVA + Omni-UI Button |
| P0-4 | **HarmonyCard** | radius_md=12 + 阴影 `0px 2px 16px rgba(0,0,0,0.06)` + surface 背景 | shadcn Card + Omni-UI Card |
| P0-5 | **HarmonyListItem** | 左缩进分割线 + text_primary/secondary 双行 + 8vp 间距 | Material 3 ListItem |
| P1-6 | **HarmonyTopBar** | 玻璃模糊背景 + 居中标题 + 48vp 高度 | HarmonyOS TopBar 规范 |
| P1-7 | **HarmonyTextField** | surface_muted 背景 + radius_sm=8 + 1vp border_divider + 16vp 内边距 | shadcn Input + Omni-UI |
| P1-8 | **HarmonyTabRow** | brand 下划线指示器 + 48vp 高度 + 14fp 标签 | Material 3 TabRow |
| P1-9 | **HarmonySheet** | radius_lg=16 顶部圆角 + 36×4vp 抓手 + surface 背景 | HarmonyOS Sheet 规范 |
| P2-10 | **HarmonyGlassPanel** | BlurView 背景 + 70% 透明度 + 玻璃拟态 | Mística Glass |
| P2-11 | **HarmonyCapsuleChip** | radius_full=999 + brand_surface 背景 + 12fp 文字 | Material 3 Chip |
| P2-12 | **HarmonyWidgetCard** | 卡片容器 + 16vp 内边距 + 阴影 + 响应式宽度 | design-tokens.json |

**实现路径**：
```
AGenUI-harmony-migration/
  platforms/android/src/main/java/com/amap/agenui/platform/harmony/
    tokens/
      HarmonyColorTokens.kt
      HarmonyDimenTokens.kt
    widgets/
      HarmonyButton.kt
      HarmonyCard.kt
      HarmonyListItem.kt
      HarmonyTopBar.kt
      HarmonyTextField.kt
      HarmonyTabRow.kt
      HarmonySheet.kt
      HarmonyGlassPanel.kt
      HarmonyCapsuleChip.kt
      HarmonyWidgetCard.kt
  platforms/android/src/main/res/values/
    harmony_colors.xml
    harmony_dimens.xml
  platforms/android/src/main/res/values-night/
    harmony_colors.xml
```

**验收标准**：
- 每个控件在浅色/深色模式下视觉正确
- `grep -ri "4F46E5\|6200EE\|2273F7\|2E82FF\|1A66FF" platforms/android/src/.../harmony/` 返回 0 行
- 按压动画 100ms + scale 0.95 实际生效
- Checker 跑 `.handoff/scripts/review.ps1 -Branch harmony-style-migration` 通过

### Phase 2：官方案例 Stories 鸿蒙化（P1）

**目标**：将 26 个 Stories 组件从 Material 2 / 硬编码风格迁移到 HarmonyOS 令牌化风格。

**参考模式**：Gallery 已有的令牌引用机制（`{"call":"token","args":{"name":"Color_BG_L1"}}`）

#### 2A. 基础展示组（4 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| Text | 7 级变体 h1-h5/body/caption | 重命名为 display/title/subtitle/body/caption/overline |
| Image | 固定 300px×200px | 用 `space_md`/`space_lg` 间距令牌 + 响应式 |
| Icon | Material 图标 + #6200EE | 切换 Lucide 图标集 + brand 色 |
| Lottie | 无问题 | 无需改动 |

#### 2B. 布局组（4 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| Column | 间距硬编码 | 用 `space_md`=16vp 间距令牌 |
| Row | 间距硬编码 | 用 `space_sm`=12vp 间距令牌 |
| Divider | `#E0E0E0` 硬编码 | 用 `divider`=`#E8EAED` |
| Card | 阴影 2px 8px (偏弱) | 升级为 `0px 2px 16px rgba(0,0,0,0.06)` + radius_md=12 |

#### 2C. 输入组（6 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| Button | 3 类型 (缺 CAPSULE) + #6200EE | 4 类型 + HarmonyButton 控件 + #007DFF |
| TextField | 无背景色 | surface_muted 背景 + radius_sm=8 |
| CheckBox | `value`+`label` API | 统一 API + brand 色 + radius_sm 圆角 |
| Slider | `#1A66FF` 轨道 | brand 色 + 4vp 轨道高度 |
| ChoicePicker | `#2E82FF` 选中 | brand 色 + brand_surface 选中背景 |
| DateTimeInput | `#2273F7` | brand 色 + surface 背景 |

#### 2D. 导航组（4 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| Tabs | `#2273F7` 指示器 | brand 下划线 + HarmonyTabRow |
| List | Material 列表样式 | HarmonyListItem + 左缩进分割线 |
| Carousel | `#00000099` 指示器 | brand 指示器 + 6vp 直径 |
| Modal | `rgba(0,0,0,0.5)` 遮罩 | `rgba(0,0,0,0.4)` + HarmonySheet 替代 |

#### 2E. 数据与富内容组（4 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| Table | `#EEEFF2` 表头 | surface_muted 表头 + text_secondary |
| Chart | chartConfig 硬编码色 | 品牌色系调色板 (brand/success/warning/danger) |
| Markdown | 无样式 | 鸿蒙排版规范 + 16vp 段间距 |
| RichText | 无问题 | 无需改动 |

#### 2F. 媒体组（3 组件）

| 组件 | 当前问题 | 迁移方案 |
|------|----------|----------|
| AudioPlayer | `#2273F7` | brand 色 + surface 背景 |
| Video | 无问题 | 无需改动 |
| Web | 无问题 | 无需改动 |

#### 2G. 全景组（1 组件）

| 组件 | 当前状态 | 迁移方案 |
|------|----------|----------|
| Gallery | ✅ 已令牌化 | 验证令牌值与 HarmonyOS 一致，更新映射关系 |

**验收标准**：
- 所有 Stories JSON 中 `grep -ri "6200EE\|2273F7\|2E82FF\|1A66FF\|#333333\|#555555\|#999999\|#E0E0E0"` 返回 0 行（Gallery 除外，需单独验证令牌映射）
- 所有 Stories 使用 `{"call":"token","args":{...}}` 引用令牌
- 浅色/深色双主题视觉正确

### Phase 3：Widget 模板 + 渲染管线（P1）

**目标**：替换 3 个 Widget 模板 + 清理 WidgetRenderActivity 硬编码。

**参考模式**：已有设计稿（`harmony-templates/weather.json` 等 3 份）

| 任务 | 当前 | 目标 |
|------|------|------|
| 3.1 替换 weather.json | 渐变 #667eea→#764ba2 + 白字 | 白底 + #007DFF 温度 + brand_surface 图标胶囊 |
| 3.2 替换 agenda.json | #F0F4FF + #333/#555/#999 | 白底 + #181818/#666 + #E8F3FF 选中 + #007DFF 时间 |
| 3.3 替换 todo.json | #F0FFF4 + #6200EE checkbox | 白底 + #007DFF checkbox + line-through 已完成 |
| 3.4 统一变体名 | h4/h5/body1/body2 | display/title/subtitle/body/caption/overline |
| 3.5 统一 CheckBox API | `checked` 属性 | `value` + `label`（与官方 Stories 一致） |
| 3.6 清理 WidgetRenderActivity | `0xFF6200EE`(L271) + `300px`(L199) + `200px`(L205) + `Color.WHITE`(L214) | `R.color.harmony_brand` + 动态 `appWidgetInfo` 尺寸 + `R.color.harmony_surface` |
| 3.7 中文本地化 | 英文文案 | 中文文案（北京/会议议程/待办事项） |

**验收标准**：
- Widget 模板 JSON 中无硬编码 hex（除 `#FFFFFF`/`#181818` 等令牌值）
- WidgetRenderActivity 中 `grep -i "6200EE\|300px\|200px\|Color.WHITE"` 返回 0 行
- Widget 在桌面显示为 HarmonyOS 视觉风格
- 浅色/深色模式自动切换

### Phase 4：Android 平台默认样式统一（P2）

**目标**：将 `component_styles.json` 中 3 种蓝色统一为 HarmonyOS brand #007DFF。

| 组件 | 当前蓝色 | 目标 |
|------|----------|------|
| Tabs indicator | `#2273F7` | `#007DFF` |
| DateTimeInput | `#2273F7` | `#007DFF` |
| AudioPlayer | `#2273F7` | `#007DFF` |
| CheckBox selected | `#2E82FF` | `#007DFF` |
| ChoicePicker selected | `#2E82FF` | `#007DFF` |
| Slider track | `#1A66FF` | `#007DFF` |

**验收标准**：
- `grep -ri "2273F7\|2E82FF\|1A66FF" platforms/android/src/main/assets/component_styles.json` 返回 0 行
- 所有品牌色引用统一指向 `harmony_brand` 令牌

### Phase 5：CI 自动化 + 文档（P2）

**目标**：建立令牌管线 CI 自动化，编写迁移文档。

| 任务 | 产出物 |
|------|--------|
| 5.1 Style Dictionary CI | `.github/workflows/build-tokens.yml` |
| 5.2 迁移指南 | `docs/HARMONY-MIGRATION-GUIDE.md`（组件级迁移步骤） |
| 5.3 视觉回归测试 | 关键控件的截图对比脚本 |
| 5.4 设计规范文档 | 更新 `docs/harmony-design-spec/` 中的组件规范 |

---

## 第四章 · 依赖关系与优先级

```
Phase 0 (令牌基础设施)
  ├── 阻塞 → Phase 1 (A2UI 控件)
  │              ├── 阻塞 → Phase 2 (Stories 鸿蒙化)
  │              │              └── 阻塞 → Phase 3 (Widget 模板)
  │              └── 阻塞 → Phase 4 (Android 默认样式)
  └── 不阻塞 → Phase 5 (CI + 文档，可并行)
```

### 里程碑

| 里程碑 | Phase | 交付物 | 依赖 |
|--------|-------|--------|------|
| M1: 令牌管线就绪 | Phase 0 | tokens.json + Style Dictionary + 资源文件 | 无 |
| M2: 核心控件可用 | Phase 1 | 12 个 Harmony 控件 + Compose 预览 | M1 |
| M3: 官方案例鸿蒙化 | Phase 2 | 26 个 Stories 全部令牌化 | M2 |
| M4: Widget 鸿蒙化 | Phase 3 | 3 个模板替换 + 渲染管线清理 | M2 |
| M5: Android 默认统一 | Phase 4 | component_styles.json 统一 brand | M1 |
| M6: CI + 文档 | Phase 5 | 自动化管线 + 迁移指南 | M1 (可并行) |

---

## 第五章 · 开源参考映射表

| 迭代任务 | 参考项目 | 参考内容 | GitHub/文档 |
|----------|----------|----------|------------|
| Phase 0 令牌管线 | Style Dictionary | JSON → 多平台代码生成 | https://styledictionary.com |
| Phase 0 令牌 CI | Mística | GitHub Actions 自动化 | https://github.com/Telefonica/mistica |
| Phase 0 令牌可视化 | Backbase | Gradle 插件可视化 | (商业) |
| Phase 1 Button | shadcn/ui | CVA 变体管理 + 源码所有权 | https://github.com/shadcn-ui/ui |
| Phase 1 Button | Omni-UI | 鸿蒙原生 Button 实现 | https://github.com/wuba/omni-ui |
| Phase 1 Card | shadcn/ui | Card 组合式 API | https://github.com/shadcn-ui/ui |
| Phase 1 Card | Omni-UI | 鸿蒙原生 Card 实现 | https://github.com/wuba/omni-ui |
| Phase 1 ListItem | Material 3 | ListItem 规范 | https://m3.material.io |
| Phase 1 GlassPanel | Mística | 玻璃模糊背景 | https://github.com/Telefonica/mistica |
| Phase 2 所有 Stories | Gallery 已有令牌 | `{"call":"token",...}` 机制 | AGenUI 内部 |
| Phase 2 动效 | Material 3 Expressive | 动效物理引擎 | https://m3.material.io/expressive |
| Phase 3 Widget 模板 | 已有设计稿 | harmony-templates/*.json | 本项目 docs/research/ |
| Phase 4 色彩统一 | Style Dictionary | 令牌别名机制 | https://styledictionary.com |

---

## 第六章 · 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| AGenUI Gallery 令牌系统与 HarmonyOS 令牌名不一致 | 令牌映射错误 | 第二章 2.2 已建立 1:1 映射表，Phase 0 先验证映射 |
| Widget 模板 CheckBox API 不一致（`checked` vs `value`+`label`） | 运行时崩溃 | Phase 3.5 统一 API，同步更新 WidgetProtocolTemplates |
| Android 鸿蒙双端资源格式不同 | 重复维护 | Style Dictionary 一次生成两端资源 |
| BlurView 依赖鸿蒙原生 API | Android 端玻璃效果降级 | Phase 2 P2 优先级，可用 RenderEffect.createBlurEffect 替代 |
| 深色模式未在 Widget 中实现 | 桌面 Widget 仅浅色 | Phase 3 验收清单增加深色截图 |
| Style Dictionary v4 ESM 配置兼容性 | CI 构建失败 | 锁定 v4.x + Node 22 环境 |

---

## 第七章 · 验收清单

### 全局验收（所有 Phase 完成后）

- [ ] `grep -ri "6200EE\|2273F7\|2E82FF\|1A66FF\|667eea\|764ba2\|4F46E5" platforms/` = 0 行
- [ ] `grep -ri "6200EE\|2273F7\|2E82FF\|1A66FF" playground/` = 0 行
- [ ] component_styles.json 中品牌色统一为 `#007DFF`
- [ ] 26 个 Stories 全部使用令牌引用
- [ ] 3 个 Widget 模板使用 HarmonyOS 风格
- [ ] WidgetRenderActivity 无硬编码颜色/尺寸
- [ ] 浅色/深色双主题视觉正确
- [ ] 按压动效 100ms + scale 0.95 生效
- [ ] Checker 审核通过 (`.handoff/scripts/review.ps1 -Branch harmony-style-migration`)
- [ ] 合入 main（通过 PR）

### Phase 级验收

见各 Phase 内的"验收标准"小节。

---

## 附录 · Worktree 信息

```
主仓库:   C:/Code/zhouqiaor-AGenUI  (main @ 2233580)
工作树:   C:/Code/AGenUI-harmony-migration  (harmony-style-migration @ 2233580)
远程:     https://github.com/zhouqiaor/AGenUI.git
创建日期: 2026-08-25
```
