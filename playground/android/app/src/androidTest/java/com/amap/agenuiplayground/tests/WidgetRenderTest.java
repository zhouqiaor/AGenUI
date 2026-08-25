package com.amap.agenuiplayground.tests;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenui.render.surface.Surface;
import com.amap.agenuiplayground.base.AGenUIBaseTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Widget 渲染单元测试 — 复用 AGenUIBaseTest 框架。
 *
 * <p>测试 A2UI Widget 三种模板的渲染管线：
 * <ol>
 *   <li>从 assets/widget_templates/ 加载模板 JSON</li>
 *   <li>逐条发送协议消息（sendMessagesAndWaitForSurface）</li>
 *   <li>验证 Surface 创建、组件数量、Bitmap 渲染</li>
 * </ol>
 *
 * <p>验收标准对应 SPEC-Widget-AutomatedTesting.md 的 WT-01 ~ WT-06。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderTest extends AGenUIBaseTest {

    private static final String TAG = "WidgetRenderTest";
    private static final String TEMPLATES_DIR = "widget_templates";
    private static final String[] TEMPLATES = {"weather", "agenda", "todo"};

    // ==================== 辅助方法 ====================

    /**
     * 从 assets/widget_templates/ 加载模板 JSON 并替换 surfaceId 占位符。
     */
    private String[] loadTemplateMessages(String templateName, String surfaceId) throws Exception {
        String fileName = TEMPLATES_DIR + "/" + templateName + ".json";
        InputStream is = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        int bytesRead = is.read(buffer);
        is.close();
        String json = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        json = json.replace("__SURFACE_ID__", surfaceId);

        // 解析为 JSONArray，返回逐条消息字符串数组
        JSONArray arr = new JSONArray(json);
        String[] messages = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            messages[i] = arr.getJSONObject(i).toString();
        }
        return messages;
    }

    /**
     * 加载模板并提取 surfaceId（从 createSurface 消息中）。
     */
    private String getTemplateSurfaceId(String templateName) throws Exception {
        String fileName = TEMPLATES_DIR + "/" + templateName + ".json";
        InputStream is = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        int bytesRead = is.read(buffer);
        is.close();
        String json = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray(json);
        JSONObject createMsg = arr.getJSONObject(0);
        return createMsg.getString("surfaceId").replace("__SURFACE_ID__",
                "test_" + templateName + "_" + System.currentTimeMillis());
    }

    /**
     * 渲染指定模板并返回 Surface。
     */
    private Surface renderTemplate(String templateName) throws Exception {
        String surfaceId = "test_" + templateName + "_" + System.currentTimeMillis();
        String[] messages = loadTemplateMessages(templateName, surfaceId);

        // 构造 JSONArray 供 sendMessagesAndWaitForSurface
        JSONArray msgArray = new JSONArray();
        for (String msg : messages) {
            msgArray.put(new JSONObject(msg));
        }

        Surface surface = sendMessagesAndWaitForSurface(msgArray, surfaceId);
        waitForMainThread();
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
        assertTrue("Surface should have components",
                surface.getComponentCount() > 0);
    }

    // ==================== WT-02: Agenda 模板渲染 ====================

    @Test
    public void WT02_agendaTemplate_rendersSuccessfully() throws Exception {
        Surface surface = renderTemplate("agenda");

        assertNotNull("Agenda surface should be created", surface);
        assertTrue("Surface should have components",
                surface.getComponentCount() > 0);
    }

    // ==================== WT-03: Todo 模板渲染 ====================

    @Test
    public void WT03_todoTemplate_rendersSuccessfully() throws Exception {
        Surface surface = renderTemplate("todo");

        assertNotNull("Todo surface should be created", surface);
        assertTrue("Surface should have components",
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

        // 检查中心像素不是纯白（说明有渲染内容）
        int centerPixel = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        int red = Color.red(centerPixel);
        int green = Color.green(centerPixel);
        int blue = Color.blue(centerPixel);
        boolean isPureWhite = red > 250 && green > 250 && blue > 250;
        boolean isPureBlack = red < 5 && green < 5 && blue < 5;

        // 纯白是可以接受的（背景色），但至少应该有一些像素不是纯白
        // 检查多个采样点
        boolean hasContent = false;
        int samplePoints = 0;
        for (int x = 10; x < bitmap.getWidth(); x += bitmap.getWidth() / 5) {
            for (int y = 10; y < bitmap.getHeight(); y += bitmap.getHeight() / 5) {
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
                "agenda", com.amap.agenuiplayground.widget.WidgetProtocolTemplates
                        .getNextTemplate("weather"));
        assertEquals("agenda → todo",
                "todo", com.amap.agenuiplayground.widget.WidgetProtocolTemplates
                        .getNextTemplate("agenda"));
        assertEquals("todo → weather",
                "weather", com.amap.agenuiplayground.widget.WidgetProtocolTemplates
                        .getNextTemplate("todo"));
        assertEquals("unknown → weather (default)",
                "weather", com.amap.agenuiplayground.widget.WidgetProtocolTemplates
                        .getNextTemplate("nonexistent"));
    }
}
