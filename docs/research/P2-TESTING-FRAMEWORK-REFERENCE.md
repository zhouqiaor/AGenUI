# AGenUI Windows P2 测试验证业界开源项目调研报告

> 日期：2026-08-26 | 范围：C++/Windows GUI 测试框架 + 视觉回归 + UI 自动化

## 1. 调研目标

为 AGenUI Windows Playground Phase 2 多组件渲染（Text/Button/Image + Yoga 布局）选择测试框架：
- 单元测试：验证 CapturedComponent 解析、hex 颜色解析、pixel 值解析
- 视觉回归：Golden file 对比 Direct2D 渲染输出
- 集成测试：A2UI JSON → engine → listener → render 全链路验证

## 2. 候选项目总览

| # | 项目 | 语言 | Stars | 用途 | 评分 |
|---|------|------|-------|------|------|
| 1 | Google Test (gtest) + gmock | C++ | 35k+ | C++ 单元测试标准框架 | ★★★★★ |
| 2 | Approval Tests for C++ | C++ (header-only) | 338 | Golden Master / Snapshot 测试 | ★★★★★ |
| 3 | ImageApprovals | C++ | ~10 | 图像像素容差比较（扩展 ApprovalTests） | ★★★★ |
| 4 | WinAppDriver | C# | 4k | Win32 UI 自动化（Selenium 协议） | ★★★ |
| 5 | Custom D2D Bitmap → PNG | C++ | — | 自定义视觉回归基线 | ★★★★ |

## 3. 详细分析

### 3.1 Google Test + Google Mock

**仓库**: https://github.com/google/googletest

**核心能力**:
- `TEST()` / `TEST_F()` / `TEST_P()` 三级测试组织
- `EXPECT_*` (非致命) vs `ASSERT_*` (致命) 断言
- `MOCK_METHOD` 宏生成 Mock 类（用于模拟 IAGenUIMessageListener 接口）
- CMake `FetchContent` 集成，与现有 AGenUI 构建系统完美兼容
- 参数化测试支持数据驱动

**对 AGenUI 的复用价值**:
- 验证 `CapturedComponent` 结构体字段（id, type, text, fontSize, x, y, width, height）
- 验证 `parsePixelValue("48px")` → 48.0f
- 验证 `ParseHexColor("#007DFF")` → (0, 0.478, 1.0, 1.0)
- Mock `IAGenUIMessageListener` 验证 onComponentsAdd 回调参数
- 参数化测试：多组 A2UI JSON → 验证引擎解析结果

**集成方式**:
```cmake
FetchContent_Declare(
  googletest
  GIT_REPOSITORY https://github.com/google/googletest.git
  GIT_TAG v1.14.0
)
FetchContent_MakeAvailable(googletest)
enable_testing()
include(GoogleTest)
```

### 3.2 Approval Tests for C++

**仓库**: https://github.com/approvals/ApprovalTests.cpp

**核心能力**:
- Header-only，单文件引入（`ApprovalTests.hpp`）
- 与 GoogleTest / Catch2 / doctest 无缝集成
- Golden Master 模式：首次运行生成 `.received` 文件，人工审批后重命名为 `.approved`
- 后续运行自动比较 `.received` vs `.approved`
- 支持自定义比较器（`ApprovalComparator` 接口）

**对 AGenUI 的复用价值**:
- Golden file 测试 listener 捕获的组件 JSON 输出
- 将 `CapturedComponent` 序列化为文本，与 approved 文件对比
- 验证引擎输出的完整规范化 styles JSON 不被意外改变
- 检测回归：如果引擎升级导致 Yoga 布局坐标变化，测试立即失败

**集成方式**:
```cpp
#include "ApprovalTests.hpp"
// 在 gtest 测试中：
TEST(ListenerTest, CapturesAllComponents) {
    // ... send A2UI protocol ...
    auto components = listener.getCapturedComponents();
    ApprovalTests::Approvals::verifyAll(
        components,
        [](const CapturedComponent& cc, std::ostream& os) {
            os << cc.id << " " << cc.type << " text=" << cc.text
               << " xywh=(" << cc.x << "," << cc.y
               << "," << cc.width << "," << cc.height << ")";
        }
    );
}
```

### 3.3 ImageApprovals

**仓库**: https://github.com/p-podsiadly/ImageApprovals

**核心能力**:
- 扩展 ApprovalTests.cpp，支持 2D 图像像素比较
- 解决 GPU 渲染不可重复性问题（不同 GPU 产生微小像素差异）
- 像素容差比较：允许每通道 ≤ N/255 的差异
- 差异图像输出：高亮显示不匹配像素

**对 AGenUI 的复用价值**:
- Phase 2 的 Direct2D 渲染输出可能因 GPU 驱动版本产生像素差异
- 用 ImageApprovals 做 D2D Bitmap → PNG 截图的 golden test
- 设定像素容差阈值，避免 CI 上不同硬件导致 false negative
- 差异图像帮助定位渲染 bug

**限制**:
- 早期阶段项目（proof of concept），文档不完善
- 需要自己实现 D2D Bitmap → PNG 文件保存（WIC 编码器）

### 3.4 WinAppDriver

**仓库**: https://github.com/microsoft/WinAppDriver

**核心能力**:
- Selenium/WebDriver 协议（HTTP 127.0.0.1:4723）
- 支持 UWP / WinForms / WPF / Win32 应用 UI 自动化
- XPath 元素定位 + 点击/输入/读取文本
- 需要 Windows 10 + 开发者模式 + 管理员权限

**对 AGenUI 的复用价值**:
- 端到端测试：启动 agenui_playground.exe → 发送 A2UI 协议 → 截图 → 验证
- 但 AGenUI Playground 用 Direct2D 自绘（非标准 Win32 控件），WinAppDriver 无法通过 UIA 树定位内部元素
- 仅适用于验证窗口标题、窗口存在性等基本属性

**评估**: ★★★ — 适合 CI 中的冒烟测试（验证应用不崩溃），但不适合验证渲染内容

### 3.5 Custom D2D Bitmap → PNG（自建方案）

**方案**: 用 WIC 编码器将 Direct2D RenderTarget 内容保存为 PNG，与 golden PNG 对比

**实现路径**:
1. `ID2D1HwndRenderTarget::EndDraw()` 后，用 `WICFactory::CreateBitmapFromHwndRenderTarget` 或 `ID2D1Bitmap::CopyFromRenderTarget` 获取像素数据
2. 用 WIC PNG 编码器保存为 `.received.png`
3. 与 `.approved.png` 逐像素比较（允许容差）
4. 不匹配时输出差异图

**优点**: 零依赖，完全控制，与 AGenUI 现有 D2D/WIC 基础设施一致
**缺点**: 需自己实现 PNG 编码和像素比较逻辑

## 4. P2 测试架构设计

```
┌─────────────────────────────────────────────────────┐
│                    P2 测试套件                        │
├──────────────┬──────────────┬───────────────────────┤
│  单元测试    │  视觉回归    │    集成测试            │
│  (gtest)    │ (Approval++) │  (gtest + D2D截图)    │
├──────────────┼──────────────┼───────────────────────┤
│ parsePixel  │ D2D渲染截图  │ A2UI JSON → Engine    │
│ ParseHex    │  → PNG       │ → Listener → Render   │
│ Component   │  → Golden    │ → Screenshot → Golden │
│ Listener    │  比较验证     │  全链路验证            │
│ Mock IF     │              │                       │
└──────────────┴──────────────┴───────────────────────┘
```

### 4.1 测试分层

| 层级 | 框架 | 用例数 | 验证内容 |
|------|------|--------|----------|
| L1 单元 | gtest | ~20 | parsePixelValue, ParseHexColor, parseFloatValue |
| L2 解析 | gtest | ~15 | CapturedComponent 字段提取，JSON 解析正确性 |
| L3 回调 | gtest + gmock | ~10 | onComponentsAdd 参数验证，Mock listener |
| L4 视觉 | ApprovalTests++ | ~5 | D2D 渲染 PNG golden file 对比 |
| L5 集成 | gtest | ~5 | 全链路：协议 → 引擎 → 渲染 → 截图 |

### 4.2 依赖清单

| 依赖 | 版本 | 引入方式 | 大小 |
|------|------|---------|------|
| Google Test | v1.14.0 | CMake FetchContent | ~5MB |
| Google Mock | (随 gtest) | 同上 | — |
| ApprovalTests.cpp | v10.13.0 | 单 header 文件 | ~2MB |
| ImageApprovals | latest | CMake subdirectory | ~1MB |

**总新增依赖: ~8MB**（仅用于测试目标，不影响发布 DLL）

## 5. 推荐方案

**Phase 2 测试采用三层组合**:

1. **gtest + gmock** — 单元测试和 Mock 回调验证（P0 必须）
2. **ApprovalTests.cpp** — Golden file 测试 listener 捕获的组件数据（P0 必须）
3. **自建 D2D → PNG 截图 + 像素比较** — 视觉回归测试（P1 推荐，可选 ImageApprovals 辅助）

**不推荐 WinAppDriver** — Direct2D 自绘窗口无 UIA 树，WinAppDriver 无法定位内部元素，仅适合冒烟测试。

## 6. 关键开源项目参考链接

- [Google Test](https://github.com/google/googletest) — C++ 测试标准
- [ApprovalTests.cpp](https://github.com/approvals/ApprovalTests.cpp) — Snapshot/Golden Master
- [ImageApprovals](https://github.com/p-podsiadly/ImageApprovals) — 图像像素容差比较
- [WinAppDriver](https://github.com/microsoft/WinAppDriver) — Win32 UI 自动化（仅冒烟测试用）
- [ApprovalTests Custom Comparators](https://github.com/approvals/ApprovalTests.cpp/blob/master/doc/CustomComparators.md) — 自定义文件比较器
