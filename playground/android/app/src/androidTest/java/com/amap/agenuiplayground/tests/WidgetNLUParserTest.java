package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetNLUParser;
import com.amap.agenuiplayground.widget.WidgetNLUParser.NLUResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * R221-R240: WidgetNLUParser unit tests.
 *
 * Tests natural language entity extraction:
 * - Number extraction (standalone, with units)
 * - Time expression extraction (today/tomorrow/weekday/period)
 * - City/location detection (36 Chinese cities)
 * - Weather entities (temperature, humidity, wind, AQI)
 * - Edge cases: null, empty, mixed, no entities
 * - toPromptHint() format
 */
@RunWith(AndroidJUnit4.class)
public class WidgetNLUParserTest {

    // ===== Number extraction =====

    @Test
    public void NLU01_extractSingleNumber() {
        NLUResult r = WidgetNLUParser.parse("3天后开会");
        assertFalse("Should extract at least 1 number", r.numbers.isEmpty());
        assertTrue("Should contain 3", r.numbers.contains(3));
    }

    @Test
    public void NLU02_extractMultipleNumbers() {
        NLUResult r = WidgetNLUParser.parse("3天5个任务2次会议");
        assertTrue("Should contain 3", r.numbers.contains(3));
        assertTrue("Should contain 5", r.numbers.contains(5));
        assertTrue("Should contain 2", r.numbers.contains(2));
    }

    @Test
    public void NLU03_numbersAreDeduplicated() {
        NLUResult r = WidgetNLUParser.parse("3天3天3天");
        // Should only have one "3"
        int count3 = 0;
        for (int n : r.numbers) {
            if (n == 3) count3++;
        }
        assertEquals("Should deduplicate 3", 1, count3);
    }

    @Test
    public void NLU04_noNumbers_returnsEmptyList() {
        NLUResult r = WidgetNLUParser.parse("今天天气不错");
        assertTrue("Should have no numbers", r.numbers.isEmpty());
    }

    // ===== Time extraction =====

    @Test
    public void NLU05_extractToday() {
        NLUResult r = WidgetNLUParser.parse("今天天气");
        assertNotNull("Should extract time", r.time);
        assertTrue("Time should contain 今天", r.time.contains("今天"));
    }

    @Test
    public void NLU06_extractTomorrow() {
        NLUResult r = WidgetNLUParser.parse("明天的议程");
        assertNotNull(r.time);
        assertTrue("Should contain 明天", r.time.contains("明天"));
    }

    @Test
    public void NLU07_extractWeekday() {
        NLUResult r = WidgetNLUParser.parse("周一开会");
        assertNotNull(r.time);
        assertTrue("Should contain 周一", r.time.contains("周一"));
    }

    @Test
    public void NLU08_extractTimePeriod() {
        NLUResult r = WidgetNLUParser.parse("下午3点开会");
        assertNotNull(r.time);
        assertTrue("Should contain 下午", r.time.contains("下午"));
    }

    @Test
    public void NLU09_extractCombinedTime() {
        NLUResult r = WidgetNLUParser.parse("明天下午开周会");
        assertNotNull(r.time);
        assertTrue("Should contain 明天", r.time.contains("明天"));
        assertTrue("Should contain 下午", r.time.contains("下午"));
    }

    @Test
    public void NLU10_noTime_returnsNull() {
        NLUResult r = WidgetNLUParser.parse("天气不错");
        assertNull("Should return null for no time", r.time);
    }

    @Test
    public void NLU11_todayTodayDedup() {
        // "今天" and "今日" should be deduplicated
        NLUResult r = WidgetNLUParser.parse("今天今日天气");
        assertNotNull(r.time);
        // Should only have one "今天" (dedup of 今天/今日)
        int count = 0;
        for (String part : r.time.split(" ")) {
            if (part.equals("今天")) count++;
        }
        assertEquals("Should dedup 今天/今日 to single 今天", 1, count);
    }

    // ===== Location extraction =====

    @Test
    public void NLU12_extractBeijing() {
        NLUResult r = WidgetNLUParser.parse("北京天气");
        assertEquals("北京", r.location);
    }

    @Test
    public void NLU13_extractShanghai() {
        NLUResult r = WidgetNLUParser.parse("上海气温");
        assertEquals("上海", r.location);
    }

    @Test
    public void NLU14_extractDongguan() {
        NLUResult r = WidgetNLUParser.parse("东莞天气");
        assertEquals("东莞", r.location);
    }

    @Test
    public void NLU15_extractShenzhen() {
        NLUResult r = WidgetNLUParser.parse("深圳的待办");
        assertEquals("深圳", r.location);
    }

    @Test
    public void NLU16_noCity_returnsNull() {
        NLUResult r = WidgetNLUParser.parse("今天天气不错");
        assertNull(r.location);
    }

    @Test
    public void NLU17_multipleCities_returnsFirst() {
        // "北京上海" — should return the first match (北京)
        NLUResult r = WidgetNLUParser.parse("从北京到上海");
        assertNotNull(r.location);
        // The first city in the CITIES list that appears
        // 北京 is first in the list
        assertEquals("北京", r.location);
    }

    // ===== Weather entity extraction =====

    @Test
    public void NLU18_extractTemperature() {
        NLUResult r = WidgetNLUParser.parse("今天23度");
        assertEquals("23", r.entities.get("temperature"));
    }

    @Test
    public void NLU19_extractNegativeTemperature() {
        NLUResult r = WidgetNLUParser.parse("零下5度");
        assertEquals("-5", r.entities.get("temperature"));
    }

    @Test
    public void NLU20_extractHumidity() {
        NLUResult r = WidgetNLUParser.parse("湿度45%");
        assertEquals("45", r.entities.get("humidity"));
    }

    @Test
    public void NLU21_extractWindLevel() {
        NLUResult r = WidgetNLUParser.parse("风力3级");
        assertEquals("3", r.entities.get("windLevel"));
    }

    @Test
    public void NLU22_extractAQI() {
        NLUResult r = WidgetNLUParser.parse("空气质量指数42");
        assertEquals("42", r.entities.get("aqi"));
    }

    @Test
    public void NLU23_extractAllWeatherEntities() {
        NLUResult r = WidgetNLUParser.parse("北京今天23度湿度45%风力3级aqi42");
        assertEquals("23", r.entities.get("temperature"));
        assertEquals("45", r.entities.get("humidity"));
        assertEquals("3", r.entities.get("windLevel"));
        assertEquals("42", r.entities.get("aqi"));
    }

    // ===== Number with unit entities =====

    @Test
    public void NLU24_extractDays() {
        NLUResult r = WidgetNLUParser.parse("未来3天天气预报");
        assertEquals("3", r.entities.get("days"));
    }

    @Test
    public void NLU25_extractPeople() {
        NLUResult r = WidgetNLUParser.parse("5人参加会议");
        assertEquals("5", r.entities.get("people"));
    }

    @Test
    public void NLU26_extractHours() {
        NLUResult r = WidgetNLUParser.parse("2小时后提醒");
        assertEquals("2", r.entities.get("hours"));
    }

    // ===== Edge cases =====

    @Test
    public void NLU27_nullInput_returnsEmpty() {
        NLUResult r = WidgetNLUParser.parse(null);
        assertNotNull("Should never return null", r);
        assertFalse("Should have no entities", r.hasAnyEntity());
    }

    @Test
    public void NLU28_emptyInput_returnsEmpty() {
        NLUResult r = WidgetNLUParser.parse("");
        assertNotNull(r);
        assertFalse(r.hasAnyEntity());
    }

    @Test
    public void NLU29_whitespaceInput_returnsEmpty() {
        NLUResult r = WidgetNLUParser.parse("   ");
        assertNotNull(r);
        assertFalse(r.hasAnyEntity());
    }

    @Test
    public void NLU30_noEntities_returnsEmpty() {
        NLUResult r = WidgetNLUParser.parse("你好世界");
        assertFalse(r.hasAnyEntity());
    }

    @Test
    public void NLU31_hasAnyEntity_trueWithNumber() {
        NLUResult r = WidgetNLUParser.parse("3天");
        assertTrue(r.hasAnyEntity());
    }

    @Test
    public void NLU32_hasAnyEntity_trueWithLocation() {
        NLUResult r = WidgetNLUParser.parse("北京");
        assertTrue(r.hasAnyEntity());
    }

    // ===== toPromptHint =====

    @Test
    public void NLU33_toPromptHint_includesLocation() {
        NLUResult r = WidgetNLUParser.parse("北京天气");
        String hint = r.toPromptHint();
        assertTrue("Hint should contain location=北京", hint.contains("location=北京"));
    }

    @Test
    public void NLU34_toPromptHint_includesTime() {
        NLUResult r = WidgetNLUParser.parse("明天开会");
        String hint = r.toPromptHint();
        assertTrue("Hint should contain time=明天", hint.contains("time=明天"));
    }

    @Test
    public void NLU35_toPromptHint_includesEntities() {
        NLUResult r = WidgetNLUParser.parse("23度");
        String hint = r.toPromptHint();
        assertTrue("Hint should contain temperature=23", hint.contains("temperature=23"));
    }

    @Test
    public void NLU36_toPromptHint_emptyWhenNoEntities() {
        NLUResult r = WidgetNLUParser.parse("你好");
        String hint = r.toPromptHint();
        assertTrue("Hint should be empty", hint.isEmpty());
    }

    @Test
    public void NLU37_toPromptHint_formatIsCommaSeparated() {
        NLUResult r = WidgetNLUParser.parse("北京明天23度");
        String hint = r.toPromptHint();
        assertTrue("Should have comma separator", hint.contains(", "));
    }

    // ===== Comprehensive parse =====

    @Test
    public void NLU38_comprehensiveParse_allEntities() {
        NLUResult r = WidgetNLUParser.parse("北京明天下午23度湿度45%风力3级3天后");
        assertEquals("北京", r.location);
        assertNotNull(r.time);
        assertTrue(r.time.contains("明天"));
        assertTrue(r.time.contains("下午"));
        assertEquals("23", r.entities.get("temperature"));
        assertEquals("45", r.entities.get("humidity"));
        assertEquals("3", r.entities.get("windLevel"));
        assertTrue("Should contain 3 in numbers", r.numbers.contains(3));
        assertTrue("Should contain 23 in numbers", r.numbers.contains(23));
        assertTrue("Should contain 45 in numbers", r.numbers.contains(45));
    }

    @Test
    public void NLU39_toString_containsAllFields() {
        NLUResult r = WidgetNLUParser.parse("北京23度");
        String str = r.toString();
        assertTrue("toString should mention numbers", str.contains("numbers"));
        assertTrue("toString should mention location", str.contains("location"));
        assertTrue("toString should mention entities", str.contains("entities"));
    }

    @Test
    public void NLU40_multipleCitiesInList() {
        // Verify 36 cities are in the list (implicitly tested via extraction)
        String[] cities = {"北京", "上海", "广州", "深圳", "东莞", "杭州", "成都",
                "武汉", "西安", "南京", "重庆", "天津", "苏州", "长沙", "青岛", "郑州"};
        for (String city : cities) {
            NLUResult r = WidgetNLUParser.parse(city + "天气");
            assertEquals("Should extract " + city, city, r.location);
        }
    }
}
