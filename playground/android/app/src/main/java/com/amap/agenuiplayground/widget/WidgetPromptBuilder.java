package com.amap.agenuiplayground.widget;

/**
 * Builds the System Prompt for LLM-based A2UI generation.
 *
 * Ported from A2UIPrompt.kt (agenui-demo) to Java.
 * Includes A2UI v0.9 protocol essentials, component catalog, and few-shot examples.
 */
public final class WidgetPromptBuilder {

    private static final String TRIPLE_BACKTICK = "```";
    private static final String DOLLAR = "$";

    public static final String SYSTEM_PROMPT = buildSystemPrompt();

    private WidgetPromptBuilder() {
        // utility class
    }

    /**
     * 构建 system prompt — 如果 NLU 提取到了实体，在基础 prompt 后注入实体提示。
     *
     * <p>注入格式示例：
     * <pre>
     * 【用户提到的实体】
     * location=北京, time=明天, temperature=23
     * 请在生成 A2UI 时参考这些实体值，填充到对应组件的 text 或 dataModel 中。
     * </pre>
     *
     * @param nlu NLU 解析结果，可为 null
     * @return system prompt 字符串
     */
    public static String buildSystemPromptWithNLU(
            WidgetNLUParser.NLUResult nlu) {
        if (nlu == null || !nlu.hasAnyEntity()) {
            return SYSTEM_PROMPT;
        }
        String hint = nlu.toPromptHint();
        if (hint == null || hint.isEmpty()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n【用户提到的实体】\n"
                + hint
                + "\n请在生成 A2UI 时参考这些实体值，"
                + "填充到对应组件的 text 或 dataModel 中。";
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
        sb.append("【示例 1：天气卡片（丰富版）】\n");
        sb.append("用户：生成一个天气卡片\n");
        sb.append("你：\n");
        sb.append(TRIPLE_BACKTICK).append("a2ui\n");
        sb.append("{\n");
        sb.append("  \"version\": \"v0.9\",\n");
        sb.append("  \"updateComponents\": {\n");
        sb.append("    \"surfaceId\": \"ai-generated\",\n");
        sb.append("    \"components\": [\n");
        sb.append("      {\"id\": \"root\", \"component\": \"Card\", \"children\": [\"col\"], \"styles\": {\"border-radius\": \"16px\", \"background-color\": \"#FFFFFF\", \"elevation\": 2}},\n");
        sb.append("      {\"id\": \"col\", \"component\": \"Column\", \"children\": [\"header\", \"divider\", \"details\"]},\n");
        sb.append("      {\"id\": \"header\", \"component\": \"Row\", \"children\": [\"iconWrap\", \"cityCol\", \"temp\"], \"styles\": {\"padding\": {\"left\": 16, \"right\": 16, \"top\": 16, \"bottom\": 12}, \"alignItems\": \"center\"}},\n");
        sb.append("      {\"id\": \"iconWrap\", \"component\": \"Container\", \"child\": \"iconText\", \"styles\": {\"width\": 48, \"height\": 48, \"border-radius\": \"999px\", \"background-color\": \"#E8F3FF\", \"alignItems\": \"center\", \"justifyContent\": \"center\"}},\n");
        sb.append("      {\"id\": \"iconText\", \"component\": \"Text\", \"text\": \"☀\", \"styles\": {\"font-size\": \"24px\"}},\n");
        sb.append("      {\"id\": \"cityCol\", \"component\": \"Column\", \"children\": [\"city\", \"desc\"], \"styles\": {\"margin-left\": 12, \"flex\": 1}},\n");
        sb.append("      {\"id\": \"city\", \"component\": \"Text\", \"text\": \"北京\", \"styles\": {\"font-size\": \"16px\", \"font-weight\": \"bold\", \"color\": \"#181818\"}},\n");
        sb.append("      {\"id\": \"desc\", \"component\": \"Text\", \"text\": \"晴 · 微风\", \"styles\": {\"font-size\": \"14px\", \"color\": \"#666666\"}},\n");
        sb.append("      {\"id\": \"temp\", \"component\": \"Text\", \"text\": \"23°\", \"styles\": {\"font-size\": \"30px\", \"font-weight\": \"bold\", \"color\": \"#007DFF\"}},\n");
        sb.append("      {\"id\": \"divider\", \"component\": \"Divider\", \"styles\": {\"background-color\": \"#E8EAED\", \"height\": 1, \"margin-left\": 16, \"margin-right\": 16}},\n");
        sb.append("      {\"id\": \"details\", \"component\": \"Row\", \"children\": [\"humi\", \"wind\", \"aqi\"], \"styles\": {\"padding\": {\"left\": 16, \"right\": 16, \"top\": 12, \"bottom\": 16}}},\n");
        sb.append("      {\"id\": \"humi\", \"component\": \"Column\", \"children\": [\"humiL\", \"humiV\"], \"styles\": {\"flex\": 1, \"alignItems\": \"center\"}},\n");
        sb.append("      {\"id\": \"humiL\", \"component\": \"Text\", \"text\": \"湿度\", \"styles\": {\"font-size\": \"12px\", \"color\": \"#999999\"}},\n");
        sb.append("      {\"id\": \"humiV\", \"component\": \"Text\", \"text\": \"45%\", \"styles\": {\"font-size\": \"14px\", \"color\": \"#181818\", \"margin-top\": 4}},\n");
        sb.append("      {\"id\": \"wind\", \"component\": \"Column\", \"children\": [\"windL\", \"windV\"], \"styles\": {\"flex\": 1, \"alignItems\": \"center\"}},\n");
        sb.append("      {\"id\": \"windL\", \"component\": \"Text\", \"text\": \"风速\", \"styles\": {\"font-size\": \"12px\", \"color\": \"#999999\"}},\n");
        sb.append("      {\"id\": \"windV\", \"component\": \"Text\", \"text\": \"3级\", \"styles\": {\"font-size\": \"14px\", \"color\": \"#181818\", \"margin-top\": 4}},\n");
        sb.append("      {\"id\": \"aqi\", \"component\": \"Column\", \"children\": [\"aqiL\", \"aqiV\"], \"styles\": {\"flex\": 1, \"alignItems\": \"center\"}},\n");
        sb.append("      {\"id\": \"aqiL\", \"component\": \"Text\", \"text\": \"AQI\", \"styles\": {\"font-size\": \"12px\", \"color\": \"#999999\"}},\n");
        sb.append("      {\"id\": \"aqiV\", \"component\": \"Text\", \"text\": \"优 42\", \"styles\": {\"font-size\": \"14px\", \"color\": \"#181818\", \"margin-top\": 4}}\n");
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
     * Builds the messages array with few-shot history examples.
     */
    public static String buildMessagesWithHistory(String systemPrompt, String userText,
                                                   WidgetHistoryRepository historyRepository) {
        if (historyRepository == null) {
            return buildMessagesJson(systemPrompt, userText);
        }

        java.util.List<WidgetHistoryRepository.FewShotExample> examples =
                historyRepository.getRecentSuccessfulExamples(3);

        StringBuilder sb = new StringBuilder();
        sb.append("[{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");

        for (WidgetHistoryRepository.FewShotExample ex : examples) {
            sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(ex.prompt)).append("\"}");
            sb.append(",{\"role\":\"assistant\",\"content\":\"").append(escapeJson(ex.a2uiJson)).append("\"}");
        }

        sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(userText)).append("\"}]");
        return sb.toString();
    }

    /**
     * Builds the messages array with both few-shot history examples and
     * multi-turn conversation memory (for context continuation).
     *
     * <p>Message order:
     * <ol>
     *   <li>system</li>
     *   <li>few-shot history examples (successful past generations)</li>
     *   <li>conversation memory history (user/assistant turns)</li>
     *   <li>current user input</li>
     * </ol>
     *
     * @param systemPrompt       System prompt
     * @param userText           Current user input
     * @param historyRepository  Successful-generation history (few-shot), nullable
     * @param conversationMemory Multi-turn conversation memory, nullable
     * @return messages JSON array string
     */
    public static String buildMessagesWithConversationMemory(
            String systemPrompt, String userText,
            WidgetHistoryRepository historyRepository,
            WidgetConversationMemory conversationMemory) {
        StringBuilder sb = new StringBuilder();
        sb.append("[{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");

        // few-shot 成功样例
        if (historyRepository != null) {
            java.util.List<WidgetHistoryRepository.FewShotExample> examples =
                    historyRepository.getRecentSuccessfulExamples(3);
            for (WidgetHistoryRepository.FewShotExample ex : examples) {
                sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(ex.prompt)).append("\"}");
                sb.append(",{\"role\":\"assistant\",\"content\":\"").append(escapeJson(ex.a2uiJson)).append("\"}");
            }
        }

        // 多轮对话记忆（历史 user/assistant 轮次）
        if (conversationMemory != null) {
            java.util.List<WidgetConversationMemory.Entry> entries = conversationMemory.getEntries();
            for (WidgetConversationMemory.Entry e : entries) {
                sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(e.userText)).append("\"}");
                if (e.template != null) {
                    sb.append(",{\"role\":\"assistant\",\"content\":\"(模板: ")
                      .append(escapeJson(e.template)).append(")\"}");
                }
            }
        }

        sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(userText)).append("\"}]");
        return sb.toString();
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
