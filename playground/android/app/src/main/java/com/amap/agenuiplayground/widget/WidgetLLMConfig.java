package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * LLM configuration backed by SharedPreferences.
 *
 * Keys: llm_api_key, llm_model, llm_endpoint.
 * Provides a three-tier model failover chain: turbo → plus → doubao.
 *
 * Tier semantics:
 *  - TURBO  (qwen-turbo)   : fast first-token latency (~1-2s), default model
 *  - PLUS   (qwen3.7-plus) : higher quality, slower
 *  - DOUBAO (doubao-1.5-pro): ByteDance fallback when Alibaba bailian fails
 *
 * All three tiers currently share the same Alibaba DashScope endpoint
 * (qwen-turbo / qwen3.7-plus are both served there). Only doubao requires
 * the Volcengine Ark endpoint.
 */
public class WidgetLLMConfig {

    private static final String TAG = "WidgetLLMConfig";
    private static final String PREFS_NAME = "a2ui_widget_prefs";
    private static final String KEY_API_KEY = "llm_api_key";
    private static final String KEY_MODEL = "llm_model";
    private static final String KEY_ENDPOINT = "llm_endpoint";

    // ===== Model constants =====
    /** Fast tier: low first-token latency, default model. */
    public static final String MODEL_TURBO = "qwen-turbo";
    /** Quality tier: higher quality, slower. */
    public static final String MODEL_PLUS = "qwen3.7-plus";
    /** Fallback tier: ByteDance doubao, different vendor/endpoint. */
    public static final String MODEL_DOUBAO = "doubao-1.5-pro";

    // ===== Default primary: Alibaba bailian qwen-turbo (fast) =====
    public static final String DEFAULT_PRIMARY_MODEL = MODEL_TURBO;
    public static final String DEFAULT_PRIMARY_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    // ===== Quality backup: qwen3.7-plus (same DashScope endpoint) =====
    public static final String QUALITY_BACKUP_MODEL = MODEL_PLUS;
    public static final String QUALITY_BACKUP_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    // ===== Default fallback: ByteDance doubao =====
    public static final String DEFAULT_FALLBACK_MODEL = MODEL_DOUBAO;
    public static final String DEFAULT_FALLBACK_ENDPOINT =
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    // ===== Test API key — MUST REPLACE before production use =====
    // 用户在使用前需要替换为自己的真实 API Key
    public static final String DEFAULT_API_KEY = "sk-ws-H.EHLPPMY.QJz8.MEYCIQCk200amtQ7U7w9eXryCE3aARf7q2M58Xd2gXJmQOke6QIhAMJ9mBKcqvUG_d-5ePJFrIQFB7NirlVnAs-SxdAyWKkU";

    /**
     * Failover tiers in priority order. Index 0 is tried first.
     * turbo (fast) → plus (quality) → doubao (vendor fallback).
     */
    public static final String[] FAILOVER_TIERS = {
            MODEL_TURBO,
            MODEL_PLUS,
            MODEL_DOUBAO,
    };

    private final SharedPreferences prefs;

    public WidgetLLMConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Ensure defaults are persisted on first construction so switchToNext()
        // can rely on reading back the endpoint alongside the model.
        if (!prefs.contains(KEY_MODEL)) {
            resetToPrimary();
        }
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY);
    }

    public String getModel() {
        return prefs.getString(KEY_MODEL, DEFAULT_PRIMARY_MODEL);
    }

    public String getEndpoint() {
        return prefs.getString(KEY_ENDPOINT, DEFAULT_PRIMARY_ENDPOINT);
    }

    public void save(String apiKey, String model, String endpoint) {
        prefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_MODEL, model)
                .putString(KEY_ENDPOINT, endpoint)
                .apply();
    }

    /**
     * Returns the endpoint URL for the given model name.
     * qwen-turbo and qwen3.7-plus share the DashScope endpoint;
     * doubao uses the Volcengine Ark endpoint.
     */
    public String getEndpointForModel(String model) {
        if (MODEL_DOUBAO.equals(model)) {
            return DEFAULT_FALLBACK_ENDPOINT;
        }
        // All qwen variants share the DashScope endpoint.
        return DEFAULT_PRIMARY_ENDPOINT;
    }

    /**
     * Returns the index into FAILOVER_TIERS for the currently persisted model,
     * or 0 if the current model is not in the chain (in which case the next
     * switchToNext() will restart from the top).
     */
    public int getCurrentTierIndex() {
        String current = getModel();
        for (int i = 0; i < FAILOVER_TIERS.length; i++) {
            if (FAILOVER_TIERS[i].equals(current)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Advances the persisted model to the next tier in the failover chain.
     * turbo → plus → doubao. Returns true if a next tier was applied, false
     * if the current model is already the last tier (doubao) — in that case
     * the caller should treat the whole chain as exhausted.
     *
     * Also persists the matching endpoint for the new model.
     */
    public boolean switchToNext() {
        int idx = getCurrentTierIndex();
        if (idx < 0) {
            // Unknown model — restart from the top (turbo) so a subsequent
            // streamChat() call begins a fresh chain.
            Log.w(TAG, "Current model '" + getModel() + "' not in failover chain; resetting to turbo");
            resetToPrimary();
            return true;
        }
        if (idx >= FAILOVER_TIERS.length - 1) {
            // Already at last tier (doubao). Chain exhausted.
            Log.w(TAG, "Failover chain exhausted at " + FAILOVER_TIERS[idx]);
            return false;
        }
        String nextModel = FAILOVER_TIERS[idx + 1];
        String nextEndpoint = getEndpointForModel(nextModel);
        Log.i(TAG, "Failover: " + FAILOVER_TIERS[idx] + " → " + nextModel);
        prefs.edit()
                .putString(KEY_MODEL, nextModel)
                .putString(KEY_ENDPOINT, nextEndpoint)
                .apply();
        return true;
    }

    /**
     * Switches directly to the fallback model configuration (doubao).
     */
    public void switchToFallback() {
        prefs.edit()
                .putString(KEY_MODEL, DEFAULT_FALLBACK_MODEL)
                .putString(KEY_ENDPOINT, DEFAULT_FALLBACK_ENDPOINT)
                .apply();
    }

    /**
     * Resets to primary model configuration (qwen-turbo, fast).
     */
    public void resetToPrimary() {
        prefs.edit()
                .putString(KEY_MODEL, DEFAULT_PRIMARY_MODEL)
                .putString(KEY_ENDPOINT, DEFAULT_PRIMARY_ENDPOINT)
                .apply();
    }

    public boolean isUsingFallback() {
        return DEFAULT_FALLBACK_MODEL.equals(getModel());
    }

    /**
     * Returns true if the current model is the fast turbo tier.
     */
    public boolean isUsingTurbo() {
        return MODEL_TURBO.equals(getModel());
    }
}
