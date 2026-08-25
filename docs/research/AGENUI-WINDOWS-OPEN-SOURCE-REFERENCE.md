# AGenUI Windows 移植：优秀开源项目复用与参考调研

> 基于 AGenUI Windows 可行性分析（`AGENUI-WINDOWS-FEASIBILITY-ANALYSIS.md`）的后续调研
> 调研时间：2026-08-25
> 调研目标：找出 Windows 移植路径中可直接复用或作为架构参考的开源项目

---

## 1. 调研总览

AGenUI Windows 移植需要构建约 50 个新文件（DLL 导出层 + 26 组件 + 测量 + 平台接口 + 构建 + Playground）。本调研覆盖 12 个开源项目，按**复用深度**分四级：

| 级别 | 含义 | 项目数 |
|------|------|--------|
| **A. 直接复用** | 引入为依赖库，直接调用 API | 4 |
| **B. 架构参考** | 学习其架构模式，不引依赖 | 4 |
| **C. 代码级参考** | 参考具体实现/源码片段 | 3 |
| **D. 渲染后端备选** | 评估作为渲染层后端的可行性 | 1 |

---

## 2. A 级：可直接复用的开源库

### 2.1 Yoga — Flexbox 布局引擎（AGenUI 已依赖）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/facebook/yoga |
| **License** | MIT |
| **Stars** | 18.4k+ |
| **语言** | C++20（C API + C++ wrapper） |
| **Windows 支持** | 原生 MSVC + CMake 构建；vcpkg 端口可用；VSCode vsdbg 调试 |
| **AGenUI 现状** | 已作为核心依赖（v2.0.0），Android/iOS/Harmony 三端均使用 |

**复用方式：**
- AGenUI C++ 核心已通过 CMake `FetchContent` 引入 yoga，Windows 构建沿用相同配置
- yoga 在 Windows 上编译零障碍——纯 C++20 无平台依赖，CMake 原生支持 MSVC 生成器
- vcpkg 端口 `yoga` 可作为备选获取方式

**对 Windows 移植的意义：**
yoga 是 AGenUI 布局层的唯一外部依赖，其 Windows 兼容性直接决定了 C++ 核心能否编译。调研确认 yoga 在 Windows MSVC 下编译无障碍，这是 Windows 移植可行性的基石。

---

### 2.2 Skia — 跨平台 2D 图形引擎（渲染后端候选）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/google/skia |
| **License** | BSD |
| **维护方** | Google |
| **Windows 后端** | Direct2D + DirectWrite（`SK_BUILD_FOR_WIN` / `SK_BUILD_FOR_WIN32`） |
| **使用方** | Chrome、Android、Flutter、Firefox |

**关键源码文件（Windows Direct2D/DirectWrite 后端）：**

```
skia/src/ports/
├── SkScalerContext_win_dw.cpp     // DirectWrite 文字测量与光栅化
├── SkTypeface_win_dw.h            // DirectWrite 字体管理
└── SkTypeface_win_dw.cpp

skia/src/utils/win/
├── SkDWriteGeometrySink.h         // DirectWrite → SkPath 几何转换
├── SkDWriteGeometrySink.cpp       // 实现 ID2D1SimplifiedGeometrySink
├── SkDWrite.h                     // DirectWrite 工厂封装
└── SkDWrite.cpp
```

**`SkDWriteGeometrySink` 核心模式（可直接参考）：**

```cpp
// SkDWriteGeometrySink 实现 ID2D1SimplifiedGeometrySink 接口
// 将 DirectWrite 的字形轮廓转换为 SkPath
class SkDWriteGeometrySink : public ID2D1SimplifiedGeometrySink {
    SkPath* fPath;
    void BeginFigure(D2D1_POINT_2F startPoint, ...);
    void AddLines(const D2D1_POINT_2F* points, UINT pointsCount);
    void AddBeziers(const D2D1_BEZIER_SEGMENT* beziers, UINT beziersCount);
    void EndFigure(D2D1_FIGURE_END figureEnd);
    void SetFillMode(D2D1_FILL_MODE fillMode);
};
```

**`SkScalerContext_win_dw` 核心模式（文字测量 + 光栅化）：**

```cpp
// 使用 IDWriteGlyphRunAnalysis 进行精确文字测量和 Alpha 纹理生成
SkScalerContext_DW::drawDWMask(const SkGlyph& glyph, ...) {
    DWRITE_GLYPH_RUN run;
    run.fontFace = fTypeface->fDWriteFontFace.get();
    run.fontEmSize = SkScalarToFloat(fTextSizeRender);
    
    SkTScopedComPtr<IDWriteGlyphRunAnalysis> glyphRunAnalysis;
    fTypeface->fFactory->CreateGlyphRunAnalysis(&run, ...);
    glyphRunAnalysis->CreateAlphaTexture(textureType, &bbox, fBits.begin(), ...);
}
```

**复用方式：**
- **方案 A（直接依赖）**：引入 Skia 作为 AGenUI Windows 渲染后端，替代手写 Direct2D 代码
  - 优势：成熟的 Direct2D/DirectWrite 集成、跨平台一致性、GPU 加速
  - 劣势：Skia 编译复杂（~200MB 仓库），增加依赖体积
- **方案 B（代码参考）**：不引入 Skia，但参考其 DirectWrite 集成模式实现 AGenUI 的 `IMeasurement` 子类
  - 优势：零依赖体积增加
  - 劣势：需要手写 DirectWrite 封装

**推荐：方案 B**（Phase 0-2 代码参考，Phase 3 评估方案 A）

---

### 2.3 Win2D — Direct2D 高级封装

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/microsoft/Win2D（WinUI3 分支） |
| **License** | MIT |
| **维护方** | Microsoft |
| **语言** | C++ 98.7% + C# 1.3% |
| **NuGet** | `Microsoft.Graphics.Win2D` |
| **集成** | WinUI 3 / WinAppSDK，XAML CanvasControl 无缝嵌入 |

**核心 API 模式：**

```cpp
// C++/WinRT 用法
void CanvasControl_Draw(CanvasControl const& sender, CanvasDrawEventArgs const& args) {
    args.DrawingSession().DrawEllipse(155, 115, 80, 30, Colors::Black(), 3);
    args.DrawingSession().DrawText(L"Hello, world!", 100, 100, Colors::Yellow());
}
```

**Direct2D 互操作（关键）：**

Win2D 提供双向互操作 API：
- `GetWrappedResource<T>(wrapper)` — 从 Win2D 对象获取底层 Direct2D 对象
- `GetOrCreate<T>(native)` — 从 Direct2D 对象创建/获取 Win2D 包装器

```cpp
// 从 Win2D 获取原生 Direct2D 设备
Microsoft::WRL::ComPtr<ID2D1Device1> device = 
    GetWrappedResource<ID2D1Device1>(canvasDevice);
```

**复用方式：**
- **作为 WinUI 3 Playground 的渲染层**：AGenUI Windows Playground 如果用 WinUI 3，Win2D 的 `CanvasControl` 可嵌入 XAML，在 `Draw` 回调中执行 AGenUI 组件的 Direct2D 绘制
- **简化 Direct2D 调用**：Win2D 封装了 Direct2D 的 COM 复杂性，API 更简洁
- **互操作**：可通过 `GetWrappedResource` 获取底层 `ID2D1DeviceContext`，与 AGenUI 直接创建的 Direct2D 资源混用

**推荐：Phase 3 Playground 引入 Win2D NuGet 包**

---

### 2.4 DirectWrite — Windows 原生文字测量（系统 API）

| 属性 | 详情 |
|------|------|
| **来源** | Windows SDK（dwrite.h / dwrite_3.h） |
| **License** | Windows 系统组件（随 SDK 分发） |
| **AGenUI 对接** | 实现 `IMeasurement` 子类 |

**关键 API（用于 AGenUI `IMeasurement` 实现）：**

```cpp
// 1. 创建工厂
IDWriteFactory* dwriteFactory;
DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), 
                     reinterpret_cast<IUnknown**>(&dwriteFactory));

// 2. 创建文本布局（测量核心）
IDWriteTextLayout* textLayout;
dwriteFactory->CreateTextLayout(
    text,              // 字符串
    textLength,        // 长度
    textFormat,        // IDWriteTextFormat（字体/字号/对齐）
    maxWidth,          // 约束宽度
    maxHeight,         // 约束高度
    &textLayout        // 输出
);

// 3. 获取测量结果
DWRITE_TEXT_METRICS metrics;
textLayout->GetMetrics(&metrics);
// metrics.width, metrics.height, metrics.layoutWidth, metrics.layoutHeight
```

**AGenUI 测量集成模式：**

```cpp
class WindowsTextMeasurement : public agenui::IMeasurement {
public:
    void measureText(const std::string& text, const TextStyle& style,
                     float maxWidth, float maxHeight, MeasurementResult* result) override {
        // 1. 创建 IDWriteTextFormat（字体族/字号/字重）
        // 2. 创建 IDWriteTextLayout（约束尺寸）
        // 3. GetMetrics → result
        // 4. 释放 COM 对象
    }
};
```

**注意：** DirectWrite 在 Windows 8/8.1 上部分调用非线程安全，需用 mutex 保护（Skia 源码中 `DWriteFactoryMutex` 即为此设计）。

---

## 3. B 级：架构参考项目

### 3.1 React Native Windows — 最接近的架构平行

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/microsoft/react-native-windows |
| **License** | MIT |
| **维护方** | Microsoft |
| **版本** | 0.77+（Fabric 新架构 Preview） |
| **架构** | C++ 核心 → Fabric 渲染器 → WinAppSDK + DirectComposition |

**为什么是 AGenUI Windows 的最佳架构参考：**

React Native Windows 的架构与 AGenUI 高度平行：

| 维度 | React Native Windows | AGenUI Windows（目标） |
|------|---------------------|----------------------|
| 核心层 | C++ JS Engine + Fabric Renderer | C++ Engine + VirtualDOM + Yoga |
| 平台 SDK | C++ ComponentView → Composition | C++ 组件 → Direct2D/HWND |
| 宿主应用 | cpp-app 模板 (Win32 + WinAppSDK) | Windows Playground (Win32/WinUI 3) |
| 组件映射 | RN 组件 → Fabric ComponentView (C++) | AGenUI 组件 → Win32 控件 (C++) |
| 构建 | MSVC + NuGet 预编译包 | MSVC + CMake + yoga FetchContent |

**Fabric 架构关键模式（值得参考）：**

1. **ComponentView 模式**：Fabric 不再使用 XAML 直接渲染，而是通过 C++ ComponentView 创建原生视觉对象——这与 AGenUI 的 "C++ 组件类创建原生节点句柄" 模式一致（参考 Harmony `OH_ArkUI_GetModuleInterface`）

2. **Composition 引擎**：Fabric 直接使用 Windows Composition API（`DirectComposition` + `Direct2D`）而非 XAML——AGenUI 可以选择相同路径，跳过 XAML 直接使用 Direct2D

3. **cpp-app 模板**：
   ```
   yarn react-native init-windows --template cpp-app --overwrite
   ```
   生成纯 C++ Win32 + WinAppSDK 应用，使用 Hermes 引擎——AGenUI 的 Playground 可以参考此模板生成方式

4. **预编译 NuGet**：Fabric 使用 `Microsoft.ReactNative` NuGet 包预编译核心库——AGenUI 可以将 `agenui_core.dll` 发布为 NuGet 包

**参考方式：不引依赖，学习其 C++ → 原生组件 → Composition/Direct2D 的分层模式**

---

### 3.2 Sciter — 嵌入式 GUI 引擎架构参考

| 属性 | 详情 |
|------|------|
| **官网** | https://sciter.com/ |
| **License** | 商业（非开源，但架构公开） |
| **体积** | 单个 DLL 4-8MB，零额外依赖 |
| **部署量** | 4.6 亿桌面端 |
| **Windows 后端** | Direct2D/DirectWrite（主）、GDI+（备）、Skia（可选） |

**Sciter 引擎架构（与 AGenUI 设计高度相似）：**

```
Sciter Engine 内部模块:
├── CSS 解析器 + 规则集合
├── HTML DOM 解析器 + DOM 树
├── 布局管理器（文本布局 / 块布局 / flex 布局）  ← 与 AGenUI YogaLayoutEngine 对应
├── 输入行为（input/select/textarea 等 DOM 元素）
├── 脚本引擎（QuickJS++）
├── 脚本 DOM（将 DOM 暴露给脚本）
├── 图形抽象层                        ← AGenUI 缺少此层，Windows 需新建
│   ├── Direct2D/DirectWrite 后端 (Windows)
│   ├── GDI+ 后端 (Windows XP)
│   ├── CoreGraphics 后端 (macOS)
│   ├── Cairo 后端 (Linux)
│   └── Skia/OpenGL 后端 (全平台可选)
└── 核心原语（string/array/hashmap）
```

**关键架构参考点：**

1. **图形抽象层**：Sciter 在布局管理器与平台渲染之间有一个独立的"图形抽象层"——AGenUI Windows 应同样设计此层，隔离 Direct2D 调用

2. **C API 导出**：Sciter 通过单个 `SciterAPI()` 函数返回一个包含所有 API 函数指针的大型结构体——AGenUI 的 `agenui_engine_entry.h` 已采用类似模式（`initAGenUIEngine()` / `getAGenUIEngine()`）

3. **单 DLL 分发**：Sciter 整个引擎打包为单个 4-8MB DLL，零依赖——AGenUI 可以将 C++ 核心 + Windows SDK 编译为单个 `agenui_windows.dll`

4. **Direct2D/DirectWrite 作为主后端**：Sciter 在 Windows 7+ 上优先使用 Direct2D/DirectWrite，GDI+ 仅作 XP 退路——AGenUI 应同样以 Direct2D 为主，GDI 不考虑

**参考方式：学习其图形抽象层设计 + 单 DLL 分发模式 + C API 导出模式**

---

### 3.3 NodeGUI — Qt + Yoga 集成参考

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/nodegui/nodegui |
| **License** | MIT |
| **Stars** | 8k+ |
| **核心** | Qt6 + Yoga FlexLayout |
| **内存** | Hello World < 20MB |

**FlexLayout 集成模式（核心参考价值）：**

NodeGUI 在 Qt 的 `QLayout` 体系上封装了一层 `FlexLayout`，桥接 JavaScript API 与 Yoga 引擎：

```
JavaScript (FlexLayout.ts)
    ↓ Node-API
C++ Wrapper (flexlayout.cpp)
    ↓ 
FlexLayout (继承 QLayout)
    ├── FlexNodeContext (维护 Yoga 节点与 QWidget 的映射)
    ├── performLayout() 
    │   ├── 更新节点维度
    │   ├── 触发 Yoga calculateLayout()
    │   ├── 设置 QLayout geometry
    │   └── 遍历子节点，应用计算后的 geometry 到每个 QWidget
    └── 10ms 节流 + 缓存 + 脏标记优化
```

**对 AGenUI Windows 的参考价值：**

1. **Yoga → 原生控件映射**：NodeGUI 的 `FlexNodeContext` 维护 Yoga 节点到 `QWidget` 的映射——AGenUI Windows 需要类似的"Yoga 节点到 HWND/Direct2D 资源"映射

2. **布局计算→原生控件应用**：`performLayout()` 在 Yoga 计算完成后遍历子节点设置 geometry——AGenUI 的 `IVirtualDOMObserver` 回调可以做同样的事

3. **性能优化模式**：10ms 节流 + 脏标记 + 布局缓存——AGenUI 的 `BatchGuard` 已有类似设计，Windows 侧可直接沿用

4. **CSS 样式 → 原生样式转换**：NodeGUI 将 CSS 字符串解析为 Qt StyleSheet——AGenUI 已有 DesignToken 系统，Windows 侧只需将令牌映射到 Direct2D 画刷/颜色

**参考方式：学习 FlexLayout 的 Yoga → 原生控件映射模式**

---

### 3.4 Qt 6 — 成熟跨平台 C++ GUI 参考

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/qt/qtbase |
| **License** | GPL/LGPL（开源）/ 商业 |
| **渲染后端** | OpenGL / D3D / Vulkan / Software |
| **布局** | QBoxLayout / QGridLayout / QFlexLayout (Qt 6.5+) |

**对 AGenUI Windows 的参考价值：**
- Qt 的 `QFlexLayout`（6.5+ 实验性）可能也基于或参考 Yoga，可作为布局实现参考
- Qt 的 D3D 后端集成方式可作为 AGenUI Direct2D 集成的参考
- Qt 的 `QWidget` 事件系统与 Win32 消息循环的桥接方式值得参考

**不推荐直接依赖（GPL/LGPL 许可证限制 + 过重）**

---

## 4. C 级：代码级参考项目

### 4.1 Skia DirectWrite 集成代码

**可参考的具体实现：**

| Skia 源码文件 | AGenUI 对应需求 | 参考内容 |
|---------------|-----------------|----------|
| `SkDWriteGeometrySink.cpp` | 文字轮廓 → Path 转换 | `ID2D1SimplifiedGeometrySink` 实现 |
| `SkScalerContext_win_dw.cpp` | 文字测量 + 光栅化 | `IDWriteGlyphRunAnalysis` 用法 |
| `SkTypeface_win_dw.h` | 字体管理 | `IDWriteFontFace` 封装 |
| `SkDWrite.h/cpp` | DirectWrite 工厂 | `DWriteCreateFactory` 封装 |

**可直接移植的代码片段：**

```cpp
// 来自 Skia SkDWrite.h 的 DirectWrite 工厂初始化
HRESULT hresult = DWriteCreateFactory(
    DWRITE_FACTORY_TYPE_SHARED,
    __uuidof(IDWriteFactory),
    &dwriteFactory
);

// 来自 Skia SkScalerContext_win_dw.cpp 的文字测量
// AGenUI IMeasurement 实现可直接参考此模式
```

---

### 4.2 React Native Windows ComponentView 模式

**Fabric ComponentView 的 C++ 模式（可参考）：**

```cpp
// RNW Fabric ComponentView 概念
class ComponentView {
    winrt::Microsoft::UI::Composition::Visual m_visual;
    
    void updateProps(folly::dynamic const& props) { /* 更新属性 */ }
    void updateLayout(int x, int y, int width, int height) {
        m_visual.Offset({static_cast<float>(x), static_cast<float>(y), 0.0f});
        m_visual.Size({static_cast<float>(width), static_cast<float>(height)});
    }
    void CreateVisual() {
        m_visual = m_compositor.CreateSpriteVisual();
        // 创建 Composition 视觉对象
    }
};
```

**与 AGenUI Harmony 模式的平行：**

| AGenUI Harmony | RNW Fabric | AGenUI Windows（目标） |
|----------------|-----------|---------------------|
| `OH_ArkUI_GetModuleInterface()` | `Compositor.CreateSpriteVisual()` | `CreateWindowEx()` / `D2D1CreateFactory()` |
| `ArkUI_NativeNodeAPI_1` | `Microsoft::UI::Composition` | Direct2D + DirectComposition |
| C++ 组件操作节点句柄 | C++ ComponentView 操作 Visual | C++ 组件操作 HWND/D2D 资源 |

---

### 4.3 WinUI 3 控件实现

WinUI 3（`microsoft-ui-xaml`，MIT）的控件源码可参考：
- `Button.cpp` — 按钮控件实现（事件路由、视觉状态、Command 绑定）
- `TextBlock.cpp` — 文本渲染（DirectWrite 集成）
- `Image.cpp` — 图像加载与显示（WIC 集成）

**参考方式：阅读 WinUI 3 源码理解 Direct2D/Composition 的实际使用方式**

---

## 5. D 级：渲染后端备选

### 5.1 渲染后端选型对比

| 后端 | 优势 | 劣势 | 推荐度 |
|------|------|------|--------|
| **Direct2D/DirectWrite（直接）** | Windows 原生、性能最优、零额外依赖 | COM API 复杂、手写代码量大 | ★★★★★ 推荐 |
| **Skia** | 跨平台一致、成熟 Direct2D 后端、Chrome/Flutter 验证 | 编译复杂、体积大（~50MB） | ★★★☆ Phase 3+ |
| **Win2D** | 简化 Direct2D 调用、MIT、XAML 集成 | 仅适用于 WinUI 3 Playground | ★★★★ Phase 3 |
| **GDI+** | 简单、老 Windows 兼容 | 无硬件加速、无抗锯齿 | ★ 不推荐 |
| **Composition API** | 系统原生、声明式、与 XAML 集成好 | 抽象层厚、调试难 | ★★★ 评估 |

**推荐路径：**
1. **Phase 0-2**：直接使用 Direct2D/DirectWrite（参考 Skia 源码片段）
2. **Phase 3**：Playground 使用 Win2D（WinUI 3 + CanvasControl）
3. **Phase 4+（可选）**：评估 Skia 作为统一跨平台渲染后端

---

## 6. 复用/参考矩阵

### 6.1 按 AGenUI Windows SDK 模块映射

| AGenUI Windows SDK 模块 | 可复用/参考项目 | 级别 | 具体复用内容 |
|------------------------|----------------|------|-------------|
| **C++ 核心编译** | yoga | A | 直接 FetchContent + MSVC |
| **DLL 导出层** | Sciter `SciterAPI()` 模式 | B | 单函数导出 API 结构体模式 |
| **DirectWrite 文字测量** | Skia `SkScalerContext_win_dw` | C | `IDWriteTextLayout::GetMetrics` + `IDWriteGlyphRunAnalysis` |
| **Direct2D 绘制** | Win2D `CanvasControl_Draw` | A/C | NuGet 包 + 互操作 `GetWrappedResource` |
| **Yoga → 原生控件映射** | NodeGUI `FlexLayout` + `FlexNodeContext` | B | Yoga 节点 → 原生句柄映射模式 |
| **C++ → 原生组件模式** | RNW Fabric `ComponentView` | B | C++ 类操作 Visual/节点句柄 |
| **单 DLL 分发** | Sciter 单 4-8MB DLL | B | 静态链接 + 单输出模式 |
| **图形抽象层设计** | Sciter 图形抽象层 | B | 布局层与渲染层之间抽象隔离 |
| **Composition 集成** | RNW Fabric → Composition | B | C++ 直接使用 Composition API |
| **字体管理** | Skia `SkTypeface_win_dw` | C | `IDWriteFontFace` 封装 |
| **图像加载** | Windows WIC（系统 API） | A | `IWICImagingFactory` |
| **Playground 宿主** | RNW `cpp-app` 模板模式 | B | Win32 + WinAppSDK 应用模板 |
| **构建系统** | yoga CMake + MSVC | A | FetchContent + CMake 生成器 |
| **NuGet 分发** | RNW `Microsoft.ReactNative` NuGet | B | 预编译 DLL → NuGet 包 |

### 6.2 按项目维度

| 项目 | License | 级别 | 与 AGenUI 的关系 | 推荐阶段 |
|------|---------|------|-----------------|---------|
| **yoga** | MIT | A | 已有依赖，Windows 编译验证通过 | Phase 0 |
| **Skia** | BSD | C | Direct2D/DirectWrite 后端代码参考 | Phase 0-2 |
| **Win2D** | MIT | A | WinUI 3 Playground 渲染简化 | Phase 3 |
| **DirectWrite** | 系统 | A | `IMeasurement` 实现核心 | Phase 1 |
| **RNW** | MIT | B | 架构平行，ComponentView 模式参考 | Phase 0-3 |
| **Sciter** | 商业 | B | 单 DLL 架构 + 图形抽象层参考 | Phase 0 设计 |
| **NodeGUI** | MIT | B | Qt + Yoga FlexLayout 集成参考 | Phase 1-2 |
| **Qt 6** | GPL/LGPL | C | D3D 后端 + QFlexLayout 参考 | 不引入 |
| **WinUI 3** | MIT | C | 控件源码参考 | Phase 2-3 |
| **Direct2D** | 系统 | A | 原生 2D 渲染 | Phase 1+ |
| **WIC** | 系统 | A | 图像加载 | Phase 1 |
| **DirectComposition** | 系统 | A | 视觉树管理 | Phase 2+ |

---

## 7. 推荐的 AGenUI Windows SDK 架构（基于调研综合）

```
┌─────────────────────────────────────────────────────────┐
│                    Playground 应用                       │
│          (Win32 + WinAppSDK, 参考 RNW cpp-app)           │
├─────────────────────────────────────────────────────────┤
│  Win2D CanvasControl (XAML 嵌入, Phase 3)                │
│  或 直接 Win32 HWND + Direct2D HwndRenderTarget          │
├─────────────────────────────────────────────────────────┤
│              Windows SDK 层 (新建 ~50 文件)              │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐ │
│  │ DLL 导出层    │ │ 26 组件类    │ │ 平台接口实现     │ │
│  │ (参考 Sciter │ │ (参考 RNW   │ │ IPlatformFunc  │ │
│  │  SciterAPI)  │ │  Component  │ │ ISurfaceSize   │ │
│  │              │ │  View 模式)  │ │ Provider       │ │
│  └──────────────┘ └──────────────┘ └────────────────┘ │
│  ┌─────────────────────────────────────────────────────┐│
│  │     图形抽象层 (参考 Sciter 图形抽象层设计)          ││
│  │  ┌─────────────────┐  ┌──────────────────────────┐ ││
│  │  │ Direct2D 渲染器  │  │ DirectWrite 文字测量      │ ││
│  │  │ (系统 API)       │  │ (参考 Skia SkScalerCtx)  │ ││
│  │  └─────────────────┘  └──────────────────────────┘ ││
│  │  ┌─────────────────┐  ┌──────────────────────────┐ ││
│  │  │ WIC 图像加载     │  │ Yoga → 原生控件映射      │ ││
│  │  │ (系统 API)       │  │ (参考 NodeGUI FlexLayout)│ ││
│  │  └─────────────────┘  └──────────────────────────┘ ││
│  └─────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────┤
│              C++ 核心层 (已有, 189 文件)                  │
│  Engine + VirtualDOM + YogaLayoutEngine + StreamParser  │
│  (零平台 #ifdef, 直接 MSVC 编译, yoga FetchContent)       │
├─────────────────────────────────────────────────────────┤
│                     yoga v2.0.0                         │
│              (MIT, 已有依赖, Windows 兼容)                │
└─────────────────────────────────────────────────────────┘
```

---

## 8. 集成优先级与实施建议

### 8.1 Phase 0（1-2 天）— C++ 核心编译验证

| 任务 | 依赖项目 | 复用方式 |
|------|---------|---------|
| CMakeLists.txt 适配 MSVC | yoga CMake 脚本 | 直接参考 yoga 的 Windows 构建配置 |
| 编译 `agenui_core.dll` | yoga v2.0.0 | FetchContent（已有配置） |
| 验证 DLL 导出 | — | `__declspec(dllexport)` + `dumpbin /exports` |

### 8.2 Phase 1（5-7 天）— MVP 三组件

| 任务 | 依赖项目 | 复用方式 |
|------|---------|---------|
| DirectWrite 测量实现 | Skia `SkScalerContext_win_dw` | 代码级参考 `IDWriteTextLayout::GetMetrics` |
| Direct2D 绘制基础 | Direct2D 系统 API | `D2D1CreateFactory` + `ID2D1HwndRenderTarget` |
| Text 组件 | DirectWrite | `IDWriteTextFormat` + `IDWriteTextLayout` |
| Button 组件 | Direct2D + Win32 | `CreateWindowEx` + `D2D1::ColorF` + `FillRoundedRectangle` |
| Image 组件 | WIC | `IWICImagingFactory` + `CreateBitmapFromWICBitmap` |
| Yoga → 控件映射 | NodeGUI `FlexLayout` | 架构参考：Yoga 节点 → HWND 映射 |

### 8.3 Phase 2（2-3 周）— 全组件补齐

| 任务 | 依赖项目 | 复用方式 |
|------|---------|---------|
| 23 个剩余组件 | WinUI 3 控件源码 | 参考 `Button.cpp` / `TextBlock.cpp` 等实现 |
| 图形抽象层 | Sciter 图形抽象层 | 架构参考：隔离 Direct2D 调用 |
| 事件系统 | Win32 消息循环 | `WM_LBUTTONDOWN` → AGenUI 事件回流 |

### 8.4 Phase 3（1 周）— Playground 应用

| 任务 | 依赖项目 | 复用方式 |
|------|---------|---------|
| WinUI 3 Playground | RNW `cpp-app` 模板 | 参考模板生成方式 |
| Win2D 渲染集成 | Win2D NuGet | `CanvasControl` + `Draw` 回调 |
| 主题/令牌 | AGenUI DesignToken 系统 | 直接复用令牌映射 |
| NuGet 打包 | RNW `Microsoft.ReactNative` NuGet | 参考预编译 DLL → NuGet 模式 |

---

## 9. 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| DirectWrite 线程安全（Win 8/8.1） | 测量崩溃 | 参考 Skia `DWriteFactoryMutex` 模式，用 `SRWLOCK` 保护 |
| COM 对象生命周期 | 内存泄漏 | 使用 `ComPtr<T>`（WRL）自动管理引用计数 |
| Skia 编译复杂 | 增加构建时间 | Phase 0-2 不引 Skia，Phase 3+ 评估预编译 NuGet |
| Win2D WinUI3 仍在开发中 | API 不稳定 | Phase 3 引入时锁定 NuGet 版本 |
| RNW Fabric 不完全稳定 | 参考代码可能变化 | 参考 Fabric 的架构模式而非具体实现 |
| Sciter 非开源 | 不能直接复用代码 | 仅参考架构设计，不复制代码 |

---

## 10. 结论

**核心发现：**

1. **yoga 是 Windows 移植可行性的基石**——已作为 AGenUI 依赖，Windows MSVC 编译零障碍，确认了 C++ 核心可移植的前提。

2. **Skia 的 Direct2D/DirectWrite 后端是最有价值的代码级参考**——`SkScalerContext_win_dw.cpp` 和 `SkDWriteGeometrySink.cpp` 提供了生产级 DirectWrite 集成代码，可直接指导 AGenUI `IMeasurement` 实现。

3. **React Native Windows 是最佳架构平行参考**——其 C++ 核心 → Fabric ComponentView → WinAppSDK + Composition 的三层架构与 AGenUI C++ 核心 → C++ 组件 → Win32 + Direct2D 的目标架构高度平行。

4. **Sciter 的图形抽象层设计最值得学习**——在布局管理器与平台渲染之间建立独立抽象层，隔离 Direct2D 调用，是 AGenUI Windows SDK 应采用的设计模式。

5. **NodeGUI 的 FlexLayout 提供了 Yoga → 原生控件映射的直接参考**——`FlexNodeContext` 维护 Yoga 节点到 QWidget 的映射，AGenUI Windows 可照此设计 Yoga 节点到 HWND/Direct2D 资源的映射。

6. **Win2D 是 Phase 3 Playground 的理想渲染层**——MIT 许可、NuGet 分发、CanvasControl 嵌入 XAML、与 Direct2D 双向互操作，简化了 WinUI 3 下的 Direct2D 使用。

**最终推荐：不引入重型框架（Qt/Skia 全量），以 Direct2D/DirectWrite 为原生渲染后端，参考 Skia 源码片段实现文字测量，参考 RNW 架构设计组件层，参考 NodeGUI 实现 Yoga 映射，Phase 3 引入 Win2D 简化 Playground 渲染。**
