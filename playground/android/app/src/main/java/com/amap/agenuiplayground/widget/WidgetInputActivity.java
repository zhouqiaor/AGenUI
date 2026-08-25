package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.agenuiplayground.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Unified input panel with three tabs: Keyboard / Voice / File.
 *
 * Shown when user taps the AI input button on the widget.
 * - Keyboard: EditText + quick chips (天气/待办/日程)
 * - Voice: Placeholder for Vosk integration (P2.2), shows status text
 * - File: SAF file picker for PDF/DOCX/TXT
 *
 * On send: launches WidgetRenderActivity in stream mode with the user text,
 * then finishes itself.
 */
public class WidgetInputActivity extends Activity {

    private static final String TAG = "WidgetInputActivity";
    private static final int REQUEST_FILE_PICK = 1001;
    private static final int REQUEST_MIC_PERMISSION = 1002;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private EditText etInput;
    private View btnSend;
    private ViewFlipper contentFlipper;
    private TextView tvVoiceStatus;
    private TextView tvVoiceResult;
    private TextView tvFilePreview;
    private TextView tvFileHint;
    private View btnSelectFile;
    private View btnMic;

    // Tab views
    private TextView tabKeyboard, tabVoice, tabFile;

    // Current input source
    private String fileText = null;
    private WidgetVoiceHelper voiceHelper;

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
        contentFlipper = findViewById(R.id.contentFlipper);

        tabKeyboard = findViewById(R.id.tabKeyboard);
        tabVoice = findViewById(R.id.tabVoice);
        tabFile = findViewById(R.id.tabFile);

        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        tvVoiceResult = findViewById(R.id.tvVoiceResult);
        btnMic = findViewById(R.id.btnMic);

        tvFileHint = findViewById(R.id.tvFileHint);
        tvFilePreview = findViewById(R.id.tvFilePreview);
        btnSelectFile = findViewById(R.id.btnSelectFile);

        btnSend.setEnabled(false);
        btnSend.setAlpha(0.4f);

        // Tab switching
        tabKeyboard.setOnClickListener(v -> switchTab(0));
        tabVoice.setOnClickListener(v -> switchTab(1));
        tabFile.setOnClickListener(v -> switchTab(2));

        // Quick chips
        findViewById(R.id.tvQuickWeather).setOnClickListener(v -> {
            etInput.setText("今天北京天气");
            etInput.setSelection(etInput.getText().length());
        });
        findViewById(R.id.tvQuickTodo).setOnClickListener(v -> {
            etInput.setText("今日待办清单");
            etInput.setSelection(etInput.getText().length());
        });
        findViewById(R.id.tvQuickAgenda).setOnClickListener(v -> {
            etInput.setText("今日会议日程");
            etInput.setSelection(etInput.getText().length());
        });

        // Text watcher for send button enable
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

        // Voice button
        btnMic.setOnClickListener(v -> {
            if (hasMicPermission()) {
                if (voiceHelper == null) {
                    voiceHelper = new WidgetVoiceHelper(this, tvVoiceStatus, tvVoiceResult,
                            (ImageButton) btnMic, new WidgetVoiceHelper.VoiceCallback() {
                                @Override
                                public void onResult(String text) {
                                    // Enable send button
                                    btnSend.setEnabled(true);
                                    btnSend.setAlpha(1.0f);
                                }
                                @Override
                                public void onError(String message) {
                                    Log.w(TAG, "Voice error: " + message);
                                }
                            });
                }
                voiceHelper.startListening();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        REQUEST_MIC_PERMISSION);
            }
        });

        // File button
        btnSelectFile.setOnClickListener(v -> launchFilePicker());

        // Send button
        btnSend.setOnClickListener(v -> {
            String text = getCurrentInputText();
            if (text == null || text.trim().isEmpty()) return;

            launchStreamRender(text.trim());
        });

        btnCancel.setOnClickListener(v -> finish());

        // Default to keyboard tab
        switchTab(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceHelper != null) {
            voiceHelper.destroy();
            voiceHelper = null;
        }
    }

    private void switchTab(int index) {
        contentFlipper.setDisplayedChild(index);
        tabKeyboard.setSelected(index == 0);
        tabVoice.setSelected(index == 1);
        tabFile.setSelected(index == 2);

        // Update send button state
        boolean hasInput = false;
        if (index == 0) {
            hasInput = etInput.getText() != null && etInput.getText().toString().trim().length() > 0;
        } else if (index == 1) {
            hasInput = tvVoiceResult.getText() != null && tvVoiceResult.getText().length() > 0;
        } else if (index == 2) {
            hasInput = fileText != null && !fileText.isEmpty();
        }
        btnSend.setEnabled(hasInput);
        btnSend.setAlpha(hasInput ? 1.0f : 0.4f);
    }

    private String getCurrentInputText() {
        int currentTab = contentFlipper.getDisplayedChild();
        if (currentTab == 0) {
            return etInput.getText().toString();
        } else if (currentTab == 1) {
            String result = tvVoiceResult.getText().toString();
            return result.isEmpty() ? null : result;
        } else if (currentTab == 2) {
            return fileText;
        }
        return null;
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
        });
        try {
            startActivityForResult(intent, REQUEST_FILE_PICK);
        } catch (Exception e) {
            Log.e(TAG, "File picker failed", e);
            tvFileHint.setText("文件选择器不可用");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                extractTextFromFile(uri);
            }
        }
    }

    private void extractTextFromFile(Uri uri) {
        tvFileHint.setText("正在解析文件...");
        new Thread(() -> {
            String text = null;
            String mime = getContentResolver().getType(uri);
            try {
                if (mime == null) mime = "";
                if (mime.contains("text/") || uri.toString().endsWith(".txt")) {
                    text = readPlainText(uri);
                } else if (mime.contains("pdf") || uri.toString().endsWith(".pdf")) {
                    text = PdfTextExtractor.extractText(WidgetInputActivity.this, uri);
                    if (text == null) {
                        text = "PDF 解析失败。文件: " + getDisplayName(uri);
                    }
                } else {
                    text = "不支持的文件格式。支持: PDF/TXT。文件: " + getDisplayName(uri);
                }
            } catch (Exception e) {
                Log.e(TAG, "File extraction failed", e);
                text = "文件解析失败: " + e.getMessage();
            }

            final String result = text;
            runOnUiThread(() -> {
                if (result != null) {
                    fileText = result;
                    String preview = result.length() > 500 ? result.substring(0, 500) + "..." : result;
                    tvFilePreview.setText(preview);
                    tvFilePreview.setVisibility(View.VISIBLE);
                    tvFileHint.setText("已解析（" + result.length() + " 字）");
                    btnSend.setEnabled(true);
                    btnSend.setAlpha(1.0f);
                } else {
                    tvFileHint.setText("解析失败");
                }
            });
        }).start();
    }

    private String readPlainText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String text = sb.toString();
        // Truncate to 4000 chars for LLM
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        return text;
    }

    private String getDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        return name != null ? name : uri.toString();
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvVoiceStatus.setText("权限已授予。语音识别需要 Vosk 模型（P2.2 待实现）");
            } else {
                tvVoiceStatus.setText("需要录音权限才能使用语音输入");
            }
        }
    }

    private void launchStreamRender(String text) {
        Intent renderIntent = new Intent(this, WidgetRenderActivity.class);
        renderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        renderIntent.putExtra(WidgetRenderActivity.EXTRA_MODE,
                WidgetRenderActivity.MODE_STREAM);
        renderIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        renderIntent.putExtra(WidgetRenderActivity.EXTRA_USER_TEXT, text);

        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE;
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    this, appWidgetId, renderIntent, flags);
            pi.send();
        } catch (android.app.PendingIntent.CanceledException e) {
            renderIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(renderIntent);
        }

        finish();
    }
}
