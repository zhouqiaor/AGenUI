package com.amap.agenuiplayground;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.ISurfaceManagerListener;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenui.render.surface.SurfaceManager;
import com.amap.agenui.render.surface.SurfaceSize;
import com.amap.agenuiplayground.component.factory.TequSettingsLinkComponentFactory;
import com.amap.agenuiplayground.component.factory.TequSettingsSliderComponentFactory;
import com.amap.agenuiplayground.component.factory.TequSettingsSwitchComponentFactory;
import com.amap.agenuiplayground.function.ToastFunction;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Settings Panel Activity — 4K 适配的设置面板界面。
 *
 * <p>初始化 AGenUI 引擎，加载 TEQU-4K Design Token + Theme，
 * 注册自定义组件（TequSettingsSwitch/Slider/Link），
 * 流式发送 settings-panel 协议消息。
 *
 * <p>启动方式：
 * <pre>
 * adb shell am start -n com.amap.agenuiplayground/.SettingsPanelActivity
 * </pre>
 *
 * <p>支持 Intent extras：
 * <ul>
 *   <li>{@code autoRender} (boolean) — 启动后自动渲染设置面板（默认 true）</li>
 *   <li>{@code surfaceId} (String) — 自定义 surfaceId（默认 "settings_panel"）</li>
 * </ul>
 */
public class SettingsPanelActivity extends AppCompatActivity {

    private static final String TAG = "SettingsPanel";
    private static final String SURFACE_ID_DEFAULT = "settings_panel";

    private SurfaceManager surfaceManager;
    private FrameLayout renderContainer;
    private String currentSurfaceId;

    // 4K Design Token & Theme JSON (loaded from assets)
    private String designTokenJson;
    private String themeJson;

    // Settings panel protocol messages (loaded from assets)
    private String createSurfaceMsg;
    private String updateComponentsMsg;
    private String updateDataModelMsg;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force fullscreen immersive mode — some devices (e.g. HUAWEI IdeaHub)
        // constrain app windows to a sub-region of the display by default.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        // Request layout across the full display bounds
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(params);

        // Full-screen, chrome-free render container
        renderContainer = new FrameLayout(this);
        renderContainer.setClipChildren(false);
        renderContainer.setBackgroundColor(Color.WHITE);
        setContentView(renderContainer);

        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Step 1: Initialize AGenUI engine
        AGenUI agenui = AGenUI.getInstance();
        agenui.initialize(getApplicationContext());

        // Log display info for diagnostics
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        Log.i(TAG, "Display real: " + dm.widthPixels + "x" + dm.heightPixels
                + " density=" + dm.densityDpi);
        Log.i(TAG, "AGenUI engine initialized");

        // Step 2: Create runtime logger
        PlaygroundRuntimeLogger runtimeLogger = new PlaygroundRuntimeLogger(getApplicationContext());
        agenui.setCustomLogger(runtimeLogger);

        // Step 3: Load 4K Design Tokens & Theme from assets
        loadDesignTokensAndTheme();

        // Step 4: Load settings panel protocol messages from assets
        loadProtocolMessages();

        // Step 5: Create SurfaceManager + attach listener
        surfaceManager = new SurfaceManager(this);
        attachSurfaceListener();

        // Step 6: Register custom components
        registerCustomComponents(agenui);

        // Step 7: Register fonts (consistent with Playground)
        // Font files may not exist in all build configs; wrap in try-catch
        String[][] fonts = {
            {"Nunito", "fonts/Nunito-Regular.ttf"},
            {"PlayfairDisplay", "fonts/PlayfairDisplay-Regular.ttf"},
            {"FiraCode", "fonts/FiraCode-Regular.ttf"}
        };
        for (String[] font : fonts) {
            try {
                agenui.registerFontFromAsset(font[0], font[1]);
            } catch (Exception e) {
                Log.w(TAG, "Font not available: " + font[0] + " (" + font[1] + "), engine will use system default");
            }
        }

        // Step 8: Register Toast function
        agenui.registerFunction(new ToastFunction(this));

        Log.i(TAG, "SettingsPanelActivity initialized, ready to render");

        // Step 9: Auto-render if requested
        boolean autoRender = getIntent().getBooleanExtra("autoRender", true);
        if (autoRender) {
            mainHandler.postDelayed(this::renderSettingsPanel, 300);
        }
    }

    // ==================== Asset Loading ====================

    /**
     * Load 4K Design Token and Theme JSON from assets.
     *
     * <p>Gradle merges assets flat (tokens/ dir → APK root), so:
     * <ul>
     *   <li>Token file: "tequ-4k-tokens.json" (not "tokens/tequ-4k-tokens.json")</li>
     *   <li>Theme file: "tequ-4k-theme.json" (not "themes/tequ-4k-theme.json")</li>
     * </ul>
     *
     * <p>If files are not in assets, falls back to empty strings (engine uses defaults).
     */
    private void loadDesignTokensAndTheme() {
        // Assets are merged flat by Gradle: tokens/ dir → APK root,
        // so "tequ-4k-tokens.json" (not "tokens/tequ-4k-tokens.json")
        designTokenJson = loadAssetAsString("tequ-4k-tokens.json");
        themeJson = loadAssetAsString("tequ-4k-theme.json");

        if (designTokenJson != null && !designTokenJson.isEmpty()) {
            Log.i(TAG, "Loaded TEQU-4K design tokens (" + designTokenJson.length() + " chars)");
        } else {
            Log.w(TAG, "TEQU-4K design tokens not found in assets, using engine defaults");
            designTokenJson = "{\"designTokens\":{}}";
        }

        if (themeJson != null && !themeJson.isEmpty()) {
            Log.i(TAG, "Loaded TEQU-4K theme config (" + themeJson.length() + " chars)");
        } else {
            Log.w(TAG, "TEQU-4K theme config not found in assets, using engine defaults");
            themeJson = "{}";
        }
    }

    /**
     * Load settings panel protocol messages from sample assets.
     *
     * <p>Gradle merges assets flat (samples/protocols/ dir → APK root), so:
     * <ul>
     *   <li>"settings-panel/updateComponents.json"</li>
     *   <li>"settings-panel/updateDataModel.json"</li>
     * </ul>
     *
     * <p>The createSurface message is constructed programmatically with the
     * loaded theme and design token config.
     */
    private void loadProtocolMessages() {
        // Assets are merged flat by Gradle: samples/protocols/ dir → APK root,
        // so "settings-panel/updateComponents.json" (not "samples/protocols/settings-panel/...")
        updateComponentsMsg = loadAssetAsString(
                "settings-panel/updateComponents.json");
        updateDataModelMsg = loadAssetAsString(
                "settings-panel/updateDataModel.json");

        if (updateComponentsMsg == null || updateComponentsMsg.isEmpty()) {
            Log.e(TAG, "Failed to load updateComponents.json from assets");
            updateComponentsMsg = "{}";
        } else {
            Log.i(TAG, "Loaded updateComponents.json (" + updateComponentsMsg.length() + " chars)");
        }

        if (updateDataModelMsg == null || updateDataModelMsg.isEmpty()) {
            Log.e(TAG, "Failed to load updateDataModel.json from assets");
            updateDataModelMsg = "{}";
        } else {
            Log.i(TAG, "Loaded updateDataModel.json (" + updateDataModelMsg.length() + " chars)");
        }

        // Construct createSurface message with theme + design token
        String sid = getIntent().getStringExtra("surfaceId");
        if (sid == null || sid.isEmpty()) {
            sid = SURFACE_ID_DEFAULT;
        }
        currentSurfaceId = sid;

        createSurfaceMsg = "{"
                + "\"version\":\"v0.9\","
                + "\"createSurface\":{"
                + "\"surfaceId\":\"" + sid + "\","
                + "\"theme\":" + themeJson + ","
                + "\"designTokens\":" + designTokenJson
                + "}"
                + "}";
    }

    /**
     * Read an asset file as a UTF-8 string. Returns null if the file does not exist.
     */
    private String loadAssetAsString(String assetPath) {
        try {
            InputStream is = getAssets().open(assetPath);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "Asset not found: " + assetPath);
            return null;
        }
    }

    // ==================== Surface Manager & Listener ====================

    private void attachSurfaceListener() {
        surfaceManager.addListener(new ISurfaceManagerListener() {
            @Override
            public void onCreateSurface(Surface surface) {
                runOnUiThread(() -> {
                    Log.i(TAG, "Surface created: " + surface.getSurfaceId());
                    renderContainer.removeAllViews();
                    renderContainer.addView(surface.getContainer(),
                            new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    Gravity.CENTER));
                });
            }

            @Override
            public void onDeleteSurface(Surface surface) {
                Log.i(TAG, "Surface deleted: " + surface.getSurfaceId());
            }

            @Override
            public void onReceiveActionEvent(String event) {
                Log.i(TAG, "Action event received: " + event);
                handleActionEvent(event);
            }

            @Override
            public void onRootComponentUpdate(Surface surface, Map<String, String> props) {
            }

            @Override
            public void onError(Surface surface, int code, String message) {
                Log.e(TAG, "Surface error: code=" + code + ", msg=" + message);
            }

            @Override
            public void onBlankCheckResult(Surface surface, boolean isBlank) {
            }

            @Override
            public void onComponentAppeared(Surface surface,
                    String parentComponentId, String parentType,
                    Map<String, Object> properties) {
            }

            @Override
            public SurfaceSize surfaceSize(String surfaceId) {
                // Return the render container's measured size for the engine's
                // bootstrap layout pass. If the container hasn't been measured
                // yet (width=0), fall back to the real display dimensions to
                // avoid under-sized layouts on devices that constrain app windows.
                int widthPx = renderContainer.getWidth();
                int heightPx = renderContainer.getHeight();
                if (widthPx > 0 && heightPx > 0) {
                    Log.d(TAG, "surfaceSize: container=" + widthPx + "x" + heightPx);
                    return new SurfaceSize(widthPx, heightPx);
                }
                // Fallback: use real display metrics
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                Log.d(TAG, "surfaceSize: fallback display=" + dm.widthPixels + "x" + dm.heightPixels);
                return new SurfaceSize(dm.widthPixels, dm.heightPixels);
            }
        });
    }

    // ==================== Custom Component Registration ====================

    private void registerCustomComponents(AGenUI agenui) {
        agenui.registerComponent("TequSettingsSwitch",
                new TequSettingsSwitchComponentFactory());
        agenui.registerComponent("TequSettingsSlider",
                new TequSettingsSliderComponentFactory());
        agenui.registerComponent("TequSettingsLink",
                new TequSettingsLinkComponentFactory());
        Log.i(TAG, "Registered custom components: TequSettingsSwitch, TequSettingsSlider, TequSettingsLink");
    }

    // ==================== Rendering ====================

    /**
     * Stream the settings panel protocol messages to the engine.
     *
     * <p>Messages are sent sequentially, each as an independent
     * begin → receive → end cycle, to avoid streaming parser truncation
     * when multiple JSON messages are concatenated into a single chunk.
     */
    private void renderSettingsPanel() {
        // Register default theme before creating surface
        try {
            AGenUI.getInstance().registerDefaultTheme(themeJson, designTokenJson);
            Log.i(TAG, "Default theme registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register default theme", e);
        }

        // Send createSurface message
        Log.i(TAG, "Sending createSurface for surfaceId=" + currentSurfaceId);
        surfaceManager.beginTextStream();
        surfaceManager.receiveTextChunk(createSurfaceMsg);
        surfaceManager.endTextStream();

        // Wait briefly for surface creation, then send updateComponents
        mainHandler.postDelayed(() -> {
            Log.i(TAG, "Sending updateComponents");
            surfaceManager.beginTextStream();
            surfaceManager.receiveTextChunk(updateComponentsMsg);
            surfaceManager.endTextStream();

            // Then send updateDataModel
            mainHandler.postDelayed(() -> {
                Log.i(TAG, "Sending updateDataModel");
                surfaceManager.beginTextStream();
                surfaceManager.receiveTextChunk(updateDataModelMsg);
                surfaceManager.endTextStream();
                Log.i(TAG, "Settings panel rendering complete");
            }, 200);
        }, 200);
    }

    // ==================== Action Event Handling ====================

    /**
     * Handle action events from the rendered settings panel.
     *
     * <p>Current events:
     * <ul>
     *   <li>{@code openSettings} — triggered by the settings trigger button</li>
     *   <li>{@code selectCategory} — triggered by clicking a category nav item;
     *       updates the DataModel to highlight the selected category and switch
     *       the right pane's switch/slider/link items</li>
     *   <li>{@code closeSettings} — triggered by the close button</li>
     * </ul>
     */
    private void handleActionEvent(String event) {
        try {
            JSONObject eventObj = new JSONObject(event);
            String eventName = eventObj.optString("event", "");

            switch (eventName) {
                case "openSettings":
                    Log.i(TAG, "openSettings event — panel should already be visible");
                    break;
                case "selectCategory":
                    String categoryId = eventObj.optJSONObject("args") != null
                            ? eventObj.getJSONObject("args").optString("categoryId", "")
                            : "";
                    Log.i(TAG, "selectCategory event — categoryId=" + categoryId);
                    handleSelectCategory(categoryId);
                    break;
                case "closeSettings":
                    Log.i(TAG, "closeSettings event — closing panel");
                    finish();
                    break;
                default:
                    Log.w(TAG, "Unknown action event: " + eventName);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse action event", e);
        }
    }

    // ==================== Category Switching ====================

    /**
     * Category-specific settings data. In a real app this would come from a
     * database or remote config; here we define it inline for the demo.
     *
     * <p>Each category has its own set of switchItems, sliderItems, and linkItems.
     * When the user selects a category, we send an updateDataModel message
     * to update the active category highlight and the right-pane item lists.
     */
    private static final String[][] CATEGORY_DATA = {
        // [categoryId, categoryTitle, switchItemsJson, sliderItemsJson, linkItemsJson]
        {"enterprise", "企业服务",
            "[{\"icon\":\"login\",\"title\":\"自动登录\",\"enabled\":true},"
              + "{\"icon\":\"lock\",\"title\":\"企业账号绑定\",\"enabled\":false}]",
            "[{\"icon\":\"volume_up\",\"title\":\"扬声器音量\",\"value\":60,\"max\":100,\"displayValue\":\"60%\"},"
              + "{\"icon\":\"brightness_7\",\"title\":\"屏幕亮度\",\"value\":75,\"max\":100,\"displayValue\":\"75%\"}]",
            "[{\"icon\":\"server\",\"title\":\"服务器配置\",\"link\":\"/settings/enterprise/server\"},"
              + "{\"icon\":\"person\",\"title\":\"企业账号管理\",\"link\":\"/settings/enterprise/account\"}]"
        },
        {"sound", "声音与显示",
            "[{\"icon\":\"volume_up\",\"title\":\"静音模式\",\"enabled\":false}]",
            "[{\"icon\":\"volume_up\",\"title\":\"媒体音量\",\"value\":40,\"max\":100,\"displayValue\":\"40%\"},"
              + "{\"icon\":\"alarm\",\"title\":\"闹钟音量\",\"value\":70,\"max\":100,\"displayValue\":\"70%\"}]",
            "[{\"icon\":\"notifications\",\"title\":\"通知声音\",\"link\":\"/settings/sound/notification\"}]"
        },
        {"camera", "摄像机",
            "[{\"icon\":\"videocam\",\"title\":\"移动追踪\",\"enabled\":true},"
              + "{\"icon\":\"hd\",\"title\":\"高清模式\",\"enabled\":true}]",
            "[]",
            "[{\"icon\":\"settings\",\"title\":\"摄像头配置\",\"link\":\"/settings/camera/config\"}]"
        },
        {"wallpaper", "壁纸",
            "[]", "[]",
            "[{\"icon\":\"image\",\"title\":\"选择壁纸\",\"link\":\"/settings/wallpaper/select\"},"
              + "{\"icon\":\"palette\",\"title\":\"色彩调节\",\"link\":\"/settings/wallpaper/color\"}]"
        },
        {"wifi", "Wi-Fi",
            "[{\"icon\":\"wifi\",\"title\":\"自动连接\",\"enabled\":true}]",
            "[]",
            "[{\"icon\":\"info\",\"title\":\"网络详情\",\"link\":\"/settings/wifi/info\"}]"
        },
        {"smart", "智慧功能",
            "[{\"icon\":\"smart_toy\",\"title\":\"AI 助手\",\"enabled\":true},"
              + "{\"icon\":\"auto_awesome\",\"title\":\"智能场景识别\",\"enabled\":false}]",
            "[]",
            "[{\"icon\":\"tune\",\"title\":\"智慧配置\",\"link\":\"/settings/smart/config\"}]"
        },
        {"advanced", "高级设置",
            "[{\"icon\":\"developer_mode\",\"title\":\"开发者选项\",\"enabled\":false}]",
            "[]",
            "[{\"icon\":\"security\",\"title\":\"安全设置\",\"link\":\"/settings/advanced/security\"},"
              + "{\"icon\":\"backup\",\"title\":\"备份与恢复\",\"link\":\"/settings/advanced/backup\"}]"
        },
    };

    /**
     * Handle category selection: build and send an updateDataModel message
     * that updates the active category highlight and switches the right-pane items.
     *
     * @param categoryId the selected category ID
     */
    private void handleSelectCategory(String categoryId) {
        if (categoryId.isEmpty()) {
            Log.w(TAG, "selectCategory: empty categoryId");
            return;
        }

        // Find the category data
        String[] catData = null;
        for (String[] cd : CATEGORY_DATA) {
            if (cd[0].equals(categoryId)) {
                catData = cd;
                break;
            }
        }
        if (catData == null) {
            Log.w(TAG, "selectCategory: unknown categoryId=" + categoryId);
            return;
        }

        // Build category array with updated active states
        String[] allCategories = {"enterprise", "sound", "camera", "wallpaper",
                "wifi", "smart", "advanced"};
        String[] allTitles = {"企业服务", "声音与显示", "摄像机", "壁纸",
                "Wi-Fi", "智慧功能", "高级设置"};
        String[] allIcons = {"business", "volume_up", "videocam", "image",
                "wifi", "smart_toy", "settings"};

        StringBuilder catArray = new StringBuilder("[");
        for (int i = 0; i < allCategories.length; i++) {
            if (i > 0) catArray.append(",");
            boolean isActive = allCategories[i].equals(categoryId);
            catArray.append("{")
                    .append("\"id\":\"").append(allCategories[i]).append("\",")
                    .append("\"title\":\"").append(allTitles[i]).append("\",")
                    .append("\"icon\":\"").append(allIcons[i]).append("\",")
                    .append("\"activeBg\":\"").append(isActive ? "#E3F2FD" : "transparent").append("\",")
                    .append("\"activeColor\":\"").append(isActive ? "#1976D2" : "#333333").append("\",")
                    .append("\"activeWeight\":\"").append(isActive ? "bold" : "normal").append("\"")
                    .append("}");
        }
        catArray.append("]");

        // Build updateDataModel message
        String updateMsg = "{"
                + "\"version\":\"v0.9\","
                + "\"updateDataModel\":{"
                + "\"surfaceId\":\"" + currentSurfaceId + "\","
                + "\"path\":\"/data\","
                + "\"value\":{"
                + "\"currentCategoryTitle\":\"" + catData[1] + "\","
                + "\"categories\":" + catArray.toString() + ","
                + "\"switchItems\":" + catData[2] + ","
                + "\"sliderItems\":" + catData[3] + ","
                + "\"linkItems\":" + catData[4]
                + "}"
                + "}"
                + "}";

        // Send the update message
        Log.i(TAG, "Sending category update for: " + categoryId);
        surfaceManager.beginTextStream();
        surfaceManager.receiveTextChunk(updateMsg);
        surfaceManager.endTextStream();
        Log.i(TAG, "Category update sent");
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (surfaceManager != null) {
            surfaceManager.destroy();
            surfaceManager = null;
        }
    }
}
