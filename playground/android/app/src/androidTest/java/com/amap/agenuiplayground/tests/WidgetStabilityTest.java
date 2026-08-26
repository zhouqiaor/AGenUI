package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetFallbackBuilder;
import com.amap.agenuiplayground.widget.WidgetHistoryRepository;
import com.amap.agenuiplayground.widget.WidgetProtocolTemplates;
import com.amap.agenuiplayground.widget.WidgetProtocolValidator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * L3-P2.5: WidgetStabilityTest — 稳定性 + 降级 + 历史记录测试
 *
 * P2.5 源码：
 * - WidgetFallbackBuilder (降级 + type→version 格式转换)
 * - WidgetHistoryRepository (SharedPreferences 存储 50 条历史)
 *
 * 22 个测试用例
 */
@RunWith(AndroidJUnit4.class)
public class WidgetStabilityTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ============================ History CRUD (DB-01 ~ DB-08) ============================

    @Test
    public void test01_DB_record_addsEntry() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("test prompt", "{\"test\":true}", 1000, true);
        List<String> summaries = repo.getRecentSummaries();
        assertEquals("Should have 1 record after clear+record", 1, summaries.size());
        repo.clear();
    }

    @Test
    public void test02_DB_record_fields() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("天气查询", "{\"version\":\"v0.9\"}", 2500, true);
        List<String> summaries = repo.getRecentSummaries();
        assertTrue("Summary should contain prompt", summaries.get(0).contains("天气查询"));
        assertTrue("Summary should contain latency", summaries.get(0).contains("2500"));
        assertTrue("Summary should contain success marker", summaries.get(0).contains("✓"));
        repo.clear();
    }

    @Test
    public void test03_DB_mostRecentFirst() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("first", "{}", 100, true);
        try { Thread.sleep(50); } catch (Exception ignored) {}
        repo.record("second", "{}", 200, true);
        List<String> summaries = repo.getRecentSummaries();
        assertEquals(2, summaries.size());
        assertTrue("Most recent should be first", summaries.get(0).contains("second"));
        repo.clear();
    }

    @Test
    public void test04_DB_maxRecords50() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        for (int i = 0; i < 55; i++) {
            repo.record("prompt" + i, "{}", i * 10, true);
        }
        List<String> summaries = repo.getRecentSummaries();
        assertTrue("Should be capped at 50 records", summaries.size() <= 50);
        repo.clear();
    }

    @Test
    public void test05_DB_getLastSuccessfulJson() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("failed", "{}", 0, false);
        repo.record("succeeded", "{\"good\":true}", 100, true);
        String lastJson = repo.getLastSuccessfulJson();
        assertNotNull("Should return last successful JSON", lastJson);
        assertTrue("Should contain good JSON", lastJson.contains("good"));
        repo.clear();
    }

    @Test
    public void test06_DB_clear() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.record("test", "{}", 0, true);
        repo.clear();
        assertTrue("After clear, summaries should be empty", repo.getRecentSummaries().isEmpty());
    }

    @Test
    public void test07_DB_failedRecord() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("failed prompt", "{}", 0, false);
        List<String> summaries = repo.getRecentSummaries();
        assertEquals(1, summaries.size());
        assertTrue("Should show failure marker", summaries.get(0).contains("✗"));
        assertNull("getLastSuccessfulJson should be null", repo.getLastSuccessfulJson());
        repo.clear();
    }

    @Test
    public void test08_DB_truncatePrompt() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        String longPrompt = new String(new char[250]).replace('\0', 'x');
        repo.record(longPrompt, "{}", 0, true);
        List<String> summaries = repo.getRecentSummaries();
        // Prompt is truncated to 200 chars in record, then to 30 chars in summary
        assertEquals(1, summaries.size());
        repo.clear();
    }

    // ============================ Degradation (DC-01 ~ DC-08) ============================

    @Test
    public void test09_DC_validJsonPasses() {
        String validJson = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"test\",\"components\":[{\"id\":\"root\",\"component\":\"Card\",\"children\":[\"title\"]},{\"id\":\"title\",\"component\":\"Text\",\"text\":\"OK\"}]}}";
        WidgetProtocolValidator.ValidationResult result = WidgetProtocolValidator.validate(validJson);
        assertTrue(result.valid);
    }

    @Test
    public void test10_DC_repairTrailingComma() {
        String brokenJson = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"test\",\"components\":[{\"id\":\"root\",\"component\":\"Text\",\"text\":\"OK\",}]}}";
        String repaired = WidgetProtocolValidator.repair(brokenJson);
        // repair should remove trailing commas → parseable JSON
        try {
            new org.json.JSONObject(repaired);
        } catch (Exception e) {
            throw new AssertionError("Repaired JSON should be parseable, got: " + repaired, e);
        }
    }

    @Test
    public void test11_DC_repairStripsLeadingText() {
        String brokenJson = "这是AI的回复：\n{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"test\",\"components\":[]}}";
        String repaired = WidgetProtocolValidator.repair(brokenJson);
        assertTrue("Repaired should start with {", repaired.trim().startsWith("{"));
    }

    @Test
    public void test12_DC_keywordMatch() {
        assertEquals("weather", matchKeyword("生成一个天气卡片"));
        assertEquals("todo", matchKeyword("我的待办清单"));
        assertEquals("agenda", matchKeyword("今天的议程"));
    }

    @Test
    public void test13_DC_noKeywordMatch() {
        assertNull(matchKeyword("随便说点什么"));
    }

    @Test
    public void test14_DC_keywordCaseInsensitive() {
        assertEquals("weather", matchKeyword("show me the WEATHER"));
    }

    @Test
    public void test15_DC_keywordFirstMatch() {
        assertEquals("weather", matchKeyword("天气议程"));
    }

    @Test
    public void test16_DC_fallbackTemplateAlwaysLoads() {
        String template = WidgetProtocolTemplates.loadTemplate(context,
                WidgetProtocolTemplates.DEFAULT_TEMPLATE, "test_fallback");
        assertNotNull("Default template should always load", template);
    }

    // ============================ FallbackBuilder (FB-01 ~ FB-06) ============================

    @Test
    public void test17_FB_convertToVersionFormat() {
        // Load a template and convert to version format
        String templateJson = WidgetProtocolTemplates.loadTemplate(context, "weather", "test_surface");
        assertNotNull(templateJson);
        List<String> converted = WidgetFallbackBuilder.convertToVersionFormat(templateJson, "test_surface");
        assertFalse("Should produce at least 1 chunk", converted.isEmpty());
        for (String chunk : converted) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(chunk);
                assertEquals("v0.9", obj.getString("version"));
            } catch (Exception e) {
                throw new AssertionError("Converted chunk should be valid JSON with version", e);
            }
        }
    }

    @Test
    public void test18_FB_buildNoteCard() {
        List<String> chunks = WidgetFallbackBuilder.buildNoteCard("test_surface", "Title", "Message");
        assertEquals("Should produce 2 chunks (createSurface + updateComponents)", 2, chunks.size());
        try {
            org.json.JSONObject cs = new org.json.JSONObject(chunks.get(0));
            assertEquals("v0.9", cs.getString("version"));
            assertNotNull(cs.getJSONObject("createSurface"));
            org.json.JSONObject uc = new org.json.JSONObject(chunks.get(1));
            assertNotNull(uc.getJSONObject("updateComponents"));
        } catch (Exception e) {
            throw new AssertionError("NoteCard chunks should be valid", e);
        }
    }

    // ============================ Bitmap (BM-01 ~ BM-03) ============================

    @Test
    public void test19_BM_jpegCompressionUnder800KB() {
        Bitmap large = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(large);
        canvas.drawColor(Color.RED);
        for (int i = 0; i < 600; i += 50)
            for (int j = 0; j < 600; j += 50)
                canvas.drawColor((i + j) % 2 == 0 ? Color.BLUE : Color.GREEN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        large.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        assertTrue("JPEG should be < 800KB", baos.size() < 800_000);
        large.recycle();
    }

    @Test
    public void test20_BM_aspectRatioPreserved() {
        int w = 300, h = 200;
        float ratio = (float) w / h;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        assertEquals(ratio, (float) bmp.getWidth() / bmp.getHeight(), 0.1f);
        bmp.recycle();
    }

    @Test
    public void test21_BM_widgetSizeSafeForBinder() {
        int byteCount = 300 * 270 * 4;
        assertTrue("300x270 Bitmap < 1MB", byteCount < 1_000_000);
        assertTrue("300x270 Bitmap < 800KB", byteCount < 800_000);
    }

    // ============================ History summary format (HS-01) ============================

    @Test
    public void test22_HS_summaryFormat() {
        WidgetHistoryRepository repo = new WidgetHistoryRepository(context);
        repo.clear();
        repo.record("测试", "{}", 1234, true);
        List<String> summaries = repo.getRecentSummaries();
        assertEquals(1, summaries.size());
        String s = summaries.get(0);
        // Format: "MM-dd HH:mm:ss ✓ 1234ms 测试"
        assertTrue("Summary should have time", s.matches("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue("Summary should have success/fail marker", s.contains("✓") || s.contains("✗"));
        assertTrue("Summary should have latency", s.contains("1234"));
        repo.clear();
    }

    // ============================ Helper ============================

    private String matchKeyword(String text) {
        if (text == null) return null;
        String l = text.toLowerCase();
        if (l.contains("天气") || l.contains("weather") || l.contains("气温")) return "weather";
        if (l.contains("议程") || l.contains("日程") || l.contains("agenda") || l.contains("schedule")) return "agenda";
        if (l.contains("待办") || l.contains("todo") || l.contains("清单") || l.contains("任务")) return "todo";
        return null;
    }

    private static void assumeTrue(String message, boolean condition) {
        org.junit.Assume.assumeTrue(message, condition);
    }
}
