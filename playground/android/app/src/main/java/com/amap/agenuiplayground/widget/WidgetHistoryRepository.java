package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Simple widget generation history repository using SharedPreferences.
 *
 * Stores the last 50 generation records (prompt, a2uiJson, timestamp, latency, success).
 * No Room/SQLite dependency — lightweight, sufficient for Phase 2.
 */
public class WidgetHistoryRepository {

    private static final String TAG = "WidgetHistoryRepo";
    private static final String PREFS_NAME = "widget_history";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 50;

    private final Context context;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    public WidgetHistoryRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Records a generation result.
     */
    public void record(String prompt, String a2uiJson, long latencyMs, boolean success) {
        try {
            JSONArray records = loadRecordsJsonArray();
            JSONObject record = new JSONObject();
            record.put("prompt", truncate(prompt, 200));
            record.put("a2uiJson", truncate(a2uiJson, 5000));
            record.put("timestamp", System.currentTimeMillis());
            record.put("latencyMs", latencyMs);
            record.put("success", success);
            record.put("timeFormatted", dateFormat.format(new Date()));

            // Prepend new record (most recent first)
            JSONArray newArray = new JSONArray();
            newArray.put(record);
            for (int i = 0; i < records.length() && newArray.length() < MAX_RECORDS; i++) {
                newArray.put(records.optJSONObject(i));
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_RECORDS, newArray.toString())
                    .apply();

            Log.d(TAG, "Recorded: success=" + success + ", latency=" + latencyMs + "ms"
                    + ", total=" + newArray.length());
        } catch (Exception e) {
            Log.e(TAG, "Failed to record history", e);
        }
    }

    /**
     * Returns the last successful A2UI JSON (for offline cache display).
     */
    public String getLastSuccessfulJson() {
        try {
            JSONArray records = loadRecordsJsonArray();
            for (int i = 0; i < records.length(); i++) {
                JSONObject rec = records.optJSONObject(i);
                if (rec != null && rec.optBoolean("success", false)) {
                    return rec.optString("a2uiJson", null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get last successful JSON", e);
        }
        return null;
    }

    /**
     * Returns the most recent successful generation records (for use as
     * few-shot examples in the next LLM prompt).
     *
     * <p>Filters: {@code success == true} AND {@code a2uiJson} is non-empty.
     * Returns at most {@code limit} records, most-recent first.
     *
     * <p>Each entry is a {@link FewShotExample} holding the original user
     * prompt and the validated A2UI JSON. Callers can further categorize
     * them by keyword to match the current request's domain (weather / todo
     * / agenda / general).
     *
     * @param limit max number of examples to return (e.g. 3)
     * @return list of examples, possibly empty.
     */
    public List<FewShotExample> getRecentSuccessfulExamples(int limit) {
        List<FewShotExample> result = new ArrayList<>();
        if (limit <= 0) return result;
        try {
            JSONArray records = loadRecordsJsonArray();
            for (int i = 0; i < records.length() && result.size() < limit; i++) {
                JSONObject rec = records.optJSONObject(i);
                if (rec == null) continue;
                if (!rec.optBoolean("success", false)) continue;
                String prompt = rec.optString("prompt", "");
                String a2uiJson = rec.optString("a2uiJson", "");
                if (prompt.isEmpty() || a2uiJson.isEmpty()) continue;
                // Skip records whose a2uiJson is just an empty/truncated placeholder
                if (a2uiJson.length() < 40) continue;
                result.add(new FewShotExample(prompt, a2uiJson));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get few-shot examples", e);
        }
        return result;
    }

    /**
     * A successful generation record used as a few-shot example.
     */
    public static class FewShotExample {
        public final String prompt;
        public final String a2uiJson;

        public FewShotExample(String prompt, String a2uiJson) {
            this.prompt = prompt;
            this.a2uiJson = a2uiJson;
        }
    }

    /**
     * Returns all records as a list of summary strings (for UI display).
     */
    public List<String> getRecentSummaries() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray records = loadRecordsJsonArray();
            for (int i = 0; i < records.length(); i++) {
                JSONObject rec = records.optJSONObject(i);
                if (rec == null) continue;
                String time = rec.optString("timeFormatted", "?");
                String prompt = truncate(rec.optString("prompt", ""), 30);
                boolean success = rec.optBoolean("success", false);
                long latency = rec.optLong("latencyMs", 0);
                String status = success ? "✓" : "✗";
                result.add(time + " " + status + " " + latency + "ms " + prompt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get summaries", e);
        }
        return result;
    }

    /**
     * Clears all history.
     */
    public void clear() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RECORDS)
                .apply();
    }

    private JSONArray loadRecordsJsonArray() {
        String json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RECORDS, "[]");
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
