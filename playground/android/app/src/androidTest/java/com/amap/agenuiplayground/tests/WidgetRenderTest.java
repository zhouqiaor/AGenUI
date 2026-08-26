package com.amap.agenuiplayground.tests;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenui.render.surface.Surface;
import com.amap.agenuiplayground.base.AGenUIBaseTest;
import com.amap.agenuiplayground.widget.WidgetProtocolTemplates;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Widget 渲染单元测试 — 复用 AGenUIBaseTest 框架。
 *
 * 将 widget 模板的 wire format（[{"type":"createSurface",...}]）
 * 转换为 AGenUI SDK 期望的 envelope format（{"version":"v0.9","createSurface":{...}}），
 * 然后用 sendAndWaitForRender 发送。
 *
 * 验收标准对应 SPEC-Widget-AutomatedTesting.md 的 WT-01 ~ WT-06。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderTest extends AGenUIBaseTest {

    private static final String TEMPLATES_DIR = "widget_templates";
    private static final String CATALOG_ID =
            "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json";

    // ==================== 辅助方法 ====================

    /**
     * 从 assets/widget_templates/ 加载模板 JSON（wire format）。
     */
    private String loadRawTemplate(String templateName) throws Exception {
        String fileName = TEMPLATES_DIR + "/" + templateName + ".json";
        InputStream is = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        int bytesRead = is.read(buffer);
        is.close();
        return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
    }

    /**
     * 将 widget 模板（wire format JSONArray）转换为 AGenUI fixture envelope format。
     *
     * Wire format:
     *   [{"type":"createSurface","surfaceId":"X","catalogId":"Y","width":300},
     *    {"type":"updateComponents","surfaceId":"X","components":[...]},
     *    {"type":"updateDataModel","surfaceId":"X","value":{}}]
     *
     * Envelope format (fixture-compatible):
     *   {"version":"v0.9","createSurface":{"surfaceId":"X","catalogId":"Y"}}
     *   {"version":"v0.9","updateComponents":{"surfaceId":"X","components":[...]}}
     *
     * Note: updateDataModel is omitted (not needed for render test).
     * Note: catalogId in widget templates uses catalogs/basic/catalog.json path,
     *       but SDK expects basic_catalog.json — we use the widget template's path
     *       and let SDK resolve it. If it fails, we fall back to the fixture catalog.
     */
    private String convertToEnvelopeFormat(String templateJson, String surfaceId) throws Exception {
        templateJson = templateJson.replace("__SURFACE_ID__", surfaceId);
        JSONArray wireMessages = new JSONArray(templateJson);

        // Build fixture-style messages array
        JSONArray envelopeMessages = new JSONArray();
        for (int i = 0; i < wireMessages.length(); i++) {
            JSONObject wireMsg = wireMessages.getJSONObject(i);
            String type = wireMsg.getString("type");

            JSONObject envelope = new JSONObject();
            envelope.put("version", "v0.9");

            if ("createSurface".equals(type)) {
                JSONObject createSurface = new JSONObject();
                createSurface.put("surfaceId", wireMsg.getString("surfaceId"));
                // Use the catalogId from the template; fallback to known catalog
                String catalogId = wireMsg.optString("catalogId", CATALOG_ID);
                createSurface.put("catalogId", catalogId);
                envelope.put("createSurface", createSurface);
                envelopeMessages.put(envelope);
            } else if ("updateComponents".equals(type)) {
                JSONObject updateComponents = new JSONObject();
                updateComponents.put("surfaceId", wireMsg.getString("surfaceId"));
                updateComponents.put("components", wireMsg.getJSONArray("components"));
                envelope.put("updateComponents", updateComponents);
                envelopeMessages.put(envelope);
            }
            // Skip updateDataModel — not needed for rendering
        }

        // Return as a single JSON array string (sendAndWaitForRender handles it)
        return envelopeMessages.toString();
    }

    /**
     * 渲染指定模板并返回 Surface。
     * Uses sendAndWaitForRender to ensure components are fully populated.
     */
    private Surface renderTemplate(String templateName) throws Exception {
        String surfaceId = "test_" + templateName + "_" + System.currentTimeMillis();
        String rawJson = loadRawTemplate(templateName);
        String envelopeJson = convertToEnvelopeFormat(rawJson, surfaceId);

        // Use sendMessagesAndWaitForSurface (per-message begin/receive/end)
        JSONArray messages = new JSONArray(envelopeJson);
        Surface surface = sendMessagesAndWaitForSurface(messages, surfaceId);

        // Poll for component count stability (like sendAndWaitForRender)
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        int stableCount = 0;
        int lastCount = -1;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            final int[] count = {-1};
            CountDownLatch barrier = new CountDownLatch(1);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                count[0] = surface.getComponentCount();
                barrier.countDown();
            });
            barrier.await(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (count[0] > 0 && count[0] == lastCount) {
                stableCount++;
                if (stableCount >= 3) break;
            } else {
                stableCount = 0;
                lastCount = count[0];
            }
        }

        return surface;
    }

    /**
     * 将 Surface container measure + draw 到 Bitmap。
     */
    private Bitmap renderToBitmap(Surface surface) {
        final Bitmap[] result = {null};
        runOnActivity(activity -> {
            View container = surface.getContainer();
            assertNotNull("Surface container should not be null", container);

            int widthSpec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            container.measure(widthSpec, heightSpec);

            int w = container.getMeasuredWidth();
            int h = container.getMeasuredHeight();
            if (h <= 0) h = 200;

            container.layout(0, 0, w, h);

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            container.draw(canvas);
            result[0] = bitmap;
        });
        return result[0];
    }

    // ==================== WT-01: Weather 模板渲染 ====================

    @Test
    public void WT01_weatherTemplate_rendersSuccessfully() throws Exception {
        Surface surface = renderTemplate("weather");

        assertNotNull("Weather surface should be created", surface);
        assertNotNull("Surface ID should be set", surface.getSurfaceId());
        assertTrue("Surface should have components (count=" +
                surface.getComponentCount() + ")",
                surface.getComponentCount() > 0);
    }

    // ==================== WT-02: Agenda 模板渲染 ====================

    @Test
    public void WT02_agendaTemplate_rendersSuccessfully() throws Exception {
        Surface surface = renderTemplate("agenda");

        assertNotNull("Agenda surface should be created", surface);
        assertTrue("Surface should have components (count=" +
                surface.getComponentCount() + ")",
                surface.getComponentCount() > 0);
    }

    // ==================== WT-03: Todo 模板渲染 ====================

    @Test
    public void WT03_todoTemplate_rendersSuccessfully() throws Exception {
        Surface surface = renderTemplate("todo");

        assertNotNull("Todo surface should be created", surface);
        assertTrue("Surface should have components (count=" +
                surface.getComponentCount() + ")",
                surface.getComponentCount() > 0);
    }

    // ==================== WT-04: Bitmap 尺寸 < 1MB ====================

    @Test
    public void WT04_weatherBitmap_underOneMB() throws Exception {
        Surface surface = renderTemplate("weather");
        Bitmap bitmap = renderToBitmap(surface);

        assertNotNull("Bitmap should be generated", bitmap);
        assertTrue("Bitmap byte count (" + bitmap.getByteCount() +
                ") must be under 1MB for Binder transfer",
                bitmap.getByteCount() < 1_000_000);
    }

    @Test
    public void WT04_agendaBitmap_underOneMB() throws Exception {
        Surface surface = renderTemplate("agenda");
        Bitmap bitmap = renderToBitmap(surface);

        assertNotNull("Bitmap should be generated", bitmap);
        assertTrue("Bitmap byte count (" + bitmap.getByteCount() +
                ") must be under 1MB",
                bitmap.getByteCount() < 1_000_000);
    }

    @Test
    public void WT04_todoBitmap_underOneMB() throws Exception {
        Surface surface = renderTemplate("todo");
        Bitmap bitmap = renderToBitmap(surface);

        assertNotNull("Bitmap should be generated", bitmap);
        assertTrue("Bitmap byte count (" + bitmap.getByteCount() +
                ") must be under 1MB",
                bitmap.getByteCount() < 1_000_000);
    }

    // ==================== WT-05: Bitmap 非空白 ====================

    @Test
    public void WT05_weatherBitmap_notBlank() throws Exception {
        Surface surface = renderTemplate("weather");
        Bitmap bitmap = renderToBitmap(surface);

        assertNotNull("Bitmap should be generated", bitmap);

        // 检查多个采样点是否有非纯白/纯黑内容
        boolean hasContent = false;
        int samplePoints = 0;
        for (int x = 10; x < bitmap.getWidth(); x += Math.max(1, bitmap.getWidth() / 5)) {
            for (int y = 10; y < bitmap.getHeight(); y += Math.max(1, bitmap.getHeight() / 5)) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                if (!(r > 250 && g > 250 && b > 250) && !(r < 5 && g < 5 && b < 5)) {
                    hasContent = true;
                    break;
                }
                samplePoints++;
            }
            if (hasContent) break;
        }
        assertTrue("Bitmap should have non-blank content (checked " +
                samplePoints + " sample points)", hasContent);
    }

    // ==================== WT-06: 模板轮换顺序 ====================

    @Test
    public void WT06_getNextTemplate_rotation() {
        // 测试模板轮换顺序：weather → agenda → todo → weather
        assertEquals("weather → agenda",
                "agenda", WidgetProtocolTemplates.getNextTemplate("weather"));
        assertEquals("agenda → todo",
                "todo", WidgetProtocolTemplates.getNextTemplate("agenda"));
        assertEquals("todo → weather",
                "weather", WidgetProtocolTemplates.getNextTemplate("todo"));
        assertEquals("unknown → weather (default)",
                "weather", WidgetProtocolTemplates.getNextTemplate("nonexistent"));
    }
}
