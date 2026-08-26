package com.amap.agenuiplayground.tests;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenui.render.surface.SurfaceManager;
import com.amap.agenuiplayground.A2UIPlaygroundActivity;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * L4: Widget Bitmap 截图回归测试
 *
 * 渲染三种模板为 Bitmap，验证：
 * - Bitmap 尺寸 < 1MB（Binder 限制）
 * - Bitmap 非空白（中心像素非纯白/纯黑）
 * - Bitmap 尺寸稳定性（同一模板多次渲染尺寸一致）
 * - Bitmap 宽度 = 300px（Widget 渲染宽度）
 *
 * 这是 instrumentation 级别截图测试，需要真实设备。
 * 作为 L2 WidgetRenderTest 的补充，更聚焦 Bitmap 输出质量。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetScreenshotTest {

    @Rule
    public ActivityScenarioRule<A2UIPlaygroundActivity> activityRule =
            new ActivityScenarioRule<>(A2UIPlaygroundActivity.class);

    private Activity activity;
    private SurfaceManager surfaceManager;
    private static final long TIMEOUT_MS = 10000;

    @Before
    public void setUp() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        activityRule.getScenario().onActivity(a -> {
            activity = a;
            if (!AGenUI.getInstance().isInitialized()) {
                AGenUI.getInstance().initialize(a.getApplicationContext());
            }
            surfaceManager = new SurfaceManager(a);
            latch.countDown();
        });
        assertTrue("Activity setup timeout", latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull("Activity should not be null", activity);
    }

    @After
    public void tearDown() {
        if (surfaceManager != null) {
            try {
                activityRule.getScenario().onActivity(a -> {
                    try {
                        surfaceManager.destroy();
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }

    // ============================
    // 辅助方法
    // ============================

    private String loadAsset(String path) throws Exception {
        InputStream is = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getAssets().open(path);
        byte[] buf = new byte[is.available()];
        is.read(buf);
        is.close();
        return new String(buf, StandardCharsets.UTF_8);
    }

    private String extractSurfaceId(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        return arr.getJSONObject(0).getString("surfaceId");
    }

    private Surface renderTemplate(String templateName) throws Exception {
        String json = loadAsset("widget_templates/" + templateName + ".json");
        json = json.replace("__SURFACE_ID__", "l4_" + templateName);
        String surfaceId = "l4_" + templateName;

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>();

        com.amap.agenui.render.surface.ISurfaceManagerListener listener =
            new com.amap.agenui.render.surface.ISurfaceManagerListener() {
                @Override
                public void onCreateSurface(Surface surface) {
                    if (surfaceId.equals(surface.getSurfaceId())) {
                        surfaceRef.set(surface);
                        latch.countDown();
                    }
                }
                @Override
                public void onDeleteSurface(Surface surface) {}
                @Override
                public void onReceiveActionEvent(String event) {}
                @Override
                public void onRootComponentUpdate(Surface surface,
                        java.util.Map<String, String> props) {}
                @Override
                public void onError(Surface surface, int code, String message) {}
                @Override
                public void onBlankCheckResult(Surface surface, boolean isBlank) {}
                @Override
                public void onComponentAppeared(Surface surface,
                        String parentComponentId, String parentType,
                        java.util.Map<String, Object> properties) {}
                @Override
                public com.amap.agenui.render.surface.SurfaceSize surfaceSize(String sid) {
                    return null;
                }
            };

        surfaceManager.addListener(listener);
        surfaceManager.beginTextStream();
        surfaceManager.receiveTextChunk(json);
        surfaceManager.endTextStream();

        boolean ok = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        surfaceManager.removeListener(listener);
        assertTrue("Surface creation timeout for " + templateName, ok);

        // Wait for main thread to complete component rendering
        final CountDownLatch barrier = new CountDownLatch(1);
        activityRule.getScenario().onActivity(a -> barrier.countDown());
        barrier.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Thread.sleep(200); // extra time for render

        return surfaceRef.get();
    }

    private Bitmap renderToBitmap(Surface surface) throws Exception {
        final AtomicReference<Bitmap> bmpRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        activityRule.getScenario().onActivity(a -> {
            try {
                View container = surface.getContainer();
                assertNotNull("Container should not be null", container);

                container.measure(
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());

                Bitmap bmp = Bitmap.createBitmap(
                    container.getMeasuredWidth(),
                    container.getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888
                );
                Canvas canvas = new Canvas(bmp);
                container.draw(canvas);
                bmpRef.set(bmp);
            } catch (Exception e) {
                // surface might not have container in this context
            }
            latch.countDown();
        });

        assertTrue("Bitmap render timeout", latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        return bmpRef.get();
    }

    private boolean isBitmapBlank(Bitmap bmp) {
        if (bmp == null) return true;
        int cx = bmp.getWidth() / 2;
        int cy = bmp.getHeight() / 2;
        int pixel = bmp.getPixel(cx, cy);
        // Check center pixel: not pure white and not pure black
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        boolean isWhite = (r > 250 && g > 250 && b > 250);
        boolean isBlack = (r < 5 && g < 5 && b < 5);
        return isWhite || isBlack;
    }

    // ============================
    // 测试用例
    // ============================

    @Test
    public void test01_weatherBitmap_under1MB() throws Exception {
        Surface surface = renderTemplate("weather");
        assertNotNull("Surface should be created", surface);
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Weather Bitmap should be under 1MB, got " +
            (bmp.getByteCount() / 1024) + "KB",
            bmp.getByteCount() < 1_000_000);
    }

    @Test
    public void test02_agendaBitmap_under1MB() throws Exception {
        Surface surface = renderTemplate("agenda");
        assertNotNull("Surface should be created", surface);
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Agenda Bitmap should be under 1MB, got " +
            (bmp.getByteCount() / 1024) + "KB",
            bmp.getByteCount() < 1_000_000);
    }

    @Test
    public void test03_todoBitmap_under1MB() throws Exception {
        Surface surface = renderTemplate("todo");
        assertNotNull("Surface should be created", surface);
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Todo Bitmap should be under 1MB, got " +
            (bmp.getByteCount() / 1024) + "KB",
            bmp.getByteCount() < 1_000_000);
    }

    @Test
    public void test04_weatherBitmap_notBlank() throws Exception {
        Surface surface = renderTemplate("weather");
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Weather Bitmap center should not be blank (white/black)", !isBitmapBlank(bmp));
    }

    @Test
    public void test05_agendaBitmap_notBlank() throws Exception {
        Surface surface = renderTemplate("agenda");
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Agenda Bitmap center should not be blank", !isBitmapBlank(bmp));
    }

    @Test
    public void test06_todoBitmap_notBlank() throws Exception {
        Surface surface = renderTemplate("todo");
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertTrue("Todo Bitmap center should not be blank", !isBitmapBlank(bmp));
    }

    @Test
    public void test07_weatherBitmap_width300() throws Exception {
        Surface surface = renderTemplate("weather");
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull("Bitmap should be rendered", bmp);
        assertEquals("Bitmap width should be 300px", 300, bmp.getWidth());
    }

    @Test
    public void test08_allTemplates_bitmapHeightPositive() throws Exception {
        for (String template : new String[]{"weather", "agenda", "todo"}) {
            Surface surface = renderTemplate(template);
            Bitmap bmp = renderToBitmap(surface);
            assertNotNull("Bitmap for " + template + " should be rendered", bmp);
            assertTrue("Bitmap for " + template + " height should be > 0, got " +
                bmp.getHeight(), bmp.getHeight() > 0);
        }
    }

    @Test
    public void test09_weatherBitmap_dimensionStability() throws Exception {
        // Render weather twice, dimensions should be stable
        Surface s1 = renderTemplate("weather");
        Bitmap b1 = renderToBitmap(s1);
        assertNotNull(b1);

        // Second render with different surfaceId
        String json = loadAsset("widget_templates/weather.json");
        json = json.replace("__SURFACE_ID__", "l4_stability");
        String surfaceId2 = "l4_stability";

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<Surface> ref2 = new AtomicReference<>();
        com.amap.agenui.render.surface.ISurfaceManagerListener listener2 =
            new com.amap.agenui.render.surface.ISurfaceManagerListener() {
                @Override
                public void onCreateSurface(Surface surface) {
                    if (surfaceId2.equals(surface.getSurfaceId())) {
                        ref2.set(surface);
                        latch2.countDown();
                    }
                }
                @Override
                public void onDeleteSurface(Surface surface) {}
                @Override
                public void onReceiveActionEvent(String event) {}
                @Override
                public void onRootComponentUpdate(Surface s,
                        java.util.Map<String, String> p) {}
                @Override
                public void onError(Surface s, int c, String m) {}
                @Override
                public void onBlankCheckResult(Surface s, boolean b) {}
                @Override
                public void onComponentAppeared(Surface s,
                        String p, String t,
                        java.util.Map<String, Object> pr) {}
                @Override
                public com.amap.agenui.render.surface.SurfaceSize surfaceSize(String sid) {
                    return null;
                }
            };

        surfaceManager.addListener(listener2);
        surfaceManager.beginTextStream();
        surfaceManager.receiveTextChunk(json);
        surfaceManager.endTextStream();
        assertTrue("Second surface timeout", latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        surfaceManager.removeListener(listener2);

        final CountDownLatch barrier = new CountDownLatch(1);
        activityRule.getScenario().onActivity(a -> barrier.countDown());
        barrier.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        Bitmap b2 = renderToBitmap(ref2.get());
        assertNotNull(b2);

        assertEquals("Width should be stable", b1.getWidth(), b2.getWidth());
        // Height might vary slightly due to async rendering, but should be close
        int heightDiff = Math.abs(b1.getHeight() - b2.getHeight());
        assertTrue("Height diff should be small (<50px), got " + heightDiff,
            heightDiff < 50);
    }

    @Test
    public void test10_weatherBitmap_pixelDiversity() throws Exception {
        // Check that the bitmap has diverse pixel colors (not all same color)
        Surface surface = renderTemplate("weather");
        Bitmap bmp = renderToBitmap(surface);
        assertNotNull(bmp);

        int samplePoints = 10;
        int differentColors = 0;
        int firstColor = bmp.getPixel(10, 10);
        for (int i = 1; i <= samplePoints; i++) {
            int x = (bmp.getWidth() * i) / (samplePoints + 1);
            int y = (bmp.getHeight() * i) / (samplePoints + 1);
            if (x >= bmp.getWidth()) x = bmp.getWidth() - 1;
            if (y >= bmp.getHeight()) y = bmp.getHeight() - 1;
            int pixel = bmp.getPixel(x, y);
            if (pixel != firstColor) {
                differentColors++;
            }
        }
        assertTrue("Bitmap should have pixel diversity, only " + differentColors +
            " different colors out of " + samplePoints + " samples",
            differentColors >= 2);
    }

    // Helper: assertEquals import
    private static void assertEquals(String msg, int expected, int actual) {
        org.junit.Assert.assertEquals(msg, expected, actual);
    }
}
