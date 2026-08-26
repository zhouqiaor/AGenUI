# AGenUI Windows P3 WinUI3 + WIC 渲染升级业界开源项目调研报告

> 日期：2026-08-26 | 范围：WinUI3 + Win2D + WIC 图像加载 + Direct2D 渲染引擎

## 1. 调研目标

为 AGenUI Windows Playground Phase 3 升级选择技术方案：
- WinUI3 CanvasControl 替代 Win32 HwndRenderTarget
- WIC 图像加载替代 Image 占位符
- XAML 控件面板（属性编辑器）
- Direct2D 高级渲染（圆角矩形 + 文本 + 图像混合布局）

## 2. 候选项目总览

| # | 项目 | 语言 | Stars | 用途 | 评分 |
|---|------|------|-------|------|------|
| 1 | Win2D (Microsoft) | C++/C# | 1.9k | WinUI3 2D 渲染 API（Direct2D 封装） | ★★★★★ |
| 2 | KKE D2D Engine | C++20 | ~新 | 完整 Direct2D/DWrite/WIC 渲染引擎 | ★★★★★ |
| 3 | D2DImageListViewer | C++17 | ~小 | Direct2D + WIC 图像列表查看器 | ★★★★ |
| 4 | Ray1024/Direct2D | C++ | 80 | Direct2D 1.0~1.3 全版本示例代码 | ★★★★ |
| 5 | Microsoft WIC 官方文档 | — | — | WIC 图像加载标准流程 | ★★★★★ |
| 6 | lecui | C++ | 19 | Direct2D GUI 控件库 | ★★★ |
| 7 | d2d-mica | C++ | 14 | 纯 Direct2D Mica 材质效果 | ★★★ |

## 3. 详细分析

### 3.1 Win2D (Microsoft 官方)

**仓库**: https://github.com/Microsoft/Win2D

**核心能力**:
- Microsoft 官方维护，WinUI3 支持（进行中，`winappsdk/main` 分支）
- 封装 Direct2D 为 WinRT API，与 XAML 无缝集成
- `CanvasControl` XAML 控件 + `CanvasDrawEventArgs.DrawingSession`
- 支持 C++/WinRT、C#、VB
- GPU 加速即时模式 2D 渲染

**对 AGenUI 的复用价值**:
- **CanvasControl** 替代 HwndRenderTarget：XAML 声明式集成，自动 DPI/resize 处理
- **DrawingSession** 封装 Direct2D BeginDraw/EndDraw：简化渲染循环
- **CanvasBitmap**：WIC 图像加载 + GPU 纹理，替代手动 WIC pipeline
- **CanvasTextFormat**：封装 DirectWrite，简化文本渲染

**代码模式**:
```cpp
// C++/WinRT
void MainPage::CanvasControl_Draw(
    CanvasControl const& sender,
    CanvasDrawEventArgs const& args)
{
    args.DrawingSession().DrawText(L"Hello", 100, 100, Colors::Yellow());
    args.DrawingSession().FillRoundedRectangle(
        Rect{50, 50, 200, 44}, 8, 8, Colors::Blue());
}
```

**当前限制**:
- WinUI3 迁移进行中，CanvasAnimatedControl 部分不支持
- 需要 Windows App SDK 1.4+
- NuGet 包依赖（`Microsoft.Graphics.Win2D`）

**集成方式**:
```xml
<!-- XAML -->
xmlns:canvas="using:Microsoft.Graphics.Canvas.UI.Xaml"
<canvas:CanvasControl Draw="CanvasControl_Draw" ClearColor="White"/>
```

### 3.2 KKE D2D Engine

**仓库**: https://github.com/huzun1/kke

**核心能力**:
- C++20 完整 Direct2D/DirectWrite/WIC 渲染引擎
- 统一 API 表面：shapes, text, textures, layers, effects
- `kke::D2dEngine` 封装 D2D 工厂 + 设备上下文
- Texture 加载（WIC → D2D Bitmap）+ 线性/最近邻插值
- 效果系统：Blur, Shadow, 自定义 EffectSource

**对 AGenUI 的复用价值**:
- **Texture 加载模式**: `engine().draw(mountainTexture(), rect, {scale, interpolation})`
  - 直接参考其 WIC → ID2D1Bitmap 封装方式
- **RoundedRect + Shadow**: 按钮渲染的高级效果（阴影 + 圆角 + 半透明）
- **Layer/Canvas**: 离屏渲染 + 图层叠加（复杂组件树渲染）
- **API 设计**: variant-based source types 可参考 AGenUI 的组件类型分发

**代码模式**:
```cpp
kke::RoundedRect rect({{400, 200}, {600, 400}}, 20.0f);
engine().renderEffect(rect, 
    kke::EffectSourceAppearance{},
    kke::ShadowEffect{.color = {0, 0, 0, 0.3f}, .mode = kke::ShadowMode::OuterShadowOnly});
engine().fill(rect, kke::SolidColorBrush({1, 1, 1, 0.6f}));
```

**集成方式**: CMake `FetchContent` 或 `add_subdirectory`

### 3.3 D2DImageListViewer

**仓库**: https://github.com/Dramcryx/D2DImageListViewer

**核心能力**:
- C++17, Direct2D + WIC 图像列表查看器
- `ID2D1DeviceContext` + DXGI swapchain（而非 HwndRenderTarget）
- `IDocument`/`IPage` 接口抽象多页文档
- `CWICImage` — WIC 图像加载的参考实现
- 4 种布局模式：左对齐/右对齐/居中/水平流式

**对 AGenUI 的复用价值**:
- **CWICImage 类**: 直接参考其 WIC 解码 → ID2D1Bitmap 实现
- **IDocument/IPage 接口**: AGenUI 的 Image 组件可参考此抽象
- **布局系统**: 多图排列方式可参考 AGenUI 的 Flexbox/Yoga 布局

### 3.4 Ray1024/Direct2D 全版本示例

**仓库**: https://github.com/Ray1024/Direct2D

**核心能力**:
- Direct2D 1.0~1.3 全版本示例代码
- 包含 WIC + DirectWrite 集成示例
- C++ 原生 Win32 API，无框架依赖

**对 AGenUI 的复用价值**:
- 直接参考其 WIC 加载 PNG/JPEG 的完整流程代码
- Direct2D 1.1+ 的 `ID2D1DeviceContext` + DXGI swapchain 模式
- 圆角矩形、渐变画刷、图层等高级渲染示例

### 3.5 Microsoft WIC 官方文档 + 示例

**文档**: 
- [How to Load a Bitmap from a File](https://learn.microsoft.com/windows/win32/direct2d/how-to-load-a-direct2d-bitmap-from-a-file)
- [Chapter 11: Using WIC](https://msdn.microsoft.com/library/ff973956.aspx)

**标准 WIC 加载流程**:
```
1. IWICImagingFactory::CreateDecoderFromFilename(uri) → IWICBitmapDecoder
2. IWICBitmapDecoder::GetFrame(0) → IWICBitmapFrameDecode
3. IWICImagingFactory::CreateFormatConverter → IWICFormatConverter
4. IWICFormatConverter::Initialize(frame, GUID_WICPixelFormat32bppPBGRA, ...)
5. ID2D1RenderTarget::CreateBitmapFromWicBitmap(converter) → ID2D1Bitmap
6. ID2D1RenderTarget::DrawBitmap(bitmap, destRect)
```

**对 AGenUI 的复用价值**:
- 这是 P3 Image 组件渲染的核心实现路径
- 替代 Phase 2 的占位矩形 + X 标记
- 支持本地文件路径 + HTTP URL（需先下载到临时文件）

**可选增强**: WIC Bitmap Scaler（缩放）:
```cpp
wicFactory->CreateBitmapScaler(&scaler);
scaler->Initialize(frame, destWidth, destHeight, WICBitmapInterpolationModeCubic);
converter->Initialize(scaler, GUID_WICPixelFormat32bppPBGRA, ...);
```

### 3.6 lecui — Direct2D GUI 控件库

**仓库**: https://github.com/alecmus/lecui

**核心能力**:
- C++ Direct2D GUI 控件库（widgets/dll/panes）
- 自绘控件：按钮、文本框、列表、面板等
- 完整的 GUI 框架而非渲染库

**对 AGenUI 的复用价值**:
- 参考 AGenUI Image 组件的控件结构设计
- 但 AGenUI 是声明式渲染（A2UI JSON → D2D），不需要完整 GUI 框架

### 3.7 d2d-mica — 纯 Direct2D Mica 材质

**仓库**: https://github.com/wangwenx190/d2d-mica

**核心能力**:
- 纯 Direct2D 实现 Windows 11 Mica/Acrylic 材质效果
- 使用 D3D11 + DXGI + D2D
- 不依赖 XAML/WinUI

**对 AGenUI 的复用价值**:
- 如需实现 Windows 11 风格的半透明/模糊背景效果
- AGenUI 鸿蒙风格的"玻璃拟态"效果可参考此实现

## 4. P3 技术方案设计

### 4.1 升级路线

```
Phase 2 (当前)                    Phase 3 (目标)
───────────────────               ───────────────────
Win32 HwndRenderTarget     →      Win2D CanvasControl (XAML)
手动 BeginDraw/EndDraw     →      CanvasControl.Draw 事件回调
Image 占位矩形 + X         →      WIC 解码 + D2D Bitmap 纹理
手动 Win32 窗口             →      WinUI3 XAML 窗口
无交互控件                  →      XAML 控件面板（属性编辑器）
```

### 4.2 两种技术路线对比

| 维度 | 方案 A: 纯 Direct2D + WIC | 方案 B: WinUI3 + Win2D |
|------|--------------------------|----------------------|
| 复杂度 | 低（渐进升级现有代码） | 高（需 XAML + WinUI3 SDK） |
| 依赖 | 零（仅 Windows SDK） | Win2D NuGet + Windows App SDK |
| 图像加载 | WIC 原生 API | CanvasBitmap 封装 |
| XAML 集成 | 无 | 天然支持 |
| DPI/Resize | 手动处理 | CanvasControl 自动 |
| 效果系统 | 自建 | 内置（Blur/Shadow 等） |
| 推荐 | **P3.1 先做** | **P3.2 后做** |

### 4.3 推荐：分两步走

**P3.1: 纯 Direct2D + WIC 图像加载**（优先）
- 在现有 Win32 + D2D 框架基础上增加 WIC 图像加载
- 替换 Image 占位符为真实图像渲染
- 参考 KKE D2D Engine 和 D2DImageListViewer 的 WIC 实现
- 依赖：仅 Windows SDK（零新增依赖）

**P3.2: WinUI3 + Win2D 升级**（可选，后续）
- 用 Win2D CanvasControl 替代 HwndRenderTarget
- 添加 XAML 控件面板
- 需要 Windows App SDK + Win2D NuGet

## 5. P3.1 实现计划（纯 D2D + WIC）

### 新增文件
- `include/win_wic_image_loader.h` — WIC 图像加载器（参考 MS 官方文档流程）
- `include/win_d2d_bitmap_cache.h` — D2D Bitmap 缓存（避免重复解码）

### 修改文件
- `win_message_listener.h` — Image 组件 src URL 捕获
- `win32_app.cpp` — RenderImage() 替换占位符为真实 D2D Bitmap 渲染
- `CMakeLists.txt` — 链接 windowscodecs（已有）

### WIC 加载流程（直接实现）
```cpp
// win_wic_image_loader.h
class WicImageLoader {
public:
    // 从文件路径加载图像 → ID2D1Bitmap
    ID2D1Bitmap* LoadFromFile(ID2D1RenderTarget* rt, const std::wstring& path);
    // 从 URL 下载+加载（P3.1 可先支持本地路径）
    // ID2D1Bitmap* LoadFromUrl(ID2D1RenderTarget* rt, const std::string& url);
private:
    IWICImagingFactory* m_wicFactory;  // 从 D2DResources 获取
    std::unordered_map<std::wstring, ID2D1Bitmap*> m_cache;
};
```

## 6. 关键开源项目参考链接

- [Win2D (Microsoft)](https://github.com/Microsoft/Win2D) — WinUI3 2D 渲染 API
- [Win2D WinUI3 文档](https://microsoft.github.io/Win2D/WinUI3/html/Introduction.htm) — 入门指南
- [KKE D2D Engine](https://github.com/huzun1/kke) — 完整 D2D/DWrite/WIC 引擎
- [D2DImageListViewer](https://github.com/Dramcryx/D2DImageListViewer) — D2D+WIC 图像列表
- [Ray1024/Direct2D](https://github.com/Ray1024/Direct2D) — D2D 全版本示例
- [MS WIC 教程](https://learn.microsoft.com/windows/win32/direct2d/how-to-load-a-direct2d-bitmap-from-a-file) — 官方图像加载
- [lecui](https://github.com/alecmus/lecui) — D2D GUI 控件库
- [d2d-mica](https://github.com/wangwenx190/d2d-mica) — D2D Mica 材质
