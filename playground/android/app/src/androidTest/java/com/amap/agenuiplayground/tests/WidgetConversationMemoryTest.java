package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetConversationMemory;
import com.amap.agenuiplayground.widget.WidgetConversationMemory.Entry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * R241-R250: WidgetConversationMemory tests.
 *
 * Tests multi-turn conversation context:
 * - addEntry + getEntries
 * - MAX_HISTORY (5) cap with FIFO eviction
 * - getLastTemplate / getLastUserText
 * - getHistoryJson format (for LLM messages)
 * - clear
 * - Edge cases: null/empty input
 */
@RunWith(AndroidJUnit4.class)
public class WidgetConversationMemoryTest {

    private WidgetConversationMemory memory;

    @Before
    public void setUp() {
        memory = new WidgetConversationMemory(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        memory.clear();
    }

    @After
    public void tearDown() {
        if (memory != null) {
            memory.clear();
        }
    }

    // ===== addEntry + getEntries =====

    @Test
    public void CM01_addSingleEntry() {
        memory.addEntry("今天天气", "weather");
        List<Entry> entries = memory.getEntries();
        assertEquals(1, entries.size());
        assertEquals("今天天气", entries.get(0).userText);
        assertEquals("weather", entries.get(0).template);
    }

    @Test
    public void CM02_addMultipleEntries() {
        memory.addEntry("天气", "weather");
        memory.addEntry("待办", "todo");
        memory.addEntry("议程", "agenda");
        List<Entry> entries = memory.getEntries();
        assertEquals(3, entries.size());
        // Order: oldest → newest
        assertEquals("天气", entries.get(0).userText);
        assertEquals("议程", entries.get(2).userText);
    }

    @Test
    public void CM03_addNullUserText_isIgnored() {
        memory.addEntry(null, "weather");
        assertEquals(0, memory.getEntries().size());
    }

    @Test
    public void CM04_addEmptyUserText_isIgnored() {
        memory.addEntry("", "weather");
        assertEquals(0, memory.getEntries().size());
    }

    @Test
    public void CM05_addWhitespaceUserText_isIgnored() {
        memory.addEntry("  ", "weather");
        assertEquals(0, memory.getEntries().size());
    }

    @Test
    public void CM06_addNullTemplate_isAllowed() {
        memory.addEntry("讲个笑话", null);
        List<Entry> entries = memory.getEntries();
        assertEquals(1, entries.size());
        assertNull(entries.get(0).template);
    }

    // ===== MAX_HISTORY cap =====

    @Test
    public void CM07_maxHistory5_capAt5() {
        for (int i = 0; i < 10; i++) {
            memory.addEntry("msg" + i, "weather");
        }
        List<Entry> entries = memory.getEntries();
        assertTrue("Should cap at 5 entries, got " + entries.size(),
                entries.size() <= 5);
    }

    @Test
    public void CM08_maxHistory5_evictsOldest() {
        for (int i = 0; i < 6; i++) {
            memory.addEntry("msg" + i, "template" + i);
        }
        List<Entry> entries = memory.getEntries();
        assertEquals("Should have 5 entries", 5, entries.size());
        // First entry should be msg1 (msg0 was evicted)
        assertEquals("msg1", entries.get(0).userText);
        // Last entry should be msg5
        assertEquals("msg5", entries.get(4).userText);
    }

    // ===== getLastTemplate =====

    @Test
    public void CM09_getLastTemplate_returnsLastMatched() {
        memory.addEntry("天气", "weather");
        memory.addEntry("待办", "todo");
        assertEquals("todo", memory.getLastTemplate());
    }

    @Test
    public void CM10_getLastTemplate_skipsNullTemplates() {
        memory.addEntry("天气", "weather");
        memory.addEntry("讲个笑话", null);
        memory.addEntry("继续", null);
        // getLastTemplate should find the last non-null template
        assertEquals("weather", memory.getLastTemplate());
    }

    @Test
    public void CM11_getLastTemplate_emptyHistory_returnsNull() {
        assertNull(memory.getLastTemplate());
    }

    @Test
    public void CM12_getLastTemplate_allNull_returnsNull() {
        memory.addEntry("笑话", null);
        memory.addEntry("继续", null);
        assertNull(memory.getLastTemplate());
    }

    // ===== getLastUserText =====

    @Test
    public void CM13_getLastUserText_returnsMostRecent() {
        memory.addEntry("first", "weather");
        memory.addEntry("second", "todo");
        assertEquals("second", memory.getLastUserText());
    }

    @Test
    public void CM14_getLastUserText_emptyHistory_returnsNull() {
        assertNull(memory.getLastUserText());
    }

    // ===== getHistoryJson =====

    @Test
    public void CM15_getHistoryJson_containsUserMessages() {
        memory.addEntry("天气", "weather");
        String json = memory.getHistoryJson();
        assertNotNull(json);
        assertTrue("Should contain user text", json.contains("天气"));
        assertTrue("Should contain role:user", json.contains("user"));
    }

    @Test
    public void CM16_getHistoryJson_containsAssistantMessages() {
        memory.addEntry("天气", "weather");
        String json = memory.getHistoryJson();
        assertTrue("Should contain role:assistant", json.contains("assistant"));
        assertTrue("Should contain template name", json.contains("weather"));
    }

    @Test
    public void CM17_getHistoryJson_skipsNullTemplateAssistant() {
        memory.addEntry("笑话", null);
        String json = memory.getHistoryJson();
        // Should have user message but no assistant message
        assertTrue(json.contains("user"));
        assertFalse("Should not have assistant message for null template",
                json.contains("assistant"));
    }

    @Test
    public void CM18_getHistoryJson_emptyHistory() {
        String json = memory.getHistoryJson();
        assertNotNull(json);
        // Should be a valid JSON array (possibly empty)
        assertEquals("[]", json);
    }

    @Test
    public void CM19_getHistoryJson_validJsonArray() {
        memory.addEntry("天气", "weather");
        memory.addEntry("待办", "todo");
        String json = memory.getHistoryJson();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            assertTrue("Should have multiple messages", arr.length() >= 2);
        } catch (Exception e) {
            throw new AssertionError("getHistoryJson should produce valid JSON", e);
        }
    }

    // ===== clear =====

    @Test
    public void CM20_clear_removesAllEntries() {
        memory.addEntry("天气", "weather");
        memory.addEntry("待办", "todo");
        memory.clear();
        assertEquals(0, memory.getEntries().size());
        assertNull(memory.getLastTemplate());
        assertNull(memory.getLastUserText());
    }

    @Test
    public void CM21_clear_onEmpty_isSafe() {
        memory.clear();
        memory.clear();
        assertEquals(0, memory.getEntries().size());
    }

    // ===== Persistence =====

    @Test
    public void CM22_persistence_survivesNewInstance() {
        memory.addEntry("persist_test", "weather");
        // Create a new instance — should load from SharedPreferences
        WidgetConversationMemory memory2 = new WidgetConversationMemory(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        List<Entry> entries = memory2.getEntries();
        boolean found = false;
        for (Entry e : entries) {
            if ("persist_test".equals(e.userText)) {
                found = true;
                break;
            }
        }
        assertTrue("Entry should persist across instances", found);
        memory2.clear();
    }

    // ===== Entry trimming =====

    @Test
    public void CM23_userTextIsTrimmed() {
        memory.addEntry("  天气  ", "weather");
        List<Entry> entries = memory.getEntries();
        assertEquals(1, entries.size());
        assertEquals("天气", entries.get(0).userText);
    }

    private static void assertFalse(String msg, boolean condition) {
        org.junit.Assert.assertFalse(msg, condition);
    }
}
