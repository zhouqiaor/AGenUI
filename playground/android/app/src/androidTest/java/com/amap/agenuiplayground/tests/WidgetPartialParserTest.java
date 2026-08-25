package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetPartialParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * L3-P2.1: WidgetPartialParser 单元测试
 *
 * 测试 P2.1 新增的流式 JSON 增量解析器：
 * - 逐 chunk 喂入，返回闭合的 JSON 对象列表
 * - 处理字符串内的 {} 和转义字符
 * - 跳过 markdown 代码围栏
 * - 不完整 JSON 保留在 buffer 中
 * - 多个 JSON 对象顺序解析
 */
@RunWith(AndroidJUnit4.class)
public class WidgetPartialParserTest {

    private WidgetPartialParser parser;

    @Before
    public void setUp() {
        parser = new WidgetPartialParser();
    }

    // ============================ 单对象解析 ============================

    @Test
    public void test01_completeJson_singleChunk() {
        String chunk = "{\"version\":\"v0.9\"}";
        List<String> results = parser.feed(chunk);
        assertEquals("Should return 1 complete JSON", 1, results.size());
        assertEquals(chunk, results.get(0));
    }

    @Test
    public void test02_completeJson_multiChunk() {
        // Split JSON across multiple chunks
        List<String> r1 = parser.feed("{\"ver");
        assertTrue("First chunk should return no complete JSON", r1.isEmpty());
        List<String> r2 = parser.feed("sion\":\"");
        assertTrue("Second chunk should return no complete JSON", r2.isEmpty());
        List<String> r3 = parser.feed("v0.9\"}");
        assertEquals("Third chunk should return 1 complete JSON", 1, r3.size());
        assertEquals("{\"version\":\"v0.9\"}", r3.get(0));
    }

    @Test
    public void test03_charByChar() {
        String json = "{\"id\":1}";
        for (int i = 0; i < json.length() - 1; i++) {
            List<String> r = parser.feed(String.valueOf(json.charAt(i)));
            assertTrue("Should not return JSON before completion at char " + i, r.isEmpty());
        }
        List<String> r = parser.feed(String.valueOf(json.charAt(json.length() - 1)));
        assertEquals("Last char should complete the JSON", 1, r.size());
        assertEquals(json, r.get(0));
    }

    // ============================ 字符串处理 ============================

    @Test
    public void test04_bracesInsideString() {
        // {} inside strings should not affect depth tracking
        String json = "{\"text\":\"has { and } inside\"}";
        List<String> r = parser.feed(json);
        assertEquals("Should handle braces inside strings", 1, r.size());
        assertEquals(json, r.get(0));
    }

    @Test
    public void test05_escapedQuoteInsideString() {
        // Escaped \" should not toggle string state
        String json = "{\"text\":\"escaped \\\"quote\\\" here\"}";
        List<String> r = parser.feed(json);
        assertEquals("Should handle escaped quotes", 1, r.size());
        assertEquals(json, r.get(0));
    }

    @Test
    public void test06_backslashInsideString() {
        String json = "{\"path\":\"C:\\\\Users\"}";
        List<String> r = parser.feed(json);
        assertEquals("Should handle escaped backslashes", 1, r.size());
        assertEquals(json, r.get(0));
    }

    // ============================ 代码围栏跳过 ============================

    @Test
    public void test07_skipCodeFence_a2ui() {
        String chunk = "```a2ui\n{\"version\":\"v0.9\"}\n```";
        List<String> r = parser.feed(chunk);
        assertEquals("Should extract JSON from ```a2ui block", 1, r.size());
        // Should not contain backticks
        assertFalse(r.get(0).contains("`"));
    }

    @Test
    public void test08_skipCodeFence_json() {
        String chunk = "```json\n{\"version\":\"v0.9\"}\n```";
        List<String> r = parser.feed(chunk);
        assertEquals("Should extract JSON from ```json block", 1, r.size());
        assertFalse(r.get(0).contains("`"));
    }

    @Test
    public void test09_skipBackticksOnly() {
        // Just backticks without JSON
        List<String> r = parser.feed("```\n```");
        assertTrue("Should return no results for backticks only", r.isEmpty());
    }

    // ============================ 多对象 ============================

    @Test
    public void test10_multipleJsonObjects_inSequence() {
        String chunk = "{\"a\":1}{\"b\":2}";
        List<String> r = parser.feed(chunk);
        assertEquals("Should return 2 complete JSON objects", 2, r.size());
        assertEquals("{\"a\":1}", r.get(0));
        assertEquals("{\"b\":2}", r.get(1));
    }

    @Test
    public void test11_multipleObjects_acrossChunks() {
        List<String> r1 = parser.feed("{\"a\":1}{\"b\":");
        assertEquals("First chunk: 1 complete + 1 partial", 1, r1.size());
        assertEquals("{\"a\":1}", r1.get(0));
        List<String> r2 = parser.feed("2}");
        assertEquals("Second chunk: 1 complete", 1, r2.size());
        assertEquals("{\"b\":2}", r2.get(0));
    }

    // ============================ 嵌套对象 ============================

    @Test
    public void test12_nestedObjects() {
        String json = "{\"outer\":{\"inner\":{\"deep\":true}}}";
        List<String> r = parser.feed(json);
        assertEquals("Should handle nested objects", 1, r.size());
        assertEquals(json, r.get(0));
    }

    @Test
    public void test13_arrayInsideObject() {
        String json = "{\"arr\":[1,2,{\"x\":3}]}";
        List<String> r = parser.feed(json);
        assertEquals("Should handle arrays with nested objects", 1, r.size());
        assertEquals(json, r.get(0));
    }

    // ============================ 边界情况 ============================

    @Test
    public void test14_nullChunk() {
        List<String> r = parser.feed(null);
        assertTrue("Null chunk should return empty list", r.isEmpty());
    }

    @Test
    public void test15_emptyChunk() {
        List<String> r = parser.feed("");
        assertTrue("Empty chunk should return empty list", r.isEmpty());
    }

    @Test
    public void test16_partialJson_remainsInBuffer() {
        // Feed partial, then complete
        parser.feed("{\"ver");
        List<String> r = parser.feed("sion\":\"v0.9\"}");
        assertEquals("Should complete the partial JSON from buffer", 1, r.size());
        assertEquals("{\"version\":\"v0.9\"}", r.get(0));
    }

    @Test
    public void test17_a2uiBlockWithPartialJson() {
        // ```a2ui\n{ incomplete, then complete
        List<String> r1 = parser.feed("```a2ui\n{\"version\":");
        assertTrue("Partial JSON should return empty", r1.isEmpty());
        List<String> r2 = parser.feed("\"v0.9\"}\n```");
        assertEquals("Complete JSON should be returned", 1, r2.size());
        assertFalse(r2.get(0).contains("`"));
    }

    // ============================ 真实 LLM 输出模拟 ============================

    @Test
    public void test18_simulatedLlmStream() {
        // Simulate how an LLM would stream an A2UI message
        String fullJson = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"ai-gen\",\"components\":[{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"text1\"]},{\"id\":\"text1\",\"component\":\"Text\",\"text\":\"Hello\"}]}}";
        int chunkSize = 20;
        int totalResults = 0;
        for (int i = 0; i < fullJson.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullJson.length());
            String chunk = fullJson.substring(i, end);
            List<String> r = parser.feed(chunk);
            totalResults += r.size();
        }
        assertEquals("Should produce exactly 1 complete JSON from chunked input",
                1, totalResults);
    }

    @Test
    public void test19_simulatedLlmStreamWithFence() {
        // LLM wraps in ```a2ui
        String prefix = "```a2ui\n";
        String json = "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"ai\",\"components\":[{\"id\":\"root\",\"component\":\"Text\",\"text\":\"Hi\"}]}}";
        String suffix = "\n```";
        String fullOutput = prefix + json + suffix;
        int chunkSize = 15;
        int totalResults = 0;
        for (int i = 0; i < fullOutput.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullOutput.length());
            List<String> r = parser.feed(fullOutput.substring(i, end));
            totalResults += r.size();
        }
        assertEquals("Should produce 1 JSON without backticks", 1, totalResults);
    }

    @Test
    public void test20_twoA2uiBlocks() {
        // LLM outputs two messages (createSurface + updateComponents)
        String chunk = "```a2ui\n" +
                "{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s1\",\"catalogId\":\"cat\"}}" +
                "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\"}]}}" +
                "\n```";
        List<String> r = parser.feed(chunk);
        assertEquals("Should extract 2 JSON objects from a2ui block", 2, r.size());
        assertTrue(r.get(0).contains("createSurface"));
        assertTrue(r.get(1).contains("updateComponents"));
    }
}
