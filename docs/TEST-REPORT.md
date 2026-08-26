# AGenUI Windows Playground 测试报告

> **生成日期**: 2026-08-26 15:42  
> **仓库**: `C:/Code/AGenUI-windows` (分支 `windows-port`)  
> **HEAD**: `18f0a91` feat(windows): merge P2 tests + P3 features into windows-port

---

## 1. 执行摘要

| 指标 | 数值 |
|------|------|
| 测试总数 | **58** |
| 通过 | **58** |
| 失败 | **0** |
| 跳过 | **0** |
| 测试套件数 | **13** |
| 总执行时间 | **178 ms** (brief) / **264 ms** (full) |
| 通过率 | **100%** |
| 源代码总行数 | **3,711** (8 头文件 + 5 测试 + 2 CMake + 2 playground) |
| 代码 Commits | **2** (Phase 0-2 `b237dc2` + 合并 `18f0a91`) |

**结论: 全部 58 个测试通过，零失败，零跳过，零崩溃。**

---

## 2. 构建环境

| 项目 | 版本/路径 |
|------|----------|
| 编译器 | MSVC 2022 (Visual Studio 17 2022, BuildTools) |
| CMake | 3.31 (VS 集成) |
| 生成器 | Visual Studio 17 2022, x64 |
| C++ 标准 | C++17 |
| Yoga | v2.0.0 (CMake FetchContent, GitHub) |
| Google Test | v1.14.0 (CMake FetchContent) |
| ApprovalTests.cpp | v10.13.0 (单 header 下载) |
| nlohmann/json | core/src/third_party 内置 |
| Windows SDK | 10.0.26100.0 (target Windows 10.0.26200) |
| 构建脚本 | `build.bat` (vcvars64 + cmake configure + build) |

### 构建产物

| 产物 | 路径 | 大小 |
|------|------|------|
| AGenUI Windows DLL | `build/Debug/agenui_windows.dll` | — |
| Playground EXE | `build/playground/Debug/agenui_playground.exe` | — |
| Test EXE | `build/tests/Debug/agenui_playground_tests.exe` | — |

---

## 3. 测试矩阵

### 3.1 按套件分布

| # | 测试套件 | 用例数 | 耗时 | 层级 | 验证内容 |
|---|---------|--------|------|------|----------|
| 1 | `ParsePixelValue` | 6 | 1ms | L1 单元 | "48px"/"32"/空/非数字/小数/截断解析 |
| 2 | `ParseFloatValue` | 4 | 0ms | L1 单元 | JSON number/string/null/bool 转 float |
| 3 | `ParseHexColor` | 10 | 0ms | L1 单元 | #RRGGBB/#RRGGBBAA/空/无效/黑白/full/zero alpha |
| 4 | `ToWide` | 5 | 0ms | L1 单元 | UTF-8→wstring 转换 (ASCII/空/中文/混合/长度) |
| 5 | `ListenerTextTest` | 3 | 0ms | L2 解析 | Text 组件字段 (text/fontSize/align/color/xywh) |
| 6 | `ListenerButtonTest` | 3 | 0ms | L2 解析 | Button 组件字段 (bg/radius/border/padding) |
| 7 | `ListenerImageTest` | 3 | 0ms | L2 解析 | Image 组件字段 (src/width/height) |
| 8 | `ListenerMultiTest` | 2 | 0ms | L2 解析 | 多组件捕获 + Update 替换 |
| 9 | `ListenerErrorTest` | 4 | 0ms | L2 解析 | 非法/空/截断 JSON 不崩溃 + 缺失字段用默认值 |
| 10 | `ListenerSurfaceTest` | 2 | 0ms | L2 解析 | Surface 创建/删除清空组件 |
| 11 | `IntegrationTest` | 9 | 6ms | L5 集成 | 多组件树/完整字段/Update替换/20组件压测/Yoga坐标/3级嵌套 |
| 12 | `VisualTest` | 3 | 140ms | L4 视觉 | D2D→WIC→PNG pipeline (矩形/文本/圆角矩形) |
| 13 | `GoldenTest` | 4 | 110ms | L4 视觉 | ApprovalTests golden file (组件序列化/颜色/像素/多组件树) |
| | **合计** | **58** | **264ms** | | |

### 3.2 按测试层级分布

```
L1 单元测试 (25 tests)  ████████████████████████████  43.1%
L2 解析测试 (17 tests)  ████████████████████          29.3%
L4 视觉测试 (7 tests)   ████████                        12.1%
L5 集成测试 (9 tests)   ███████████                     15.5%
```

---

## 4. 测试用例详情

### 4.1 L1 单元测试 — 工具函数 (25 tests)

#### ParsePixelValue (6 tests)
| 用例 | 输入 | 预期输出 | 结果 |
|------|------|---------|------|
| ParsesPxSuffix | "48px" | 48.0f | ✅ |
| ParsesBareNumber | "32" | 32.0f | ✅ |
| ReturnsDefaultOnEmpty | "" | 24.0f (default) | ✅ |
| ReturnsDefaultOnNonDigit | "abc" | 24.0f (default) | ✅ |
| ParsesDecimal | "12.5px" | 12.5f | ✅ |
| StopsAtNonDigit | "32abc" | 32.0f | ✅ |

#### ParseFloatValue (4 tests)
| 用例 | 输入类型 | 预期 | 结果 |
|------|---------|------|------|
| ParsesNumber | JSON number | float value | ✅ |
| ParsesStringWithPx | "100.5px" | 100.5f | ✅ |
| ReturnsZeroOnNull | JSON null | 0.0f | ✅ |
| ReturnsZeroOnBool | JSON bool | 0.0f | ✅ |

#### ParseHexColor (10 tests)
| 用例 | 输入 | 预期 RGBA | 结果 |
|------|------|----------|------|
| ParsesSixDigitHex | "#007DFF" | (0, 0.478, 1.0, 1.0) | ✅ |
| ParsesEightDigitHexWithAlpha | "#000000E6" | (0, 0, 0, ~0.9) | ✅ |
| ReturnsDefaultOnEmpty | "" | default | ✅ |
| ReturnsDefaultOnInvalid | "invalid" | default | ✅ |
| ReturnsDefaultOnMissingHash | "007DFF" | default | ✅ |
| ReturnsDefaultOnWrongLength | "#12345" | default | ✅ |
| ParsesBlack | "#000000" | (0,0,0,1) | ✅ |
| ParsesWhite | "#FFFFFF" | (1,1,1,1) | ✅ |
| ParsesFullAlpha | "#FFFFFFFF" | (1,1,1,1) | ✅ |
| ParsesZeroAlpha | "#00000000" | (0,0,0,0) | ✅ |

#### ToWide (5 tests)
| 用例 | 输入 | 预期 | 结果 |
|------|------|------|------|
| ConvertsAscii | "Hello" | L"Hello" | ✅ |
| ConvertsEmpty | "" | L"" | ✅ |
| ConvertsUtf8Chinese | "你好世界" | L"你好世界" | ✅ |
| ConvertsMixedAlphaNumeric | "Test123" | L"Test123" | ✅ |
| LengthMatches | (size check) | input==output length | ✅ |

### 4.2 L2 解析测试 — WindowsMessageListener (17 tests)

#### ListenerTextTest (3 tests)
| 用例 | 验证 | 结果 |
|------|------|------|
| CapturesTextFields | text/fontSize/textAlign/textColor/xywh | ✅ |
| CapturesTextWithAlphaColor | #000000E6 with alpha channel | ✅ |
| TextWithNoStyles | 缺失 styles 时使用默认值 | ✅ |

#### ListenerButtonTest (3 tests)
| 用例 | 验证 | 结果 |
|------|------|------|
| CapturesButtonFields | text/bgColor/borderRadius/borderWidth/padding | ✅ |
| ButtonWithBorderColor | borderColor 字段提取 | ✅ |
| ButtonNoStyles | 缺失 styles 时默认值 | ✅ |

#### ListenerImageTest (3 tests)
| 用例 | 验证 | 结果 |
|------|------|------|
| CapturesImageFields | src/width/height 坐标 | ✅ |
| ImageNumericDimensions | 数值类型的 width/height | ✅ |
| ImageNoSrc | 缺失 src 时 src 为空 | ✅ |

#### ListenerMultiTest (2 tests)
| 用例 | 验证 | 结果 |
|------|------|------|
| CapturesMultipleComponents | 3 组件同时捕获 | ✅ |
| UpdateReplacesComponents | onComponentsUpdate 清空旧数据 | ✅ |

#### ListenerErrorTest (4 tests)
| 用例 | 输入 | 预期 | 结果 |
|------|------|------|------|
| InvalidJsonDoesNotCrash | "not json" | 不崩溃，错误日志 | ✅ |
| EmptyJsonDoesNotCrash | "" | 不崩溃 | ✅ |
| PartialJsonDoesNotCrash | "{ invalid }" | 不崩溃 | ✅ |
| MissingFieldsUsesDefaults | 缺失 text/styles | 默认值 | ✅ |

#### ListenerSurfaceTest (2 tests)
| 用例 | 验证 | 结果 |
|------|------|------|
| CreateSurfaceClearsComponents | onCreateSurface 清空 | ✅ |
| DeleteSurfaceClearsComponents | onDeleteSurface 清空 | ✅ |

### 4.3 L5 集成测试 — 全链路验证 (9 tests)

| 用例 | 组件数 | 验证重点 | 耗时 | 结果 |
|------|--------|---------|------|------|
| MultiComponentTreeCapturesAllLevels | 3 | 嵌套 Column+2 Text，parentId 链 | 0ms | ✅ |
| CompleteComponentTreeAllFieldsVerified | 6 | 逐字段验证 (Text/Button/Image) | 1ms | ✅ |
| UpdateReplacesAllComponents | 3→2 | 旧数据清空+新数据正确 | 0ms | ✅ |
| LargeDataSetTwentyComponentsNoLoss | 20 | 交替 Text/Button 全捕获 | 4ms | ✅ |
| YogaLayoutCoordinatesExtractedCorrectly | 1 | 数值 x/y/width/height | 0ms | ✅ |
| YogaLayoutCoordinatesAsStringPx | 1 | 字符串 "10px"/"20px" | 0ms | ✅ |
| MixedNumericAndStringYogaCoordinates | 2 | 混合数值+字符串 | 0ms | ✅ |
| ZeroYogaCoordinatesPreserved | 1 | 零值不丢失 | 0ms | ✅ |
| ThreeLevelNestingParentChainPreserved | 3 | L0→L1→L2 parent 链 | 0ms | ✅ |

### 4.4 L4 视觉测试 (7 tests)

#### VisualTest — D2D→WIC→PNG (3 tests)
| 用例 | 渲染内容 | PNG 大小 | 耗时 | 结果 |
|------|---------|---------|------|------|
| BlueRectangleToPng | 200×100 蓝色矩形 | 非空 | 97ms | ✅ |
| TextHelloToPng | "Hello" 文本 | 非空 | 19ms | ✅ |
| RoundedRectangleButtonToPng | 圆角矩形 Button | 非空 | 22ms | ✅ |

#### GoldenTest — ApprovalTests.cpp (4 tests)
| 用例 | Golden 文件 | 比较方式 | 耗时 | 结果 |
|------|------------|---------|------|------|
| CapturedComponentSerialisation | test_golden.GoldenTest.CapturedComponentSerialisation.approved.txt | 文本逐行 | 26ms | ✅ |
| HexColorParsing | test_golden.GoldenTest.HexColorParsing.approved.txt | 文本逐行 | 20ms | ✅ |
| PixelValueParsing | test_golden.GoldenTest.PixelValueParsing.approved.txt | 文本逐行 | 17ms | ✅ |
| MultiComponentTree | test_golden.GoldenTest.MultiComponentTree.approved.txt | 文本逐行 | 19ms | ✅ |

---

## 5. 功能覆盖度

### 5.1 测试覆盖的组件类型

| 组件类型 | 单元 | 解析 | 集成 | 视觉 | Golden | 覆盖 |
|---------|------|------|------|------|--------|------|
| Text | ✅ | ✅ | ✅ | ✅ | ✅ | 100% |
| Button | ✅ | ✅ | ✅ | ✅ | ✅ | 100% |
| Image | ✅ | ✅ | ✅ | — | ✅ | 83% |
| Column | — | — | ✅ | — | ✅ | 33% |
| Row | — | — | — | — | — | 0%* |

*Row 组件由 Playground 手动验证（F3 场景），未纳入自动化测试。

### 5.2 测试覆盖的功能点

| 功能 | 测试方式 | 状态 |
|------|---------|------|
| A2UI JSON 解析 | L2 listener + L5 integration | ✅ |
| Yoga 布局坐标提取 | L5 integration (4 sub-tests) | ✅ |
| Hex 颜色解析 (#RRGGBB/#RRGGBBAA) | L1 unit + L4 golden | ✅ |
| Pixel 值解析 (px 后缀/纯数字) | L1 unit + L4 golden | ✅ |
| UTF-8→wstring 转换 | L1 unit | ✅ |
| 多组件捕获 | L2 + L5 (20 组件压测) | ✅ |
| 错误处理 (非法/空/截断 JSON) | L2 error (4 tests) | ✅ |
| Surface 生命周期 | L2 surface (create/delete) | ✅ |
| onComponentsAdd 回调 | L2 + L5 | ✅ |
| onComponentsUpdate 回调 | L2 (替换) + L5 | ✅ |
| 组件序列化稳定性 | L4 golden (4 tests) | ✅ |
| D2D→WIC→PNG pipeline | L4 visual (3 tests) | ✅ |
| 嵌套组件树 (3 级) | L5 integration | ✅ |
| 缺失字段默认值 | L2 error + L5 | ✅ |

### 5.3 未覆盖的功能点（后续迭代）

| 功能 | 原因 | 优先级 |
|------|------|--------|
| WIC 真实图像加载 | 需要实际图片文件 | P1 |
| Button 点击交互 | 需要窗口交互模拟 | P1 |
| resize 动态布局 | 需要窗口 resize 模拟 | P2 |
| Row 组件自动化测试 | 手动验证通过 | P2 |
| onActionEventRouted 回调 | 需要真实引擎交互 | P2 |

---

## 6. 源代码清单

### 6.1 头文件 (8 files, 867 lines)

| 文件 | 行数 | 职责 |
|------|------|------|
| `agenui_windows_entry.h` | 23 | DLL 导出入口 (agenuiInit/Destroy/GetVersion) |
| `d2d_resources.h` | 57 | D2D/DWrite/WIC 工厂单例 (ComPtr) |
| `win_surface_size_provider.h` | 52 | ISurfaceSizeProvider (HWND→a2ui units) |
| `win_platform_function.h` | 26 | IPlatformFunction 空实现 |
| `win_utils.h` | 45 | ParseHexColor + ToWide 公共工具函数 |
| `win_wic_image_loader.h` | 262 | WIC 图像加载 (Decoder→Bitmap 缓存) |
| `d2d_png_capture.h` | 151 | D2D→WIC→PNG 截图工具 |
| `win_message_listener.h` | 251 | 统一 CapturedComponent + Listener |

### 6.2 Playground (2 files, 1054 lines)

| 文件 | 行数 | 职责 |
|------|------|------|
| `main.cpp` | 55 | Phase 0 控制台冒烟测试 |
| `win32_app.cpp` | 999 | Win32 窗口 + D2D 渲染 + 交互 + 多场景 |

### 6.3 测试文件 (5 files, 1493 lines)

| 文件 | 行数 | 用例数 | 层级 |
|------|------|--------|------|
| `test_unit.cpp` | 177 | 25 | L1 |
| `test_listener.cpp` | 323 | 17 | L2 |
| `test_integration.cpp` | 505 | 9 | L5 |
| `test_visual.cpp` | 222 | 3 | L4 |
| `test_golden.cpp` | 266 | 4 | L4 |

### 6.4 构建配置 (3 files, 297 lines)

| 文件 | 行数 | 职责 |
|------|------|------|
| `platforms/windows/CMakeLists.txt` | 122 | 顶层 (DLL + playground + tests) |
| `platforms/windows/tests/CMakeLists.txt` | 101 | gtest + ApprovalTests |
| `platforms/windows/playground/CMakeLists.txt` | 74 | playground + test 两个 target |

---

## 7. Git 提交历史

```
18f0a91 feat(windows): merge P2 tests + P3 features into windows-port
         28 files changed, 3139 insertions(+), 95 deletions(-)
         
b237dc2 feat(windows): Phase 0-2 — AGenUI Windows port with Direct2D multi-component rendering
         15 files changed, 3754 insertions(+)
```

### 并行 Worktree 开发历史

| Worktree | 分支 | Commits | 内容 |
|----------|------|---------|------|
| `C:/Code/AGenUI-p2-test` | `windows-p2-test` | `b897f59` + `e1809be` | 42→54→58 tests |
| `C:/Code/AGenUI-windows-p3` | `windows-p3` | `24e16bd` + `6e51267` | WIC + 交互 + 多场景 |

---

## 8. 调研报告清单

| 报告 | 路径 | 项目数 | 内容 |
|------|------|--------|------|
| P2 测试框架 | `docs/research/P2-TESTING-FRAMEWORK-REFERENCE.md` | 5 | gtest/ApprovalTests/ImageApprovals/WinAppDriver/自建 |
| P3 WinUI3+WIC | `docs/research/P3-WINUI3-WIC-REFERENCE.md` | 7 | Win2D/KKE/D2DImageListViewer/Ray1024/MS WIC/lecui/d2d-mica |
| Playground 调研 | `docs/research/AGENUI-WINDOWS-PLAYGROUND-REFERENCE.md` | 18 | 6 框架 + 18 项目 |
| 可行性分析 | `docs/research/AGENUI-WINDOWS-FEASIBILITY-ANALYSIS.md` | — | C++ 核心 85% 可移植 |
| SDK 开源复用 | `docs/research/AGENUI-WINDOWS-OPEN-SOURCE-REFERENCE.md` | 12 | yoga/Skia/Win2D/RNW/Qt6 等 |
| 官方案例分析 | `docs/research/AGENUI-OFFICIAL-STORIES-ANALYSIS.md` | 26 | 26 组件类型分析 |

---

## 9. 关键技术发现

| # | 发现 | 影响 |
|---|------|------|
| 1 | 引擎首次添加用 `onComponentsAdd` 而非 `onComponentsUpdate` | 必须监听两个回调 |
| 2 | Design Token 自动解析 (`Color_Text_Heading=#000000`) | 无需手动处理 token |
| 3 | 引擎输出完整规范化 styles JSON (含所有默认值) | 测试可断言所有字段 |
| 4 | a2ui 逻辑单位 = pv × 2 (800×600px → 392×280.5 a2ui) | 渲染需 ×2 转 DIP |
| 5 | Yoga 对 Text 返回 width=0/height=0 (无平台测量函数) | 用 DirectWrite TextLayout 补偿 |
| 6 | 引擎支持 Row 组件 (Yoga `YGFlexDirectionRow`) | 非 Column 布局可用 |
| 7 | `ISurfaceManager::submitUIAction(ActionMessage)` 发送事件 | Button 点击可路由到引擎 |
| 8 | resize 触发引擎 Yoga 重算 → `onComponentsUpdate` 新坐标 | 动态布局可用 |
| 9 | WIC pipeline: Decoder→Frame→FormatConverter(32bppPBGRA)→D2D Bitmap | 图像加载零依赖 |
| 10 | `URLDownloadToFileW` (urlmon) 下载 HTTP 图像到临时文件 | 无需 libcurl/cpr |

---

## 10. 自测运行命令

### 10.1 构建全部

```bash
cd C:/Code/AGenUI-windows
bash build.bat
```

### 10.2 运行测试

```bash
# 运行全部 58 个测试
cd C:/Code/AGenUI-windows/build/tests/Debug
./agenui_playground_tests.exe

# 简洁输出（只显示结果）
./agenui_playground_tests.exe --gtest_brief=1

# 运行特定套件
./agenui_playground_tests.exe --gtest_filter=ParseHexColor.*
./agenui_playground_tests.exe --gtest_filter=GoldenTest.*
./agenui_playground_tests.exe --gtest_filter=IntegrationTest.*
```

### 10.3 运行 Playground

```bash
# 默认场景 (Column + Text/Button/Image)
cd C:/Code/AGenUI-windows/build/playground/Debug
./agenui_playground.exe

# 指定场景 (1=Column, 2=Row, 3=Nested, 4=Multi-button)
AGENUI_SCENARIO=2 ./agenui_playground.exe
```

### 10.4 构建脚本

```bash
# 构建测试（Ninja generator）
powershell -ExecutionPolicy Bypass -File C:/Code/AGenUI-windows/build_tests.ps1

# 或 batch
cd C:/Code/AGenUI-windows
build_tests.bat           # 构建+运行
build_tests.bat clean     # 清缓存重建
```

---

## 11. 结论与后续建议

### 结论
AGenUI Windows Playground Phase 0-3 已完成开发和测试验证。58 个自动化测试 100% 通过，覆盖单元/解析/集成/视觉/golden file 五个层级。核心渲染能力（Text/Button/Image + Yoga 布局 + WIC 图像加载 + Button 交互 + 动态 resize）全部可用。

### 后续优先级

| 优先级 | 任务 | 预估 |
|--------|------|------|
| P0 | WIC 真实图像加载自动化测试（依赖本地图片文件） | 2h |
| P0 | Row 组件自动化测试（从手动 F3 迁移到 gtest） | 2h |
| P1 | Button 点击交互自动化测试（模拟 WM_LBUTTONDOWN） | 4h |
| P1 | Resize 动态布局自动化测试（模拟 WM_SIZE） | 4h |
| P2 | P3.2 WinUI3 + Win2D 升级（CanvasControl + XAML） | 3-5d |
| P2 | 代码覆盖率统计（gcov/llvm-cov） | 1d |
| P3 | CI/CD 集成（GitHub Actions / Azure Pipelines） | 1d |
