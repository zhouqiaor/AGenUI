# Phase 4 — Jetpack Glance 迁移预研

> Worktree: `C:/Code/AGenUI-wt-phase4` | 分支: `feature/phase4-glance`
> 基线: `38270b7` (main, Phase 2 合入) | 制定: 2026-08-26

---

## 一、目标

评估 Jetpack Glance 替代 RemoteViews 的可行性和收益，产出评估报告 + PoC 代码。

---

## 二、任务拆分

### F1: Glance 环境搭建 + PoC

**文件**: 新增 `glance/` 目录, `build.gradle` 修改

- 引入 `androidx.glance:glance-appwidget:1.1.1`
- 创建 `A2UIGlanceWidgetReceiver` + `A2UIGlanceWidget` (GlanceAppWidget)
- 最小 PoC：显示一个 Text + Button 的 Glance widget
- 验证 Glance 能在测试设备上正常显示

### F2: Glance + Bitmap 混合方案验证

**文件**: `A2UIGlanceWidget.kt`

- Glance 的 `provideGlance` 中渲染 AGenUI bitmap 到 `Image`
- 验证 Glance 能否接收外部 Bitmap 并显示
- 对比 RemoteViews `setImageViewBitmap` 的路径

### F3: 延迟对比测试

- PendingIntent vs Glance `actionRunCallback` 点击延迟
- `updateAppWidget` vs Glance `updateAll` 更新延迟
- 产出对比数据表

### F4: 评估报告

**文件**: `docs/widget/PHASE4-GLANCE-EVALUATION.md`

评估维度：

| 维度 | RemoteViews (当前) | Glance |
|------|-------------------|--------|
| 点击延迟 | PendingIntent ~16ms | actionRunCallback ? |
| 更新延迟 | updateAppWidget ~50ms | updateAll ? |
| 可预览性 | 无 | @Preview |
| 响应式 | 手动调用 | StateFlow |
| APK 体积 | 0 | ~2MB |
| Bitmap 支持 | setImageViewBitmap ✅ | ? (待验证) |
| 最低 SDK | 21 | 21 |
| 结论 | — | 迁移 or 保持 |

---

## 三、验收标准

- [ ] Glance PoC widget 能在设备上显示
- [ ] Glance + Bitmap 混合方案验证（能否显示外部 Bitmap）
- [ ] 延迟对比数据表（PendingIntent vs actionRunCallback）
- [ ] 评估报告产出，给出"迁移 or 保持"结论

---

## 四、技术约束

- 构建命令: `cd playground/android && ANDROID_HOME=/c/Programs/Android/Sdk ./gradlew assembleDebug`
- 测试设备: `200.49.0.251:5555`
- 新依赖：`androidx.glance:glance-appwidget:1.1.1` (~2MB)
- 这是**预研**，不替代现有 RemoteViews 实现
- 只在 `feature/phase4-glance` 分支提交
- 如果 Glance 不支持外部 Bitmap 或延迟无优势，结论为"保持 RemoteViews"
