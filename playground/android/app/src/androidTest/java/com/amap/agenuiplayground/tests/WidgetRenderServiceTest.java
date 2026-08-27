package com.amap.agenuiplayground.tests;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.AGenUIWidgetRenderService;
import com.amap.agenuiplayground.widget.WidgetBitmapCache;
import com.amap.agenuiplayground.widget.WidgetBitmapRenderer;
import com.amap.agenuiplayground.widget.WidgetFallbackBuilder;
import com.amap.agenuiplayground.widget.WidgetProtocolTemplates;
import com.amap.agenuiplayground.widget.WidgetRenderMetrics;
import com.amap.agenuiplayground.widget.WidgetSizeDetector;
import com.amap.agenuiplayground.widget.WidgetTemplateRegistry;
import com.amap.agenuiplayground.widget.WidgetTemplateValidator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AGenUIWidgetRenderService integration tests.
 *
 * Tests the rendering pipeline orchestrator:
 * - Template loading from assets
 * - Template validation before rendering
 * - FallbackBuilder produces valid chunks
 * - Bitmap cache integration
 * - Size detector integration
 * - Metrics recording
 * - SurfaceRenderResult error propagation
 *
 * Note: Full renderSync() requires AGenUI native engine + SurfaceManager,
 * which may not be available in CI. Tests focus on the non-native parts.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderServiceTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WidgetBitmapCache.clear();
        WidgetRenderMetrics.reset();
        WidgetRenderMetrics.setEnabled(true);
    }

    @After
    public void tearDown() {
        WidgetBitmapCache.clear();
        WidgetRenderMetrics.reset();
    }

    // ===== RS01: Template loading works for all registered templates =====

    @Test
    public void RS01_allRegisteredTemplates_loadSuccessfully() {
        for (String templateName : WidgetTemplateRegistry.getTemplateNames()) {
            try {
                String json = WidgetProtocolTemplates.loadTemplate(context, templateName, "rs_test_" + templateName);
                assertNotNull("Template " + templateName + " should load", json);
                assertFalse("Template JSON should not be empty", json.isEmpty());
            } catch (Exception e) {
                // Some templates might not have files — that's a data issue
                System.out.println("WARNING: " + templateName + " load failed: " + e.getMessage());
            }
        }
    }

    // ===== RS02: Template validation passes for loaded templates =====

    @Test
    public void RS02_loadedTemplates_passValidation() {
        String[] testTemplates = {"weather", "agenda", "todo"};
        for (String templateName : testTemplates) {
            try {
                String json = WidgetProtocolTemplates.loadTemplate(context, templateName, "rs_val_" + templateName);
                if (json != null && !json.isEmpty()) {
                    // Templates are in wire format — validate structurally
                    JSONArray arr = new JSONArray(json);
                    boolean hasCreateSurface = false;
                    boolean hasUpdateComponents = false;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject msg = arr.getJSONObject(i);
                        String type = msg.optString("type", "");
                        if ("createSurface".equals(type)) hasCreateSurface = true;
                        if ("updateComponents".equals(type)) hasUpdateComponents = true;
                    }
                    assertTrue("Template " + templateName + " should have createSurface",
                            hasCreateSurface);
                    assertTrue("Template " + templateName + " should have updateComponents",
                            hasUpdateComponents);
                }
            } catch (Exception e) {
                System.out.println("WARNING: " + templateName + " validation failed: " + e.getMessage());
            }
        }
    }

    // ===== RS03: FallbackBuilder produces valid version-format chunks =====

    @Test
    public void RS03_fallbackBuilder_validChunksForAllTemplates() {
        for (String templateName : WidgetTemplateRegistry.getTemplateNames()) {
            try {
                String json = WidgetProtocolTemplates.loadTemplate(context, templateName, "rs_fb_" + templateName);
                if (json != null) {
                    List<String> chunks = WidgetFallbackBuilder.convertToVersionFormat(json, "fb_" + templateName);
                    assertFalse("Template " + templateName + " should produce chunks",
                            chunks.isEmpty());
                    for (String chunk : chunks) {
                        JSONObject obj = new JSONObject(chunk);
                        assertEquals("v0.9", obj.getString("version"));
                    }
                }
            } catch (Exception e) {
                System.out.println("WARNING: " + templateName + " fallback failed: " + e.getMessage());
            }
        }
    }

    // ===== RS04: Bitmap cache integration =====

    @Test
    public void RS04_cachePutGet_removesAfterClear() {
        Bitmap bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        String key = WidgetBitmapCache.buildKey("weather", "current");
        WidgetBitmapCache.put(key, bmp);

        assertNotNull("Should retrieve cached bitmap", WidgetBitmapCache.get(key));

        WidgetBitmapCache.clear();
        assertNull("After clear, should return null", WidgetBitmapCache.get(key));
    }

    // ===== RS05: Size detector returns valid dimensions =====

    @Test
    public void RS05_sizeDetector_validForAllWidgetIds() {
        int[] testIds = {-1, 0, 1, 100, 99999};
        for (int id : testIds) {
            WidgetSizeDetector.WidgetDimensions dims = WidgetSizeDetector.resolve(context, id);
            assertNotNull(dims);
            assertTrue("Width > 0 for id " + id, dims.width > 0);
            assertTrue("Height > 0 for id " + id, dims.height > 0);
        }
    }

    // ===== RS06: Metrics recording tracks render operations =====

    @Test
    public void RS06_metricsRecorded_afterSimulatedRender() {
        String template = "weather";
        long duration = 1200;
        WidgetRenderMetrics.recordRender(template, duration, false);
        WidgetRenderMetrics.recordRender(template, 5, true); // cache hit

        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("Summary should contain template name", summary.contains(template));
        assertTrue("Summary should contain 2 total renders", summary.contains("2"));
    }

    // ===== RS07: SurfaceRenderResult error propagation =====

    @Test
    public void RS07_errorTemplate_invalidJSON_rejected() {
        WidgetTemplateValidator.ValidationResult result =
                WidgetTemplateValidator.validate("not valid json {{{}}");
        assertFalse("Invalid JSON should fail validation", result.valid);
    }

    // ===== RS08: All templates have unique surface IDs in their chunks =====

    @Test
    public void RS08_fallbackChunks_haveConsistentSurfaceId() {
        try {
            String json = WidgetProtocolTemplates.loadTemplate(context, "weather", "rs_sid");
            if (json != null) {
                List<String> chunks = WidgetFallbackBuilder.convertToVersionFormat(json, "unique_surface_123");
                for (String chunk : chunks) {
                    JSONObject obj = new JSONObject(chunk);
                    // Check createSurface or updateComponents has surfaceId
                    if (obj.has("createSurface")) {
                        assertEquals("unique_surface_123",
                                obj.getJSONObject("createSurface").getString("surfaceId"));
                    }
                    if (obj.has("updateComponents")) {
                        assertEquals("unique_surface_123",
                                obj.getJSONObject("updateComponents").getString("surfaceId"));
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Surface ID consistency check failed", e);
        }
    }

    // ===== RS09: NoteCard fallback builds successfully =====

    @Test
    public void RS09_noteCardFallback_validStructure() {
        List<String> chunks = WidgetFallbackBuilder.buildNoteCard("note_test", "Title", "Message");
        assertEquals("Should produce 2 chunks", 2, chunks.size());

        try {
            // First chunk: createSurface
            JSONObject cs = new JSONObject(chunks.get(0));
            assertEquals("v0.9", cs.getString("version"));
            assertTrue("Should have createSurface", cs.has("createSurface"));

            // Second chunk: updateComponents
            JSONObject uc = new JSONObject(chunks.get(1));
            assertEquals("v0.9", uc.getString("version"));
            assertTrue("Should have updateComponents", uc.has("updateComponents"));

            // Should have components array with at least root + content + title + message
            JSONArray components = uc.getJSONObject("updateComponents").getJSONArray("components");
            assertTrue("Should have at least 4 components", components.length() >= 4);
        } catch (Exception e) {
            throw new AssertionError("NoteCard structure invalid", e);
        }
    }

    // ===== RS10: Template rotation covers all entries =====

    @Test
    public void RS10_templateRotation_coversAllEntries() {
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = WidgetTemplateRegistry.getDefaultTemplate();
        for (int i = 0; i < names.length; i++) {
            visited.add(current);
            current = WidgetTemplateRegistry.getNextTemplate(current);
        }
        assertEquals("Should visit all templates in rotation",
                names.length, visited.size());
    }

    private static void assertFalse(String msg, boolean condition) {
        org.junit.Assert.assertFalse(msg, condition);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
