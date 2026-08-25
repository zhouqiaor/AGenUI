package com.amap.agenuiplayground.widget;

import android.util.Log;

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
     * Reset the parser state (e.g. before a new stream).
     */
    public void reset() {
        buf.setLength(0);
        depth = 0;
        inString = false;
        escaped = false;
    }
}
