package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * LLM streaming client using HttpURLConnection + SSE.
 *
 * Supports OpenAI-compatible API format (Alibaba bailian qwen / ByteDance doubao).
 * Streams SSE events: data: {"choices":[{"delta":{"content":"..."}}]} + [DONE].
 *
 * On failure, automatically retries with the fallback model.
 */
public class WidgetLLMClient {

    private static final String TAG = "WidgetLLMClient";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    public interface StreamCallback {
        void onChunk(String delta);
        void onComplete(String fullContent);
        void onError(Exception e);
    }

    private final WidgetLLMConfig config;

    public WidgetLLMClient(Context context) {
        this.config = new WidgetLLMConfig(context);
    }

    /**
     * Starts a streaming chat request with pre-built messages JSON.
     * Runs on the calling thread (should be a background thread).
     *
     * @param systemPrompt System prompt (used for failover retry)
     * @param userText User input text (used for failover retry)
     * @param messagesJson Pre-built messages JSON array (includes history)
     * @param callback Stream callback
     */
    public void streamChat(String systemPrompt, String userText,
                           String messagesJson, StreamCallback callback) {
        // Multi-tier failover: iterate FAILOVER_TIERS starting from current model
        String currentModel = config.getModel();
        int startIndex = 0;
        for (int i = 0; i < WidgetLLMConfig.FAILOVER_TIERS.length; i++) {
            if (WidgetLLMConfig.FAILOVER_TIERS[i].equals(currentModel)) {
                startIndex = i;
                break;
            }
        }

        for (int i = startIndex; i < WidgetLLMConfig.FAILOVER_TIERS.length; i++) {
            String model = WidgetLLMConfig.FAILOVER_TIERS[i];
            String endpoint = WidgetLLMConfig.getEndpointForModel(model);
            String apiKey = config.getApiKey();

            Log.d(TAG, "Trying model tier " + i + ": " + model + " @ " + endpoint);
            boolean success = doStreamRequest(endpoint, apiKey, model,
                    systemPrompt, userText, messagesJson, callback);
            if (success) {
                config.save(apiKey, model, endpoint);
                return;
            }
            Log.w(TAG, "Model tier " + i + " (" + model + ") failed, trying next tier");
        }

        Log.e(TAG, "All failover tiers exhausted, resetting to primary");
        config.resetToPrimary();
    }

    /**
     * Starts a streaming chat request.
     * Runs on the calling thread (should be a background thread).
     * Calls callback methods on the same thread.
     *
     * @param systemPrompt System prompt
     * @param userText User input text
     * @param callback Stream callback
     */
    public void streamChat(String systemPrompt, String userText, StreamCallback callback) {
        streamChat(systemPrompt, userText, null, callback);
    }

    /**
     * Performs a single SSE stream request.
     * @return true if the request completed successfully (even if content was empty),
     *         false if a network/HTTP error occurred (eligible for fallback retry).
     */
    private boolean doStreamRequest(String endpoint, String apiKey, String model,
                                     String systemPrompt, String userText,
                                     String messagesJson,
                                     StreamCallback callback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            // Build request body — use custom messagesJson if provided, else build from system+user
            String body = (messagesJson != null && !messagesJson.isEmpty())
                    ? buildRequestBodyWithMessages(model, messagesJson)
                    : buildRequestBody(model, systemPrompt, userText);
            Log.d(TAG, "Request: model=" + model + ", body length=" + body.length());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errBody = readErrorStream(conn);
                Log.e(TAG, "HTTP " + responseCode + ": " + errBody);
                callback.onError(new java.io.IOException("HTTP " + responseCode + ": " + errBody));
                return false;
            }

            // Read SSE stream
            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        Log.d(TAG, "SSE stream done");
                        break;
                    }

                    try {
                        JSONObject json = new JSONObject(data);
                        org.json.JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.optJSONObject(0);
                            if (choice != null) {
                                JSONObject delta = choice.optJSONObject("delta");
                                if (delta != null) {
                                    String content = delta.optString("content", "");
                                    if (!content.isEmpty()) {
                                        fullContent.append(content);
                                        callback.onChunk(content);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse SSE chunk: " + data, e);
                    }
                }
            }

            Log.d(TAG, "Stream complete: " + fullContent.length() + " chars");
            callback.onComplete(fullContent.toString());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Stream request failed", e);
            callback.onError(e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildRequestBody(String model, String systemPrompt, String userText) {
        // Use JSONObject for safe JSON construction
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("messages",
                    new org.json.JSONArray(
                            WidgetPromptBuilder.buildMessagesJson(systemPrompt, userText)));
            body.put("stream", true);
            body.put("temperature", 0.2);
            // Disable thinking mode for fast widget generation (qwen3.7-plus supports this)
            body.put("enable_thinking", false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build request body", e);
        }
        return body.toString();
    }

    private String buildRequestBodyWithMessages(String model, String messagesJson) {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("messages", new org.json.JSONArray(messagesJson));
            body.put("stream", true);
            body.put("temperature", 0.2);
            body.put("enable_thinking", false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build request body with messages", e);
        }
        return body.toString();
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            java.io.InputStream errStream = conn.getErrorStream();
            if (errStream == null) return "(no error body)";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errStream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return "(failed to read error body)";
        }
    }

}
