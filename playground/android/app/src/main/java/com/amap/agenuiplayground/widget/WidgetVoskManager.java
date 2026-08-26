package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.IOException;

/**
 * Vosk 离线语音识别管理器。
 *
 * 流程:
 * 1. 通过 VoskModelLoader 异步加载/下载中文小模型
 * 2. 加载完成后用 SpeechService 进行麦克风识别(实时部分结果)
 * 3. final result 通过 VoiceCallback 回调
 *
 * 如模型加载失败,由调用方降级到 WidgetVoiceHelper(在线 Google SpeechRecognizer)。
 */
public class WidgetVoskManager implements RecognitionListener {

    private static final String TAG = "WidgetVoskManager";

    private static final float SAMPLE_RATE = 16000.0f;

    private final Activity activity;
    private final TextView tvStatus;
    private final TextView tvResult;
    private final ImageButton btnMic;
    private final WidgetVoiceHelper.VoiceCallback callback;

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private boolean listening = false;
    private VoskModelLoader modelLoader;

    public WidgetVoskManager(Activity activity, TextView tvStatus, TextView tvResult,
                            ImageButton btnMic, WidgetVoiceHelper.VoiceCallback callback) {
        this.activity = activity;
        this.tvStatus = tvStatus;
        this.tvResult = tvResult;
        this.btnMic = btnMic;
        this.callback = callback;
        this.modelLoader = new VoskModelLoader(activity);
    }

    /**
     * 开始监听。首次调用会异步加载模型,加载完成后自动开始识别。
     */
    public void startListening() {
        String path = modelLoader.getModelPathSync();
        if (path != null) {
            startRecognition(path);
            return;
        }

        tvStatus.setText("正在下载离线语音模型...");
        tvResult.setVisibility(View.VISIBLE);
        btnMic.setAlpha(0.5f);

        modelLoader.loadAsync(new VoskModelLoader.LoadCallback() {
            @Override
            public void onReady(String modelPath) {
                activity.runOnUiThread(() -> startRecognition(modelPath));
            }

            @Override
            public void onProgress(int percent) {
                activity.runOnUiThread(() ->
                        tvStatus.setText("下载模型: " + percent + "%"));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Model load failed: " + message);
                activity.runOnUiThread(() -> {
                    tvStatus.setText("离线模型加载失败,请检查网络");
                    callback.onError("Vosk model load failed: " + message);
                });
            }
        });
    }

    private void startRecognition(String modelPath) {
        try {
            if (model == null) {
                model = new Model(modelPath);
            }
            if (recognizer == null) {
                recognizer = new Recognizer(model, SAMPLE_RATE);
            }
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(this);
            listening = true;
            btnMic.setAlpha(1.0f);
            tvStatus.setText("正在聆听...");
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setText("");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start Vosk recognition", e);
            tvStatus.setText("离线语音启动失败");
            callback.onError("Vosk start failed: " + e.getMessage());
        }
    }

    public void stopListening() {
        if (speechService != null && listening) {
            speechService.stop();
            listening = false;
        }
    }

    public void destroy() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
    }

    @Override
    public void onPartialResult(String partial) {
        try {
            String text = new JSONObject(partial).optString("partial", "");
            if (!text.isEmpty()) {
                tvResult.setText(text);
            }
        } catch (Exception e) {
            Log.w(TAG, "partial result parse failed", e);
        }
    }

    @Override
    public void onResult(String result) {
        // 实时(非 final)结果
        try {
            String text = new JSONObject(result).optString("text", "");
            if (!text.isEmpty()) {
                tvResult.setText(text);
            }
        } catch (Exception e) {
            Log.w(TAG, "result parse failed", e);
        }
    }

    @Override
    public void onFinalResult(String finalResult) {
        try {
            String text = new JSONObject(finalResult).optString("text", "");
            if (!text.isEmpty()) {
                tvResult.setText(text);
                tvStatus.setText("识别完成");
                callback.onResult(text);
            } else {
                tvStatus.setText("未识别到语音内容");
            }
        } catch (Exception e) {
            Log.w(TAG, "final result parse failed", e);
        }
        listening = false;
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Vosk timeout");
        listening = false;
        tvStatus.setText("语音输入超时");
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Vosk error", e);
        listening = false;
        tvStatus.setText("语音识别错误");
        callback.onError(e.getMessage() != null ? e.getMessage() : "Vosk error");
    }
}
