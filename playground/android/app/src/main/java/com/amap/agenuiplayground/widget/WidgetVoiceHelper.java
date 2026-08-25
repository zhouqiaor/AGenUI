package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.amap.agenuiplayground.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Voice input helper using Android built-in SpeechRecognizer (Google online STT).
 *
 * This is a lightweight alternative to Vosk for Phase 2.2.
 * Vosk offline model (42MB) can be added later if offline recognition is needed.
 *
 * Flow: tap mic → start listening → partial results update UI → final result → send
 */
public class WidgetVoiceHelper implements RecognitionListener {

    private static final String TAG = "WidgetVoiceHelper";

    private final Activity activity;
    private SpeechRecognizer speechRecognizer;
    private final TextView tvStatus;
    private final TextView tvResult;
    private final ImageButton btnMic;
    private final VoiceCallback callback;
    private boolean listening = false;

    public interface VoiceCallback {
        void onResult(String text);
        void onError(String message);
    }

    public WidgetVoiceHelper(Activity activity, TextView tvStatus, TextView tvResult,
                              ImageButton btnMic, VoiceCallback callback) {
        this.activity = activity;
        this.tvStatus = tvStatus;
        this.tvResult = tvResult;
        this.btnMic = btnMic;
        this.callback = callback;
    }

    public void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            tvStatus.setText("语音识别不可用（需要 Google 服务）");
            callback.onError("SpeechRecognizer not available");
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            speechRecognizer.setRecognitionListener(this);
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        listening = true;
        btnMic.setAlpha(1.0f);
        tvStatus.setText("正在聆听...");
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText("");
        speechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (speechRecognizer != null && listening) {
            speechRecognizer.stopListening();
            listening = false;
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        tvStatus.setText("正在聆听...");
    }

    @Override
    public void onBeginningOfSpeech() {
        tvStatus.setText("检测到语音...");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // Could update a volume bar here
    }

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        tvStatus.setText("识别中...");
    }

    @Override
    public void onError(int error) {
        listening = false;
        String message = getErrorMessage(error);
        Log.w(TAG, "Speech error: " + error + " (" + message + ")");
        tvStatus.setText(message);
        callback.onError(message);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            tvResult.setText(text);
            tvStatus.setText("识别完成");
            callback.onResult(text);
        } else {
            tvStatus.setText("未识别到语音内容");
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (partial != null && !partial.isEmpty()) {
            tvResult.setText(partial.get(0));
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}

    private static String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "未匹配到语音";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "语音输入超时";
            case SpeechRecognizer.ERROR_AUDIO:
                return "录音错误";
            case SpeechRecognizer.ERROR_NETWORK:
                return "网络错误";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "网络超时";
            case SpeechRecognizer.ERROR_SERVER:
                return "服务器错误";
            case SpeechRecognizer.ERROR_CLIENT:
                return "客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "权限不足";
            default:
                return "语音识别错误 (" + error + ")";
        }
    }
}
