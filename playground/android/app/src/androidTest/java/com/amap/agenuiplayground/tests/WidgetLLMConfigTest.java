package com.amap.agenuiplayground.tests;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetLLMConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * L3-P2.1: WidgetLLMConfig + WidgetPromptBuilder 单元测试
 *
 * WidgetLLMConfig:
 * - SharedPreferences 持久化读写
 * - 默认值（primary model/endpoint、fallback model/endpoint、API key）
 * - switchToFallback 切换
 * - isUsingFallback 状态
 *
 * WidgetPromptBuilder:
 * - SYSTEM_PROMPT 非空
 * - 包含 A2UI 协议要点
 * - 包含 few-shot 样本
 * - 包含组件 Catalog
 */
@RunWith(AndroidJUnit4.class)
public class WidgetLLMConfigTest {

    private Context ctx;
    private static final String PREFS_NAME = "a2ui_widget_prefs";
    private static final String KEY_API_KEY = "llm_api_key";
    private static final String KEY_MODEL = "llm_model";
    private static final String KEY_ENDPOINT = "llm_endpoint";

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearPrefs();
    }

    @After
    public void tearDown() {
        clearPrefs();
    }

    private void clearPrefs() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // ============================ WidgetLLMConfig ============================

    @Test
    public void test01_defaultApiKey() {
        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertNotNull("API key should not be null", config.getApiKey());
        assertTrue("API key should not be empty", config.getApiKey().length() > 0);
    }

    @Test
    public void test02_defaultModel_isPrimary() {
        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("Default model should be primary",
                WidgetLLMConfig.DEFAULT_PRIMARY_MODEL, config.getModel());
    }

    @Test
    public void test03_defaultEndpoint_isPrimary() {
        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("Default endpoint should be primary",
                WidgetLLMConfig.DEFAULT_PRIMARY_ENDPOINT, config.getEndpoint());
    }

    @Test
    public void test04_isUsingFallback_defaultFalse() {
        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertFalse("Should not be using fallback by default", config.isUsingFallback());
    }

    @Test
    public void test05_switchToFallback() {
        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertFalse("Before switch: not using fallback", config.isUsingFallback());

        config.switchToFallback();
        assertTrue("After switch: should be using fallback", config.isUsingFallback());
        assertEquals("Model should be fallback model",
                WidgetLLMConfig.DEFAULT_FALLBACK_MODEL, config.getModel());
        assertEquals("Endpoint should be fallback endpoint",
                WidgetLLMConfig.DEFAULT_FALLBACK_ENDPOINT, config.getEndpoint());
    }

    @Test
    public void test06_customApiKey_persisted() {
        // Save custom key
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_KEY, "sk-custom-test-key").apply();

        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("Should read custom key", "sk-custom-test-key", config.getApiKey());
    }

    @Test
    public void test07_customModel_persisted() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL, "custom-model-v1").apply();

        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("Should read custom model", "custom-model-v1", config.getModel());
    }

    @Test
    public void test08_customEndpoint_persisted() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ENDPOINT, "https://custom.api.com/v1").apply();

        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("Should read custom endpoint", "https://custom.api.com/v1", config.getEndpoint());
    }

    @Test
    public void test09_switchToFallback_overridesCustom() {
        // Set custom values
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_MODEL, "custom-model")
                .putString(KEY_ENDPOINT, "https://custom.api.com")
                .apply();

        WidgetLLMConfig config = new WidgetLLMConfig(ctx);
        assertEquals("custom-model", config.getModel());

        config.switchToFallback();
        assertEquals("After switch, should use fallback model",
                WidgetLLMConfig.DEFAULT_FALLBACK_MODEL, config.getModel());
        assertEquals("After switch, should use fallback endpoint",
                WidgetLLMConfig.DEFAULT_FALLBACK_ENDPOINT, config.getEndpoint());
    }

    @Test
    public void test10_primaryEndpoint_isBailian() {
        assertTrue("Primary endpoint should be dashscope",
                WidgetLLMConfig.DEFAULT_PRIMARY_ENDPOINT.contains("dashscope"));
    }

    @Test
    public void test11_fallbackEndpoint_isVolces() {
        assertTrue("Fallback endpoint should be volces/ark",
                WidgetLLMConfig.DEFAULT_FALLBACK_ENDPOINT.contains("volces.com") ||
                WidgetLLMConfig.DEFAULT_FALLBACK_ENDPOINT.contains("ark"));
    }

    @Test
    public void test12_primaryModel_isQwen() {
        assertTrue("Primary model should be qwen",
                WidgetLLMConfig.DEFAULT_PRIMARY_MODEL.toLowerCase().contains("qwen"));
    }

    @Test
    public void test13_fallbackModel_isDoubao() {
        assertTrue("Fallback model should be doubao",
                WidgetLLMConfig.DEFAULT_FALLBACK_MODEL.toLowerCase().contains("doubao"));
    }

    // ============================ WidgetPromptBuilder ============================

    @Test
    public void test14_systemPrompt_notNullNotEmpty() {
        assertNotNull("System prompt should not be null",
                com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT);
        assertTrue("System prompt should not be empty",
                com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT.length() > 100);
    }

    @Test
    public void test15_systemPrompt_containsA2UIProtocol() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should mention A2UI", prompt.contains("A2UI"));
        assertTrue("Should mention v0.9", prompt.contains("v0.9"));
        assertTrue("Should mention version", prompt.contains("version"));
        assertTrue("Should mention updateComponents", prompt.contains("updateComponents"));
    }

    @Test
    public void test16_systemPrompt_containsComponentCatalog() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should mention Column", prompt.contains("Column"));
        assertTrue("Should mention Row", prompt.contains("Row"));
        assertTrue("Should mention Text", prompt.contains("Text"));
        assertTrue("Should mention Card", prompt.contains("Card"));
        assertTrue("Should mention Button", prompt.contains("Button"));
    }

    @Test
    public void test17_systemPrompt_containsFewShot() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should have few-shot example", prompt.contains("示例"));
        assertTrue("Should mention weather/weather card in examples",
                prompt.contains("天气") || prompt.contains("weather"));
    }

    @Test
    public void test18_systemPrompt_containsOutputRequirements() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should mention ```a2ui code block",
                prompt.contains("```") && prompt.contains("a2ui"));
        assertTrue("Should mention surfaceId fixed",
                prompt.contains("surfaceId"));
        assertTrue("Should mention trailing comma prohibition",
                prompt.contains("trailing comma"));
    }

    @Test
    public void test19_systemPrompt_containsStyleHints() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should mention styles", prompt.contains("styles"));
        assertTrue("Should mention padding or margin",
                prompt.contains("padding") || prompt.contains("margin"));
    }

    @Test
    public void test20_systemPrompt_containsInteractionEvent() {
        String prompt = com.amap.agenuiplayground.widget.WidgetPromptBuilder.SYSTEM_PROMPT;
        assertTrue("Should mention functionCall", prompt.contains("functionCall"));
        assertTrue("Should mention action", prompt.contains("action"));
    }
}
