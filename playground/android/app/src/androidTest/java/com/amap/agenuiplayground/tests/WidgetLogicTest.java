package com.amap.agenuiplayground.tests;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.A2UIWidgetProvider;
import com.amap.agenuiplayground.widget.WidgetProtocolCache;
import com.amap.agenuiplayground.widget.WidgetProtocolTemplates;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * L3: Widget 逻辑层单元测试
 *
 * 覆盖范围：
 * - WidgetProtocolTemplates：模板加载、surfaceId 替换、轮换逻辑
 * - WidgetProtocolCache：SharedPreferences 持久化读写
 * - A2UIWidgetProvider：广播 Intent 解析与分发（ACTION_REFRESH / ACTION_SWITCH_TEMPLATE）
 *
 * 不需要 AGenUI 引擎或 SurfaceManager，纯逻辑层验证。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetLogicTest {

    private Context ctx;
    private static final int TEST_WIDGET_ID = 99999;
    private static final String PREFS_NAME = "a2ui_widget_prefs";

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 清理测试 widget 的 prefs
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    @After
    public void tearDown() {
        // 清理测试 widget 的 prefs
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // ============================
    // WT-L3-01~03: 模板加载
    // ============================

    @Test
    public void test01_loadWeatherTemplate_validJson() throws Exception {
        String json = WidgetProtocolTemplates.loadTemplate(ctx, "weather", "test_surf_01");
        assertNotNull("weather template should load", json);

        JSONArray arr = new JSONArray(json);
        assertEquals("template should have 3 messages", 3, arr.length());

        JSONObject createMsg = arr.getJSONObject(0);
        assertEquals("createSurface", createMsg.getString("type"));
        assertEquals("test_surf_01", createMsg.getString("surfaceId"));
        assertEquals(300, createMsg.getInt("width"));

        JSONObject updateMsg = arr.getJSONObject(1);
        assertEquals("updateComponents", updateMsg.getString("type"));
        assertEquals("test_surf_01", updateMsg.getString("surfaceId"));
        assertTrue("components array should exist", updateMsg.has("components"));
        JSONArray components = updateMsg.getJSONArray("components");
        assertTrue("should have at least 1 component", components.length() > 0);

        JSONObject root = components.getJSONObject(0);
        assertEquals("root", root.getString("id"));
        assertEquals("Card", root.getString("type"));
    }

    @Test
    public void test02_loadAgendaTemplate_validJson() throws Exception {
        String json = WidgetProtocolTemplates.loadTemplate(ctx, "agenda", "test_surf_02");
        assertNotNull("agenda template should load", json);

        JSONArray arr = new JSONArray(json);
        JSONObject createMsg = arr.getJSONObject(0);
        assertEquals("test_surf_02", createMsg.getString("surfaceId"));

        JSONArray components = arr.getJSONObject(1).getJSONArray("components");
        JSONObject root = components.getJSONObject(0);
        assertEquals("Card", root.getString("type"));
        // agenda should contain Row components for time slots
        String jsonStr = json.toString();
        assertTrue("agenda should contain Row", jsonStr.contains("\"Row\""));
    }

    @Test
    public void test03_loadTodoTemplate_validJson() throws Exception {
        String json = WidgetProtocolTemplates.loadTemplate(ctx, "todo", "test_surf_03");
        assertNotNull("todo template should load", json);

        JSONArray arr = new JSONArray(json);
        JSONArray components = arr.getJSONObject(1).getJSONArray("components");
        assertTrue("todo should have components", components.length() > 0);
        // todo template should contain checkbox-like markers
        assertTrue("todo should contain checkbox markers",
                json.contains("☐") || json.contains("✓"));
    }

    // ============================
    // WT-L3-04: 无效模板名 → null
    // ============================

    @Test
    public void test04_loadInvalidTemplate_returnsNull() {
        String json = WidgetProtocolTemplates.loadTemplate(ctx, "nonexistent_template", "surf");
        assertNull("invalid template should return null", json);
    }

    // ============================
    // WT-L3-05: surfaceId 替换
    // ============================

    @Test
    public void test05_surfaceIdReplacement() throws Exception {
        String customId = "my_custom_surface_id_12345";
        String json = WidgetProtocolTemplates.loadTemplate(ctx, "weather", customId);
        assertNotNull(json);
        assertFalse("should not contain placeholder", json.contains("__SURFACE_ID__"));
        assertTrue("should contain custom surfaceId", json.contains(customId));

        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject msg = arr.getJSONObject(i);
            if (msg.has("surfaceId")) {
                assertEquals(customId, msg.getString("surfaceId"));
            }
        }
    }

    // ============================
    // WT-L3-06: 模板轮换
    // ============================

    @Test
    public void test06_templateRotation() {
        // weather → agenda → todo → weather
        assertEquals("agenda", WidgetProtocolTemplates.getNextTemplate("weather"));
        assertEquals("todo", WidgetProtocolTemplates.getNextTemplate("agenda"));
        assertEquals("weather", WidgetProtocolTemplates.getNextTemplate("todo"));

        // unknown template → default (weather)
        assertEquals("weather", WidgetProtocolTemplates.getNextTemplate("unknown"));
        assertEquals("weather", WidgetProtocolTemplates.getNextTemplate(""));
        assertEquals("weather", WidgetProtocolTemplates.getNextTemplate(null));
    }

    @Test
    public void test07_allTemplatesRotateFully() {
        String current = WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        for (int i = 0; i < WidgetProtocolTemplates.AVAILABLE_TEMPLATES.length; i++) {
            current = WidgetProtocolTemplates.getNextTemplate(current);
        }
        // After full cycle, should be back to default
        assertEquals(WidgetProtocolTemplates.DEFAULT_TEMPLATE, current);
    }

    // ============================
    // WT-L3-08~10: WidgetProtocolCache 持久化
    // ============================

    @Test
    public void test08_cacheSaveAndLoadTemplate() {
        WidgetProtocolCache.saveTemplate(ctx, TEST_WIDGET_ID, "agenda");

        String loaded = WidgetProtocolCache.getTemplate(ctx, TEST_WIDGET_ID);
        assertEquals("agenda", loaded);
    }

    @Test
    public void test09_cacheDefaultTemplate() {
        // New widget ID should return default template
        String template = WidgetProtocolCache.getTemplate(ctx, 88888);
        assertEquals(WidgetProtocolTemplates.DEFAULT_TEMPLATE, template);
    }

    @Test
    public void test10_cacheProtocolPersistence() {
        String protocolJson = "[{\"type\":\"createSurface\",\"surfaceId\":\"s1\"}]";
        WidgetProtocolCache.saveProtocol(ctx, TEST_WIDGET_ID, protocolJson);

        String loaded = WidgetProtocolCache.getProtocol(ctx, TEST_WIDGET_ID);
        assertEquals(protocolJson, loaded);

        // New widget should have null protocol
        assertNull(WidgetProtocolCache.getProtocol(ctx, 77777));
    }

    @Test
    public void test11_cacheOverwriteTemplate() {
        WidgetProtocolCache.saveTemplate(ctx, TEST_WIDGET_ID, "weather");
        assertEquals("weather", WidgetProtocolCache.getTemplate(ctx, TEST_WIDGET_ID));

        WidgetProtocolCache.saveTemplate(ctx, TEST_WIDGET_ID, "todo");
        assertEquals("todo", WidgetProtocolCache.getTemplate(ctx, TEST_WIDGET_ID));
    }

    // ============================
    // WT-L3-12~14: A2UIWidgetProvider 广播 Intent 构建
    // ============================

    @Test
    public void test12_refreshIntent_hasCorrectAction() {
        Intent intent = new Intent(A2UIWidgetProvider.ACTION_REFRESH);
        intent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        assertEquals(A2UIWidgetProvider.ACTION_REFRESH, intent.getAction());
        assertEquals(TEST_WIDGET_ID,
                intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, -1));
    }

    @Test
    public void test13_switchTemplateIntent_hasCorrectExtras() {
        Intent intent = new Intent(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
        intent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);
        intent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, "todo");

        assertEquals(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE, intent.getAction());
        assertEquals(TEST_WIDGET_ID,
                intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, -1));
        assertEquals("todo",
                intent.getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE));
    }

    @Test
    public void test14_actionConstants_areUnique() {
        assertFalse(A2UIWidgetProvider.ACTION_REFRESH.equals(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE));
        assertTrue(A2UIWidgetProvider.ACTION_REFRESH.startsWith("com.amap.agenuiplayground.widget."));
        assertTrue(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE.startsWith("com.amap.agenuiplayground.widget."));
    }

    // ============================
    // WT-L3-15: 所有模板加载一致性
    // ============================

    @Test
    public void test15_allTemplatesLoadSuccessfully() {
        for (String templateName : WidgetProtocolTemplates.AVAILABLE_TEMPLATES) {
            String json = WidgetProtocolTemplates.loadTemplate(ctx, templateName, "surf_" + templateName);
            assertNotNull("Template " + templateName + " should load", json);
            assertFalse("Template " + templateName + " should not contain placeholder",
                    json.contains("__SURFACE_ID__"));
        }
    }

    // ============================
    // WT-L3-16: 模板 JSON 协议结构完整性
    // ============================

    @Test
    public void test16_allTemplatesHaveValidProtocolStructure() throws Exception {
        for (String templateName : WidgetProtocolTemplates.AVAILABLE_TEMPLATES) {
            String json = WidgetProtocolTemplates.loadTemplate(ctx, templateName, "struct_test");
            JSONArray arr = new JSONArray(json);

            // Message 0: createSurface
            JSONObject createMsg = arr.getJSONObject(0);
            assertEquals("createSurface", createMsg.getString("type"));
            assertTrue("should have surfaceId", createMsg.has("surfaceId"));
            assertTrue("should have width", createMsg.has("width"));
            assertTrue("should have catalogId", createMsg.has("catalogId"));

            // Message 1: updateComponents
            JSONObject updateMsg = arr.getJSONObject(1);
            assertEquals("updateComponents", updateMsg.getString("type"));
            assertTrue("should have components", updateMsg.has("components"));
            JSONArray comps = updateMsg.getJSONArray("components");
            assertTrue("should have root component", comps.length() > 0);

            // Message 2: updateDataModel
            JSONObject dataMsg = arr.getJSONObject(2);
            assertEquals("updateDataModel", dataMsg.getString("type"));
        }
    }

    // ============================
    // WT-L3-17: 多 Widget 实例缓存隔离
    // ============================

    @Test
    public void test17_multipleWidgetInstancesIsolatedCache() {
        int widgetA = 10001;
        int widgetB = 10002;

        WidgetProtocolCache.saveTemplate(ctx, widgetA, "weather");
        WidgetProtocolCache.saveTemplate(ctx, widgetB, "todo");

        assertEquals("weather", WidgetProtocolCache.getTemplate(ctx, widgetA));
        assertEquals("todo", WidgetProtocolCache.getTemplate(ctx, widgetB));

        // Change A should not affect B
        WidgetProtocolCache.saveTemplate(ctx, widgetA, "agenda");
        assertEquals("agenda", WidgetProtocolCache.getTemplate(ctx, widgetA));
        assertEquals("todo", WidgetProtocolCache.getTemplate(ctx, widgetB));

        // Cleanup
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("template_" + widgetA).remove("template_" + widgetB).apply();
    }
}
