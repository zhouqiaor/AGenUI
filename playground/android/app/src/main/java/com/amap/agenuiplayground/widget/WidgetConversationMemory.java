package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * 多轮对话上下文记忆 — 用 SharedPreferences 持久化最近 5 轮对话历史。
 *
 * <p>每条记录由 (userText, matchedTemplate) 组成，用于：
 * <ul>
 *   <li>构建 LLM messages 参数（few-shot 上下文续接）</li>
 *   <li>帮助 WidgetIntentMatcher / WidgetTemplateRecommender 做上下文感知推荐</li>
 * </ul>
 *
 * <p>持久化格式（SharedPreferences JSON）：key="history"，value 为 JSON 数组字符串：
 * <pre>[{"user":"今天北京天气","template":"weather"}, ...]</pre>
 */
public class WidgetConversationMemory {

    private static final String TAG = "WidgetConvMemory";

    private static final String PREFS_NAME = "agenui_conversation_memory";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 5;

    private final SharedPreferences prefs;
    // 内存缓存，避免每次读取都反序列化
    private final LinkedList<Entry> cache = new LinkedList<>();

    /**
     * 单条对话记录。
     */
    public static class Entry {
        public final String userText;
        public final String template;  // 可为 null

        public Entry(String userText, String template) {
            this.userText = userText;
            this.template = template;
        }
    }

    public WidgetConversationMemory(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromDisk();
    }

    /**
     * 添加一条对话记录。
     * 超出 MAX_HISTORY 时自动淘汰最旧的一条。
     */
    public synchronized void addEntry(String userText, String template) {
        if (TextUtils.isEmpty(userText)) return;
        Entry entry = new Entry(userText.trim(), template);
        cache.addLast(entry);
        while (cache.size() > MAX_HISTORY) {
            cache.removeFirst();
        }
        saveToDisk();
        Log.d(TAG, "addEntry: \"" + truncate(userText, 30) + "\" → "
                + (template != null ? template : "null")
                + ", size=" + cache.size());
    }

    /**
     * 返回最近 MAX_HISTORY 轮对话历史（按时间正序：旧 → 新）。
     */
    public synchronized List<Entry> getEntries() {
        return new ArrayList<>(cache);
    }

    /**
     * 返回 JSON 格式的消息数组，供 LLM messages 参数使用。
     *
     * <p>格式示例：
     * <pre>[
     *   {"role":"user","content":"今天北京天气"},
     *   {"role":"assistant","content":"(weather template)"},
     *   {"role":"user","content":"那明天的呢"}
     * ]</pre>
     *
     * <p>该数组只包含历史记录，不包含当前轮的用户输入。
     * 调用方应在其后追加当前轮的 user message。
     */
    public synchronized String getHistoryJson() {
        JSONArray arr = new JSONArray();
        for (Entry e : cache) {
            JSONObject userMsg = new JSONObject();
            try {
                userMsg.put("role", "user");
                userMsg.put("content", e.userText);
                arr.put(userMsg);
                if (e.template != null) {
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", "(模板: " + e.template + ")");
                    arr.put(assistantMsg);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Failed to serialize entry", ex);
            }
        }
        return arr.toString();
    }

    /**
     * 返回上次匹配的模板名（用于上下文续接）。
     * 例如：用户先问"今天北京天气"（weather），再说"那明天的呢"，可续接 weather。
     */
    public synchronized String getLastTemplate() {
        if (cache.isEmpty()) return null;
        // 从后往前找第一个有 template 的条目
        for (int i = cache.size() - 1; i >= 0; i--) {
            Entry e = cache.get(i);
            if (e.template != null) return e.template;
        }
        return null;
    }

    /**
     * 返回上一条用户文本（用于上下文续接判断）。
     */
    public synchronized String getLastUserText() {
        if (cache.isEmpty()) return null;
        return cache.getLast().userText;
    }

    /**
     * 清空历史（内存 + 磁盘）。
     */
    public synchronized void clear() {
        cache.clear();
        saveToDisk();
        Log.d(TAG, "history cleared");
    }

    // ---------------------------------------------------------------------
    // 持久化
    // ---------------------------------------------------------------------

    private void loadFromDisk() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && i < MAX_HISTORY; i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String u = obj.optString("user", "");
                String t = obj.has("template") && !obj.isNull("template")
                        ? obj.optString("template") : null;
                if (!TextUtils.isEmpty(u)) {
                    cache.add(new Entry(u, t));
                }
            }
            Log.d(TAG, "loaded " + cache.size() + " entries from disk");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load history from disk", e);
        }
    }

    private void saveToDisk() {
        JSONArray arr = new JSONArray();
        for (Entry e : cache) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("user", e.userText);
                if (e.template != null) {
                    obj.put("template", e.template);
                } else {
                    obj.put("template", JSONObject.NULL);
                }
                arr.put(obj);
            } catch (Exception ex) {
                Log.w(TAG, "Failed to serialize entry", ex);
            }
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
