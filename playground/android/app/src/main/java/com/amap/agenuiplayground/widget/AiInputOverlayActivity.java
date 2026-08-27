package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.agenuiplayground.R;

/**
 * 全屏小艺 AI 输入浮层 Activity — 覆盖整个 4K 大屏。
 *
 * <p>解决 App 窗口被系统限制在屏幕局部时，DrawerLayout 内的 AI 面板
 * 无法占据屏幕右侧足够宽度的问题。
 *
 * <p>本 Activity 使用透明主题，覆盖全屏，右侧显示 30% 宽度的 AI 面板，
 * 左侧为半透明遮罩（点击关闭）。
 *
 * <p>用户输入文本后，通过 {@link Intent#EXTRA_TEXT} 返回给调用方，
 * 调用方在 {@link AppCompatActivity#onActivityResult} 中接收并推流。
 *
 * <p>复用 {@link AiInputDrawerController} 管理面板交互逻辑。
 */
public class AiInputOverlayActivity extends AppCompatActivity {

    private static final String TAG = "AiInputOverlay";

    public static final String EXTRA_INPUT_TEXT = "input_text";
    public static final String EXTRA_SOURCE = "source";

    private AiInputDrawerController controller;
    private View panelContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_input_overlay);

        // Find views
        View dimBackground = findViewById(R.id.overlayDim);
        panelContainer = findViewById(R.id.overlayPanelContainer);

        // Slide-in animation from right
        TranslateAnimation slideIn = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        slideIn.setDuration(280);
        slideIn.setInterpolator(new DecelerateInterpolator());
        panelContainer.startAnimation(slideIn);

        // Dim background click → dismiss
        dimBackground.setOnClickListener(v -> finishWithAnimation());

        // Initialize AI drawer controller
        View aiRoot = panelContainer.getChildAt(0);
        if (aiRoot == null) {
            Log.e(TAG, "AI root view not found, finishing");
            finish();
            return;
        }

        controller = new AiInputDrawerController(this, new AiInputDrawerController.Callback() {
            @Override
            public void onSend(String text) {
                // Return the text to the caller
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_INPUT_TEXT, text);
                resultIntent.putExtra(EXTRA_SOURCE, "ai_overlay");
                setResult(RESULT_OK, resultIntent);
                finishWithAnimation();
            }

            @Override
            public void onClose() {
                finishWithAnimation();
            }
        });
        controller.bind(aiRoot);
    }

    @Override
    public void onBackPressed() {
        finishWithAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.destroy();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (controller != null) {
            controller.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (controller != null) {
            controller.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Slide-out animation then finish.
     */
    private void finishWithAnimation() {
        TranslateAnimation slideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        slideOut.setDuration(200);
        slideOut.setInterpolator(new AccelerateInterpolator());
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                finish();
                overridePendingTransition(0, 0);
            }
        });
        panelContainer.startAnimation(slideOut);
    }
}
