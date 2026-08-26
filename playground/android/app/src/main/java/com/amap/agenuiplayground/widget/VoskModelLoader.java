package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * Vosk 离线模型加载器。
 *
 * 模型: vosk-model-small-cn-0.15 (约 42MB)
 * 下载地址: https://alphacephei.com/vosk/models/vosk-model-small-cn-0.15.zip
 *
 * 下载流程:
 * 1. 检查本地是否已有解压后的模型目录
 * 2. 没有则下载 zip 到缓存目录
 * 3. 解压到 filesDir/vosk-model-small-cn-0.15
 * 4. 返回模型路径
 *
 * 所有操作在工作线程执行。
 */
public class VoskModelLoader {

    private static final String TAG = "VoskModelLoader";
    private static final String MODEL_DIR_NAME = "vosk-model-small-cn-0.15";
    private static final String MODEL_ZIP_NAME = "vosk-model-small-cn-0.15.zip";
    private static final String DOWNLOAD_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.15.zip";
    private static final long EXPECTED_SIZE = 42 * 1024 * 1024; // 约 42MB,用作进度参考

    public interface LoadCallback {
        void onReady(String modelPath);
        void onProgress(int percent);
        void onError(String message);
    }

    private final Context context;
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public VoskModelLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 同步获取模型路径。如未下载会阻塞下载。需在工作线程调用。
     */
    public String getModelPathSync() {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
        if (isModelPresent(modelDir)) {
            return modelDir.getAbsolutePath();
        }
        return null;
    }

    /**
     * 异步加载模型(下载 + 解压)。
     */
    public void loadAsync(LoadCallback callback) {
        if (loading.get()) {
            if (callback != null) callback.onError("Model loading already in progress");
            return;
        }
        loading.set(true);

        new Thread(() -> {
            try {
                File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
                if (isModelPresent(modelDir)) {
                    notifyReady(callback, modelDir.getAbsolutePath());
                    return;
                }

                File zipFile = new File(context.getCacheDir(), MODEL_ZIP_NAME);
                if (!zipFile.exists() || zipFile.length() < 1024) {
                    downloadZip(zipFile, callback);
                }

                unzip(zipFile, context.getFilesDir());

                if (isModelPresent(modelDir)) {
                    // 下载完成,清理 zip
                    if (zipFile.exists()) zipFile.delete();
                    notifyReady(callback, modelDir.getAbsolutePath());
                } else {
                    notifyError(callback, "Unzip failed: model dir not found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Model load failed", e);
                notifyError(callback, e.getMessage() != null ? e.getMessage() : "Load failed");
            } finally {
                loading.set(false);
            }
        }).start();
    }

    private boolean isModelPresent(File modelDir) {
        return modelDir.isDirectory()
                && new File(modelDir, "README").exists()
                && new File(modelDir, "conf").isDirectory();
    }

    private void downloadZip(File target, LoadCallback callback) throws IOException {
        Log.d(TAG, "Downloading model from " + DOWNLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            long total = conn.getContentLength();
            if (total <= 0) total = EXPECTED_SIZE;

            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                long done = 0;
                long lastReport = 0;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (callback != null && now - lastReport > 500) {
                        int percent = (int) (done * 100 / total);
                        callback.onProgress(percent);
                        lastReport = now;
                    }
                }
                fos.flush();
            }
            Log.d(TAG, "Download complete: " + target.length() + " bytes");
        } finally {
            conn.disconnect();
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (InputStream is = zf.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = is.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                }
            }
        }
    }

    private void notifyReady(LoadCallback cb, String path) {
        if (cb != null) cb.onReady(path);
    }

    private void notifyError(LoadCallback cb, String msg) {
        if (cb != null) cb.onError(msg);
    }
}
