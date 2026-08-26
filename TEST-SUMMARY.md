# AGenUI 全量测试报告

## 1. 测试资产总览

| 类别 | 文件 | 测试用例数 | 状态 |
|------|------|-----------|------|
| C++ 流式管线 | streaming_pipeline_test.cpp | 15 (ST001-ST015) | ✅ 代码就绪 |
| C++ 流式合并 | streaming_coalescing_test.cpp | 12 (SC001-SC012) | ✅ 代码就绪 |
| Java E2E Settings | SettingsPanelE2ETest.java | 5 (01-05) | ⚠️ 2/5 已验证 |
| Java List 虚拟化 | ListVirtualizationTest.java | 3 | ✅ 代码就绪 |
| JSON Fixture | settings_panel/01-12 | 12 | ✅ 代码就绪 |

**总计: 47 个测试用例, 12 个 fixture**

## 2. C++ 测试详情

### 2.1 流式管线 (streaming_pipeline_test.cpp)
| ID | 场景 | 预期 |
|----|------|------|
| ST001 | begin/end 无数据 | 0 callbacks |
| ST002 | 完整 createSurface | 1 createSurface callback |
| ST003 | 无 begin 直接 receive | 兼容解析 |
| ST004 | 分块 JSON 重组 | 正确解析 |
| ST005 | 逐字节 1 char/chunk | 正确解析 |
| ST006 | 5 个 createSurface | 5 callbacks |
| ST007 | begin 重置 buffer | 丢弃旧数据 |
| ST008 | 垃圾数据后 end | 不崩溃 |
| ST009 | updateComponents | componentsAdd callback |
| ST010 | deleteSurface | deleteSurface callback |
| ST013 | 畸形后恢复 | 正常解析 |
| ST014 | 空 chunk | 忽略 |
| ST015 | 多 SM 隔离 | 各自独立 |

### 2.2 流式合并 (streaming_coalescing_test.cpp)
| ID | 场景 | 预期 |
|----|------|------|
| SC001 | 同 chunk 同 surface 合并 | ≥1 callback |
| SC002 | 同 chunk 不同 surface | 各自 dispatch |
| SC003 | DataModel 中断合并 | ≥2 callbacks |
| SC004 | 单项 fast path | 1 callback |
| SC005 | 跨 chunk 不合并 (旧) | ≥2 callbacks |
| SC006 | end 重置后重新 begin | 1 callback |
| SC007 | 10 条大批量 | 不崩溃 |
| SC008 | 16ms 内跨 chunk | ≥1 callback |
| SC009 | NormalEvent 刷新 pending | 不丢数据 |
| SC010 | endTextStream 刷新 pending | 不丢数据 |
| SC011 | 20 chunk burst | 不崩溃 |
| SC012 | >16ms gap 刷新 pending | ≥1 callback |

## 3. Java E2E 测试详情

### 3.1 SettingsPanelE2ETest
| ID | 场景 | 设备验证 | 备注 |
|----|------|---------|------|
| E2E-01 | 基础结构 | ✅ PASS | 4 组件, 2 层容器 |
| E2E-02 | 双栏+列表 | ❌ FAIL | switch-item-template 缺失 (APK 未含修复) |
| E2E-03 | 滑块列表 | ❌ FAIL | count=2 expected ≥9 (APK 未含修复) |
| E2E-04 | 组件树完整性 | ✅ PASS | parent/child 关系正确 |
| E2E-05 | 分类切换 | 未验证 | 多消息 DataModel 更新 |

**已知失败根因**: `sendMessagesAndWaitForRender` 修复在代码中, 但 AV 锁阻塞 Gradle 构建, 新 APK 未生成。

### 3.2 ListVirtualizationTest
| ID | 场景 | 预期 |
|----|------|------|
| VT-01 | 垂直列表 lazy 创建 | ≥2 组件 |
| VT-02 | 水平列表仍用 RV | ≥2 组件 |
| VT-03 | 空列表不崩溃 | 1 组件 |

## 4. Fixture 详情
| 编号 | 名称 | 组件数 | 特性 |
|------|------|--------|------|
| 01 | basic | 4 | 基础容器+文本 |
| 02 | two_pane | 8 | 左右双栏 |
| 03 | slider_items | 9 | 滑块+开关 |
| 04 | category_switch | 8 | 分类切换 DataModel |
| 05 | dark_mode | 3 | 暗色主题 |
| 06 | responsive | 12 | flex-grow 响应式 |
| 07 | multi_list | 17 | 3 个 List 实例 |
| 08 | empty_list | 3 | 空列表+提示 |
| 09 | single_item | 2 | 单项列表 |
| 10 | nested_containers | 8 | 嵌套多 section |
| 11 | theme_switch | 4 | ${theme} 绑定 |
| 12 | dynamic_add_remove | 4 | 动态增删改 |

## 5. 构建状态

| 构建步骤 | 状态 | 阻塞 |
|----------|------|------|
| C++ 编译 | 未执行 (无本地编译环境) | - |
| Android Gradle | ❌ FAIL | AV 锁 native-platform.dll |
| APK 生成 | ❌ 未生成 | Gradle 阻塞 |
| 设备测试 | 2/5 PASS | APK 未更新 |

## 6. 覆盖差距

| 领域 | 差距 | 优先级 |
|------|------|--------|
| 设备 E2E | E2E-02/03/05 需新 APK | P0 |
| C++ 编译 | 需配置本地 CMake + gtest | P1 |
| 性能基准 | 无 baseline 数据 | P2 |
| iOS/HarmonyOS | 无端到端测试 | P3 |
