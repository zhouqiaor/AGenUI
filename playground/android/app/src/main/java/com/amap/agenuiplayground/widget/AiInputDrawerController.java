package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.agenuiplayground.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 小艺风格右侧 AI 输入侧边面板控制器。
 *
 * <p>管理三选项卡(键盘/语音/文件)、输入状态、发送逻辑。
 *
 * <p>与 {@link WidgetInputActivity} 的差异：
 * <ul>
 *   <li>宿主在 Playground Activity 内,不启动新 Activity</li>
 *   <li>发送时直接回调到 {@link Callback#onSend(String)},由宿主推流到当前 Surface</li>
 *   <li>不依赖 appWidgetId,不需要 PendingIntent/BAL 豁免</li>
 * </ul>
 *
 * 语音与文件解析逻辑复用 widget 包下的 {@link WidgetVoiceHelper}、
 * {@link WidgetVoskManager}、{@link PdfTextExtractor}。
 */
public class AiInputDrawerController {

    private static final String TAG = "AiInputDrawer";
    private static final int REQUEST_MIC_PERMISSION = 1002;
    private static final int REQUEST_FILE_PICK = 1003;

    private final Activity host;
    private final Callback callback;

    // 根 View
    private View root;

    // Tab
    private TextView tabKeyboard, tabVoice, tabFile;
    private ViewFlipper contentFlipper;

    // 键盘
    private EditText etInput;

    // 语音
    private TextView tvVoiceStatus;
    private TextView tvVoiceResult;
    private View btnMic;

    // 文件
    private TextView tvFileHint;
    private TextView tvFilePreview;
    private View btnSelectFile;
    private String fileText = null;

    // 底部
    private View btnSend;
    private View btnCancel;
    private View btnClose;
    private View btnClear;
    private TextView tvStatus;

    // 语音 helper
    private WidgetVoiceHelper voiceHelper;
    private WidgetVoskManager voskManager;
    private boolean useVosk = true;

    public interface Callback {
        /**
         * 用户点击发送,输入文本已组装完毕,宿主推流到当前 Surface。
         * @param text 用户输入文本(键盘/语音/文件)
         */
        void onSend(String text);

        /**
         * 用户关闭面板。
         */
        void onClose();
    }

    public AiInputDrawerController(@NonNull Activity host, @NonNull Callback callback) {
        this.host = host;
        this.callback = callback;
    }

    /**
     * 绑定 View,必须在 setContentView 之后调用。
     */
    public void bind(@NonNull View root) {
        this.root = root;

        // Tabs
        tabKeyboard = root.findViewById(R.id.aiTabKeyboard);
        tabVoice = root.findViewById(R.id.aiTabVoice);
        tabFile = root.findViewById(R.id.aiTabFile);
        contentFlipper = root.findViewById(R.id.aiContentFlipper);

        // 键盘
        etInput = root.findViewById(R.id.etAiDrawerInput);

        // 语音
        tvVoiceStatus = root.findViewById(R.id.tvAiVoiceStatus);
        tvVoiceResult = root.findViewById(R.id.tvAiVoiceResult);
        btnMic = root.findViewById(R.id.btnAiMic);

        // 文件
        tvFileHint = root.findViewById(R.id.tvAiFileHint);
        tvFilePreview = root.findViewById(R.id.tvAiFilePreview);
        btnSelectFile = root.findViewById(R.id.btnAiSelectFile);

        // 底部
        btnSend = root.findViewById(R.id.btnAiDrawerSend);
        btnCancel = root.findViewById(R.id.btnAiDrawerCancel);
        btnClose = root.findViewById(R.id.btnAiDrawerClose);
        btnClear = root.findViewById(R.id.tvAiClear);
        tvStatus = root.findViewById(R.id.tvAiDrawerStatus);

        setupListeners();
        switchTab(0);
    }

    private void setupListeners() {
        // Tab 切换
        tabKeyboard.setOnClickListener(v -> switchTab(0));
        tabVoice.setOnClickListener(v -> switchTab(1));
        tabFile.setOnClickListener(v -> switchTab(2));

        // 快捷芯片
        root.findViewById(R.id.tvAiQuickWeather).setOnClickListener(v -> {
            etInput.setText("今天北京天气");
            etInput.setSelection(etInput.getText().length());
        });
        root.findViewById(R.id.tvAiQuickTodo).setOnClickListener(v -> {
            etInput.setText("今日待办清单");
            etInput.setSelection(etInput.getText().length());
        });
        root.findViewById(R.id.tvAiQuickAgenda).setOnClickListener(v -> {
            etInput.setText("今日会议日程");
            etInput.setSelection(etInput.getText().length());
        });
        btnClear.setOnClickListener(v -> {
            etInput.setText("");
            fileText = null;
            tvFilePreview.setVisibility(View.GONE);
            tvFileHint.setText("选择 PDF/TXT 文件");
            updateSendButton();
        });

        // 文本监听
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (contentFlipper.getDisplayedChild() == 0) {
                    updateSendButton();
                }
            }
        });

        // 语音
        btnMic.setOnClickListener(v -> {
            if (hasMicPermission()) {
                startVoiceInput();
            } else {
                ActivityCompat.requestPermissions(host,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        REQUEST_MIC_PERMISSION);
            }
        });

        // 文件
        btnSelectFile.setOnClickListener(v -> launchFilePicker());

        // 底部按钮
        btnSend.setOnClickListener(v -> {
            String text = getCurrentInputText();
            if (text == null || text.trim().isEmpty()) return;
            setStatus("发送中...");
            btnSend.setEnabled(false);
            btnSend.setAlpha(0.4f);
            callback.onSend(text.trim());
        });

        btnCancel.setOnClickListener(v -> callback.onClose());
        btnClose.setOnClickListener(v -> callback.onClose());
    }

    /**
     * 切换 Tab。
     */
    private void switchTab(int index) {
        contentFlipper.setDisplayedChild(index);
        tabKeyboard.setSelected(index == 0);
        tabVoice.setSelected(index == 1);
        tabFile.setSelected(index == 2);
        updateSendButton();
    }

    private void updateSendButton() {
        int currentTab = contentFlipper.getDisplayedChild();
        boolean hasInput = false;
        if (currentTab == 0) {
            hasInput = etInput.getText() != null
                    && etInput.getText().toString().trim().length() > 0;
        } else if (currentTab == 1) {
            hasInput = tvVoiceResult.getText() != null
                    && tvVoiceResult.getText().length() > 0;
        } else if (currentTab == 2) {
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

    private void startVoiceInput() {
        WidgetVoiceHelper.VoiceCallback cb = new WidgetVoiceHelper.VoiceCallback() {
            @Override
            public void onResult(String text) {
                updateSendButton();
                setStatus("语音识别完成");
            }
            @Override
            public void onError(String message) {
                Log.w(TAG, "Voice error: " + message);
                setStatus("语音错误: " + message);
            }
        };

        if (useVosk) {
            if (voskManager == null) {
                voskManager = new WidgetVoskManager(host, tvVoiceStatus, tvVoiceResult,
                        (ImageButton) btnMic, cb);
            }
            voskManager.startListening();
        } else {
            if (voiceHelper == null) {
                voiceHelper = new WidgetVoiceHelper(host, tvVoiceStatus, tvVoiceResult,
                        (ImageButton) btnMic, cb);
            }
            voiceHelper.startListening();
        }
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
            host.startActivityForResult(intent, REQUEST_FILE_PICK);
        } catch (Exception e) {
            Log.e(TAG, "File picker failed", e);
            tvFileHint.setText("文件选择器不可用");
        }
    }

    /**
     * Activity 的 onActivityResult 转发入口。
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE_PICK && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    host.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    extractTextFromFile(uri);
                } catch (Exception e) {
                    Log.e(TAG, "Persistable uri permission failed", e);
                    tvFileHint.setText("文件权限获取失败");
                }
            }
        }
    }

    private void extractTextFromFile(Uri uri) {
        tvFileHint.setText("正在解析文件...");
        new Thread(() -> {
            String text = null;
            String mime = host.getContentResolver().getType(uri);
            try {
                if (mime == null) mime = "";
                if (mime.contains("text/") || uri.toString().endsWith(".txt")) {
                    text = readPlainText(uri);
                } else if (mime.contains("pdf") || uri.toString().endsWith(".pdf")) {
                    text = PdfTextExtractor.extractText(host, uri);
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
            host.runOnUiThread(() -> {
                if (result != null) {
                    fileText = result;
                    String preview = result.length() > 500
                            ? result.substring(0, 500) + "..." : result;
                    tvFilePreview.setText(preview);
                    tvFilePreview.setVisibility(View.VISIBLE);
                    tvFileHint.setText("已解析（" + result.length() + " 字）");
                    updateSendButton();
                } else {
                    tvFileHint.setText("解析失败");
                }
            });
        }).start();
    }

    private String readPlainText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = host.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String text = sb.toString();
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
        return ContextCompat.checkSelfPermission(host,
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Activity 权限请求回调,转发到语音 helper。
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvVoiceStatus.setText("权限已授予,点击麦克风开始");
            } else {
                tvVoiceStatus.setText("需要录音权限才能使用语音输入");
            }
        }
    }

    /**
     * 重置面板状态(关闭时调用)。
     */
    public void reset() {
        etInput.setText("");
        fileText = null;
        tvFilePreview.setVisibility(View.GONE);
        tvFileHint.setText("选择 PDF/TXT 文件");
        tvVoiceResult.setVisibility(View.GONE);
        tvVoiceResult.setText("");
        tvVoiceStatus.setText("点击麦克风开始语音输入");
        setStatus("就绪 · 输入描述生成 A2UI");
        switchTab(0);
    }

    /**
     * 发送完成回调(宿主通知结果)。
     */
    public void onSendComplete(boolean success, String message) {
        host.runOnUiThread(() -> {
            if (success) {
                setStatus("生成完成: " + message);
            } else {
                setStatus("生成失败: " + message);
            }
            btnSend.setEnabled(true);
            btnSend.setAlpha(1.0f);
        });
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }

    /**
     * 销毁时清理资源。
     */
    public void destroy() {
        if (voiceHelper != null) {
            voiceHelper.destroy();
            voiceHelper = null;
        }
        if (voskManager != null) {
            voskManager.destroy();
            voskManager = null;
        }
    }
}
