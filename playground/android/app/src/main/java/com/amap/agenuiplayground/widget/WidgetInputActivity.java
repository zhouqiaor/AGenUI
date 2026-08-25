package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.amap.agenuiplayground.R;

/**
 * Transparent Activity for receiving user text input for AI generation.
 *
 * Shown when user taps the AI input button on the widget.
 * On send: launches WidgetRenderActivity in stream mode with the user text,
 * then finishes itself.
 */
public class WidgetInputActivity extends Activity {

    private static final String TAG = "WidgetInputActivity";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private EditText etInput;
    private View btnSend;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appWidgetId = getIntent().getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.widget_input_activity);

        etInput = findViewById(R.id.etAiInput);
        btnSend = findViewById(R.id.btnSend);
        View btnCancel = findViewById(R.id.btnCancel);

        btnSend.setEnabled(false);
        btnSend.setAlpha(0.4f);

        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                btnSend.setEnabled(hasText);
                btnSend.setAlpha(hasText ? 1.0f : 0.4f);
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) return;

            Intent renderIntent = new Intent(this, WidgetRenderActivity.class);
            renderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            renderIntent.putExtra(WidgetRenderActivity.EXTRA_MODE,
                    WidgetRenderActivity.MODE_STREAM);
            renderIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            renderIntent.putExtra(WidgetRenderActivity.EXTRA_USER_TEXT, text);

            // Launch via PendingIntent to bypass BAL restriction
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    | android.app.PendingIntent.FLAG_IMMUTABLE;
            try {
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                        this, appWidgetId, renderIntent, flags);
                pi.send();
            } catch (android.app.PendingIntent.CanceledException e) {
                // Fallback: direct start (may fail on Android 10+ from background)
                renderIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(renderIntent);
            }

            finish();
        });

        btnCancel.setOnClickListener(v -> finish());
    }
}
