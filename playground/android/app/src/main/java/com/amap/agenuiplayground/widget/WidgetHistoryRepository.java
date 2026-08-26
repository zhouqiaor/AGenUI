package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Widget 生成历史仓库 — 基于 Room SQLite 持久化。
 *
 * Phase 3B 替换原 SharedPreferences 实现:
 * - 写入走 Room DAO(异步 ExecutorService)
 * - 读取提供同步接口(阻塞,只在非主线程/降级链路调用)
 * - 兼容原有 API:record / getLastSuccessfulJson / getRecentSummaries / clear
 */
public class WidgetHistoryRepository {

    private static final String TAG = "WidgetHistoryRepo";
    private static final int MAX_RECORDS = 50;

    private final Context context;
    private final WidgetHistoryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    public WidgetHistoryRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dao = WidgetHistoryDatabase.getInstance(this.context).historyDao();
    }

    /**
     * 记录一次生成结果(异步写入)。
     */
    public void record(String prompt, String a2uiJson, long latencyMs, boolean success) {
        final WidgetHistoryEntity entity = new WidgetHistoryEntity();
        entity.prompt = truncate(prompt, 200);
        entity.a2uiJson = truncate(a2uiJson, 5000);
        entity.timestamp = System.currentTimeMillis();
        entity.latencyMs = latencyMs;
        entity.success = success;
        entity.widgetId = 0;
        entity.timeFormatted = dateFormat.format(new Date());

        executor.execute(() -> {
            try {
                dao.insert(entity);
                dao.trimOld(MAX_RECORDS);
                Log.d(TAG, "Recorded: success=" + success + ", latency=" + latencyMs + "ms");
            } catch (Exception e) {
                Log.e(TAG, "Failed to record history", e);
            }
        });
    }

    /**
     * 返回上次成功的 A2UI JSON(用于断网缓存)。同步阻塞调用,需在工作线程。
     */
    public String getLastSuccessfulJson() {
        try {
            return syncGet(new java.util.concurrent.Callable<String>() {
                @Override
                public String call() {
                    return dao.getLastSuccessfulJson();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to get last successful JSON", e);
            return null;
        }
    }

    /**
     * Returns the most recent successful generation records (for use as
     * few-shot examples in the next LLM prompt).
     *
     * <p>Phase 3A: filters {@code success == true} AND {@code a2uiJson} is non-empty.
     * Phase 3B: reads from Room DAO (同步阻塞,需在工作线程调用).
     *
     * @param limit max number of examples to return (e.g. 3)
     * @return list of examples, possibly empty.
     */
    public List<FewShotExample> getRecentSuccessfulExamples(int limit) {
        if (limit <= 0) return new ArrayList<>();
        try {
            List<WidgetHistoryEntity> records = syncGet(new java.util.concurrent.Callable<List<WidgetHistoryEntity>>() {
                @Override
                public List<WidgetHistoryEntity> call() {
                    return dao.observeRecent(limit * 4); // 多讀一些,過濾後取 limit
                }
            });
            List<FewShotExample> result = new ArrayList<>();
            if (records != null) {
                for (WidgetHistoryEntity rec : records) {
                    if (!rec.success) continue;
                    String prompt = rec.prompt != null ? rec.prompt : "";
                    String a2uiJson = rec.a2uiJson != null ? rec.a2uiJson : "";
                    if (prompt.isEmpty() || a2uiJson.length() < 40) continue;
                    result.add(new FewShotExample(prompt, a2uiJson));
                    if (result.size() >= limit) break;
                }
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get few-shot examples", e);
            return new ArrayList<>();
        }
    }

    /**
     * A successful generation record used as a few-shot example.
     */
    public static class FewShotExample {
        public final String prompt;
        public final String a2uiJson;

        public FewShotExample(String prompt, String a2uiJson) {
            this.prompt = prompt;
            this.a2uiJson = a2uiJson;
        }
    }

    /**
     * 返回最近 50 条记录摘要(同步阻塞,需在工作线程)。
     */
    public List<String> getRecentSummaries() {
        try {
            List<WidgetHistoryEntity> records = syncGet(new java.util.concurrent.Callable<List<WidgetHistoryEntity>>() {
                @Override
                public List<WidgetHistoryEntity> call() {
                    return dao.observeRecent(MAX_RECORDS);
                }
            });
            List<String> result = new ArrayList<>();
            if (records != null) {
                for (WidgetHistoryEntity rec : records) {
                    String time = rec.timeFormatted != null ? rec.timeFormatted : "?";
                    String prompt = truncate(rec.prompt != null ? rec.prompt : "", 30);
                    String status = rec.success ? "✓" : "✗";
                    result.add(time + " " + status + " " + rec.latencyMs + "ms " + prompt);
                }
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get summaries", e);
            return new ArrayList<>();
        }
    }

    /**
     * 清空历史(异步)。
     */
    public void clear() {
        executor.execute(() -> {
            try {
                dao.clear();
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear history", e);
            }
        });
    }

    /**
     * 同步执行 Callable 并返回结果。用 future.get 带超时避免主线程卡死。
     */
    private <T> T syncGet(final java.util.concurrent.Callable<T> callable) throws Exception {
        final java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(callable);
        executor.execute(task);
        return task.get(2, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
