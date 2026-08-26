package com.amap.agenuiplayground.widget;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模板推荐器 — 结合当前意图匹配 + 历史上下文，返回推荐模板列表。
 *
 * <p>参考 Coze/Dify 的意图路由 + 上下文续接机制：
 * <ol>
 *   <li>用 {@link WidgetIntentMatcher#matchWithScore} 获取当前意图置信度</li>
 *   <li>从 {@link WidgetConversationMemory} 读取历史，若当前输入是追问/续接
 *       （如"那明天的呢"），则继承上一轮的模板</li>
 *   <li>综合两者给出推荐列表，按优先级排序</li>
 * </ol>
 *
 * <p>典型场景：
 * <ul>
 *   <li>用户先问"今天北京天气"（weather），再说"温度呢" → 推荐 weather</li>
 *   <li>用户说"帮我做个投票"（poll） → 推荐 poll</li>
 *   <li>用户说"随便看看" → 返回默认推荐列表</li>
 * </ul>
 */
public class WidgetTemplateRecommender {

    private static final String TAG = "WidgetTemplateRecomm";

    /**
     * 推荐结果项。
     */
    public static class Recommendation {
        /** 推荐的模板名 */
        public final String template;
        /** 推荐置信度 0.0-1.0 */
        public final float score;
        /** 推荐来源说明 */
        public final String reason;

        public Recommendation(String template, float score, String reason) {
            this.template = template;
            this.score = score;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "Recommendation{template='" + template + "', score=" + score
                    + ", reason='" + reason + "'}";
        }
    }

    /**
     * 续接指示词 — 当用户输入包含这些词时，倾向于继承上一轮的模板。
     */
    private static final List<String> CONTINUATION_CUES = Arrays.asList(
            "呢", "那", "还有", "另外", "再", "也", "和", "跟", "然后",
            "关于", "对", "这个", "那个", "它"
    );

    /**
     * 默认推荐列表（当无明确意图时）。
     * 按通用程度排序：天气 > 待办 > 日程 > 笔记 > 日历 > 投票。
     */
    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            "weather", "todo", "agenda", "note", "calendar", "poll"
    );

    private WidgetTemplateRecommender() {
        // utility class
    }

    /**
     * 推荐模板列表 — 结合当前意图匹配 + 历史上下文。
     *
     * @param userText          当前用户输入
     * @param conversationMemory 对话记忆（可为 null）
     * @return 按置信度排序的推荐列表，至少返回 1 项；首个为最佳推荐
     */
    public static List<Recommendation> recommend(String userText,
                                                  WidgetConversationMemory conversationMemory) {
        List<Recommendation> result = new ArrayList<>();
        if (userText == null || userText.trim().isEmpty()) {
            // 空输入：返回默认列表
            for (String t : DEFAULT_ORDER) {
                result.add(new Recommendation(t, 0.2f, "默认推荐"));
            }
            return result;
        }

        String text = userText.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        // 1. 当前意图匹配（带置信度）
        WidgetIntentMatcher.IntentMatch currentMatch =
                WidgetIntentMatcher.matchWithScore(text);

        // 2. 历史上下文续接判断
        boolean isContinuation = isContinuationQuery(lower);
        String lastTemplate = null;
        String lastUserText = null;
        if (conversationMemory != null) {
            lastTemplate = conversationMemory.getLastTemplate();
            lastUserText = conversationMemory.getLastUserText();
        }
        boolean hasHistory = lastTemplate != null;

        // 3. 综合推荐
        // 情况 A：当前匹配到明确意图且置信度高 → 首选当前匹配
        if (currentMatch != null && currentMatch.score >= 0.5f) {
            result.add(new Recommendation(
                    currentMatch.template, currentMatch.score, "当前意图匹配"));
        }

        // 情况 B：当前是续接问句 + 有历史 → 继承上一轮模板
        if (isContinuation && hasHistory) {
            float inheritScore = currentMatch != null
                    ? Math.max(currentMatch.score, 0.6f) : 0.6f;
            // 若与首选不同，追加；若相同则提升分数
            if (!result.isEmpty() && result.get(0).template.equals(lastTemplate)) {
                // 已在列表，提升分数
                Recommendation existing = result.get(0);
                result.set(0, new Recommendation(existing.template,
                        Math.max(existing.score, inheritScore),
                        existing.reason + " + 上下文续接"));
            } else {
                result.add(new Recommendation(lastTemplate, inheritScore,
                        "上下文续接（上一轮：" + lastTemplate + "）"));
            }
        }

        // 情况 C：当前匹配到但置信度一般（0.3-0.5）→ 追加但分数较低
        if (currentMatch != null && currentMatch.score < 0.5f
                && (result.isEmpty() || !result.get(0).template.equals(currentMatch.template))) {
            result.add(new Recommendation(currentMatch.template, currentMatch.score,
                    "弱意图匹配"));
        }

        // 情况 D：若仍为空，使用默认列表
        if (result.isEmpty()) {
            // 若有历史，把上一轮模板放第一
            if (hasHistory) {
                result.add(new Recommendation(lastTemplate, 0.4f,
                        "回退到上一轮模板"));
            }
            for (String t : DEFAULT_ORDER) {
                if (!hasHistory || !t.equals(lastTemplate)) {
                    result.add(new Recommendation(t, 0.2f, "默认推荐"));
                }
            }
        }

        // 补全默认推荐（保证列表长度 >= 3）
        for (String t : DEFAULT_ORDER) {
            boolean exists = false;
            for (Recommendation r : result) {
                if (r.template.equals(t)) { exists = true; break; }
            }
            if (!exists) {
                result.add(new Recommendation(t, 0.15f, "备选"));
            }
            if (result.size() >= 5) break;
        }

        Log.d(TAG, "recommend: \"" + truncate(text, 30) + "\" → "
                + result.size() + " recommendations, top=" + result.get(0));
        return result;
    }

    /**
     * 判断当前输入是否为续接/追问。
     *
     * <p>启发式规则：
     * <ul>
     *   <li>输入较短（< 10 字符）且包含续接指示词</li>
     *   <li>输入以续接指示词开头</li>
     *   <li>输入包含"呢"字（典型追问）</li>
     * </ul>
     */
    private static boolean isContinuationQuery(String lower) {
        if (lower.isEmpty()) return false;
        // 包含"呢"字 → 典型追问
        if (lower.contains("呢")) return true;
        // 以续接指示词开头
        for (String cue : CONTINUATION_CUES) {
            if (lower.startsWith(cue)) return true;
        }
        // 输入较短且包含任意续接词
        if (lower.length() < 10) {
            for (String cue : CONTINUATION_CUES) {
                if (lower.contains(cue)) return true;
            }
        }
        return false;
    }

    /**
     * 返回最佳推荐模板名（列表第一项），或 null。
     */
    public static String recommendTop(String userText,
                                       WidgetConversationMemory conversationMemory) {
        List<Recommendation> list = recommend(userText, conversationMemory);
        if (list.isEmpty()) return null;
        // 只返回 score >= 0.3 的，否则 null
        Recommendation top = list.get(0);
        return top.score >= 0.3f ? top.template : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
