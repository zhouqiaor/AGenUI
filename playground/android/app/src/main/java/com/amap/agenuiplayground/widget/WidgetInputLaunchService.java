package com.amap.agenuiplayground.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * ForegroundService 中介,用于绕过 Android 10+ Background Activity Launch (BAL) 限制。
 *
 * Widget 的 RemoteViews 点击通过 PendingIntent.send() 触发 broadcast,直接从 broadcast
 * 启动 Activity 在 Android 10+ 上会被系统拦截。改为:receiver → startForegroundService(this)
 * → 在 Service 中 startActivity 启动 WidgetInputActivity。
 *
 * Service 生命周期极短,startActivity 后立即 stopSelf。
 */
public class WidgetInputLaunchService extends Service {

    private static final String TAG = "WidgetInputLaunchService";
    private static final String CHANNEL_ID = "widget_input_launch";
    private static final int NOTIFICATION_ID = 0x77A1; // AGenUI widget input

    public static final String EXTRA_APPWIDGET_ID = A2UIWidgetProvider.EXTRA_APPWIDGET_ID;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int appWidgetId = intent != null
                ? intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                : AppWidgetManager.INVALID_APPWIDGET_ID;

        Log.d(TAG, "onStartCommand: appWidgetId=" + appWidgetId);

        // 必须先 startForeground,否则 Android 12+ 会抛 ForegroundServiceStartNotAllowedException
        startForeground(NOTIFICATION_ID, buildNotification());

        try {
            Intent inputIntent = new Intent(this, WidgetInputActivity.class);
            inputIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            inputIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivity(inputIntent);
            Log.d(TAG, "Started WidgetInputActivity from foreground service");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WidgetInputActivity", e);
        }

        // 立即停止自身,Service 只用于中介启动 Activity
        stopSelf();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(
                        CHANNEL_ID,
                        "AGenUI 小组件输入",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("用于在桌面小组件点击时启动输入面板");
                channel.setSound(null, null);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AGenUI")
                .setContentText("正在打开输入面板...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .build();
    }
}
