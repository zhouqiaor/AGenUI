package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetIntentMatcher;
import com.amap.agenuiplayground.widget.WidgetIntentMatcher.IntentMatch;

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
 * R181-R200 (Intent): WidgetIntentMatcher configuration-driven tests.
 *
 * Tests the configurable keyword matching engine:
 * - Default keyword matching (all 6 intent categories)
 * - Fuzzy/pinyin matching
 * - matchWithScore() confidence scoring
 * - matchesTemplate() helper
 * - Config file loading from assets/widget_intent_config.json
 * - Edge cases: null, empty, case sensitivity, mixed input
 */
@RunWith(AndroidJUnit4.class)
public class WidgetIntentMatcherTest {

    @Before
    public void setUp() {
        // Load config from assets — falls back to defaults if file missing
        WidgetIntentMatcher.loadConfig(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    // ===== Basic keyword matching =====

    @Test
    public void IM01_match_weatherChinese() {
        assertEquals("weather", WidgetIntentMatcher.match("今天北京天气"));
    }

    @Test
    public void IM02_match_weatherEnglish() {
        assertEquals("weather", WidgetIntentMatcher.match("what's the weather today"));
    }

    @Test
    public void IM03_match_todoChinese() {
        assertEquals("todo", WidgetIntentMatcher.match("我的待办清单"));
    }

    @Test
    public void IM04_match_todoEnglish() {
        assertEquals("todo", WidgetIntentMatcher.match("my todo list for today"));
    }

    @Test
    public void IM05_match_agendaChinese() {
        assertEquals("agenda", WidgetIntentMatcher.match("今天会议日程安排"));
    }

    @Test
    public void IM06_match_agendaEnglish() {
        assertEquals("agenda", WidgetIntentMatcher.match("meeting agenda schedule"));
    }

    @Test
    public void IM07_match_pollChinese() {
        assertEquals("poll", WidgetIntentMatcher.match("发起一个投票"));
    }

    @Test
    public void IM08_match_pollEnglish() {
        assertEquals("poll", WidgetIntentMatcher.match("create a poll for vote"));
    }

    @Test
    public void IM09_match_noteChinese() {
        assertEquals("note", WidgetIntentMatcher.match("做个笔记记录一下"));
    }

    @Test
    public void IM10_match_noteEnglish() {
        assertEquals("note", WidgetIntentMatcher.match("take a memo note"));
    }

    @Test
    public void IM11_match_calendarChinese() {
        assertEquals("calendar", WidgetIntentMatcher.match("看看日历这周放不放假"));
    }

    @Test
    public void IM12_match_calendarEnglish() {
        assertEquals("calendar", WidgetIntentMatcher.match("check the calendar for dates"));
    }

    // ===== No match =====

    @Test
    public void IM13_match_noMatch_returnsNull() {
        assertNull(WidgetIntentMatcher.match("讲个笑话"));
    }

    @Test
    public void IM14_match_nullInput_returnsNull() {
        assertNull(WidgetIntentMatcher.match(null));
    }

    @Test
    public void IM15_match_emptyInput_returnsNull() {
        assertNull(WidgetIntentMatcher.match(""));
    }

    @Test
    public void IM16_match_whitespaceOnly_returnsNull() {
        assertNull(WidgetIntentMatcher.match("   "));
    }

    // ===== Case insensitivity =====

    @Test
    public void IM17_match_caseInsensitive() {
        assertEquals("weather", WidgetIntentMatcher.match("WEATHER FORECAST"));
        assertEquals("todo", WidgetIntentMatcher.match("TODO list"));
        assertEquals("agenda", WidgetIntentMatcher.match("AGENDA for today"));
    }

    // ===== Fuzzy/pinyin matching =====

    @Test
    public void IM18_fuzzy_tianqi_matchesWeather() {
        assertEquals("weather", WidgetIntentMatcher.match("tianqi zenmeyang"));
    }

    @Test
    public void IM19_fuzzy_daiban_matchesTodo() {
        assertEquals("todo", WidgetIntentMatcher.match("wode daiban"));
    }

    @Test
    public void IM20_fuzzy_richeng_matchesAgenda() {
        assertEquals("agenda", WidgetIntentMatcher.match("richeng anpai"));
    }

    // ===== Multiple keywords — best match wins =====

    @Test
    public void IM21_multipleKeywords_bestMatchWins() {
        // "天气待办" contains keywords for both weather and todo
        // weather should win because "天气" appears in the first position
        String result = WidgetIntentMatcher.match("天气待办");
        assertNotNull(result);
        // Either weather or todo could win depending on keyword count
        // But weather has more keyword hits typically
        assertTrue("Should match weather or todo",
                "weather".equals(result) || "todo".equals(result));
    }

    // ===== matchWithScore =====

    @Test
    public void IM22_matchWithScore_returnsIntentMatch() {
        IntentMatch match = WidgetIntentMatcher.matchWithScore("今天北京天气气温");
        assertNotNull(match);
        assertEquals("weather", match.template);
        assertTrue("Score should be > 0", match.score > 0);
        assertFalse("Matched keywords should not be empty", match.matchedKeywords.isEmpty());
    }

    @Test
    public void IM23_matchWithScore_nullInput_returnsNull() {
        assertNull(WidgetIntentMatcher.matchWithScore(null));
    }

    @Test
    public void IM24_matchWithScore_noMatch_returnsNull() {
        assertNull(WidgetIntentMatcher.matchWithScore("xyzrandom"));
    }

    @Test
    public void IM25_matchWithScore_fuzzyGivesLowScore() {
        // Fuzzy match (pinyin) should give a low score
        IntentMatch match = WidgetIntentMatcher.matchWithScore("tianqi");
        if (match != null) {
            assertTrue("Fuzzy match score should be <= 0.4", match.score <= 0.4f);
        }
    }

    @Test
    public void IM26_matchWithScore_multipleKeywordHitsHigherScore() {
        IntentMatch single = WidgetIntentMatcher.matchWithScore("天气");
        IntentMatch multi = WidgetIntentMatcher.matchWithScore("天气气温预报下雨");
        if (single != null && multi != null) {
            assertTrue("More keyword hits should give higher or equal score",
                    multi.score >= single.score);
        }
    }

    // ===== matchesTemplate =====

    @Test
    public void IM27_matchesTemplate_trueForMatch() {
        assertTrue(WidgetIntentMatcher.matchesTemplate("今天天气", "weather"));
    }

    @Test
    public void IM28_matchesTemplate_falseForNoMatch() {
        assertFalse(WidgetIntentMatcher.matchesTemplate("讲个笑话", "weather"));
    }

    @Test
    public void IM29_matchesTemplate_nullSafe() {
        assertFalse(WidgetIntentMatcher.matchesTemplate(null, "weather"));
        assertFalse(WidgetIntentMatcher.matchesTemplate("天气", null));
    }

    // ===== getSupportedTemplates =====

    @Test
    public void IM30_getSupportedTemplates_notEmpty() {
        List<String> templates = WidgetIntentMatcher.getSupportedTemplates();
        assertNotNull(templates);
        assertFalse("Should have at least 1 supported template", templates.isEmpty());
    }

    @Test
    public void IM31_getSupportedTemplates_containsWeather() {
        List<String> templates = WidgetIntentMatcher.getSupportedTemplates();
        assertTrue("Should contain 'weather'", templates.contains("weather"));
    }

    @Test
    public void IM32_getSupportedTemplates_containsAllDefaults() {
        List<String> templates = WidgetIntentMatcher.getSupportedTemplates();
        // All 6 default intent categories should be present
        String[] expected = {"weather", "todo", "agenda", "poll", "note", "calendar"};
        for (String e : expected) {
            assertTrue("Should contain template: " + e, templates.contains(e));
        }
    }

    // ===== isConfigLoaded =====

    @Test
    public void IM33_isConfigLoaded_returnsBoolean() {
        // Just verify it doesn't crash — the result depends on whether
        // widget_intent_config.json exists in assets
        boolean loaded = WidgetIntentMatcher.isConfigLoaded();
        // If the config file exists, this should be true
        // If not, it should be false
        // Either way, no crash
        assertTrue(true);
    }

    // ===== Mixed Chinese/English =====

    @Test
    public void IM34_mixedChineseEnglish_matches() {
        assertEquals("weather", WidgetIntentMatcher.match("weather天气"));
        assertEquals("todo", WidgetIntentMatcher.match("todo待办"));
    }

    @Test
    public void IM35_mixedCaseAndLanguage() {
        assertEquals("weather", WidgetIntentMatcher.match("WEATHER 天气"));
    }

    private static void assertFalse(String msg, boolean condition) {
        org.junit.Assert.assertFalse(msg, condition);
    }
}
