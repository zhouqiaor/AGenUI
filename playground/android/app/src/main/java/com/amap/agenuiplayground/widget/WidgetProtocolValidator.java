package com.amap.agenuiplayground.widget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and repairs A2UI protocol JSON produced by LLM.
 *
 * Ported from A2UIPrompt.kt validation logic to Java.
 * Three-level validation: JSON syntax + protocol structure + component whitelist.
 */
public final class WidgetProtocolValidator {

    private static final String TAG = "WidgetProtocolValidator";
    private static final String TRIPLE_BACKTICK = "```";

    // Component whitelist
    private static final Set<String> VALID_COMPONENTS = new HashSet<>();
    static {
        VALID_COMPONENTS.add("Column");
        VALID_COMPONENTS.add("Row");
        VALID_COMPONENTS.add("Text");
        VALID_COMPONENTS.add("Button");
        VALID_COMPONENTS.add("Image");
        VALID_COMPONENTS.add("Card");
        VALID_COMPONENTS.add("Divider");
        VALID_COMPONENTS.add("TextField");
        VALID_COMPONENTS.add("CheckBox");
        VALID_COMPONENTS.add("List");
        VALID_COMPONENTS.add("Carousel");
        VALID_COMPONENTS.add("ProgressBar");
        VALID_COMPONENTS.add("Modal");
        VALID_COMPONENTS.add("Slider");
    }

    private WidgetProtocolValidator() {
        // utility class
    }

    /**
     * Extracts the A2UI JSON from LLM output.
     * Tries ```a2ui block, then ```json block, then raw JSON starting with {.
     */
    public static String extractA2UIJson(String content) {
        if (content == null || content.isEmpty()) return null;

        // Try ```a2ui ... ``` block
        String result = extractCodeBlock(content, "a2ui");
        if (result != null) return result;

        // Try ```json ... ``` block
        result = extractCodeBlock(content, "json");
        if (result != null) return result;

        // Try raw JSON (starts with {)
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            // Find the last } to handle trailing text
            int lastBrace = trimmed.lastIndexOf('}');
            if (lastBrace > 0) {
                return trimmed.substring(0, lastBrace + 1);
            }
            return trimmed;
        }

        return null;
    }

    private static String extractCodeBlock(String content, String lang) {
        String startMarker = TRIPLE_BACKTICK + lang;
        int startIdx = content.indexOf(startMarker);
        if (startIdx < 0) return null;

        int contentStart = startIdx + startMarker.length();
        // Skip optional newline after marker
        if (contentStart < content.length() && content.charAt(contentStart) == '\n') {
            contentStart++;
        }
        // Find closing ```
        int endIdx = content.indexOf(TRIPLE_BACKTICK, contentStart);
        if (endIdx < 0) {
            // No closing fence — take everything to end as the JSON
            String partial = content.substring(contentStart).trim();
            return partial.isEmpty() ? null : partial;
        }
        return content.substring(contentStart, endIdx).trim();
    }

    /**
     * Validates the A2UI JSON (three-level check).
     * @return ValidationResult with details.
     */
    public static ValidationResult validate(String json) {
        if (json == null || json.isEmpty()) {
            return new ValidationResult(false, "JSON is null or empty", 0, false, false);
        }

        try {
            JSONObject obj = new JSONObject(json);

            // Level 1: protocol structure
            if (!obj.has("version")) {
                return new ValidationResult(false, "缺少 version 字段", 0, false, false);
            }
            if (!obj.has("updateComponents")) {
                return new ValidationResult(false, "缺少 updateComponents 字段", 0, false, false);
            }

            JSONObject update = obj.getJSONObject("updateComponents");
            boolean hasSurfaceId = update.has("surfaceId");
            if (!hasSurfaceId) {
                return new ValidationResult(false, "updateComponents 缺少 surfaceId", 0, false, false);
            }

            JSONArray components = update.optJSONArray("components");
            int componentCount = components != null ? components.length() : 0;
            if (components == null || componentCount == 0) {
                return new ValidationResult(false, "components 数组为空或不存在", 0, hasSurfaceId, false);
            }

            // Level 2: component checks
            boolean hasRoot = false;
            Set<String> componentIds = new HashSet<>();
            for (int i = 0; i < components.length(); i++) {
                JSONObject comp = components.optJSONObject(i);
                if (comp == null) continue;

                String id = comp.optString("id", "");
                if (id.isEmpty()) continue;
                componentIds.add(id);
                if ("root".equals(id)) hasRoot = true;

                // Level 3: component type whitelist
                String type = comp.optString("component", "");
                if (!type.isEmpty() && !VALID_COMPONENTS.contains(type)) {
                    Log.w(TAG, "Unknown component type: " + type + " (id=" + id + ")");
                    // Not a hard failure — just warn
                }
            }

            if (!hasRoot) {
                return new ValidationResult(false, "缺少 id 为 root 的根组件",
                        componentCount, hasSurfaceId, false);
            }

            return new ValidationResult(true, null, componentCount, hasSurfaceId, hasRoot);

        } catch (Exception e) {
            return new ValidationResult(false, "JSON 解析失败: " + e.getMessage(), 0, false, false);
        }
    }

    /**
     * Attempts to repair common JSON issues from LLM output:
     * - Remove trailing commas
     * - Remove text before first { and after last }
     * - Balance braces/brackets
     */
    public static String repair(String json) {
        if (json == null || json.isEmpty()) return json;

        String result = json.trim();

        // Remove text before first {
        int firstBrace = result.indexOf('{');
        if (firstBrace > 0) {
            result = result.substring(firstBrace);
        }

        // Remove text after last }
        int lastBrace = result.lastIndexOf('}');
        if (lastBrace >= 0 && lastBrace < result.length() - 1) {
            result = result.substring(0, lastBrace + 1);
        }

        // Remove trailing commas (common LLM error)
        // Manual implementation to avoid Android ICU regex issues with \s and character classes
        result = removeTrailingCommas(result);

        // Collapse consecutive commas (common LLM error: ,, → ,)
        result = removeConsecutiveCommas(result);

        // Remove trailing commas again after collapsing (,, → , may create new ,}/,] cases)
        result = removeTrailingCommas(result);

        // Remove control characters that break JSON parsing (manual, no regex)
        result = removeControlChars(result);

        return result;
    }

    /**
     * Removes trailing commas before } or ] (common LLM JSON error).
     * Manual string scan to avoid Android ICU regex quirks with \s.
     */
    private static String removeTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json);
        int i = 0;
        while (i < sb.length()) {
            if (sb.charAt(i) == ',') {
                // Look ahead for optional whitespace then } or ]
                int j = i + 1;
                while (j < sb.length() && (sb.charAt(j) == ' ' || sb.charAt(j) == '\t'
                        || sb.charAt(j) == '\n' || sb.charAt(j) == '\r')) {
                    j++;
                }
                if (j < sb.length() && (sb.charAt(j) == '}' || sb.charAt(j) == ']')) {
                    sb.deleteCharAt(i);
                    continue; // don't increment i, re-check at same position
                }
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * Collapses consecutive commas (,, → ,) that LLMs sometimes produce.
     * Also handles ,\s, → , (comma, optional whitespace, comma → single comma).
     * Manual scan, no regex.
     */
    private static String removeConsecutiveCommas(String json) {
        StringBuilder sb = new StringBuilder(json);
        int i = 0;
        while (i < sb.length() - 1) {
            if (sb.charAt(i) == ',') {
                // Check if next non-whitespace char is also a comma
                int j = i + 1;
                while (j < sb.length() && (sb.charAt(j) == ' ' || sb.charAt(j) == '\t'
                        || sb.charAt(j) == '\n' || sb.charAt(j) == '\r')) {
                    j++;
                }
                if (j < sb.length() && sb.charAt(j) == ',') {
                    // Delete the whitespace + second comma, keep first comma
                    sb.delete(i + 1, j + 1);
                    continue; // re-check at same i — handles 3+ consecutive commas
                }
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * Removes ASCII control characters (0x00-0x1F) except \t \n \r.
     * Manual char scan, no regex.
     */
    private static String removeControlChars(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                continue; // skip control char
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Validates component types against the whitelist.
     * @return true if all component types are valid.
     */
    public static boolean validateComponentTypes(String json) {
        if (json == null || json.isEmpty()) return false;

        try {
            JSONObject obj = new JSONObject(json);
            JSONObject update = obj.getJSONObject("updateComponents");
            JSONArray components = update.getJSONArray("components");
            for (int i = 0; i < components.length(); i++) {
                JSONObject comp = components.optJSONObject(i);
                if (comp == null) continue;
                String type = comp.optString("component", "");
                if (!type.isEmpty() && !VALID_COMPONENTS.contains(type)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Result of validation.
     */
    public static class ValidationResult {
        public final boolean valid;
        public final String error;
        public final int componentCount;
        public final boolean hasSurfaceId;
        public final boolean hasRoot;

        public ValidationResult(boolean valid, String error, int componentCount,
                                boolean hasSurfaceId, boolean hasRoot) {
            this.valid = valid;
            this.error = error;
            this.componentCount = componentCount;
            this.hasSurfaceId = hasSurfaceId;
            this.hasRoot = hasRoot;
        }

        @Override
        public String toString() {
            return "ValidationResult{valid=" + valid + ", error='" + error + "'"
                    + ", componentCount=" + componentCount
                    + ", hasSurfaceId=" + hasSurfaceId
                    + ", hasRoot=" + hasRoot + "}";
        }
    }
}
