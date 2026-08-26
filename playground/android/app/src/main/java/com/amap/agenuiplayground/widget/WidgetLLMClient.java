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
     * Starts a streaming chat request with full failover chain.
     *
     * Failover order: turbo (qwen-turbo, fast) → plus (qwen3.7-plus, quality)
     * → doubao (doubao-1.5-pro, vendor fallback).
     *
     * Each tier runs an independent {@link #doStreamRequest}. On failure
     * (network / HTTP error / parse error before any content is emitted),
     * {@link WidgetLLMConfig#switchToNext()} advances to the next tier and
     * the request is retried. The chain stops as soon as a tier completes
     * successfully OR all tiers are exhausted.
     *
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
     * Starts a streaming chat request with full failover chain and optional
     * dynamic few-shot messages.
     *
     * <p>If {@param messagesJson} is non-null, it overrides the default
     * system+user two-message format. This is used by Phase 3A's dynamic
     * few-shot feature ({@link WidgetPromptBuilder#buildMessagesWithHistory})
     * to inject prior successful examples into the prompt.
     *
     * <p>Failover chain is identical to the simpler overload.
     *
     * @param systemPrompt System prompt (used only when messagesJson is null)
     * @param userText User input text (used only when messagesJson is null)
     * @param messagesJson Pre-built messages JSON array string (may be null)
     * @param callback Stream callback
     */
    public void streamChat(String systemPrompt, String userText,
                           String messagesJson, StreamCallback callback) {
        // Always start the chain from the fast tier (turbo) so a previous
        // failure's persisted state (e.g. stuck on doubao) does not poison
        // the next user request.
        config.resetToPrimary();

        int attempt = 0;
        boolean success = false;
        while (attempt < WidgetLLMConfig.FAILOVER_TIERS.length) {
            attempt++;
            String endpoint = config.getEndpoint();
            String apiKey = config.getApiKey();
            String model = config.getModel();
            Log.i(TAG, "Attempt " + attempt + "/" + WidgetLLMConfig.FAILOVER_TIERS.length
                    + ": model=" + model + ", endpoint=" + endpoint
                    + (messagesJson != null ? ", few-shot=on" : ""));

            success = doStreamRequest(endpoint, apiKey, model,
                    systemPrompt, userText, messagesJson, callback);

            if (success) {
                Log.i(TAG, "Stream succeeded with model=" + model + " on attempt " + attempt);
                break;
            }

            // Failed — try to advance to the next tier.
            boolean advanced = config.switchToNext();
            if (!advanced) {
                // Chain exhausted (already at doubao).
                Log.w(TAG, "Failover chain exhausted after " + attempt + " attempts");
                break;
            }
            // Loop continues with the new model/endpoint.
        }

        // Reset persisted state to turbo for the next streamChat() invocation.
        // The failover state is per-request; we don't want a transient network
        // blip to permanently pin the user to the slower doubao model.
        config.resetToPrimary();
    }

    /**
     * Performs a single SSE stream request.
     * @return true if the request completed successfully (even if content was empty),
     *         false if a network/HTTP error occurred (eligible for fallback retry).
     */
    private boolean doStreamRequest(String endpoint, String apiKey, String model,
                                     String systemPrompt, String userText,
                                     StreamCallback callback) {
        return doStreamRequest(endpoint, apiKey, model, systemPrompt, userText, null, callback);
    }

    /**
     * Performs a single SSE stream request with optional pre-built messages.
     *
     * @param messagesJson Pre-built messages JSON array string. If non-null,
     *                     overrides the system+user format built from
     *                     {@code systemPrompt}/{@code userText}. Used by
     *                     Phase 3A few-shot.
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

            // Build request body
            String body = (messagesJson != null)
                    ? buildRequestBodyFromMessages(model, messagesJson)
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

    /**
     * Builds the request body from a pre-built messages JSON array string.
     * Used by Phase 3A dynamic few-shot — the messages array already contains
     * the system prompt, few-shot user/assistant pairs, and the final user
     * message.
     */
    private String buildRequestBodyFromMessages(String model, String messagesJson) {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("messages", new org.json.JSONArray(messagesJson));
            body.put("stream", true);
            body.put("temperature", 0.2);
            body.put("enable_thinking", false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build request body from messages JSON", e);
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
