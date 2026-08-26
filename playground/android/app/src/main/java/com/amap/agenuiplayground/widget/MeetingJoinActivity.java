package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Simulates joining a meeting: shows meeting info + "正在加入会议..." + a progress
 * bar, then auto-dismisses after 2 seconds to mimic a successful join.
 *
 * <p>Launched from the widget quick-join button ({@code ACTION_QUICK_JOIN}).
 * Uses {@link android.R.style#Theme_Translucent} via the manifest-declared
 * {@code Theme.AGenUIPlayground.Transparent} theme so it appears as a brief
 * floating overlay.
 */
public class MeetingJoinActivity extends Activity {

    private static final String TAG = "MeetingJoinActivity";
    private static final long AUTO_DISMISS_MS = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        String template = getIntent().getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE);
        String meetingTitle = resolveMeetingTitle(template);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(new android.graphics.drawable.ColorDrawable(0xCC000000));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(meetingTitle);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(18);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        TextView tvStatus = new TextView(this);
        tvStatus.setText("正在加入会议...");
        tvStatus.setTextColor(0xFFCCCCCC);
        tvStatus.setTextSize(14);
        tvStatus.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);

        ProgressBar pb = new ProgressBar(this);
        android.view.ViewGroup.LayoutParams pbLp = new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams pbContainerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbContainerLp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);

        root.addView(tvTitle);
        root.addView(tvStatus, statusLp);
        root.addView(pb, pbContainerLp);

        setContentView(root);

        handler.postDelayed(this::finish, AUTO_DISMISS_MS);
    }

    private String resolveMeetingTitle(String template) {
        if ("meeting".equals(template)) return "产品周会";
        if ("agenda".equals(template)) return "今日会议";
        return "会议";
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
