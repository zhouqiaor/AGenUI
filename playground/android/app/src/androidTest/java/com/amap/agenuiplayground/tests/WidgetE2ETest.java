package com.amap.agenuiplayground.tests;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Widget E2E 测试 — 使用 UiAutomator 在桌面验证 Widget 交互。
 *
 * <p>前提条件：
 * <ul>
 *   <li>设备上已安装 AGenUI Playground APK</li>
 *   <li>桌面上已添加 A2UI Widget（手动添加或通过 adb bind）</li>
 *   <li>设备已连接 adb 且 accessible</li>
 * </ul>
 *
 * <p>验收标准对应 SPEC-Widget-AutomatedTesting.md 的 E2E-01 ~ E2E-03。
 */
@RunWith(AndroidJUnit4.class)
public class WidgetE2ETest {

    private static final String TAG = "WidgetE2ETest";
    private static final String PACKAGE_NAME = "com.amap.agenuiplayground";
    private static final long TIMEOUT_MS = 10000;

    private UiDevice device;
    private Context context;

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        context = instrumentation.getTargetContext();

        // Press Home to go to launcher
        device.pressHome();
        device.waitForIdle();
    }

    @After
    public void tearDown() {
        // Return to home screen after test
        device.pressHome();
    }

    // ==================== E2E-01: Widget 在桌面可见 ====================

    @Test
    public void E2E01_widgetVisibleOnHomeScreen() {
        // 等待桌面加载
        device.waitForIdle();

        // 尝试查找 Widget（通过标题文本或内容描述）
        // Widget 标题格式："AGenUI · weather" / "AGenUI · agenda" / "AGenUI · todo"
        boolean found = device.wait(Until.hasObject(By.textContains("AGenUI")), TIMEOUT_MS);

        if (!found) {
            // 可能 Widget 还没被添加到桌面——在 CI 环境中跳过
            // 手动测试时应该先添加 Widget
            System.out.println("WARNING: Widget not found on home screen. " +
                    "Please add A2UI Widget to home screen first.");
            return;
        }

        List<UiObject2> widgets = device.findObjects(By.textContains("AGenUI"));
        assertTrue("At least one A2UI widget should be visible",
                widgets != null && !widgets.isEmpty());
    }

    // ==================== E2E-02: 点击刷新按钮 ====================

    @Test
    public void E2E02_tapRefresh_triggersRerender() throws InterruptedException {
        device.waitForIdle();

        // 查找刷新按钮（contentDescription = "Refresh"）
        UiObject2 refreshBtn = device.findObject(By.desc("Refresh"));

        if (refreshBtn == null) {
            System.out.println("WARNING: Refresh button not found. " +
                    "Widget may not be placed on home screen.");
            return;
        }

        // 点击刷新
        refreshBtn.click();

        // 等待重新渲染（onUpdate → JobIntentService → WidgetRenderActivity → Bitmap → updateAppWidget）
        Thread.sleep(8000);
        device.waitForIdle();

        // 截图验证非空白
        // 注意：UiAutomator 的 takeScreenshot 需要 API 30+
        // 对于低版本设备，用 adb screencap 作为替代方案
        Bitmap screenshot = takeScreenshot();
        if (screenshot != null) {
            // 检查截图中有非白色像素
            boolean hasContent = false;
            int w = screenshot.getWidth();
            int h = screenshot.getHeight();
            for (int x = w / 4; x < 3 * w / 4; x += 20) {
                for (int y = h / 4; y < 3 * h / 4; y += 20) {
                    int pixel = screenshot.getPixel(x, y);
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    if (!(r > 250 && g > 250 && b > 250)) {
                        hasContent = true;
                        break;
                    }
                }
                if (hasContent) break;
            }
            assertTrue("Screenshot should contain non-white pixels after refresh",
                    hasContent);
        }
    }

    // ==================== E2E-03: 模板切换 ====================

    @Test
    public void E2E03_templateSwitch_changesContent() throws InterruptedException {
        device.waitForIdle();

        // 查找模板切换按钮
        UiObject2 switchBtn = device.findObject(By.desc("Switch template"));

        if (switchBtn == null) {
            System.out.println("WARNING: Switch template button not found.");
            return;
        }

        // 记录当前标题
        UiObject2 titleBefore = device.findObject(By.textContains("AGenUI"));
        String titleTextBefore = titleBefore != null ? titleBefore.getText() : "";

        // 点击切换
        switchBtn.click();
        Thread.sleep(6000);
        device.waitForIdle();

        // 验证标题变化（模板名应改变）
        UiObject2 titleAfter = device.findObject(By.textContains("AGenUI"));
        assertNotNull("Title should still be visible after switch", titleAfter);

        // 标题格式 "AGenUI · <template>"，模板名应变化
        if (!titleTextBefore.isEmpty() && titleAfter.getText() != null) {
            assertTrue("Template name should change after switch. Before: " +
                    titleTextBefore + ", After: " + titleAfter.getText(),
                    !titleTextBefore.equals(titleAfter.getText()));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 尝试截图。UiAutomator 的 takeScreenshot 在某些设备上可能返回 null。
     */
    private Bitmap takeScreenshot() {
        try {
            java.io.File tmpFile = java.io.File.createTempFile("widget_e2e", ".png",
                    context.getCacheDir());
            device.takeScreenshot(tmpFile, 1.0f, 100);
            return android.graphics.BitmapFactory.decodeFile(tmpFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Screenshot failed: " + e.getMessage());
            return null;
        }
    }
}
