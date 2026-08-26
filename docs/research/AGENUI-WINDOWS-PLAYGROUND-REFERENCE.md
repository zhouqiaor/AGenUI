# AGenUI Windows Playground：业界优秀开源项目复用调研

> 调研时间：2026-08-25
> 前置文档：`AGENUI-WINDOWS-FEASIBILITY-ANALYSIS.md`（可行性分析）、`AGENUI-WINDOWS-OPEN-SOURCE-REFERENCE.md`（SDK 层开源复用）
> 调研目标：为 AGenUI Windows Playground（宿主应用层）选型提供开源项目复用依据
> 当前状态：Phase 0 已完成（`agenui_windows.dll` 编译通过 + 7 项冒烟测试全 PASS，commit `9f34e18`）

---

## 1. 调研总览

AGenUI Windows 移植分两层：**SDK 层**（DLL 导出 + 26 组件 + 测量 + 平台接口）和 **Playground 层**（宿主应用，负责窗口管理、渲染表面、输入分发、DPI 适配）。前者已在 `AGENUI-WINDOWS-OPEN-SOURCE-REFERENCE.md` 覆盖 12 个项目；本报告专注后者——Playground 宿主框架选型。

### 1.1 Playground 的核心职责

| 职责 | 说明 | AGenUI 对接点 |
|------|------|--------------|
| **窗口管理** | 创建/调整/销毁窗口，处理 WM_SIZE/WM_CLOSE | `ISurfaceSizeProvider` 回供窗口尺寸 |
| **渲染表面** | 提供 Direct2D/Direct3D 绘图目标 | AGenUI 组件的 Direct2D 绘制目标 |
| **消息循环** | Win32 消息泵 / 游戏循环 / 事件回调 | 引擎主线程调用入口 |
| **输入分发** | 鼠标/键盘/触摸 → 组件事件 | `IAGenUIMessageListener` 事件回流 |
| **DPI 适配** | Per-Monitor DPI 感知 | 布局/测量尺寸缩放 |
| **资源生命周期** | Direct2D/DirectWrite 设备相关资源管理 | `D2DERR_RECREATE_TARGET` 恢复 |

### 1.2 候选框架矩阵

| 框架 | 语言 | 渲染后端 | 许可证 | AGenUI 契合度 | 推荐阶段 |
|------|------|---------|--------|--------------|---------|
| **Win32 + Direct2D** | C++ | Direct2D/DirectWrite（原生） | 系统 API | ★★★★★ | Phase 1-2 |
| **WinUI 3 + Win2D** | C++/WinRT | Direct2D via Win2D | MIT | ★★★★ | Phase 3 |
| **ImGui + DX11** | C++ | DirectX11 immediate mode | MIT | ★★★ | 原型验证 |
| **SDL2/SDL3 + D3D11** | C | D3D11/OpenGL/Vulkan | zlib | ★★ | 不推荐 |
| **GLFW + OpenGL/D2D** | C/C++ | OpenGL（D2D 需 hack） | zlib/MIT | ★★ | 不推荐 |
| **Flutter Desktop** | Dart | Skia/Impeller | BSD | ★ | 不适用 |

---

## 2. Win32 + Direct2D — 首选 Playground 框架

### 2.1 Microsoft Direct2D 官方 DemoApp 模式

| 属性 | 详情 |
|------|------|
| **来源** | Microsoft Learn — Direct2D QuickStart |
| **文档** | https://learn.microsoft.com/en-us/windows/win32/direct2d/getting-started-with-direct2d |
| **许可证** | 公开文档（可参考实现） |
| **核心模式** | `DemoApp` 类封装窗口 + 渲染 |

**Direct2D HwndRenderTarget 核心代码模式：**

```cpp
// 1. 创建 D2D 工厂
D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &m_pD2DFactory);

// 2. 创建 HwndRenderTarget（绑定 HWND）
m_pD2DFactory->CreateHwndRenderTarget(
    RenderTargetProperties(),
    HwndRenderTargetProperties(m_hwnd, size, PresentOptions()),
    &m_pRenderTarget);

// 3. 绘制循环
m_pRenderTarget->BeginDraw();
m_pRenderTarget->Clear(D2D1::ColorF(D2D1::ColorF::White));
m_pRenderTarget->DrawRectangle(rect, m_pBlackBrush);
m_pRenderTarget->EndDraw();

// 4. 处理设备丢失
if (hr == D2DERR_RECREATE_TARGET) {
    m_pRenderTarget.Reset();
    // 下次 OnRender 重建
}
```

**AGenUI Playground 对接方式：**
- `ISurfaceSizeProvider::getSize()` → `m_pRenderTarget->GetSize()`
- AGenUI 组件的 `paint()` → `m_pRenderTarget->DrawText()/FillRectangle()/DrawBitmap()`
- `WM_SIZE` → 重建/调整 `HwndRenderTarget`
- `D2DERR_RECREATE_TARGET` → 标记设备资源失效，等待重建

**复用深度：A 级（直接按此模式实现）**

### 2.2 CodeProject Direct2D Tutorial（FactorySingleton 模式）

| 属性 | 详情 |
|------|------|
| **来源** | CodeProject — Direct2D Tutorial (Part 1-5) |
| **许可证** | CPOL（Code Project Open License） |
| **核心价值** | COM 生命周期管理 + 工厂单例模式 |

**关键改进模式（优于官方 DemoApp）：**

```cpp
// FactorySingleton 模式 — D2D/DWrite/WIC 工厂全局唯一
class FactorySingleton {
    Microsoft::WRL::ComPtr<ID2D1Factory> m_D2DFactory;
    Microsoft::WRL::ComPtr<IDWriteFactory> m_DWriteFactory;
    Microsoft::WRL::ComPtr<IWICImagingFactory> m_WICFactory;
public:
    static FactorySingleton& GetInstance();
    ID2D1Factory* GetD2DFactory() { return m_D2DFactory.Get(); }
    IDWriteFactory* GetDWriteFactory() { return m_DWriteFactory.Get(); }
    IWICImagingFactory* GetWICFactory() { return m_WICFactory.Get(); }
};

// ComPtr<T> 智能指针 — 自动 COM 引用计数
Microsoft::WRL::ComPtr<ID2D1HwndRenderTarget> m_DCTarget;

// 窗口状态检查（避免不可见时绘制）
if (m_DCTarget->CheckWindowState() & D2D1_WINDOW_STATE_OCCLUDED)
    return; // 窗口被遮挡，跳过绘制
```

**AGenUI 对接价值：**
- FactorySingleton 模式可直接用于 AGenUI Windows SDK 的工厂层
- `ComPtr<T>` 是 AGenUI 管理 COM 对象生命周期的标准做法
- `CheckWindowState()` 遮挡检查可用于 AGenUI 的绘制优化

**复用深度：A 级（直接采用 FactorySingleton + ComPtr 模式）**

### 2.3 asdlei99/Direct2D-demo（MIT 开源示例）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/asdlei99/Direct2D-demo |
| **许可证** | MIT |
| **价值** | 修复微软官方示例 64 位崩溃的完整 Win32 + Direct2D 示例 |
| **技术栈** | ATL（Active Template Library） |

**可参考内容：**
- 完整的 Win32 窗口创建 + Direct2D 初始化 + 绘制循环
- 64 位兼容性修复（官方示例在 x64 下崩溃的已知问题）
- ATL 窗口类封装模式（比裸 Win32 API 更简洁）

**复用深度：B 级（参考代码结构，不一定引入 ATL）**

### 2.4 LHY1339/Direct2DExample（渐进式中文教程）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/LHY1339/Direct2DExample |
| **许可证** | 未声明（参考用途） |
| **价值** | 6 步渐进式 Direct2D 教程，含中文注释 |

**6 步内容（每步直接对应 AGenUI 组件）：**

| 步骤 | 内容 | AGenUI 对应组件 |
|------|------|----------------|
| 1 | 创建窗口（RegisterClass + CreateWindow） | Playground 主窗口 |
| 2 | 初始化 D2D（ID2D1Factory + ID2D1HwndRenderTarget） | SDK 初始化 |
| 3 | 绘制纯色矩形（ID2D1SolidColorBrush + D2D1_ROUNDED_RECT） | Card / Container |
| 4 | 绘制渐变矩形（D2D1_GRADIENT_STOP + ID2D1LinearGradientBrush） | Card 渐变背景 |
| 5 | 绘制文字（IDWriteFactory + IDWriteTextFormat + DrawTextW） | Text 组件 |
| 6 | 绘制图片（IWICImagingFactory + ID2D1BitmapBrush） | Image 组件 |

**复用深度：C 级（代码片段参考）**

### 2.5 ocucu/direct2d-directwrite-samples（MFC 封装集）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/ocucu/direct2d-directwrite-samples |
| **许可证** | MIT |
| **Stars** | 5 |
| **内容** | 8 个 Direct2D/DirectWrite Demo（MFC 框架 + 封装类） |

**Demo 列表与 AGenUI 对应：**
- Direct2D Brush → 纯色/渐变/位图画刷（所有自绘组件）
- Direct2D Brush Transform → 画刷变换（Card 渐变）
- Direct2D Render Target Transform → 渲染目标变换（Carousel 滚动）
- Direct2D Photo/Filter Effects → 图片效果（Image 组件）
- DirectWrite Text Format → 文本格式化（Text 组件）
- DirectWrite Text Layout → 文本布局（RichText 组件）

**复用深度：C 级（代码片段参考，不引入 MFC）**

---

## 3. WinUI 3 + Win2D — Phase 3 现代化升级路径

### 3.1 Win2D for WinUI 3

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/microsoft/Win2D（WinUI3 分支） |
| **许可证** | MIT |
| **维护方** | Microsoft |
| **NuGet** | `Microsoft.Graphics.Win2D` |
| **状态** | Work in progress（WinUI3 迁移进行中） |
| **语言** | C# / C++/WinRT / C++/CX / VB |

**核心 API 模式（CanvasControl + DrawingSession）：**

```cpp
// C++/WinRT 用法
void MainWindow::CanvasControl_Draw(
    CanvasControl const& sender,
    CanvasDrawEventArgs const& args)
{
    // 简洁的 immediate-mode 绘制
    args.DrawingSession().DrawEllipse(155, 115, 80, 30, Colors::Black(), 3);
    args.DrawingSession().DrawText(L"Hello, world!", 100, 100, Colors::Yellow());
}
```

**XAML 嵌入方式：**
```xml
xmlns:canvas="using:Microsoft.Graphics.Canvas.UI.Xaml"
<canvas:CanvasControl Draw="CanvasControl_Draw" />
```

**Direct2D 双向互操作（关键能力）：**

```cpp
// 从 Win2D 对象获取底层 Direct2D 设备
Microsoft::WRL::ComPtr<ID2D1Device1> device =
    GetWrappedResource<ID2D1Device1>(canvasDevice);

// 从 Direct2D 原生对象创建/获取 Win2D 包装器
auto brush = GetOrCreate<ID2D1SolidColorBrush>(nativeBrush);
```

**AGenUI Playground 对接方式（Phase 3）：**
- `CanvasControl` 嵌入 WinUI 3 XAML 页面，作为 AGenUI 渲染容器
- `Draw` 回调中调用 AGenUI 组件的 Direct2D 绘制命令
- 通过 `GetWrappedResource` 获取底层 `ID2D1DeviceContext`，与 AGenUI SDK 层直接创建的 Direct2D 资源混用
- `CanvasControl.Invalidate()` 触发重绘，对应 AGenUI 的 `markDirty` 机制

**CanvasControl 生命周期事件：**
- `CreateResources` — 资源初始化（对应 AGenUI 引擎 init）
- `Draw` — 每次重绘（对应 AGenUI 布局→绘制循环）
- `Invalidate()` — 手动触发重绘（对应 AGenUI 虚拟 DOM diff → 重绘）

**注意：** Win2D for WinUI3 仍在开发中，`CanvasAnimatedControl` 部分支持，`CanvasVirtualControl` 支持不完整。Phase 3 引入时需锁定 NuGet 版本。

**复用深度：A 级（Phase 3 直接引入 NuGet 包）**

### 3.2 WinUI 3 + Direct2D Composition（castorix 模式）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/castorix/WinUI3_Direct2D_Composition |
| **许可证** | MIT（推测） |
| **技术** | WinUI 3 + `Microsoft.UI.Composition.CompositionDrawingSurface` + `SpriteVisual` |
| **价值** | 在 XAML 控件上叠加 Direct2D 绘制（支持透明） |

**核心模式：**
```cpp
// 在 WinUI 3 XAML 上叠加 Direct2D 绘制
auto compositor = winrt::Microsoft::UI::Composition::Compositor();
auto spriteVisual = compositor.CreateSpriteVisual();
auto drawingSurface = compositor.CreateDrawingSurface(
    size, DirectXPixelFormat::B8G8R8A8UIntNormalized,
    DirectXAlphaMode::Premultiplied);

// 使用 Direct2D 在 CompositionDrawingSurface 上绘制
auto d2dDevice = GetD2DDevice(compositionDevice);
auto d2dContext = CreateD2DContext(d2dDevice, drawingSurface);
d2dContext->BeginDraw();
d2dContext->DrawText(...);
d2dContext->EndDraw();

spriteVisual.Brush(compositor.CreateSurfaceBrush(drawingSurface));
```

**AGenUI 对接价值：**
- 如果 Phase 3 选择 WinUI 3 但不使用 Win2D，此模式可直接在 XAML 上渲染 AGenUI 组件
- 透明叠加能力允许 AGenUI 渲染层与 XAML 原生控件混合
- `CompositionDrawingSurface` 提供与 AGenUI Direct2D 绘制的直接对接

**复用深度：B 级（架构参考，Phase 3 评估）**

### 3.3 RobsonPontin/WinUI3（C++/WinRT Playground 示例集）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/RobsonPontin/WinUI3 |
| **许可证** | 未声明（参考用途） |
| **内容** | WinUI 3 C++/WinRT 示例集合（PlaygroundApp 等） |

**可参考示例：**
- **PlaygroundApp** — 文件选择器、图片缩放（WIC/D2D）、视频帧提取（D3D11）
- 展示了 WinUI 3 C++/WinRT 的实际项目结构和构建配置

**复用深度：B 级（项目结构参考）**

---

## 4. DirectWrite 文字测量 — IMeasurement 实现核心

### 4.1 Direct2D 文本渲染三层 API

来自 Microsoft 官方文档（https://learn.microsoft.com/en-us/windows/win32/direct2d/text-and-paragraphs）：

```
层次 1: DrawText()         — 简单文本 + TextFormat（适合固定布局 UI）
层次 2: DrawTextLayout()    — TextLayout 对象（多格式/换行/命中测试）
层次 3: DrawGlyphRun()      — 字形级渲染（需自实现布局，如文字处理器）
```

**AGenUI 组件使用层次建议：**

| AGenUI 组件 | 推荐层次 | 理由 |
|-------------|---------|------|
| Text | 层次 1 `DrawText()` | 简单文本显示，固定格式 |
| Button | 层次 1 `DrawText()` + `FillRoundedRectangle()` | 按钮内文字 |
| RichText | 层次 2 `DrawTextLayout()` | 多格式/链接/嵌入对象 |
| List/Table | 层次 1 `DrawText()` | 单元格内简单文本 |

### 4.2 DirectWrite 文字测量核心代码

**测量流程（来自多个源综合）：**

```cpp
// 1. 创建 DWrite 工厂
ComPtr<IDWriteFactory> dwriteFactory;
DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED,
    __uuidof(IDWriteFactory), &dwriteFactory);

// 2. 创建 TextFormat（字体/字号/字重/对齐）
ComPtr<IDWriteTextFormat> textFormat;
dwriteFactory->CreateTextFormat(
    L"Segoe UI",                    // 字体族
    nullptr,                         // 字体集合（null = 系统默认）
    DWRITE_FONT_WEIGHT_NORMAL,       // 字重
    DWRITE_FONT_STYLE_NORMAL,        // 样式（normal/italic/oblique）
    DWRITE_FONT_STRETCH_NORMAL,     // 拉伸
    14.0f,                           // 字号（DIP）
    L"",                             // locale
    &textFormat);

// 3. 创建 TextLayout（约束尺寸）
ComPtr<IDWriteTextLayout> textLayout;
dwriteFactory->CreateTextLayout(
    text,              // 字符串
    textLength,         // 长度
    textFormat.Get(),   // TextFormat
    maxWidth,           // 约束宽度（0 = 不限制）
    maxHeight,          // 约束高度（0 = 不限制）
    &textLayout);

// 4. 获取测量结果
DWRITE_TEXT_METRICS metrics;
textLayout->GetMetrics(&metrics);
// metrics.width                          — 文本宽度（不含尾部空白）
// metrics.widthIncludingTrailingWhitespace — 含尾部空白
// metrics.height                         — 文本高度
// metrics.layoutWidth / layoutHeight      — 布局尺寸
```

**AGenUI `IMeasurement` 实现对接：**

```cpp
class WindowsTextMeasurement : public agenui::IMeasurement {
    void measureText(const std::string& text, const TextStyle& style,
                     float maxWidth, float maxHeight,
                     MeasurementResult* result) override {
        // 1. 从 TextStyle 创建/缓存 IDWriteTextFormat
        // 2. 创建 IDWriteTextLayout
        // 3. GetMetrics → result->width / result->height
        // 4. 释放 TextLayout（TextFormat 可缓存复用）
    }
};
```

**性能优化关键点：**
- `IDWriteTextLayout` 对象**可缓存复用**——同一文本重复绘制时无需重新测量/布局
- `IDWriteTextFormat` 对象**按字体样式缓存**——避免每次创建
- DirectWrite 在 Windows 8/8.1 上部分调用非线程安全——需用 `SRWLOCK` 保护（参考 Skia `DWriteFactoryMutex`）

**复用深度：A 级（直接按此模式实现 IMeasurement）**

### 4.3 DirectWrite 精确行高计算

来自 Microsoft 文档 + Stack Overflow 讨论：

```cpp
// 获取字体的精确行高（含 ascent + descent + lineGap）
IDWriteFontCollection* collection;
IDWriteFontFamily* fontFamily;
IDWriteFont* font;
DWRITE_FONT_METRICS metrics;

textFormat->GetFontCollection(&collection);
collection->FindFamilyName(fontName, &fontIndex, &exists);
collection->GetFontFamily(fontIndex, &fontFamily);
fontFamily->GetFirstMatchingFont(
    textFormat->GetFontWeight(),
    textFormat->GetFontStretch(),
    textFormat->GetFontStyle(),
    &font);
font->GetMetrics(&metrics);

// 计算 em-size 缩放比
float ratio = textFormat->GetFontSize() / (float)metrics.designUnitsPerEm;
float lineSpacing = (metrics.ascent + metrics.descent + metrics.lineGap) * ratio;
```

**AGenUI 对接价值：** 用于 List/Table 组件的行高计算——精确行高避免文字裁剪/重叠。

**复用深度：A 级（List/Table 组件行高计算必需）**

### 4.4 DirectWrite 自定义文本渲染器

| 属性 | 详情 |
|------|------|
| **来源** | Microsoft Learn — How to Implement a Custom Text Renderer |
| **文档** | https://learn.microsoft.com/en-us/windows/win32/directwrite/how-to-implement-a-custom-text-renderer |

**IDWriteTextRenderer 接口（高级定制）：**

```cpp
class CustomTextRenderer : public IDWriteTextRenderer {
    HRESULT DrawGlyphRun(
        void* clientDrawingContext,
        FLOAT baselineOriginX, FLOAT baselineOriginY,
        DWRITE_MEASURING_MODE measuringMode,
        const DWRITE_GLYPH_RUN* glyphRun,
        const DWRITE_GLYPH_RUN_DESCRIPTION* glyphRunDescription,
        IUnknown* clientDrawingEffect) override;

    HRESULT DrawUnderline(...);
    HRESULT DrawStrikethrough(...);
    HRESULT DrawInlineObject(...);

    // 像素对齐/DPI/变换
    HRESULT IsPixelSnappingDisabled(void*, BOOL*);
    HRESULT GetCurrentTransform(void*, DWRITE_MATRIX*);
    HRESULT GetPixelsPerDip(void*, FLOAT*);
};
```

**AGenUI 对接价值：**
- RichText 组件如果需要自定义渲染效果（如位图填充文字），需实现 `IDWriteTextRenderer`
- 大部分组件不需要——`DrawText()` / `DrawTextLayout()` 足够

**复用深度：C 级（仅 RichText 组件需要时参考）**

---

## 5. ImGui + DirectX11 — 快速原型验证

### 5.1 Dear ImGui

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/ocornut/imgui |
| **许可证** | MIT |
| **Stars** | 63k+ |
| **后端** | `imgui_impl_win32.cpp` + `imgui_impl_dx11.cpp` |
| **特点** | Immediate-mode GUI，无保留 UI 状态 |

**Win32 + DX11 集成代码：**

```cpp
// 初始化
ImGui::CreateContext();
ImGui_ImplWin32_Init(hwnd);
ImGui_ImplDX11_Init(g_pd3dDevice, g_pd3dDeviceContext);

// 主循环
while (msg.message != WM_QUIT) {
    if (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
        TranslateMessage(&msg); DispatchMessage(&msg);
        continue;
    }
    ImGui_ImplDX11_NewFrame();
    ImGui_ImplWin32_NewFrame();
    ImGui::NewFrame();

    // AGenUI 控制 + 测试面板
    ImGui::Begin("AGenUI Playground");
    // 在此处调用 AGenUI 引擎 + 组件
    ImGui::End();

    ImGui::Render();
    g_pd3dDeviceContext->OMSetRenderTargets(1, &g_mainRenderTargetView, NULL);
    g_pd3dDeviceContext->ClearRenderTargetView(g_mainRenderTargetView, clearColor);
    ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
    g_pSwapChain->Present(1, 0);
}
```

**AGenUI 对接可能性：**
- ImGui 可作为 AGenUI Playground 的"控制面板"（JSON 编辑器、组件树、属性面板）
- AGenUI 渲染结果通过 Direct2D 绘制到 DX11 纹理，再在 ImGui 窗口中显示
- 多视口（Multi-Viewport）支持允许 AGenUI 渲染窗口与控制面板分离

**局限：** ImGui 是 immediate-mode，AGenUI 是 retained-mode（虚拟 DOM + yoga 布局），两种范式冲突。不适合作为长期 Playground 框架。

**复用深度：B 级（仅原型/调试面板参考）**

### 5.2 ImGui-AppKit-DX11（项目模板）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/90th/ImGui-AppKit-DX11 |
| **许可证** | 未声明 |
| **价值** | 单/多窗口 GUI 应用项目模板，零依赖编译为单 .exe |

**可参考内容：**
- 智能指针管理的窗口管理器
- 多窗口支持（AGenUI 多 Surface 场景）

**复用深度：C 级（项目模板参考）**

---

## 6. SDL2/SDL3 — 跨平台窗口管理（不推荐）

### 6.1 SDL 概述

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/libsdl-org/SDL |
| **许可证** | zlib（可静态链接闭源） |
| **Stars** | 12k+ (SDL2) |
| **SDL3** | 2025 年 1 月正式发布 |
| **语言** | C API |

**SDL3 新特性（对 AGenUI 有价值的）：**
- High DPI 自动适配（`SDL_WINDOW_HIGH_PIXEL_DENSITY`）
- 回调式主函数（替代传统 `main` + 消息循环）
- 统一 GPU API（`SDL_GPU`，支持 D3D/Metal/Vulkan/OpenGL）
- 原生文件对话框（无需 Win32 `GetOpenFileName`）
- 笔 API（支持数位板/Apple Pencil → 可用于 AGenUI 手写组件）

**致命缺陷：** SDL 的渲染后端是 D3D11/OpenGL/Vulkan，**不直接支持 Direct2D**。AGenUI 组件用 Direct2D 绘制，需要额外的 D3D11→D2D 互操作（复杂且低效）。

**复用深度：不推荐**（除非未来 AGenUI Playground 需要跨平台窗口管理）

### 6.2 Demonstrandum/Playground（GLFW + BGFX + ImGui 跨平台模板）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/Demonstrandum/Playground |
| **许可证** | 未声明 |
| **技术栈** | GLFW（窗口） + BGFX（渲染） + ImGui（GUI） + FreeType2（文字） |
| **构建** | Bazel（跨平台） |

**架构模式：**
```
GLFW（窗口/输入）
  ↓
BGFX（渲染抽象层 — D3D/OpenGL/Vulkan/Metal）
  ↓
ImGui（UI 控制面板）
  ↓
FreeType2（文字渲染）
```

**AGenUI 对接价值：** 如果 AGenUI Playground 需要跨平台，BGFX 模式比 SDL 更适合——BGFX 是渲染抽象层，可以在 Windows 上用 D3D 后端，且 BGFX 支持高级效果。

**复用深度：B 级（跨平台 Playground 架构参考，当前阶段不需要）**

---

## 7. GLFW — OpenGL 取向的窗口管理（不推荐）

### 7.1 GLFW 概述

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/glfw/glfw |
| **许可证** | zlib（可静态链接闭源） |
| **版本** | 3.4（2024） |
| **语言** | C API |
| **定位** | OpenGL/Vulkan 开发的窗口管理 |

**GLFW 特点：**
- 轻量级（单库 ~1MB）
- 跨平台（Windows/macOS/Linux）
- 原生支持 OpenGL/Vulkan 上下文创建
- 不支持 Direct2D/Direct3D 上下文管理

**致命缺陷：** GLFW 面向 OpenGL/Vulkan 开发，**不直接支持 Direct2D**。要通过 GLFW 使用 Direct2D，需要从 GLFW 的 HWND 手动创建 D2D Factory + HwndRenderTarget——这实质上退化成了"Win32 + Direct2D"模式，GLFW 只是提供了窗口创建。

**复用深度：不推荐**（相比直接 Win32，GLFW 无额外价值）

### 7.2 alansley/cpp_glfw3_basecode（GLFW3 基础模板）

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/alansley/cpp_glfw3_basecode |
| **许可证** | MIT |
| **内容** | GLFW3 + GLAD + GLM + STB + ImGui 预集成 |
| **平台** | Windows (VS2022) + Linux (Code::Blocks) |

**可参考内容：**
- Window 类（鼠标/键盘/窗口事件处理）
- ShaderProgram 类（着色器加载/链接）
- Point/Line/Quad/TexturedQuad 绘制类
- WaveFront .OBJ 3D 模型加载器

**AGenUI 对接价值：** 此项目展示了 GLFW 的完整 C++ 封装模式。但 AGenUI 不使用 OpenGL，所以仅有窗口管理部分的封装模式可参考。

**复用深度：C 级（窗口类封装参考）**

---

## 8. Flutter Desktop — 架构参考（不适用）

### 8.1 Flutter Desktop 引擎架构

| 属性 | 详情 |
|------|------|
| **仓库** | https://github.com/flutter/flutter |
| **许可证** | BSD |
| **渲染后端** | Skia（→ Impeller 迁移中） |
| **Windows 集成** | 基于 Embedder API |
| **架构层级** | Framework(Dart) → Engine(C++/Skia) → Embedder API → Windows Embedder |

**Flutter Desktop 架构与 AGenUI 对比：**

| 维度 | Flutter Desktop | AGenUI Windows |
|------|----------------|----------------|
| 核心 | Dart Runtime + Skia | C++ Engine + Yoga |
| 渲染 | Skia（自绘） | Direct2D（自绘） |
| 布局 | 自有布局算法 | Yoga Flexbox |
| 窗口管理 | Embedder API → Windows Embedder | Win32 直接 |
| Resize 处理 | `FlutterResizeSynchronizer` | WM_SIZE → HwndRenderTarget 重建 |
| 线程模型 | UI Thread + Raster Thread | 主线程 + 工作线程 |

**关键参考价值：**

1. **`FlutterResizeSynchronizer` 模式**：Flutter 桌面端专门设计了窗口大小变化同步器——解决异步线程渲染与主线程窗口变化的竞态问题（Crash/重影）。AGenUI 的 `ISurfaceManager` 在"外部接口在业务线程；内部逻辑在子线程"模型下同样需要 resize 同步机制。

2. **Embedder API 分层**：Flutter 通过平台无关的 Embedder API 接入新平台——AGenUI 的 `agenui_engine_entry.h` 纯 C 接口采用类似理念。

3. **渲染管线分层**：Framework → DisplayList → Engine(Skia/Impeller) 的分层方式，与 AGenUI 的 VirtualDOM → YogaLayout → Direct2D 分层高度平行。

**局限性：** Flutter 是 Dart 语言生态，与 AGenUI 的 C++ 生态不兼容。Flutter 的 Embedder API 层变得非常臃肿（`FlutterEngineInitialize` 函数 460 行、`FlutterProjectArgs` 34 个成员），不值得直接借鉴。

**复用深度：B 级（架构参考，不引依赖）**

---

## 9. DirectComposition — 视觉合成层（Phase 2+）

### 9.1 DirectComposition 概述

| 属性 | 详情 |
|------|------|
| **来源** | Windows API（dcomp.h） |
| **许可证** | Windows 系统组件 |
| **最低版本** | Windows 8.1（`DCompositionCreateSurfaceHandle`） |
| **作用** | GPU 加速视觉树管理、透明/变换/动画 |

**DirectComposition 核心对象：**

```
IDCompositionDevice          — 设备（创建其他对象的工厂）
  ├─ IDCompositionVisual       — 视觉对象（位置/大小/变换/裁剪）
  │   ├─ CreateSurface()        — 绘制表面
  │   ├─ CreateSurfaceFromHandle() — 从 DXGI Surface 创建
  │   └─ CreateSurfaceFromHwnd()   — 从 HWND 创建（分层窗口）
  ├─ IDCompositionTarget       — 绑定到 HWND
  └─ IDCompositionSurface      — 绘制表面（可用 D2D 绘制）
```

**AGenUI 对接价值（Phase 2+）：**

```cpp
// AGenUI 可用 DirectComposition 管理多 Surface 的视觉树
IDCompositionDevice* dcompDevice;
DCompositionCreateDevice(dxgiDevice, __uuidof(IDCompositionDevice), &dcompDevice);

// 为每个 AGenUI Surface 创建一个 Visual
IDCompositionVisual* visual;
dcompDevice->CreateVisual(&visual);
visual->SetTransform(D2D1::Matrix3x2F::Translation(x, y));
visual->SetSize(D2D1::SizeF(width, height));

// 绑定 Direct2D 绘制表面
IDCompositionSurface* surface;
dcompDevice->CreateSurface(width, height, DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_ALPHA_MODE_PREMULTIPLIED, &surface);
// 在 surface 上使用 Direct2D 绘制
ComPtr<ID2D1DeviceContext> d2dContext;
// ... BeginDraw → DrawText/FillRectangle → EndDraw
visual->SetContent(surface);
```

**DCompositionCreateSurfaceHandle 关键函数：**
```cpp
HRESULT DCompositionCreateSurfaceHandle(
    DWORD desiredAccess,           // COMPOSITIONOBJECT_ALL_ACCESS
    SECURITY_ATTRIBUTES* sa,       // 安全属性（可 null）
    HANDLE* surfaceHandle          // 输出句柄
);
// 可跨进程共享 composition surface（用于多进程 AGenUI 场景）
```

**复用深度：A 级（Phase 2+ 系统原生 API，零依赖）**

### 9.2 AGenUI 多 Surface 场景与 DirectComposition

AGenUI 的 `ISurfaceManager` 支持多 Surface 实例（`instanceId`）。在 Windows 上，多 Surface 可以用 DirectComposition 的多 Visual 管理视觉树：

```
Playground HWND
  └─ IDCompositionTarget（绑定到 HWND）
       ├─ Visual 1（AGenUI Surface instanceId=1）
       │    └─ DCompositionSurface → Direct2D 绘制
       ├─ Visual 2（AGenUI Surface instanceId=2）
       │    └─ DCompositionSurface → Direct2D 绘制
       └─ Visual 3（AGenUI Surface instanceId=3）
            └─ DCompositionSurface → Direct2D 绘制
```

**优势：**
- GPU 加速的视觉变换（移动/缩放/旋转/透明度）
- 透明叠加（AGenUI Surface 之间的层叠）
- 不需要每个 Surface 独立的 HWND（减少窗口管理开销）

**复用深度：B 级（Phase 2+ 架构设计参考）**

---

## 10. 复用/参考矩阵

### 10.1 按 Playground 模块映射

| Playground 模块 | 可复用/参考项目 | 级别 | 具体复用内容 |
|----------------|----------------|------|-------------|
| **窗口管理** | Win32 API（系统） | A | RegisterClass + CreateWindowEx + 消息循环 |
| **D2D/DWrite/WIC 工厂** | CodeProject FactorySingleton | A | 全局单例 + ComPtr 生命周期管理 |
| **HwndRenderTarget** | Microsoft DemoApp 模式 | A | CreateHwndRenderTarget + BeginDraw/EndDraw |
| **设备丢失恢复** | Microsoft DemoApp | A | `D2DERR_RECREATE_TARGET` 处理 |
| **窗口遮挡检查** | CodeProject Tutorial | A | `CheckWindowState()` 优化 |
| **文本测量** | DirectWrite 系统 API | A | `IDWriteTextLayout::GetMetrics` |
| **文本渲染** | Direct2D 系统 API | A | `DrawText()` / `DrawTextLayout()` |
| **精确行高** | DirectWrite Font Metrics | A | `DWRITE_FONT_METRICS` + em-size 缩放 |
| **图片解码** | WIC 系统 API | A | `IWICImagingFactory` + `CreateBitmapFromWICBitmap` |
| **画刷创建** | LHY1339 教程 | C | 纯色/渐变/位图画刷创建代码 |
| **Direct2D 效果** | ocucu/samples | C | Photo/Filter Effects 代码片段 |
| **Win2D CanvasControl** | Win2D for WinUI3 | A | Phase 3 XAML 嵌入渲染 |
| **Win2D 互操作** | Win2D `GetWrappedResource` | A | Phase 3 D2D ↔ Win2D 资源转换 |
| **XAML 叠加 D2D** | castorix/WinUI3_D2D_Composition | B | Phase 3 CompositionDrawingSurface |
| **WinUI 3 项目结构** | RobsonPontin/WinUI3 | B | Phase 3 C++/WinRT Playground 结构 |
| **调试面板** | Dear ImGui | B | 原型期 JSON 编辑器 + 属性面板 |
| **多窗口管理** | ImGui-AppKit-DX11 | C | 多窗口模式参考 |
| **视觉树管理** | DirectComposition | A | Phase 2+ 多 Surface 视觉合成 |
| **Resize 同步** | Flutter `FlutterResizeSynchronizer` | B | 异步渲染 + 窗口变化竞态处理 |
| **跨平台备选** | SDL3 / GLFW + BGFX | B | 未来跨平台 Playground 参考 |

### 10.2 按项目维度

| 项目 | License | 级别 | Playground 角色 | 推荐阶段 |
|------|---------|------|----------------|---------|
| **Win32 API** | 系统 | A | 窗口管理 + 消息循环 | Phase 1+ |
| **Direct2D** | 系统 | A | 2D 渲染后端 | Phase 1+ |
| **DirectWrite** | 系统 | A | 文字测量 + 渲染 | Phase 1+ |
| **WIC** | 系统 | A | 图像解码 | Phase 1+ |
| **DirectComposition** | 系统 | A | 视觉合成 | Phase 2+ |
| **Win2D** | MIT | A | WinUI 3 渲染简化 | Phase 3 |
| **Microsoft DemoApp 模式** | 公开 | A | D2D 基础模式参考 | Phase 1 |
| **CodeProject D2D Tutorial** | CPOL | A | FactorySingleton + ComPtr | Phase 1 |
| **asdlei99/Direct2D-demo** | MIT | B | 64 位兼容 Win32+D2D 示例 | Phase 1 |
| **LHY1339/Direct2DExample** | 未声明 | C | 6 步渐进式教程 | Phase 1 |
| **ocucu/d2d-directwrite-samples** | MIT | C | D2D/DWrite 8 个 Demo | Phase 1-2 |
| **castorix/WinUI3_D2D_Composition** | MIT | B | XAML 上叠加 D2D | Phase 3 |
| **RobsonPontin/WinUI3** | 未声明 | B | WinUI 3 C++/WinRT 结构 | Phase 3 |
| **Dear ImGui** | MIT | B | 调试面板/原型 | 可选 |
| **ImGui-AppKit-DX11** | 未声明 | C | 多窗口模板 | 可选 |
| **SDL3** | zlib | — | 不推荐（无 D2D 支持） | — |
| **GLFW** | zlib | — | 不推荐（无 D2D 支持） | — |
| **Flutter Desktop** | BSD | B | Resize 同步架构参考 | 设计参考 |

---

## 11. 推荐的 AGenUI Windows Playground 架构

### 11.1 Phase 1-2：Win32 + Direct2D（最小依赖）

```
┌─────────────────────────────────────────────────────────┐
│              AGenUI Windows Playground (Phase 1-2)       │
│                                                        │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Win32 窗口 (RegisterClass + CreateWindowEx)    │   │
│  │  ┌─────────────────────────────────────────┐    │   │
│  │  │  ID2D1HwndRenderTarget (绑定 HWND)        │    │   │
│  │  │  ┌──────────┐  ┌──────────┐  ┌────────┐ │    │   │
│  │  │  │ AGenUI   │  │ AGenUI   │  │ AGenUI │ │    │   │
│  │  │  │ Surface 1│  │ Surface 2│  │ ...    │ │    │   │
│  │  │  └──────────┘  └──────────┘  └────────┘ │    │   │
│  │  └─────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────┘   │
│                                                        │
│  FactorySingleton:                                     │
│  ├─ ID2D1Factory (D2D 工厂)                            │
│  ├─ IDWriteFactory (DWrite 工厂)                        │
│  └─ IWICImagingFactory (WIC 工厂)                       │
├─────────────────────────────────────────────────────────┤
│              agenui_windows.dll (已编译)                 │
│  Engine + VirtualDOM + YogaLayout + StreamParser        │
│  + 26 组件 + IMeasurement(DirectWrite) + IPlatformFunc  │
├─────────────────────────────────────────────────────────┤
│                     yoga v2.0.0                         │
└─────────────────────────────────────────────────────────┘
```

**Phase 1-2 Playground 代码结构：**

```
platforms/windows/playground/
├── CMakeLists.txt         (已存在 Phase 0)
├── main.cpp                (Phase 0 冒烟测试, 已存在)
├── win32_app.h             (Phase 1: Win32 窗口封装)
├── win32_app.cpp           (Phase 1: 窗口创建 + 消息循环)
├── d2d_renderer.h          (Phase 1: Direct2D 渲染器)
├── d2d_renderer.cpp        (Phase 1: HwndRenderTarget + 绘制循环)
├── factory_singleton.h     (Phase 1: D2D/DWrite/WIC 工厂单例)
├── playground_scene.h      (Phase 2: AGenUI JSON 加载 + 渲染)
├── playground_scene.cpp    (Phase 2: beginTextStream → 渲染 → endTextStream)
└── dpi_awareness.h         (Phase 2: Per-Monitor DPI)
```

### 11.2 Phase 3：WinUI 3 + Win2D（现代化升级）

```
┌─────────────────────────────────────────────────────────┐
│           AGenUI Windows Playground (Phase 3)            │
│                                                        │
│  ┌─────────────────────────────────────────────────┐   │
│  │  WinUI 3 Window (Microsoft.UI.Xaml.Window)       │   │
│  │  ┌──────────────────┐  ┌──────────────────┐     │   │
│  │  │ XAML 控制面板    │  │ CanvasControl     │     │   │
│  │  │ (JSON 编辑器     │  │ (Win2D, MIT)     │     │   │
│  │  │  + 组件树        │  │  ┌────────────┐ │     │   │
│  │  │  + 属性面板)     │  │  │ AGenUI 渲染 │ │     │   │
│  │  │                  │  │  │ Draw 回调   │ │     │   │
│  │  └──────────────────┘  │  └────────────┘ │     │   │
│  │                        └──────────────────┘     │   │
│  └─────────────────────────────────────────────────┘   │
│                                                        │
│  Win2D CanvasControl:                                  │
│  ├─ CreateResources → 初始化 AGenUI 引擎                │
│  ├─ Draw → AGenUI 布局 → Direct2D 绘制                  │
│  └─ Invalidate() → AGenUI markDirty → 重绘             │
│                                                        │
│  互操作: GetWrappedResource<ID2D1DeviceContext>()      │
├─────────────────────────────────────────────────────────┤
│              agenui_windows.dll (已编译)                 │
├─────────────────────────────────────────────────────────┤
│                     yoga v2.0.0                         │
└─────────────────────────────────────────────────────────┘
```

---

## 12. 分阶段实施计划

### Phase 1（3-5 天）— Win32 + Direct2D MVP Playground

**目标：** 从 Phase 0 控制台冒烟测试升级为 Win32 窗口 + Direct2D 渲染 AGenUI 组件。

| 任务 | 参考项目 | 产出 |
|------|---------|------|
| Win32 窗口封装（RegisterClass + CreateWindowEx + 消息循环） | Microsoft DemoApp | `win32_app.h/cpp` |
| FactorySingleton（D2D/DWrite/WIC 工厂单例 + ComPtr） | CodeProject Tutorial | `factory_singleton.h` |
| HwndRenderTarget 创建 + BeginDraw/EndDraw 循环 | Microsoft DemoApp | `d2d_renderer.h/cpp` |
| D2DERR_RECREATE_TARGET 设备丢失恢复 | Microsoft DemoApp | `d2d_renderer.cpp` |
| ISurfaceSizeProvider 实现（基于 HWND client rect） | — | `win32_surface_size_provider.h` |
| AGenUI 引擎初始化 + SurfaceManager 创建 | Phase 0 已验证 | `playground_scene.h/cpp` |

**验收标准：** 窗口创建成功，Direct2D 清屏为白色背景，AGenUI 引擎初始化 + SurfaceManager 创建成功。

### Phase 2（5-7 天）— 加载 A2UI JSON 渲染

**目标：** 加载 A2UI JSON 流，渲染 Text + Button + Image 三个 MVP 组件。

| 任务 | 参考项目 | 产出 |
|------|---------|------|
| beginTextStream + JSON 片段流入 | AGenUI 核心 API | `playground_scene.cpp` |
| Text 组件渲染 | DirectWrite 系统 API | AGenUI `TextComponent::paint()` |
| Button 组件渲染 | Direct2D FillRoundedRectangle + DrawText | AGenUI `ButtonComponent::paint()` |
| Image 组件渲染 | WIC + ID2D1Bitmap | AGenUI `ImageComponent::paint()` |
| WM_SIZE → Surface 尺寸更新 + 重绘 | Microsoft DemoApp | `win32_app.cpp` |
| Per-Monitor DPI 感知 | Win32 API | `dpi_awareness.h` |
| 遮挡检查优化 | CodeProject `CheckWindowState()` | `d2d_renderer.cpp` |

**验收标准：** 窗口中显示 A2UI JSON 定义的文本、按钮、图片，窗口缩放时内容自适应。

### Phase 3（1 周）— WinUI 3 + Win2D 升级

**目标：** 迁移到 WinUI 3 + Win2D，加入 XAML 控制面板。

| 任务 | 参考项目 | 产出 |
|------|---------|------|
| WinUI 3 C++/WinRT 项目搭建 | RobsonPontin/WinUI3 | PlaygroundWinUI3 项目 |
| Win2D NuGet 包引入 | Win2D for WinUI3 | NuGet 配置 |
| CanvasControl 嵌入 + Draw 回调 | Win2D 文档 | CanvasControl_Draw() |
| GetWrappedResource 互操作 | Win2D 文档 | D2D ↔ Win2D 资源转换 |
| XAML JSON 编辑器面板 | — | XAML 控件 |
| XAML 组件树面板 | — | TreeView 控件 |
| Composition 视觉树（多 Surface） | DirectComposition API | 多 Visual 管理 |

**验收标准：** WinUI 3 窗口中通过 Win2D CanvasControl 渲染 AGenUI 组件，左侧 XAML 面板可编辑 JSON 并实时预览。

---

## 13. 关键代码模式速查

### 13.1 FactorySingleton + ComPtr 模式

```cpp
// 推荐采用的生命周期管理模式
class D2DResources {
public:
    static D2DResources& Instance() {
        static D2DResources instance;
        return instance;
    }

    ID2D1Factory*       D2DFactory()    { return m_d2dFactory.Get(); }
    IDWriteFactory*     DWriteFactory()  { return m_dwriteFactory.Get(); }
    IWICImagingFactory* WICFactory()     { return m_wicFactory.Get(); }

private:
    D2DResources() {
        D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, m_d2dFactory.GetAddressOf());
        DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory),
            reinterpret_cast<IUnknown**>(m_dwriteFactory.GetAddressOf()));
        CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
            IID_PPV_ARGS(m_wicFactory.GetAddressOf()));
    }

    Microsoft::WRL::ComPtr<ID2D1Factory>       m_d2dFactory;
    Microsoft::WRL::ComPtr<IDWriteFactory>     m_dwriteFactory;
    Microsoft::WRL::ComPtr<IWICImagingFactory> m_wicFactory;
};
```

### 13.2 Win32 窗口 + D2D 渲染循环模式

```cpp
// 推荐的 Playground 主循环
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    auto* app = reinterpret_cast<PlaygroundApp*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
    switch (msg) {
        case WM_CREATE:
            app = reinterpret_cast<PlaygroundApp*>(reinterpret_cast<LPCREATESTRUCT>(lp)->lpCreateParams);
            SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(app));
            app->OnCreate(hwnd);
            return 0;
        case WM_SIZE:
            if (app) app->OnSize(LOWORD(lp), HIWORD(lp));
            return 0;
        case WM_PAINT:
            if (app) app->OnRender();
            ValidateRect(hwnd, nullptr);
            return 0;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProc(hwnd, msg, wp, lp);
}

void PlaygroundApp::OnRender() {
    HRESULT hr = m_renderTarget->BeginDraw();
    // 遮挡检查
    if (m_renderTarget->CheckWindowState() & D2D1_WINDOW_STATE_OCCLUDED)
        return;

    m_renderTarget->Clear(D2D1::ColorF(D2D1::ColorF::White));

    // AGenUI 渲染
    m_scene->Render(m_renderTarget.Get());

    hr = m_renderTarget->EndDraw();
    if (hr == D2DERR_RECREATE_TARGET) {
        m_renderTarget.Reset();  // 下次 OnRender 重建
    }
}
```

### 13.3 DirectWrite 文字测量模式

```cpp
// AGenUI IMeasurement 实现核心
struct TextMeasureResult { float width; float height; };

TextMeasureResult MeasureText(
    const std::wstring& text,
    IDWriteTextFormat* format,    // 缓存的 TextFormat
    float maxWidth, float maxHeight)
{
    ComPtr<IDWriteTextLayout> layout;
    D2DResources::Instance().DWriteFactory()->CreateTextLayout(
        text.c_str(),
        static_cast<UINT32>(text.length()),
        format,
        maxWidth,
        maxHeight,
        &layout);

    DWRITE_TEXT_METRICS metrics;
    layout->GetMetrics(&metrics);
    return {
        ceil(metrics.widthIncludingTrailingWhitespace),
        ceil(metrics.height)
    };
}
```

### 13.4 AGenUI Surface → Direct2D 绘制对接模式

```cpp
// Playground Scene 渲染
void PlaygroundScene::Render(ID2D1RenderTarget* target) {
    // AGenUI 引擎已在 init 时创建 SurfaceManager
    // 这里在每帧调用渲染

    // 方式 1: AGenUI 组件直接绘制到传入的 RenderTarget
    // （AGenUI 组件的 paint() 方法接收 ID2D1RenderTarget*）
    for (auto& component : m_visibleComponents) {
        component->paint(target);
    }

    // 方式 2: 使用 AGenUI 的 IVirtualDOMObserver 回调驱动绘制
    // m_surfaceManager->updateLayout();  // Yoga 布局计算
    // 布局结果通过回调返回，在回调中调用 Direct2D 绘制
}
```

---

## 14. 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| DirectWrite 线程安全（Win 8/8.1） | 测量崩溃 | 用 `SRWLOCK` 保护（参考 Skia `DWriteFactoryMutex`） |
| COM 对象生命周期 | 内存泄漏 | 使用 `ComPtr<T>`（WRL）自动管理引用计数 |
| HwndRenderTarget 设备丢失 | 渲染中断 | `D2DERR_RECREATE_TARGET` → 丢弃设备资源，下次 OnRender 重建 |
| 窗口遮挡时无效绘制 | 性能浪费 | `CheckWindowState()` 遮挡检查跳过绘制 |
| Per-Monitor DPI 变化 | 布局错位 | `WM_DPICHANGED` → 重建 RenderTarget + 重新测量 |
| Win2D WinUI3 不稳定 | Phase 3 API 变化 | Phase 3 引入时锁定 NuGet 版本 |
| ImGui immediate-mode 范式冲突 | 不适合长期框架 | 仅作原型/调试面板，不作正式 Playground |
| SDL/GLFW 无 Direct2D 支持 | 无法直接用 D2D | 不采用，坚持 Win32 + Direct2D |
| Flutter Embedder API 过于臃肿 | 不值得借鉴 | 仅参考 Resize 同步器设计思路 |

---

## 15. 结论

**核心发现：**

1. **Win32 + Direct2D 是 Phase 1-2 Playground 的唯一正确选择**——零额外依赖、系统原生 API、与 AGenUI 的 Direct2D 组件绘制直接对接。Microsoft DemoApp 模式 + CodeProject FactorySingleton 模式提供了完整的代码参考。

2. **Win2D for WinUI 3 是 Phase 3 Playground 的理想升级路径**——MIT 许可、NuGet 分发、CanvasControl 嵌入 XAML、与 Direct2D 双向互操作。`GetWrappedResource` 互操作能力是关键——允许 AGenUI SDK 层直接创建的 Direct2D 资源与 Win2D 管理的资源混用。

3. **DirectWrite 文字测量是 `IMeasurement` 实现的核心**——`IDWriteTextLayout::GetMetrics()` 提供精确的文本宽高测量，`IDWriteTextFormat` 可缓存复用提升性能。参考 DirectWrite Font Metrics 计算精确行高，用于 List/Table 组件。

4. **DirectComposition 是 Phase 2+ 多 Surface 管理的最佳方案**——系统原生 API、零依赖、GPU 加速视觉变换。`DCompositionCreateSurfaceHandle` 支持跨进程 Surface 共享。

5. **ImGui 可选作调试面板**——在 AGenUI Playground 开发期间，ImGui 可提供 JSON 编辑器 + 组件树 + 属性面板，但其 immediate-mode 范式与 AGenUI 的 retained-mode 不兼容，不适合作为主框架。

6. **SDL/GLFW 不适用于 AGenUI**——两者面向 OpenGL/Vulkan，不直接支持 Direct2D。SDL/GLFW 窗口中使用 Direct2D 需要额外 D3D 互操作，复杂且低效。

7. **Flutter Desktop 的 `FlutterResizeSynchronizer` 模式值得参考**——异步渲染线程与主线程窗口变化的竞态问题在 AGenUI 的 `ISurfaceManager` 线程模型中同样存在。

**最终推荐路径：**

```
Phase 0 ✅ 已完成: 控制台冒烟测试 (agenui_windows.dll + 7 项 PASS)
    ↓
Phase 1 (3-5 天): Win32 + Direct2D MVP Playground
    参考: Microsoft DemoApp + CodeProject FactorySingleton
    产出: Win32 窗口 + HwndRenderTarget + AGenUI 引擎初始化
    ↓
Phase 2 (5-7 天): 加载 A2UI JSON + 渲染 3 组件
    参考: DirectWrite 系统 API + WIC + LHY1339 教程
    产出: Text + Button + Image 组件在窗口中渲染
    ↓
Phase 3 (1 周): WinUI 3 + Win2D 升级
    参考: Win2D NuGet + CanvasControl + GetWrappedResource
    产出: XAML 控制面板 + Win2D CanvasControl 渲染
```
