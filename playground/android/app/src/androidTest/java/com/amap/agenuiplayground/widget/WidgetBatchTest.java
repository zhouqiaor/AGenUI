package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 3B F5: LLM JSON 合法率批量测试。
 *
 * 用 20 组不同 prompt 同步调用 WidgetLLMClient,统计:
 * - 合法 A2UI JSON 比例(目标 >80%)
 * - 平均延迟
 *
 * 注:本测试需要真实 LLM API key(在 WidgetLLMConfig 中配置),且需要网络。
 * 在无 API key / 无网络环境下会失败,属于预期行为。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetBatchTest {

    private static final String TAG = "WidgetBatchTest";
    private static final int TIMEOUT_PER_PROMPT_MS = 60000;

    private Context context;
    private WidgetLLMClient client;

    private static final String[] PROMPTS = {
            "今天北京天气",
            "今天上海天气",
            "今天深圳天气如何",
            "今日待办清单",
            "明天的待办事项",
            "本周任务清单",
            "今日会议日程",
            "明天上午的议程",
            "本周日程安排",
            "今天有什么会议",
            "帮我做一个购物清单",
            "番茄炒蛋的做法步骤",
            "每日喝水提醒",
            "本周健身计划",
            "读书清单推荐",
            "旅行行李清单",
            "项目进度跟踪表",
            "今日工作安排",
            "学习计划表",
            "家庭开支预算表"
    };

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        client = new WidgetLLMClient(context);
    }

    @Test
    public void testBatchLlmJsonValidity() {
        int total = PROMPTS.length;
        int validCount = 0;
        long totalLatency = 0;
        StringBuilder report = new StringBuilder();

        for (int i = 0; i < total; i++) {
            String prompt = PROMPTS[i];
            Log.i(TAG, "Test " + (i + 1) + "/" + total + ": " + prompt);

            TestResult result = runSingle(prompt);
            Log.i(TAG, "  valid=" + result.valid + ", latency=" + result.latencyMs + "ms");

            report.append(String.format("%-20s %s %dms%n",
                    prompt.length() > 20 ? prompt.substring(0, 20) : prompt,
                    result.valid ? "✓" : "✗",
                    result.latencyMs));

            if (result.valid) validCount++;
            totalLatency += result.latencyMs;
        }

        double validRate = 100.0 * validCount / total;
        long avgLatency = totalLatency / total;

        String summary = String.format(
                "%n===== WidgetBatchTest Report =====%n" +
                        "Total: %d%n" +
                        "Valid: %d%n" +
                        "Valid rate: %.1f%%%n" +
                        "Avg latency: %dms%n" +
                        "===================================%n",
                total, validCount, validRate, avgLatency);

        Log.i(TAG, summary + report.toString());

        // 验收标准:合法率 > 80%
        assertTrue("LLM JSON valid rate too low: " + validRate + "% (target >80%)",
                validRate > 80.0);
    }

    private TestResult runSingle(String prompt) {
        final TestResult result = new TestResult();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> contentRef = new AtomicReference<>("");
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        final long startTime = System.currentTimeMillis();

        // LLMClient 在调用线程回调,这里用新线程避免阻塞
        new Thread(() -> {
            client.streamChat(WidgetPromptBuilder.SYSTEM_PROMPT, prompt,
                    new WidgetLLMClient.StreamCallback() {
                        @Override
                        public void onChunk(String delta) {
                            String cur = contentRef.get();
                            contentRef.compareAndSet(cur, cur + (delta != null ? delta : ""));
                        }

                        @Override
                        public void onComplete(String content) {
                            contentRef.set(content != null ? content : "");
                            result.latencyMs = System.currentTimeMillis() - startTime;
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            errorRef.set(e);
                            result.latencyMs = System.currentTimeMillis() - startTime;
                            latch.countDown();
                        }
                    });
        }).start();

        try {
            boolean done = latch.await(TIMEOUT_PER_PROMPT_MS, TimeUnit.MILLISECONDS);
            if (!done) {
                Log.w(TAG, "Timeout for prompt: " + prompt);
                return result;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result;
        }

        Exception err = errorRef.get();
        if (err != null) {
            Log.w(TAG, "LLM error for prompt '" + prompt + "': " + err.getMessage());
            return result;
        }

        String content = contentRef.get();
        String a2uiJson = WidgetProtocolValidator.extractA2UIJson(content);
        if (a2uiJson != null) {
            WidgetProtocolValidator.ValidationResult vr = WidgetProtocolValidator.validate(a2uiJson);
            if (!vr.valid) {
                String repaired = WidgetProtocolValidator.repair(a2uiJson);
                WidgetProtocolValidator.ValidationResult rr = WidgetProtocolValidator.validate(repaired);
                if (rr.valid) {
                    a2uiJson = repaired;
                }
            }
            WidgetProtocolValidator.ValidationResult finalVr = WidgetProtocolValidator.validate(a2uiJson);
            result.valid = finalVr.valid;
        }

        return result;
    }

    private static class TestResult {
        boolean valid = false;
        long latencyMs = 0;
    }
}
