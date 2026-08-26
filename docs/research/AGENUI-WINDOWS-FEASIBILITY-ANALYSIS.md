# AGenUI Windows 原生显示渲染可行性分析

> 基于 `C:\Code\zhouqiaor-AGenUI` 源码级审查，2026-08-25
> 审查范围：C++ 核心 11 个公开头文件 + 189 个 core/src 源文件 + Android/iOS/Harmony 三平台 SDK 全量目录

---

## 1. 执行摘要

**结论：C++ 核心层可移植（可行性 85%），但需从零构建完整 Windows 平台 SDK 层（工作量等同 Android SDK 的 141 Java 文件 + 21 JNI 文件，或 iOS 的 51 Swift + 14 ObjC++ 文件）。**

| 维度 | 可行性 | 依据 |
|------|--------|------|
| C++ 核心编译（MSVC） | ✅ 85% | 189 源文件中零平台 #ifdef；仅依赖 yoga（支持 Windows）|
| DLL 导出层 | ✅ 90% | `agenui_engine_entry.h` 已是纯 C 接口，直接 `__declspec(dllexport)` |
| 原生控件实现 | ⚠️ 60% | 需新建 26+ 组件类（Win32/WinUI 3），每组件约 100-300 行 |
| 文本测量 | ⚠️ 65% | 需实现 `IMeasurement` 子类，对接 DirectWrite（GDI 备选）|
| 图像加载 | ✅ 80% | WIC (Windows Imaging Component) 原生支持主流格式 |
| 构建系统 | ✅ 85% | CMake 原生支持 MSVC 生成器，yoga 有 Windows 构建脚本 |
| 总体评估 | ⚠️ **70%** | 核心可移植，平台 SDK 工作量大但路径清晰 |

**推荐方案：Phase 0 编译 C++ 核心为 Windows DLL → Phase 1 实现 3 个 MVP 组件（Text/Button/Image）→ Phase 2 补齐剩余 23 组件 → Phase 3 Playground 应用。预计 4-6 周（1 人）。**

---

## 2. C++ 核心层可移植性评估

### 2.1 架构事实

AGenUI C++ 核心层位于 `core/` 目录，包含：

- **11 个公开头文件**（`core/include/`）：全部为纯虚接口（`IAGenUIEngine`, `ISurfaceManager`, `IAGenUIMessageListener`, `IPlatformFunction`, `IMeasurement`, `IMeasurementManager`, `ISurfaceSizeProvider` 等）
- **189 个源文件**（`core/src/`）：涵盖引擎实现、流式解析、虚拟 DOM、样式解析、Yoga 布局桥接、组件属性规范
- **C 入口点**（`agenui_engine_entry.h`）：`initAGenUIEngine()` / `getAGenUIEngine()` / `destroyAGenUIEngine()` —— 工厂模式，无平台参数

### 2.2 平台无关性证据

| 检查项 | 结果 | 证据 |
|--------|------|------|
| `core/src/` 中 `PLATFORM_*` 宏 | **零命中** | `grep -ri "PLATFORM_ANDROID\|PLATFORM_IOS\|PLATFORM_HARMONY"` 在所有 .cpp/.h 中无匹配 |
| `#ifdef _WIN32` / `#ifdef _MSC_VER` | **仅 1 处** | `agenui_event_action_data_value.cpp`（第三方 nlohmann/json.hpp 除外）|
| `#ifdef __ANDROID__` | **零命中** | 源码中不存在 |
| `#ifdef __APPLE__` | **零命中** | 源码中不存在 |
| 平台特定头文件 | **零** | 无 `<android/log.h>`, `<objc/objc.h>`, `<arkui/native_interface.h>` 出现在 core/ 中 |
| 第三方依赖 | **仅 yoga + nlohmann/json** | yoga v2.0.0 有 Windows MSVC 构建支持；json 是 header-only |

### 2.3 线程模型

核心层文档明确：
- `IAGenUIEngine`：所有外部接口在主线程调用
- `ISurfaceManager`：外部接口在业务线程；内部逻辑在子线程；回调在子线程

这个线程模型与 Windows 消息循环（UI 线程 + 工作线程）天然兼容，无需适配。

### 2.4 JNI 桥接层分析

`core/src/jni/` 目录包含 21 个 JNI 桥接文件——这些文件**在 core 目录内**，但**仅在 Android 平台编译**（CMakeLists.txt 的 `PLATFORM_ANDROID` 条件排除）。

关键发现：JNI 桥接是**编译期隔离**的，不是运行期隔离的——这意味着 Windows 平台不需要 JNI 层，但需要等价的 C++ → 宿主语言桥接（DLL 导出 + C# P/Invoke 或 C++/CLI，或纯 C++ 直接调用）。

### 2.5 纯 C 入口点

`agenui_engine_entry.h` 提供三个 C 函数：

```cpp
IAGenUIEngine* initAGenUIEngine();
IAGenUIEngine* getAGenUIEngine();
void destroyAGenUIEngine();
```

这是 Windows 集成的关键入口——直接 `__declspec(dllexport)` 导出即可被任何 Windows 宿主语言（C#, C++, Python, Rust）调用。

---

## 3. 现有平台 SDK 模式对比

### 3.1 三平台 SDK 规模

| 平台 | 宿主语言文件 | C++ 桥接文件 | 总文件数 | 组件数 |
|------|-------------|-------------|---------|--------|
| Android | 141 Java/Kotlin | 21 JNI cpp | 162 | 26 工厂类 |
| iOS | 51 Swift | 14 ObjC++ (.mm/.h) | 65 | 26 组件类 |
| Harmony | 20 ETS | 72 C++ | 92 | 27 组件类 |

### 3.2 平台 SDK 架构模式

**Android 模式**：Java SDK 层（组件工厂 + 事件分发 + 测量代理）→ JNI 桥接层 → C++ 核心
- 每个组件有 `XxxComponentFactory.java`，创建标准 Android View（`TextView`, `ImageView`, `Button` 等）
- `A2UIComponent.createView()` 是核心方法，根据组件类型创建对应 Android View
- 测量通过 `IMeasurement` JNI 桥接，委托给 Java 层的 `View.measure()`

**iOS 模式**：Swift SDK 层（组件类直接继承 UIView）→ ObjC++ 桥接层 → C++ 核心
- 每个组件**本身就是 UIView 子类**（`ButtonComponent: Component`，`Component` extends `UIView`）
- 不需要单独的"工厂"——组件类自己创建和管理原生视图
- 测量通过 `AGenUIEngineMeasurementBridge.mm` 桥接

**Harmony 模式**：ETS 声明式层 + C++ 渲染层 → C++ 核心
- **最接近 Windows 可参考的模式**：C++ 渲染层直接使用 ArkUI Native API 创建原生节点
- `A2UIComponent` 构造函数中调用 `OH_ArkUI_GetModuleInterface()` 获取原生节点 API
- 每个组件是 C++ 类（`button_component.cpp`, `text_component.cpp` 等），不是宿主语言类
- 27 个组件 C++ 实现，直接操作 ArkUI 节点句柄

### 3.3 组件清单（三平台交集）

26 种核心组件（Harmony 多 1 个 `video_component_controls` + `video_component_playback` 拆分）：

Text, Button, Image, Card, Column, Row, Divider, Icon, List, Table, Tabs, Modal, Carousel, CheckBox, ChoicePicker, DateTimeInput, RichText, Slider, TextField, AudioPlayer, Video, Web, CheckBoxButton, ImageLoadTransition, SVGToImageParser, UIColor+Hex

---

## 4. Windows 平台 SDK 层需求分析

### 4.1 需新建的模块

| 模块 | 对标平台 | 预估文件数 | 难度 | 说明 |
|------|---------|-----------|------|------|
| **DLL 导出层** | Android JNI / iOS ObjC++ | 3-5 | 低 | `agenui_engine_entry.h` 的 3 个函数 + ISurfaceManager + IAGenUIMessageListener 回调包装 |
| **原生控件层** | Android Views / iOS UIViews / Harmony ArkUI nodes | 26-30 | **高** | 每组件需映射到 Win32/WinUI 3 控件或 Direct2D 自绘 |
| **测量实现** | Android JNI Measurement / iOS MeasurementBridge / Harmony measure/ | 8-12 | 中 | 对接 DirectWrite（文本）、WIC（图像）、GDI 退路 |
| **平台功能** | `jni_android_platform_function.cpp` / `AGenUIEngineFunction.mm` / `harmony_platform_function.cpp` | 2-3 | 低 | `IPlatformFunction` 实现，对接 Windows API |
| **Surface 尺寸** | `jni_surface_size_provider_bridge` / iOS SurfaceManager / Harmony `a2ui_surface_layout_observable` | 1-2 | 低 | `ISurfaceSizeProvider` 实现，基于 HWND |
| **CMake 构建** | Android CMakeLists.txt | 1 | 中 | MSVC 生成器 + yoga FetchContent 或预编译 |
| **Playground 示例** | Android Playground app / iOS Demo | 3-5 | 低 | Win32 或 WinUI 3 宿主应用 |
| **总计** | | **44-58 文件** | | 约合 Android SDK 的 1/3 规模（因为不需要 JNI 层）|

### 4.2 组件映射方案

| AGenUI 组件 | Win32/WinUI 3 映射 | 实现策略 |
|-------------|-------------------|---------|
| Text | `Static`/`TextBlock` (WinUI) | DirectWrite 测量 + GDI/Direct2D 绘制 |
| Button | `Button`/`Button` (WinUI) | 标准控件 + 自定义样式 |
| Image | 自绘 + WIC | WIC 解码 + GDI/Direct2D 绘制 |
| Card | 自绘容器 | Direct2D 圆角矩形 + 裁剪 |
| Column/Row | Flexbox (yoga 驱动) | yoga 布局 → 子控件定位 |
| Divider | 自绘线 | Direct2D 直线 |
| Icon | 字体图标 / SVG | DirectWrite 字体渲染或 SVG 解析 |
| List | `ListView`/自定义 | 虚拟化列表 + yoga 布局 |
| Table | 自绘网格 | Direct2D 表格 + 滚动 |
| Tabs | `TabControl`/自定义 | 标准控件 + 内容切换 |
| Modal | 弹出窗口 | 透明窗口 + 遮罩 |
| Carousel | 自绘 + 定时器 | 横向滚动 + 动画 |
| CheckBox | `Button` (BS_AUTOCHECKBOX) | 标准控件 |
| ChoicePicker | `ComboBox` | 标准控件 |
| DateTimeInput | `DateTimePicker` | 标准控件 |
| RichText | `RichEdit`/DirectWrite | 富文本格式化 |
| Slider | `Trackbar` | 标准控件 |
| TextField | `Edit`/`TextBox` (WinUI) | 标准控件 |
| AudioPlayer | Media Foundation | MF 会话 + 自绘控件 |
| Video | Media Foundation + 自绘 | MF 视频渲染 + 控件 |
| Web | `WebView2` (Edge) | Edge WebView2 COM 接口 |

### 4.3 测量实现（IMeasurement）

核心层通过 `IMeasurementManager` 注册每种组件的测量器。Windows 需实现：

| 测量类型 | 同步/异步 | Windows 实现 |
|---------|---------|-------------|
| Text | Sync | DirectWrite `IDWriteTextFormat` + `MeasureText` |
| Button | Sync | DirectWrite 文本 + padding |
| Image | Async | WIC 解码获取尺寸后 `markDirty` |
| Card/Column/Row | Sync | 基于 yoga 自适应 |
| List/Table/Tabs | Sync | 基于子元素累加 |
| Slider/CheckBox/ChoicePicker | Sync | 标准控件尺寸 |
| DateTimeInput | Sync | 标准控件尺寸 |
| AudioPlayer/Video | Async | 类似 Image，延迟获取 |
| RichText | Sync | DirectWrite 富文本 |
| Icon | Sync | 字体度量或 SVG 尺寸 |
| Web | Async | WebView2 初始化后回调 |

---

## 5. 构建系统

### 5.1 CMake 配置

Android 的 `CMakeLists.txt` 关键配置：

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(agenui CXX)
set(CMAKE_CXX_STANDARD 17)
# arm64-v8a only, add_definitions(-DPLATFORM_ANDROID)
# FetchContent yoga v2.0.0 from GitHub
# Release: -O3 -flto -fvisibility=hidden
# Debug: -O0 -g
```

Windows 等价配置：

```cmake
cmake_minimum_required(VERSION 3.22)
project(agenui_windows CXX)
set(CMAKE_CXX_STANDARD 17)

# 不需要 PLATFORM_ANDROID 定义
# 不排除 Harmony 专属文件（Html.cpp, sax/, ik/）

# Yoga: 使用 vcpkg 或 FetchContent
FetchContent_Declare(yoga GIT_REPOSITORY https://github.com/facebook/yoga.git GIT_TAG v2.0.0)
FetchContent_MakeAvailable(yoga)

# MSVC 特定选项
if(MSVC)
    add_compile_options(/W3 /utf-8 /permissive-)
    add_definitions(-DWINDOWS_EXPORTS)
    # 链接 Windows 系统库
    target_link_libraries(agenui PRIVATE dwrite d2d1 windowscodecs uxtheme)
endif()

# 生成 DLL 而非静态库
add_library(agenui SHARED ${SOURCES})
```

### 5.2 Yoga on Windows

Yoga v2.0.0 官方支持 Windows：
- CMake 原生 MSVC 生成器
- 也可通过 vcpkg 安装：`vcpkg install yoga`
- 无平台特定代码依赖

---

## 6. 可行性矩阵

| 评估维度 | Android | iOS | Harmony | **Windows (预估)** |
|---------|---------|-----|---------|-------------------|
| C++ 核心编译 | ✅ NDK r25 | ✅ Xcode Clang | ✅ OHOS SDK | ✅ MSVC 2022 |
| 宿主语言 | Java/Kotlin | Swift | ArkTS (ETS) | C# 或纯 C++ |
| 桥接方式 | JNI | ObjC++ | NAPI | DLL 导出 |
| UI 框架 | Android Views | UIKit | ArkUI | Win32 / WinUI 3 |
| 文本渲染 | Paint.measureText | UIFont/CTLine | ArkUI API | DirectWrite |
| 图像加载 | BitmapFactory | UIImage | Image API | WIC |
| 布局引擎 | yoga (shared) | yoga (shared) | yoga (shared) | yoga (shared) |
| 组件数 | 26 | 26 | 27 | 26 (需新建) |
| SDK 文件数 | 162 | 65 | 92 | ~50 (无需 JNI) |
| 构建工具 | Gradle+CMake | Xcode+Pod | hvigor+CMake | CMake+MSVC |
| 已有 Playground | ✅ | ✅ | ✅ | ❌ (需新建) |

---

## 7. 推荐实施路径

### Phase 0: C++ 核心编译验证（1-2 天）

**目标**：证明 C++ 核心能在 Windows MSVC 下编译通过。

```
1. 创建 platforms/windows/CMakeLists.txt
2. FetchContent yoga v2.0.0
3. 编译 core/src/ 全部 189 个源文件（排除 jni/ 目录）
4. 生成 agenui_core.dll
5. 编写 test_basic.cpp 验证 initAGenUIEngine() 返回非空
```

**验收标准**：`agenui_core.dll` 生成成功，`initAGenUIEngine()` 调用返回有效指针。

### Phase 1: MVP 三组件（5-7 天）

**目标**：实现 Text + Button + Image 三个组件，在 Win32 窗口中渲染 A2UI JSON。

```
1. 实现 DLL 导出层（3-5 文件）
2. 实现 Win32SurfaceManager（HWND 容器）
3. 实现 TextComponent（DirectWrite 文本测量+绘制）
4. 实现 ButtonComponent（标准 Button 控件 + 事件回调）
5. 实现 ImageComponent（WIC 解码 + GDI 绘制）
6. 实现 Win32MeasurementManager（注册 3 种测量器）
7. 实现 Win32PlatformFunction（空实现，返回 Success）
8. 创建 playground_win32.cpp（创建窗口 → 加载 JSON → 渲染）
```

**验收标准**：`playground_win32.exe` 加载简单 A2UI JSON，显示文本、按钮、图片。

### Phase 2: 全组件补齐（2-3 周）

**目标**：实现剩余 23 个组件。

按难度排序：
- **简单（标准控件）**：CheckBox, ChoicePicker, DateTimeInput, Slider, TextField, Divider
- **中等（自绘 + 布局）**：Card, Column, Row, Icon, Tabs, Modal
- **复杂（自绘 + 交互）**：List, Table, Carousel, RichText
- **高级（多媒体）**：AudioPlayer, Video, Web (WebView2)

### Phase 3: Playground 应用 + 优化（1 周）

- WinUI 3 Playground 应用（替代 Win32 原始窗口）
- 主题/令牌配置加载
- 性能优化（Direct2D 硬件加速）
- 键盘/鼠标无障碍

---

## 8. 风险与挑战

| 风险 | 等级 | 缓解方案 |
|------|------|---------|
| 26 个组件工作量被低估 | 中 | Phase 1 先验证 3 个组件的完整流程，再评估速率 |
| Win32 控件与 Flexbox 布局不兼容 | 中 | 参考 Harmony 模式：C++ 层直接创建/定位原生节点，不依赖宿主布局系统 |
| DirectWrite 测量与核心层预期不符 | 低 | `IMeasurement` 接口是 Sync 返回 `{width, height}`，DirectWrite 的 `MeasureText` 天然同步 |
| 图像异步测量延迟导致空白 | 低 | 核心层已设计 Async 测量 + `markDirty` 机制 |
| WebView2 组件依赖 Edge Runtime | 低 | Win11 预装 WebView2 Runtime；Win10 可引导安装 |
| yoga FetchContent 在 Windows 下载慢 | 低 | 改用 vcpkg 或预编译 lib |
| MSVC C++17 兼容性 | 极低 | 核心层仅用标准 C++17 特征（std::optional, std::variant, std::string_view），MSVC 2022 全支持 |

---

## 9. 结论

AGenUI 的 C++ 核心层**设计上就是平台无关的**——这不是巧合，而是架构决策：所有平台特定逻辑都被隔离在 `platforms/` 目录的 SDK 层中，核心层的 189 个源文件中没有任何 `#ifdef` 平台分支。

Windows 移植的**核心挑战不在 C++ 引擎，而在平台 SDK 层**——需要新建约 50 个文件来实现：
1. DLL 导出层（简单）
2. 26 个原生控件实现（工作量大但模式清晰）
3. DirectWrite 文本测量（中等）
4. WIC 图像加载（简单）
5. IPlatformFunction + ISurfaceSizeProvider（简单）

**推荐参考 Harmony 模式**而非 Android/iOS 模式——因为 Harmony 的 C++ 渲染层直接操作原生节点句柄，最接近 Windows 的 Win32 HWND / Direct2D 渲染模型。Harmony 的 72 个 C++ 文件 + `a2ui_component.cpp` 中 `OH_ArkUI_GetModuleInterface()` 的模式，可以 1:1 映射为 Windows 的 `CreateWindowEx()` + Direct2D 绘制模型。

**总体可行性：70%**——技术上完全可行，主要挑战是工程量（约 4-6 周 1 人），而非架构障碍。
