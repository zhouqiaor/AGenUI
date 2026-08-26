package com.amap.agenuiplayground.widget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental JSON parser for streaming LLM output.
 *
 * Ported from POC engine.js makePartialParser (L1168-L1196) with enhancements:
 * - Tracks {}/[] nesting depth, string state, escape state.
 * - Ignores markdown code-fence markers (```a2ui, ```json, ```).
 * - When depth returns to 0, extracts the complete top-level JSON object,
 *   validates it with JSONObject, and adds it to the returned list.
 * - Incomplete content is kept in buffer for the next chunk.
 *
 * Phase 3A additions:
 * - {@link #extractCompletedComponents()} scans the current buffer for any
 *   fully-closed JSON object inside the "components" array and returns them
 *   wrapped as an updateComponents JSON, enabling progressive rendering during
 *   streaming (before the top-level object closes).
 *
 * Typical LLM stream output:
 *   ```a2ui
 *   {"version":"v0.9","updateComponents":{...}}
 *   ```
 *
 * The parser only emits the inner JSON object(s), skipping the fences.
 */
public class WidgetPartialParser {

    private static final String TAG = "WidgetPartialParser";

    private final StringBuilder buf = new StringBuilder();
    private int depth = 0;
    private boolean inString = false;
    private boolean escaped = false;

    // Tracks the surfaceId captured from an earlier createSurface / updateComponents
    // object so progressive updateComponents can re-use it.
    private String progressiveSurfaceId = "ai-generated";

    /**
     * Feed a chunk of LLM output.
     *
     * @return List of complete, closed JSON object strings that were closed by this chunk.
     *         May be empty if the chunk only added partial content.
     */
    public List<String> feed(String chunk) {
        List<String> results = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) return results;

        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);

            // Inside a string — handle escape and quote toggling
            if (inString) {
                buf.append(c);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }

            // Not in string
            // Skip markdown code-fence markers: backticks and the a2ui/json lang hint
            if (c == '`') {
                // Skip all backticks (handles ```a2ui, ```json, ```)
                continue;
            }
            // Skip the language identifier chars right after an opening fence.
            // We only skip 'a','2','u','i','j','s','o','n' when they appear as a
            // contiguous run immediately before the first '{' of a top-level object
            // (i.e. depth==0 and buffer has no unresolved JSON). This is conservative
            // and avoids eating real JSON content.
            if (depth == 0 && buf.length() == 0
                    && (c == 'a' || c == '2' || c == 'u' || c == 'i'
                    || c == 'j' || c == 's' || c == 'o' || c == 'n'
                    || c == '\n' || c == '\r' || c == ' ' || c == '\t')) {
                // Skip leading whitespace and the code-fence language token
                continue;
            }

            // Toggle string state
            if (c == '"') {
                inString = true;
                buf.append(c);
                continue;
            }

            // Track nesting depth
            if (c == '{' || c == '[') {
                depth++;
                buf.append(c);
                continue;
            }
            if (c == '}' || c == ']') {
                depth--;
                buf.append(c);
                if (depth == 0) {
                    // Complete top-level object closed — try to parse
                    String jsonStr = buf.toString();
                    try {
                        new JSONObject(jsonStr); // validate parseable
                        results.add(jsonStr);
                        // Capture surfaceId for future progressive renders
                        captureSurfaceId(jsonStr);
                    } catch (Exception e) {
                        Log.w(TAG, "Depth-0 slice not parseable, discarding: "
                                + (jsonStr.length() > 80
                                ? jsonStr.substring(0, 80) + "..." : jsonStr));
                    }
                    buf.setLength(0); // reset buffer for next object
                }
                continue;
            }

            // Ordinary char outside string — only buffer if we've started an object
            // (depth>0) to avoid accumulating inter-object text/explanation.
            if (depth > 0) {
                buf.append(c);
            }
        }
        return results;
    }

    /**
     * Extracts any fully-closed JSON objects that appear as elements of the
     * "components" array in the current buffer, and wraps them into a
     * complete {@code updateComponents} JSON suitable for pushing to
     * {@link SurfaceManager#receiveTextChunk}.
     *
     * <p>This enables progressive rendering during streaming: even before the
     * top-level JSON object closes, the components already completed in the
     * "components" array can be drawn so the user sees incremental progress.
     *
     * <p>The returned JSON uses the {@code updateComponents} schema with the
     * surfaceId captured from any earlier createSurface / updateComponents
     * chunk (or "ai-generated" as default).
     *
     * @return A complete updateComponents JSON string, or null if no
     *         fully-closed component object is available in the buffer.
     */
    public String extractCompletedComponents() {
        if (depth <= 0 || buf.length() == 0) return null;

        // Find the "components" array inside the buffer. We scan the raw buffer
        // text (which may be partial top-level JSON) looking for the key
        // "components" followed by '['. Then we walk the array tracking depth
        // and string state, collecting any fully-closed {...} objects.
        String s = buf.toString();
        int componentsKeyIdx = findComponentsArrayStart(s);
        if (componentsKeyIdx < 0) return null;

        int arrayStart = s.indexOf('[', componentsKeyIdx);
        if (arrayStart < 0) return null;

        List<String> completed = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int d = 0;
        boolean inStr = false;
        boolean esc = false;
        boolean started = false; // have we entered the first object?

        for (int i = arrayStart + 1; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inStr) {
                current.append(c);
                if (esc) { esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') inStr = false;
                continue;
            }

            if (c == '"') {
                inStr = true;
                current.append(c);
                started = true;
                continue;
            }

            if (c == '{') {
                d++;
                current.append(c);
                started = true;
                continue;
            }
            if (c == '}') {
                d--;
                current.append(c);
                if (d == 0 && started) {
                    // A complete object closed.
                    String obj = current.toString();
                    try {
                        new JSONObject(obj); // validate
                        completed.add(obj);
                    } catch (Exception e) {
                        Log.w(TAG, "Progressive: skipping unparseable component");
                    }
                    current.setLength(0);
                    started = false;
                }
                continue;
            }

            if (d > 0) {
                current.append(c);
            }
            // If d==0 and we see ']' the array closed; stop.
            if (d == 0 && c == ']') break;
        }

        if (completed.isEmpty()) return null;

        // Build updateComponents JSON
        try {
            JSONObject wrapper = new JSONObject();
            JSONObject update = new JSONObject();
            update.put("surfaceId", progressiveSurfaceId);
            JSONArray arr = new JSONArray();
            for (String c : completed) {
                arr.put(new JSONObject(c));
            }
            update.put("components", arr);
            wrapper.put("version", "v0.9");
            wrapper.put("updateComponents", update);
            return wrapper.toString();
        } catch (Exception e) {
            Log.w(TAG, "Progressive wrapper build failed", e);
            return null;
        }
    }

    /** Finds the start index of the "components" key in the raw buffer string. */
    private int findComponentsArrayStart(String s) {
        // Look for "components" as a JSON key. We match the quoted key to avoid
        // matching the word inside a string value.
        String key = "\"components\"";
        int idx = s.indexOf(key);
        if (idx < 0) {
            // Fallback: unquoted (shouldn't happen in valid JSON but be lenient)
            idx = s.indexOf("components");
        }
        return idx;
    }

    /** Captures surfaceId from a complete top-level JSON for progressive rendering. */
    private void captureSurfaceId(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            JSONObject update = obj.optJSONObject("updateComponents");
            if (update != null) {
                String sid = update.optString("surfaceId", null);
                if (sid != null && !sid.isEmpty()) {
                    progressiveSurfaceId = sid;
                }
            }
        } catch (Exception e) {
            // ignore — not a top-level JSON we care about
        }
    }

    /**
     * Reset the parser state (e.g. before a new stream).
     */
    public void reset() {
        buf.setLength(0);
        depth = 0;
        inString = false;
        escaped = false;
        progressiveSurfaceId = "ai-generated";
    }
}
