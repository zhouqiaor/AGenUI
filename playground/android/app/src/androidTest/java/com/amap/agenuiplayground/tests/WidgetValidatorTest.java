package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetProtocolValidator;
import com.amap.agenuiplayground.widget.WidgetProtocolValidator.ValidationResult;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * L3-P2.1: WidgetProtocolValidator 单元测试
 *
 * 测试 P2.1 新增的三级校验 + JSON 提取/修复逻辑：
 * - extractA2UIJson：从 LLM 输出中提取 A2UI JSON（```a2ui / ```json / raw）
 * - validate：三级校验（JSON 语法 → 协议结构 → 组件白名单）
 * - repair：JSON 修复（trailing comma、多余文本、控制字符）
 * - validateComponentTypes：组件类型白名单检查
 */
@RunWith(AndroidJUnit4.class)
public class WidgetValidatorTest {

    // ============================ extractA2UIJson ============================

    @Test
    public void test01_extractFromA2uiCodeBlock() {
        String llmOutput = "这是 AI 生成的组件：\n```a2ui\n" + validEnvelopeJson() + "\n```\n好的。";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNotNull("Should extract from ```a2ui block", extracted);
        ValidationResult result = WidgetProtocolValidator.validate(extracted);
        assertTrue("Extracted JSON should be valid", result.valid);
    }

    @Test
    public void test02_extractFromJsonCodeBlock() {
        String llmOutput = "```json\n" + validEnvelopeJson() + "\n```";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNotNull("Should extract from ```json block", extracted);
        assertTrue(WidgetProtocolValidator.validate(extracted).valid);
    }

    @Test
    public void test03_extractRawJson() {
        String llmOutput = validEnvelopeJson();
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNotNull("Should extract raw JSON", extracted);
        assertTrue(WidgetProtocolValidator.validate(extracted).valid);
    }

    @Test
    public void test04_extractWithTrailingText() {
        String llmOutput = validEnvelopeJson() + " 这是多余的解释文字";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNotNull("Should extract JSON even with trailing text", extracted);
        assertTrue(WidgetProtocolValidator.validate(extracted).valid);
    }

    @Test
    public void test05_extractNullInput() {
        assertNull(WidgetProtocolValidator.extractA2UIJson(null));
        assertNull(WidgetProtocolValidator.extractA2UIJson(""));
    }

    @Test
    public void test06_extractNoJson() {
        String llmOutput = "抱歉，我无法生成组件。";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNull("Should return null when no JSON found", extracted);
    }

    // ============================ validate ============================

    @Test
    public void test07_validJson_passes() {
        ValidationResult result = WidgetProtocolValidator.validate(validEnvelopeJson());
        assertTrue("Valid JSON should pass", result.valid);
        assertEquals("Should have component count > 0", true, result.componentCount > 0);
        assertTrue("Should have surfaceId", result.hasSurfaceId);
        assertTrue("Should have root", result.hasRoot);
    }

    @Test
    public void test08_nullJson_fails() {
        ValidationResult result = WidgetProtocolValidator.validate(null);
        assertFalse("Null JSON should fail", result.valid);
    }

    @Test
    public void test09_emptyJson_fails() {
        ValidationResult result = WidgetProtocolValidator.validate("");
        assertFalse("Empty JSON should fail", result.valid);
    }

    @Test
    public void test10_missingVersion_fails() {
        String json = "{\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\"}]}}";
        ValidationResult result = WidgetProtocolValidator.validate(json);
        assertFalse("Missing version should fail", result.valid);
        assertTrue(result.error.contains("version"));
    }

    @Test
    public void test11_missingUpdateComponents_fails() {
        String json = "{\"version\":\"v0.9\"}";
        ValidationResult result = WidgetProtocolValidator.validate(json);
        assertFalse("Missing updateComponents should fail", result.valid);
        assertTrue(result.error.contains("updateComponents"));
    }

    @Test
    public void test12_missingSurfaceId_fails() {
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"components\":[{\"id\":\"root\",\"component\":\"Column\"}]}}";
        ValidationResult result = WidgetProtocolValidator.validate(json);
        assertFalse("Missing surfaceId should fail", result.valid);
        assertTrue(result.error.contains("surfaceId"));
    }

    @Test
    public void test13_emptyComponents_fails() {
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[]}}";
        ValidationResult result = WidgetProtocolValidator.validate(json);
        assertFalse("Empty components should fail", result.valid);
    }

    @Test
    public void test14_missingRoot_fails() {
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"notroot\",\"component\":\"Text\",\"text\":\"hi\"}]}}";
        ValidationResult result = WidgetProtocolValidator.validate(json);
        assertFalse("Missing root component should fail", result.valid);
        assertTrue(result.error.contains("root"));
    }

    @Test
    public void test15_invalidJson_fails() {
        ValidationResult result = WidgetProtocolValidator.validate("{not valid json}");
        assertFalse("Invalid JSON should fail", result.valid);
    }

    // ============================ repair ============================

    @Test
    public void test16_repairTrailingComma() {
        String broken = "{\"version\":\"v0.9\",,\"updateComponents\":{\"surfaceId\":\"s1\",}}";
        String repaired = WidgetProtocolValidator.repair(broken);
        assertNotNull(repaired);
        // After repair, should at least be better formed
        // Trailing commas inside { } should be removed
        assertFalse("Should not have ,, ", repaired.contains(",,"));
    }

    @Test
    public void test17_repairRemovesTrailingCommaBeforeBrace() {
        String broken = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\",}],},}";
        String repaired = WidgetProtocolValidator.repair(broken);
        // Should not have ,} or ,]
        assertFalse(repaired.contains(",}") || repaired.contains(",]"));
    }

    @Test
    public void test18_repairRemovesLeadingText() {
        String broken = "Here is the JSON: {\"version\":\"v0.9\"}";
        String repaired = WidgetProtocolValidator.repair(broken);
        assertNotNull(repaired);
        assertTrue("Should start with {", repaired.startsWith("{"));
    }

    @Test
    public void test19_repairRemovesTrailingText() {
        String broken = "{\"version\":\"v0.9\"} trailing text here";
        String repaired = WidgetProtocolValidator.repair(broken);
        assertTrue("Should end with }", repaired.endsWith("}"));
    }

    @Test
    public void test20_repairNullInput() {
        String repaired = WidgetProtocolValidator.repair(null);
        assertNull(repaired);
    }

    @Test
    public void test21_repairEmptyInput() {
        String repaired = WidgetProtocolValidator.repair("");
        assertEquals("", repaired);
    }

    @Test
    public void test22_repairControlChars() {
        String broken = "{\"version\":\u0000\"v0.9\"}";
        String repaired = WidgetProtocolValidator.repair(broken);
        assertFalse("Should not contain control chars", repaired.contains("\u0000"));
    }

    // ============================ validateComponentTypes ============================

    @Test
    public void test23_validComponentTypes() {
        String json = validEnvelopeJson();
        assertTrue("Valid component types should pass",
                WidgetProtocolValidator.validateComponentTypes(json));
    }

    @Test
    public void test24_invalidComponentType() {
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"FakeComponent\"}]}}";
        assertFalse("Invalid component type should fail",
                WidgetProtocolValidator.validateComponentTypes(json));
    }

    @Test
    public void test25_emptyComponentType() {
        // component field missing — should not be a hard failure
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\"}]}}";
        // Empty type is skipped, returns true (warning only)
        assertTrue(WidgetProtocolValidator.validateComponentTypes(json));
    }

    @Test
    public void test26_nullJsonComponentTypes() {
        assertFalse(WidgetProtocolValidator.validateComponentTypes(null));
        assertFalse(WidgetProtocolValidator.validateComponentTypes(""));
    }

    // ============================ 端到端：提取+修复+校验 ============================

    @Test
    public void test27_endToEnd_llmOutputWithErrors() {
        // Simulate realistic LLM output with common errors
        String llmOutput = "好的，这是一个天气卡片：\n```a2ui\n" +
                "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"ai-gen\"," +
                "\"components\":[{\"id\":\"root\",\"component\":\"Card\",," +
                "\"child\":\"inner\"},{\"id\":\"inner\",\"component\":\"Column\",}]}}" +
                "\n```\n以上是生成的组件。";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNotNull(extracted);
        String repaired = WidgetProtocolValidator.repair(extracted);
        ValidationResult result = WidgetProtocolValidator.validate(repaired);
        assertTrue("End-to-end: extract + repair + validate should succeed for realistic LLM output",
                result.valid);
        assertTrue("Should have root", result.hasRoot);
        assertTrue("Should have surfaceId", result.hasSurfaceId);
    }

    @Test
    public void test28_endToEnd_completelyBrokenLlmOutput() {
        String llmOutput = "抱歉，无法生成组件。";
        String extracted = WidgetProtocolValidator.extractA2UIJson(llmOutput);
        assertNull("No JSON in broken output should return null", extracted);
    }

    @Test
    public void test29_allValidComponentTypes() {
        // Test all components in the whitelist
        String[] components = {"Column", "Row", "Text", "Button", "Image", "Card",
                "Divider", "TextField", "CheckBox", "List", "Carousel",
                "ProgressBar", "Modal", "Slider"};
        for (String comp : components) {
            String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\"," +
                    "\"components\":[{\"id\":\"root\",\"component\":\"" + comp + "\"}]}}";
            assertTrue("Component " + comp + " should be in whitelist",
                    WidgetProtocolValidator.validateComponentTypes(json));
        }
    }

    // ============================ 辅助方法 ============================

    private String validEnvelopeJson() {
        return "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"test-surf\"," +
                "\"components\":[" +
                "{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"text1\"]}," +
                "{\"id\":\"text1\",\"component\":\"Text\",\"text\":\"Hello\"}" +
                "]}}";
    }
}
