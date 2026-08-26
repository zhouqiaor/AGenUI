# AGenUI 项目长期记忆

## Glance 演进分支 (feature/glance-evolution)
- 基线: PoC 4a69e53 → 演进版 d4b4f0f (20轮自迭代)
- 5 个 Kotlin 文件，~950 行代码
- 架构: "Glance 管壳 + AGenUI Bitmap 管内容"
- 结论: 暂不合入 main，等设备验证
- Git AV 问题: 用 Python git_nested_commit.py 绕过 index.lock

## Windows AV Git 问题模式
- 360/Defender 会锁定 .git/index.lock，持续重建
- 解决: 用 Python subprocess 调 git hash-object + mktree + commit-tree
- 脚本: scripts/git_nested_commit.py (递归 mktree 处理嵌套目录)
- GRADLE_USER_HOME 必须设在项目内 .gradle-home/ 避免 AV 监控

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
