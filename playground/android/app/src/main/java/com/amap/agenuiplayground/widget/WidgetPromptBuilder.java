package com.amap.agenuiplayground.widget;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the System Prompt for LLM-based A2UI generation.
 *
 * Ported from A2UIPrompt.kt (agenui-demo) to Java.
 * Includes A2UI v0.9 protocol essentials, component catalog, and few-shot examples.
 *
 * Phase 3A: supports dynamic few-shot from generation history —
 * {@link #buildMessagesWithHistory} appends up to 3 prior successful
 * examples (classified by keyword: weather / todo / agenda / general)
 * so the LLM is more likely to produce valid JSON.
 */
public final class WidgetPromptBuilder {

    private static final String TAG = "WidgetPromptBuilder";
    private static final String TRIPLE_BACKTICK = "```";
    private static final String DOLLAR = "$";

    public static final String SYSTEM_PROMPT = buildSystemPrompt();

    /** Keyword categories used to match few-shot examples to the user request. */
    private static final String CATEGORY_WEATHER = "weather";
    private static final String CATEGORY_TODO = "todo";
    private static final String CATEGORY_AGENDA = "agenda";
    private static final String CATEGORY_GENERAL = "general";

    private WidgetPromptBuilder() {
        // utility class
    }

    private static String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名 A2UI 前端生成专家，擅长根据用户的自然语言描述，生成符合 A2UI v0.9 协议的 JSON 组件描述。\n");
        sb.append("\n");
        sb.append("【A2UI 协议要点】\n");
        sb.append("1. 响应格式：用 ").append(TRIPLE_BACKTICK).append("a2ui 代码块包裹完整的 JSON，")
                .append("JSON 顶层包含 version 和 updateComponents 两个字段\n");
        sb.append("2. 组件体系：\n");
        sb.append("   - 布局：Column（纵向）、Row（横向）\n");
        sb.append("   - 基础：Text（文本）、Button（按钮，子组件用 child 属性）、Image（图片）、Card（卡片）、Divider（分割线）\n");
        sb.append("   - 输入：TextField（文本输入，value 属性）、CheckBox（复选框，checked 属性）\n");
        sb.append("   - 列表：List（列表，dataSource/itemTemplate）、Carousel（轮播）\n");
        sb.append("   - 反馈：ProgressBar（进度条）、Modal（弹窗）、Slider（滑块）\n");
        sb.append("   - 数据绑定：在 text 属性中用 ").append(DOLLAR).append("{/path/to/value} 语法引用 dataModel 数据\n");
        sb.append("   - 一个投票/选项类 UI 用 Column 嵌套 Row 即可，不必也不要生成过深的组件树\n");
        sb.append("3. 交互事件：\n");
        sb.append("   - 按钮 action: { \"functionCall\": { \"call\": \"函数名\", \"args\": {...} } }\n");
        sb.append("   - 内置函数：toast(message, duration)、navigate(route)、log(level, tag, message)\n");
        sb.append("4. 样式：styles 对象，支持 width/height/padding/margin/background-color/color/font-size/font-weight/border-radius/text-align/gap 等\n");
        sb.append("5. 尺寸单位：px（逻辑像素）\n");
        sb.append("\n");
        sb.append("【输出要求】\n");
        sb.append("- 只输出一个 ").append(TRIPLE_BACKTICK).append("a2ui 代码块，代码块内是完整合法的 JSON\n");
        sb.append("- 不要输出多余的解释文字\n");
        sb.append("- surfaceId 固定为 \"ai-generated\"\n");
        sb.append("- 确保 JSON 语法正确，没有 trailing comma\n");
        sb.append("- **JSON 必须完整闭合**：所有 { } [ ] 和字符串引号都要收尾；如果内容较长，优先精简文案，不要中途截断\n");
        sb.append("- 组件 id 用有意义的英文命名，如 root/title/btn-submit/input-name 等\n");
        sb.append("\n");
        sb.append("【示例 1：天气卡片】\n");
        sb.append("用户：生成一个天气卡片\n");
        sb.append("你：\n");
        sb.append(TRIPLE_BACKTICK).append("a2ui\n");
        sb.append("{\n");
        sb.append("  \"version\": \"v0.9\",\n");
        sb.append("  \"updateComponents\": {\n");
        sb.append("    \"surfaceId\": \"ai-generated\",\n");
        sb.append("    \"components\": [\n");
        sb.append("      {\"id\": \"root\", \"component\": \"Card\", \"children\": [\"content\"], \"styles\": {\"margin\": {\"all\": 8}, \"border-radius\": \"12px\"}},\n");
        sb.append("      {\"id\": \"content\", \"component\": \"Column\", \"children\": [\"city\", \"temp\", \"detail\"], \"styles\": {\"padding\": {\"all\": 16}, \"crossAxisAlignment\": \"center\"}},\n");
        sb.append("      {\"id\": \"city\", \"component\": \"Text\", \"text\": \"北京\", \"styles\": {\"font-size\": \"18px\", \"font-weight\": \"bold\"}},\n");
        sb.append("      {\"id\": \"temp\", \"component\": \"Text\", \"text\": \"28°C 晴\", \"styles\": {\"font-size\": \"32px\", \"font-weight\": \"bold\"}},\n");
        sb.append("      {\"id\": \"detail\", \"component\": \"Text\", \"text\": \"湿度 45% · 东南风 3级\", \"styles\": {\"font-size\": \"12px\", \"color\": \"#666666\"}}\n");
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append(TRIPLE_BACKTICK).append("\n");
        sb.append("\n");
        sb.append("【示例 2：待办清单】\n");
        sb.append("用户：生成一个待办清单\n");
        sb.append("你：\n");
        sb.append(TRIPLE_BACKTICK).append("a2ui\n");
        sb.append("{\n");
        sb.append("  \"version\": \"v0.9\",\n");
        sb.append("  \"updateComponents\": {\n");
        sb.append("    \"surfaceId\": \"ai-generated\",\n");
        sb.append("    \"components\": [\n");
        sb.append("      {\"id\": \"root\", \"component\": \"Card\", \"children\": [\"title\", \"item1\", \"item2\", \"item3\"], \"styles\": {\"margin\": {\"all\": 8}, \"border-radius\": \"12px\"}},\n");
        sb.append("      {\"id\": \"title\", \"component\": \"Text\", \"text\": \"今日待办\", \"styles\": {\"font-size\": \"18px\", \"font-weight\": \"bold\", \"padding\": {\"all\": 16}}},\n");
        sb.append("      {\"id\": \"item1\", \"component\": \"Row\", \"children\": [\"check1\", \"label1\"], \"styles\": {\"padding\": {\"left\": 16, \"right\": 16, \"bottom\": 8}, \"crossAxisAlignment\": \"center\"}},\n");
        sb.append("      {\"id\": \"check1\", \"component\": \"CheckBox\", \"checked\": false},\n");
        sb.append("      {\"id\": \"label1\", \"component\": \"Text\", \"text\": \"完成项目报告\", \"styles\": {\"font-size\": \"14px\", \"padding\": {\"left\": 8}}},\n");
        sb.append("      {\"id\": \"item2\", \"component\": \"Row\", \"children\": [\"check2\", \"label2\"], \"styles\": {\"padding\": {\"left\": 16, \"right\": 16, \"bottom\": 8}, \"crossAxisAlignment\": \"center\"}},\n");
        sb.append("      {\"id\": \"check2\", \"component\": \"CheckBox\", \"checked\": false},\n");
        sb.append("      {\"id\": \"label2\", \"component\": \"Text\", \"text\": \"回复邮件\", \"styles\": {\"font-size\": \"14px\", \"padding\": {\"left\": 8}}},\n");
        sb.append("      {\"id\": \"item3\", \"component\": \"Row\", \"children\": [\"check3\", \"label3\"], \"styles\": {\"padding\": {\"left\": 16, \"right\": 16, \"bottom\": 16}, \"crossAxisAlignment\": \"center\"}},\n");
        sb.append("      {\"id\": \"check3\", \"component\": \"CheckBox\", \"checked\": true},\n");
        sb.append("      {\"id\": \"label3\", \"component\": \"Text\", \"text\": \"准备演示文稿\", \"styles\": {\"font-size\": \"14px\", \"padding\": {\"left\": 8}, \"color\": \"#999999\"}}\n");
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append(TRIPLE_BACKTICK).append("\n");
        return sb.toString();
    }

    /**
     * Builds the messages array as a JSON string for OpenAI-compatible API.
     * Format: [{"role":"system","content":"..."},{"role":"user","content":"..."}]
     */
    public static String buildMessagesJson(String systemPrompt, String userText) {
        // Escape JSON special chars in content
        String escapedSystem = escapeJson(systemPrompt);
        String escapedUser = escapeJson(userText);
        return "[{\"role\":\"system\",\"content\":\"" + escapedSystem + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapedUser + "\"}]";
    }

    /**
     * Phase 3A: Builds the messages array with dynamic few-shot examples
     * taken from the generation history.
     *
     * <p>Layout: [system, …few-shot user/assistant pairs, user]. The
     * few-shot examples are selected by matching the current userText's
     * keyword category (weather / todo / agenda / general) against prior
     * successful generations; up to 3 examples are appended.
     *
     * <p>If history is null or has no successful records, falls back to the
     * plain two-message format (system + user).
     *
     * @param systemPrompt System prompt (typically {@link #SYSTEM_PROMPT})
     * @param userText User input text for this request
     * @param history Repository to source few-shot examples from (may be null)
     * @return JSON messages array string
     */
    public static String buildMessagesWithHistory(String systemPrompt, String userText,
                                                   WidgetHistoryRepository history) {
        if (history == null) {
            return buildMessagesJson(systemPrompt, userText);
        }

        String category = classifyCategory(userText);
        List<WidgetHistoryRepository.FewShotExample> all =
                history.getRecentSuccessfulExamples(20);
        if (all.isEmpty()) {
            return buildMessagesJson(systemPrompt, userText);
        }

        // Filter examples by keyword category, then top up with general ones.
        List<WidgetHistoryRepository.FewShotExample> selected = selectByCategory(all, category, 3);
        if (selected.isEmpty()) {
            // No category matches — fall back to plain format to avoid
            // polluting the prompt with off-domain examples.
            Log.d(TAG, "No category-matching few-shot examples; using plain prompt");
            return buildMessagesJson(systemPrompt, userText);
        }

        Log.d(TAG, "Few-shot: category=" + category + ", examples=" + selected.size());

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");
        for (WidgetHistoryRepository.FewShotExample ex : selected) {
            sb.append(",");
            sb.append("{\"role\":\"user\",\"content\":\"")
                    .append(escapeJson(ex.prompt)).append("\"}");
            sb.append(",");
            sb.append("{\"role\":\"assistant\",\"content\":\"")
                    .append(escapeJson(ex.a2uiJson)).append("\"}");
        }
        sb.append(",");
        sb.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userText)).append("\"}");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Classifies a user prompt into one of the keyword categories used for
     * few-shot selection.
     */
    private static String classifyCategory(String text) {
        if (text == null) return CATEGORY_GENERAL;
        String lower = text.toLowerCase();
        if (lower.contains("天气") || lower.contains("weather") || lower.contains("气温")) {
            return CATEGORY_WEATHER;
        }
        if (lower.contains("待办") || lower.contains("todo") || lower.contains("清单")
                || lower.contains("任务")) {
            return CATEGORY_TODO;
        }
        if (lower.contains("议程") || lower.contains("日程") || lower.contains("agenda")
                || lower.contains("schedule")) {
            return CATEGORY_AGENDA;
        }
        return CATEGORY_GENERAL;
    }

    /**
     * Selects up to {@code limit} examples whose prompt matches the given
     * category. If fewer than {@code limit} category-matches are found, the
     * remainder is filled with general (uncategorized) examples.
     */
    private static List<WidgetHistoryRepository.FewShotExample> selectByCategory(
            List<WidgetHistoryRepository.FewShotExample> all, String category, int limit) {
        List<WidgetHistoryRepository.FewShotExample> matched = new ArrayList<>();
        List<WidgetHistoryRepository.FewShotExample> general = new ArrayList<>();
        for (WidgetHistoryRepository.FewShotExample ex : all) {
            String cat = classifyCategory(ex.prompt);
            if (category.equals(cat)) {
                if (matched.size() < limit) matched.add(ex);
            } else if (CATEGORY_GENERAL.equals(cat)) {
                if (general.size() < limit) general.add(ex);
            }
        }
        // Top up with general examples if we don't have enough matched ones.
        for (WidgetHistoryRepository.FewShotExample ex : general) {
            if (matched.size() >= limit) break;
            matched.add(ex);
        }
        return matched;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
