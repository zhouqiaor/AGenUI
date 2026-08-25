package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * LLM configuration backed by SharedPreferences.
 *
 * Keys: llm_api_key, llm_model, llm_endpoint.
 * Provides default primary (qwen) and fallback (doubao) configurations.
 */
public class WidgetLLMConfig {

    private static final String PREFS_NAME = "a2ui_widget_prefs";
    private static final String KEY_API_KEY = "llm_api_key";
    private static final String KEY_MODEL = "llm_model";
    private static final String KEY_ENDPOINT = "llm_endpoint";

    // ===== Default primary: Alibaba bailian qwen =====
    public static final String DEFAULT_PRIMARY_MODEL = "qwen3.7-plus";
    public static final String DEFAULT_PRIMARY_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    // ===== Default fallback: ByteDance doubao =====
    public static final String DEFAULT_FALLBACK_MODEL = "doubao-1.5-pro";
    public static final String DEFAULT_FALLBACK_ENDPOINT =
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    // ===== Test API key — MUST REPLACE before production use =====
    // 用户在使用前需要替换为自己的真实 API Key
    public static final String DEFAULT_API_KEY = "sk-ws-H.EHLPPMY.QJz8.MEYCIQCk200amtQ7U7w9eXryCE3aARf7q2M58Xd2gXJmQOke6QIhAMJ9mBKcqvUG_d-5ePJFrIQFB7NirlVnAs-SxdAyWKkU";

    private final SharedPreferences prefs;

    public WidgetLLMConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
     * Switches to the fallback model configuration (doubao).
     */
    public void switchToFallback() {
        prefs.edit()
                .putString(KEY_MODEL, DEFAULT_FALLBACK_MODEL)
                .putString(KEY_ENDPOINT, DEFAULT_FALLBACK_ENDPOINT)
                .apply();
    }

    /**
     * Resets to primary model configuration (qwen).
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
}
