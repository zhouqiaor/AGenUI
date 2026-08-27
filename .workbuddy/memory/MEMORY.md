# AGenUI 项目长期记忆

## Glance 演进 (R1-R210, main 分支直接迭代)
- 基线: PoC 4a69e53 → 演进版 1450 行 (R1-R200, 35+ 问题修复)
- Sprint 7 (R201-R210): Manifest 注册 + 编译验证 ✅ 完成
  - A2UIGlanceWidgetReceiver 已注册到 AndroidManifest.xml
  - compileDebugKotlin BUILD SUCCESSFUL (2m16s, 零错误)
  - widget_glance_title = "A2UI Glance Widget" (独立 label)
  - Lint 受 AV PNG 锁定阻断，需 CI 环境
- 架构: "Glance 管壳 + AGenUI Bitmap 管内容"
- 结论: 暂不正式发布，等 Sprint 8 测试 + Sprint 10 设备验证
- Git AV 问题: 用 Python git_nested_commit.py 绕过 index.lock
- Worktree 方案废弃: AV 持续删除 .git/worktrees/ 元数据，改为直接在 main 迭代

## Windows AV Git 问题模式
- 360/Defender 会锁定 .git/index.lock，持续重建
- AV 还会删除 .git/worktrees/ 目录、refs/heads/ 下的 ref 文件、gradle-wrapper.jar
- 解决: 用 Python subprocess 调 git hash-object + mktree + commit-tree
- 脚本: scripts/git_nested_commit.py (递归 mktree 处理嵌套目录)
- 提交后必须用 Python 手动写入 refs/heads/main (AV 会删除)
- GRADLE_USER_HOME 必须设在项目内 .gradle-home/ 避免 AV 监控
- gradle-wrapper.jar 被删时从其他仓库复制恢复
- Worktree 不可靠: AV 持续删除 worktree 元数据，避免使用

## Settings Panel 开发 (settings-panel 分支)
- Worktree: C:/Code/AGenUI-settings (branch: settings-panel)
- AGenUIBaseTest.sendMessagesAndWaitForRender: 逐条发送+轮询稳定，解决多消息截断
- 流式解析器 resetState() 根因: endTextStream() 清空状态导致后续消息丢失
- 3 个自定义组件骨架: TequSettingsSwitch/Slider/Link (委托内置组件, Phase 1)
- SettingsPanelActivity: 初始化 AGenUI+加载 4K tokens/theme+注册组件+流式发送
- selectCategory 事件: 7 个分类内置数据, 点击切换激活态+右侧内容
- E2E 测试 5 个: 01_basic/02_twoPane/03_slider/04_treeIntegrity/05_categorySwitch
- 4K Design Token: tequ-4k-tokens.json (17 tokens, fp/vp 单位)
- 4K Theme: tequ-4k-theme.json (颜色+字体+间距+圆角+阴影+断点)
- 架构检视: docs/ARCHITECTURE-REVIEW.md (C++ 102.h+87.cpp, Android 149.java)
- 业界对比: AGenUI 唯一原生流式渲染, C++ 单引擎三端, 差距在组件规模+测试维度

## 性能优化 (R29-R35, main 分支)
- List 垂直虚拟化: ListComponent 统一 RecyclerView, 移除 YogaAbsoluteLayout eager path
- Yoga removeNode: O(pool) → O(childCount) via YGNodeGetContext back-pointer
- calculateLayoutWithAdjust: 无 Tabs 时跳过两遍布局 fast path
- 跨 chunk 16ms coalescing: StreamingContentParser _pendingUpdates + tryCrossChunkCoalesce
- 流式 Coalescing 测试: streaming_coalescing_test.cpp (SC001-SC007)
- ListComponent 测试: ListVirtualizationTest.java (3 cases)
- E2E fixtures: 08-12 (empty_list/single_item/nested/theme_switch/dynamic_add_remove)

## SettingsPanelActivity 空白屏修复 (R36)
- 根因: Gradle assets srcDirs 平铺合并, Activity 用了带目录前缀的路径 → 全部 asset 加载失败
- 修复: asset 路径去前缀 + 创建 tequ-4k-tokens.json ({"designTokens":{...}} 格式) + 字体 try-catch
- 验证: uiautomator dump 确认完整设置面板渲染 (7分类+switch/slider/link列表+版本页脚)
- 教训: Gradle assets srcDirs 合并是平铺的, 不保留目录前缀; TokenParser 要 {"designTokens":{}} 外层包裹

## SettingsPanel 全屏修复 (R37)
- Worktree: C:/Code/AGenUI-fix-settings (branch: fix_fullscreen) — 与 main 隔离
- 全屏修复: portrait→landscape + FLAG_FULLSCREEN + IMMERSIVE_STICKY + MATCH_PARENT + onResume 重应用
- 构建修复: GRADLE_USER_HOME 用全新目录避免 AV JNI lock; 从主仓库复制 gradle-wrapper.jar + distribution
- 窗口全屏确认: bounds=[0,0][3840,2160], WindowManager isFullscreen=true
- 引擎处理成功: createSurface+updateComponents+updateDataModel 全 success
- 渲染问题: 截图全白 (0 彩色像素) — 引擎 handleSurfaceSizeChanged 收到 2560×1440 而非 3840×2160
- 待调查: surfaceSize() 回调日志未出现; Surface View 树内容未渲染到屏幕
