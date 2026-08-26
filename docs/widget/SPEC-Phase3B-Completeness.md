# Phase 3B — P2 遗留补齐 + 稳定性

> Worktree: `C:/Code/AGenUI-wt-phase3b` | 分支: `feature/phase3b-completeness`
> 基线: `38270b7` (main, Phase 2 合入) | 制定: 2026-08-26

---

## 一、目标

补齐 Phase 2 计划中未完成的功能项和稳定性要求，使产品可进入众测。

---

## 二、任务拆分

### F1: WidgetInputActivity BAL 限制修复

**文件**: `A2UIWidgetProvider.java`, 新增 `WidgetInputLaunchService.java`

- 当前问题：从 broadcast receiver 通过 `PendingIntent.send()` 启动 Activity 受 Android 10+ BAL 限制
- 方案：改用 `ForegroundService` 中介 — receiver 启动 Service，Service 再 `startActivity`
- Service 用 `startForeground` + 通知提升优先级
- 验收：从 widget 点击 AI 按钮能可靠启动输入面板（Android 10+ 不被拦截）

### F2: Room 数据库替换 SharedPreferences

**文件**: 新增 `WidgetHistoryDatabase.java`, `WidgetHistoryDao.java`, `WidgetHistoryEntity.java`
        修改 `WidgetHistoryRepository.java`

- 引入 `androidx.room:room-runtime:2.6.1` + `room-compiler`
- Entity: id/prompt/a2uiJson/timestamp/latency/success/widgetId
- DAO: insert + observeRecent(50) + search
- Repository 改为调用 Room DAO
- 验收：历史记录持久化到 SQLite，重启不丢失，可查询最近 50 条

### F3: Vosk 离线语音替换

**文件**: 新增 `WidgetVoskManager.java`, `VoskModelLoader.java`
        修改 `WidgetInputActivity.java`, `build.gradle`

- 引入 `com.alphacephei:vosk-android:0.3.70`
- 首次使用时动态下载 vosk-model-small-cn-0.15 (42MB)
- AudioRecord → Vosk SpeechRecognizer → partial/final result
- 保留 WidgetVoiceHelper（在线）作为降级方案
- 验收：离线语音识别可用，首字延迟 <500ms

### F4: 断网缓存显示

**文件**: `WidgetRenderActivity.java`, `WidgetHistoryRepository.java`

- LLM 网络失败时，从历史记录取上次成功渲染的 A2UI JSON
- 用 WidgetFallbackBuilder 转换后推送到 SurfaceManager
- 显示标题："AGenUI · 离线缓存"
- 验收：断网时 widget 显示上次成功结果而非空白

### F5: 批量测试 — LLM JSON 合法率统计

**文件**: 新增 `WidgetBatchTest.java` (androidTest)

- 输入 20 组不同 prompt（天气/待办/日程/混合）
- 统计 LLM 输出合法 JSON 比例
- 记录平均延迟
- 验收：合法率 >80%，输出测试报告

---

## 三、验收标准

- [ ] Widget AI 按钮在 Android 10+ 可靠启动输入面板
- [ ] 历史记录持久化到 Room SQLite
- [ ] Vosk 离线语音识别可用
- [ ] 断网时显示上次成功渲染结果
- [ ] LLM JSON 合法率 >80%（20 次统计）

---

## 四、技术约束

- 构建命令: `cd playground/android && ANDROID_HOME=/c/Programs/Android/Sdk ./gradlew assembleDebug`
- 测试设备: `200.49.0.251:5555`
- adb 命令必须带 `-s 200.49.0.251:5555`
- 新依赖：room（~3MB）、vosk（lib ~5MB，模型动态下载不打包）
- 只在 `feature/phase3b-completeness` 分支提交
