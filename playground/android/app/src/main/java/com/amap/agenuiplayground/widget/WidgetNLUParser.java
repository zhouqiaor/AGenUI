package com.amap.agenuiplayground.widget;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言实体提取器 — 从用户文本中提取结构化实体（数字/时间/地点/天气实体）。
 *
 * <p>用于：
 * <ol>
 *   <li>填充模板数据模型（如识别到"北京"则更新 weather 模板的 city 字段）</li>
 *   <li>注入 LLM prompt 帮助生成更准确的 A2UI</li>
 * </ol>
 *
 * <p>提取维度：
 * <ul>
 *   <li>数字：正则匹配 \d+，识别"3天"/"5个"等</li>
 *   <li>时间：匹配"今天/明天/后天/下周/周X/上午/下午/晚上" + 组合</li>
 *   <li>地点：匹配中国主要城市名（北京/上海/广州/深圳/东莞/杭州/成都/武汉/西安/南京等）</li>
 *   <li>天气实体：识别"温度XX度"/"湿度XX%"/"风力X级"等</li>
 * </ul>
 */
public final class WidgetNLUParser {

    private static final String TAG = "WidgetNLUParser";

    // ============================================================
    // 城市名表（至少 20 个中国主要城市）
    // ============================================================
    private static final List<String> CITIES = Arrays.asList(
            "北京", "上海", "广州", "深圳", "东莞", "杭州", "成都", "武汉",
            "西安", "南京", "重庆", "天津", "苏州", "长沙", "青岛", "郑州",
            "沈阳", "大连", "哈尔滨", "济南", "福州", "厦门", "昆明", "贵阳",
            "南宁", "兰州", "太原", "合肥", "南昌", "石家庄", "无锡", "宁波",
            "佛山", "珠海", "中山", "惠州"
    );

    // ============================================================
    // 时间关键词
    // ============================================================
    private static final List<String> TIME_KEYWORDS = Arrays.asList(
            "今天", "今日", "明天", "明日", "后天", "大后天",
            "昨天", "前天",
            "下周", "下下周", "本周",
            "周一", "周二", "周三", "周四", "周五", "周六", "周日", "周末",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "星期天",
            "上午", "中午", "下午", "晚上", "傍晚", "凌晨", "早晨", "清晨",
            "白天", "夜间", "今晚", "今早", "明早", "明晚"
    );

    // ============================================================
    // 天气实体正则
    // ============================================================
    // 温度："-5度"/"23度"/"零下3度" → entities.put("temperature", "23")
    private static final Pattern TEMP_PATTERN = Pattern.compile(
            "(?:零下|负)?(-?\\d{1,3})\\s*度");
    // 湿度：湿度45% / 湿度45 → entities.put("humidity", "45")
    private static final Pattern HUMIDITY_PATTERN = Pattern.compile(
            "湿度\\s*(\\d{1,3})\\s*%?");
    // 风力：风力3级 / 3级风 → entities.put("windLevel", "3")
    private static final Pattern WIND_PATTERN = Pattern.compile(
            "风力\\s*(\\d{1,2})\\s*级|(\\d{1,2})\\s*级风");
    // AQI：空气质量指数42 / aqi42 → entities.put("aqi", "42")
    private static final Pattern AQI_PATTERN = Pattern.compile(
            "(?:空气质量指数|aqi|AQI)\\s*(\\d{1,3})");

    // 通用数字提取
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    // 带量词的数字："3天"/"5个"/"2次" → 保留数字 + 单位
    private static final Pattern NUMBER_WITH_UNIT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(天|个|次|条|项|级|度|人|份|页|张|本|件|款|篇|封|瓶|杯|盒|件|周|月|年|小时|分钟|秒)");

    private WidgetNLUParser() {
        // utility class
    }

    // ============================================================
    // NLUResult
    // ============================================================

    /**
     * NLU 解析结果。
     */
    public static class NLUResult {
        /** 提取到的所有数字（去重保序） */
        public final List<Integer> numbers;
        /** 提取到的时间表达（如"明天下午"），可能为 null */
        public final String time;
        /** 提取到的城市名（如"北京"），可能为 null */
        public final String location;
        /** 其他实体键值对（temperature/humidity/windLevel/aqi 等） */
        public final Map<String, String> entities;

        public NLUResult(List<Integer> numbers, String time, String location,
                         Map<String, String> entities) {
            this.numbers = numbers != null ? numbers : new ArrayList<>();
            this.time = time;
            this.location = location;
            this.entities = entities != null ? entities : new LinkedHashMap<>();
        }

        /**
         * 是否提取到了任意实体。
         */
        public boolean hasAnyEntity() {
            return !numbers.isEmpty() || time != null
                    || location != null || !entities.isEmpty();
        }

        /**
         * 构造用于注入 prompt 的实体摘要字符串。
         * 格式："location=北京, time=明天, temperature=23"
         */
        public String toPromptHint() {
            List<String> parts = new ArrayList<>();
            if (location != null) parts.add("location=" + location);
            if (time != null) parts.add("time=" + time);
            for (Map.Entry<String, String> e : entities.entrySet()) {
                parts.add(e.getKey() + "=" + e.getValue());
            }
            if (parts.isEmpty()) return "";
            return String.join(", ", parts);
        }

        @Override
        public String toString() {
            return "NLUResult{numbers=" + numbers
                    + ", time='" + time + "'"
                    + ", location='" + location + "'"
                    + ", entities=" + entities + "}";
        }
    }

    // ============================================================
    // parse
    // ============================================================

    /**
     * 解析用户文本，提取结构化实体。
     *
     * @param userText 用户原始输入，可为 null
     * @return NLUResult，永不返回 null
     */
    public static NLUResult parse(String userText) {
        if (userText == null || userText.trim().isEmpty()) {
            return new NLUResult(new ArrayList<>(), null, null,
                    new LinkedHashMap<>());
        }
        String text = userText.trim();

        List<Integer> numbers = extractNumbers(text);
        String time = extractTime(text);
        String location = extractLocation(text);
        Map<String, String> entities = extractEntities(text);

        NLUResult result = new NLUResult(numbers, time, location, entities);
        Log.d(TAG, "parse: \"" + truncate(text, 40) + "\" → " + result);
        return result;
    }

    // ------------------------------------------------------------
    // 数字提取
    // ------------------------------------------------------------

    private static List<Integer> extractNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group());
                if (!numbers.contains(n)) {
                    numbers.add(n);
                }
            } catch (NumberFormatException ignored) {
                // 超大数字跳过
            }
        }
        return numbers;
    }

    // ------------------------------------------------------------
    // 时间提取
    // ------------------------------------------------------------

    private static String extractTime(String text) {
        List<String> hits = new ArrayList<>();
        for (String kw : TIME_KEYWORDS) {
            if (text.contains(kw)) {
                if (!hits.contains(kw)) hits.add(kw);
            }
        }
        if (hits.isEmpty()) return null;
        // 组合多个时间词：如"明天下午" → "明天 下午"
        // 但去重避免"今天"和"今日"同时出现
        List<String> deduped = dedupTimeKeywords(hits);
        return String.join(" ", deduped);
    }

    /**
     * 去除同义时间词的重复（今天/今日、明天/明日后天 等）。
     */
    private static List<String> dedupTimeKeywords(List<String> raw) {
        List<String> result = new ArrayList<>();
        boolean hasToday = false;
        boolean hasTomorrow = false;
        boolean hasDayAfter = false;
        boolean hasWeekday = false;
        boolean hasPeriod = false;
        for (String kw : raw) {
            if (kw.equals("今天") || kw.equals("今日")) {
                if (!hasToday) { result.add("今天"); hasToday = true; }
            } else if (kw.equals("明天") || kw.equals("明日")) {
                if (!hasTomorrow) { result.add("明天"); hasTomorrow = true; }
            } else if (kw.equals("后天")) {
                if (!hasDayAfter) { result.add("后天"); hasDayAfter = true; }
            } else if (kw.startsWith("周") || kw.startsWith("星期")) {
                if (!hasWeekday) {
                    result.add(kw);
                    hasWeekday = true;
                }
            } else if (kw.equals("上午") || kw.equals("中午") || kw.equals("下午")
                    || kw.equals("晚上") || kw.equals("傍晚") || kw.equals("凌晨")
                    || kw.equals("早晨") || kw.equals("清晨")
                    || kw.equals("白天") || kw.equals("夜间")
                    || kw.equals("今晚") || kw.equals("今早")
                    || kw.equals("明早") || kw.equals("明晚")) {
                if (!hasPeriod) { result.add(kw); hasPeriod = true; }
            } else {
                result.add(kw);
            }
        }
        return result;
    }

    // ------------------------------------------------------------
    // 地点提取
    // ------------------------------------------------------------

    private static String extractLocation(String text) {
        // 优先匹配最长的城市名（避免"北京"先于"北京市"匹配问题）
        List<String> candidates = new ArrayList<>();
        for (String city : CITIES) {
            if (text.contains(city)) {
                candidates.add(city);
            }
        }
        if (candidates.isEmpty()) return null;
        // 返回第一个匹配的（列表已按优先级排列）
        return candidates.get(0);
    }

    // ------------------------------------------------------------
    // 天气实体提取
    // ------------------------------------------------------------

    private static Map<String, String> extractEntities(String text) {
        Map<String, String> entities = new LinkedHashMap<>();

        // 温度
        Matcher tempM = TEMP_PATTERN.matcher(text);
        if (tempM.find()) {
            String val = tempM.group(1);
            entities.put("temperature", val);
        }

        // 湿度
        Matcher humM = HUMIDITY_PATTERN.matcher(text);
        if (humM.find()) {
            entities.put("humidity", humM.group(1));
        }

        // 风力
        Matcher windM = WIND_PATTERN.matcher(text);
        if (windM.find()) {
            String w1 = windM.group(1);
            String w2 = windM.group(2);
            String wind = w1 != null ? w1 : w2;
            entities.put("windLevel", wind);
        }

        // AQI
        Matcher aqiM = AQI_PATTERN.matcher(text);
        if (aqiM.find()) {
            entities.put("aqi", aqiM.group(1));
        }

        // 带量词的数字也记入 entities（如 "3天" → days=3）
        Matcher unitM = NUMBER_WITH_UNIT_PATTERN.matcher(text);
        while (unitM.find()) {
            String num = unitM.group(1);
            String unit = unitM.group(2);
            String key = unitToKey(unit);
            if (key != null && !entities.containsKey(key)) {
                entities.put(key, num);
            }
        }

        return entities;
    }

    /**
     * 将中文量词映射为 entities key。
     */
    private static String unitToKey(String unit) {
        switch (unit) {
            case "天": return "days";
            case "个": return "count";
            case "次": return "times";
            case "条": return "items";
            case "项": return "items";
            case "级": return "level";
            case "度": return null; // 已被 temperature 捕获
            case "人": return "people";
            case "份": return "portions";
            case "页": return "pages";
            case "小时": return "hours";
            case "分钟": return "minutes";
            case "秒": return "seconds";
            case "周": return "weeks";
            case "月": return "months";
            case "年": return "years";
            default: return null;
        }
    }

    // ------------------------------------------------------------

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
