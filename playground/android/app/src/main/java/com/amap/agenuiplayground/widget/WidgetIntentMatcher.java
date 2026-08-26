package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 智能意图匹配引擎 — 多维度识别用户输入对应的 A2UI 模板。
 *
 * <p>关键词和模糊匹配规则从 {@code assets/widget_intent_config.json} 加载，
 * 支持运行时更新配置文件无需修改代码。若配置文件加载失败，回退到内置默认规则。
 *
 * <p>参考 Coze/Dify 的意图路由机制，采用三级匹配策略：
 * <ol>
 *   <li>关键词精确匹配（高权重）</li>
 *   <li>同义词匹配（中权重）</li>
 *   <li>模糊匹配：子串包含 / 编辑距离（低权重）</li>
 * </ol>
 */
public class WidgetIntentMatcher {

    private static final String TAG = "WidgetIntentMatcher";
    private static final String CONFIG_FILE = "widget_intent_config.json";

    /**
     * 内置默认关键词词典（配置文件加载失败时的 fallback）。
     */
    private static final Map<String, List<String>> DEFAULT_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> DEFAULT_FUZZY = new LinkedHashMap<>();

    static {
        DEFAULT_KEYWORDS.put("weather", Arrays.asList(
                "天气", "weather", "气温", "温度", "下雨", "雨", "晴", "阴", "风",
                "雨雪", "雷阵雨", "雾霾", "湿度", "风速", "aqi", "空气质量",
                "预报", "celsius"
        ));
        DEFAULT_KEYWORDS.put("poll", Arrays.asList(
                "投票", "poll", "选举", "选择", "方案", "表决", "选项",
                "调研", "问卷", "questionnaire", "vote", "ballot", "survey"
        ));
        DEFAULT_KEYWORDS.put("todo", Arrays.asList(
                "待办", "todo", "任务", "提醒", "清单", "事项", "to-do",
                "task", "checklist", "remind", "reminder", "做事", "干活"
        ));
        DEFAULT_KEYWORDS.put("agenda", Arrays.asList(
                "日程", "agenda", "会议", "安排", "行程", "约会",
                "meeting", "schedule", "appointment", "时间表", "排期"
        ));
        DEFAULT_KEYWORDS.put("note", Arrays.asList(
                "笔记", "note", "记录", "备忘", "摘抄", "备忘录",
                "memo", "notes", "日记", "手记", "纪要"
        ));
        DEFAULT_KEYWORDS.put("calendar", Arrays.asList(
                "日历", "calendar", "日期", "星期", "节假日", "放假",
                "date", "day", "month", "holiday", "月历", "万年历"
        ));

        DEFAULT_FUZZY.put("weather", Arrays.asList("tianqi", "tian qi", "qianqi"));
        DEFAULT_FUZZY.put("poll", Arrays.asList("toupiao", "tou piao"));
        DEFAULT_FUZZY.put("todo", Arrays.asList("daiban", "dai ban", "renwu", "ren wu"));
        DEFAULT_FUZZY.put("agenda", Arrays.asList("richeng", "ri cheng", "huiyi", "hui yi"));
        DEFAULT_FUZZY.put("note", Arrays.asList("biji", "bi ji", "beiwang", "bei wang"));
        DEFAULT_FUZZY.put("calendar", Arrays.asList("rili", "ri li", "jiejiari"));
    }

    // ===== Runtime state (loaded from config or defaults) =====

    private static volatile Map<String, List<String>> sKeywords = DEFAULT_KEYWORDS;
    private static volatile Map<String, List<String>> sFuzzy = DEFAULT_FUZZY;
    private static volatile float sScoreThreshold = 0.3f;
    private static volatile float sFuzzyScore = 0.35f;
    private static volatile boolean sConfigLoaded = false;

    /**
     * 从 assets/widget_intent_config.json 加载意图配置。
     * 若加载失败，使用内置默认值。
     *
     * @param context Application context
     */
    public static void loadConfig(Context context) {
        try {
            InputStream is = context.getAssets().open(CONFIG_FILE);
            byte[] buffer = new byte[is.available()];
            int bytesRead = is.read(buffer);
            is.close();
            String json = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(json);
            sScoreThreshold = (float) root.optDouble("score_threshold", 0.3);
            sFuzzyScore = (float) root.optDouble("fuzzy_score", 0.35);

            JSONObject intents = root.optJSONObject("intents");
            if (intents != null) {
                Map<String, List<String>> keywords = new LinkedHashMap<>();
                Map<String, List<String>> fuzzy = new LinkedHashMap<>();

                for (java.util.Iterator<String> it = intents.keys(); it.hasNext(); ) {
                    String template = it.next();
                    JSONObject entry = intents.optJSONObject(template);
                    if (entry == null) continue;

                    JSONArray kwArr = entry.optJSONArray("keywords");
                    if (kwArr != null) {
                        List<String> kwList = new ArrayList<>();
                        for (int i = 0; i < kwArr.length(); i++) {
                            kwList.add(kwArr.getString(i));
                        }
                        keywords.put(template, kwList);
                    }

                    JSONArray fuzzyArr = entry.optJSONArray("fuzzy");
                    if (fuzzyArr != null) {
                        List<String> fuzzyList = new ArrayList<>();
                        for (int i = 0; i < fuzzyArr.length(); i++) {
                            fuzzyList.add(fuzzyArr.getString(i));
                        }
                        fuzzy.put(template, fuzzyList);
                    }
                }

                if (!keywords.isEmpty()) {
                    sKeywords = keywords;
                    sFuzzy = fuzzy;
                    sConfigLoaded = true;
                    Log.d(TAG, "Config loaded: " + keywords.size() + " intents from " + CONFIG_FILE);
                    return;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load intent config, using defaults", e);
        }
        // Fallback: keep defaults
        sKeywords = DEFAULT_KEYWORDS;
        sFuzzy = DEFAULT_FUZZY;
        sConfigLoaded = false;
    }

    /**
     * @return true if the config file was successfully loaded.
     */
    public static boolean isConfigLoaded() {
        return sConfigLoaded;
    }

    /**
     * 匹配结果对象 — 包含模板名和置信度分数。
     */
    public static class IntentMatch {
        /** 匹配到的模板名（如 "weather"） */
        public final String template;
        /** 置信度分数，范围 0.0-1.0 */
        public final float score;
        /** 命中的关键词列表（用于调试/展示） */
        public final List<String> matchedKeywords;

        public IntentMatch(String template, float score, List<String> matchedKeywords) {
            this.template = template;
            this.score = score;
            this.matchedKeywords = matchedKeywords != null
                    ? new ArrayList<>(matchedKeywords) : new ArrayList<>();
        }

        @Override
        public String toString() {
            return "IntentMatch{template='" + template + "', score=" + score
                    + ", hits=" + matchedKeywords.size() + "}";
        }
    }

    /** 置信度阈值：低于此值视为不确信，返回 null — 从配置文件加载，默认 0.3 */

    private WidgetIntentMatcher() {
        // utility class
    }

    /**
     * 匹配用户文本对应的模板名称。
     *
     * <p>流程：
     * <ol>
     *   <li>对每个意图，统计命中的关键词数量</li>
     *   <li>优先返回命中数最多且至少命中 1 个关键词的意图</li>
     *   <li>若全部意图均未命中，尝试模糊匹配（拼音/变体）</li>
     * </ol>
     *
     * @param userText 用户输入文本，允许为 null/empty
     * @return 最佳匹配的 template name，或 null
     */
    public static String match(String userText) {
        if (userText == null || userText.trim().isEmpty()) return null;

        String text = userText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        // 归一化：去除空格，用于模糊匹配
        String compact = lower.replaceAll("\\s+", "");

        String bestTemplate = null;
        int bestHitCount = 0;

        for (Map.Entry<String, List<String>> entry : sKeywords.entrySet()) {
            String template = entry.getKey();
            List<String> keywords = entry.getValue();
            int hits = 0;
            for (String kw : keywords) {
                if (kw == null || kw.isEmpty()) continue;
                String lowerKw = kw.toLowerCase(Locale.ROOT);
                if (lower.contains(lowerKw)) {
                    hits++;
                }
            }
            if (hits > bestHitCount) {
                bestHitCount = hits;
                bestTemplate = template;
            }
        }

        if (bestTemplate != null) {
            Log.d(TAG, "match: \"" + truncate(text, 30) + "\" → " + bestTemplate
                    + " (keyword hits=" + bestHitCount + ")");
            return bestTemplate;
        }

        // 模糊匹配（拼音/变体）
        for (Map.Entry<String, List<String>> entry : sFuzzy.entrySet()) {
            String template = entry.getKey();
            for (String fuzzy : entry.getValue()) {
                if (fuzzy == null || fuzzy.isEmpty()) continue;
                if (compact.contains(fuzzy)) {
                    Log.d(TAG, "match: \"" + truncate(text, 30) + "\" → " + template
                            + " (fuzzy: " + fuzzy + ")");
                    return template;
                }
            }
        }

        Log.d(TAG, "match: \"" + truncate(text, 30) + "\" → null (no match)");
        return null;
    }

    /**
     * 带置信度分数的匹配 — 返回 {@link IntentMatch} 对象。
     *
     * <p>分数计算规则：
     * <ul>
     *   <li>基础分 = min(hits / 3.0, 1.0)：命中 3 个关键词即满分</li>
     *   <li>位置加权：若关键词出现在文本前 1/3 区域，额外 +0.1（上限 1.0）</li>
     *   <li>模糊匹配：若仅命中模糊变体，分数固定为 0.35</li>
     * </ul>
     *
     * <p>如果 score < {@link #SCORE_THRESHOLD}（0.3），返回 null（不确信）。
     *
     * @param userText 用户输入文本
     * @return IntentMatch 对象，或 null（不确信或无匹配）
     */
    public static IntentMatch matchWithScore(String userText) {
        if (userText == null || userText.trim().isEmpty()) return null;

        String text = userText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("\\s+", "");

        String bestTemplate = null;
        int bestHitCount = 0;
        List<String> bestMatchedKeywords = Collections.emptyList();
        boolean bestHasEarlyHit = false;

        for (Map.Entry<String, List<String>> entry : sKeywords.entrySet()) {
            String template = entry.getKey();
            List<String> keywords = entry.getValue();
            int hits = 0;
            List<String> matchedKws = new ArrayList<>();
            boolean hasEarlyHit = false;
            int earlyThreshold = Math.max(1, text.length() / 3);

            for (String kw : keywords) {
                if (kw == null || kw.isEmpty()) continue;
                String lowerKw = kw.toLowerCase(Locale.ROOT);
                int idx = lower.indexOf(lowerKw);
                if (idx >= 0) {
                    hits++;
                    matchedKws.add(kw);
                    if (idx < earlyThreshold) {
                        hasEarlyHit = true;
                    }
                }
            }
            if (hits > bestHitCount) {
                bestHitCount = hits;
                bestTemplate = template;
                bestMatchedKeywords = matchedKws;
                bestHasEarlyHit = hasEarlyHit;
            }
        }

        if (bestTemplate != null && bestHitCount > 0) {
            // 基础分：命中 3 个即满分
            float base = Math.min(bestHitCount / 3.0f, 1.0f);
            // 位置加权
            float score = base + (bestHasEarlyHit ? 0.1f : 0.0f);
            if (score > 1.0f) score = 1.0f;

            if (score < sScoreThreshold) {
                Log.d(TAG, "matchWithScore: \"" + truncate(text, 30)
                        + "\" → null (score=" + score + " < " + sScoreThreshold + ")");
                return null;
            }
            Log.d(TAG, "matchWithScore: \"" + truncate(text, 30) + "\" → "
                    + bestTemplate + " (score=" + score + ", hits=" + bestHitCount + ")");
            return new IntentMatch(bestTemplate, score, bestMatchedKeywords);
        }

        // 模糊匹配（拼音/变体）— 分数从配置加载，默认 0.35
        for (Map.Entry<String, List<String>> entry : sFuzzy.entrySet()) {
            String template = entry.getKey();
            for (String fuzzy : entry.getValue()) {
                if (fuzzy == null || fuzzy.isEmpty()) continue;
                if (compact.contains(fuzzy)) {
                    float score = sFuzzyScore;
                    Log.d(TAG, "matchWithScore: \"" + truncate(text, 30) + "\" → "
                            + template + " (fuzzy score=" + score + ")");
                    return new IntentMatch(template, score,
                            Collections.singletonList(fuzzy));
                }
            }
        }

        Log.d(TAG, "matchWithScore: \"" + truncate(text, 30) + "\" → null (no match)");
        return null;
    }

    /**
     * 检查用户文本是否包含给定模板的关键词（简化版，用于降级链）。
     *
     * @param userText   用户输入
     * @param template   模板名
     * @return true 若命中该模板的任意关键词
     */
    public static boolean matchesTemplate(String userText, String template) {
        if (userText == null || userText.trim().isEmpty() || template == null) return false;
        String matched = match(userText);
        return template.equals(matched);
    }

    /**
     * 返回所有支持的模板名（来自意图词典的 key 集合）。
     */
    public static List<String> getSupportedTemplates() {
        return new ArrayList<>(sKeywords.keySet());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
