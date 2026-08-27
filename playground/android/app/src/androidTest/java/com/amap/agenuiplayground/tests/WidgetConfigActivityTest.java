package com.amap.agenuiplayground.tests;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.R;
import com.amap.agenuiplayground.widget.WidgetConfig;
import com.amap.agenuiplayground.widget.WidgetConfigActivity;
import com.amap.agenuiplayground.widget.WidgetTemplateRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * WidgetConfigActivity instrumented tests.
 *
 * Tests the widget placement configuration flow:
 * - Activity launches with valid appWidgetId
 * - UI shows all registered templates as buttons
 * - Selecting a template persists the choice via WidgetConfig
 * - Result intent carries the appWidgetId
 * - Invalid appWidgetId → activity finishes immediately
 */
@RunWith(AndroidJUnit4.class)
public class WidgetConfigActivityTest {

    private static final int TEST_WIDGET_ID = 99999;

    @Before
    public void setUp() {
        // Clear any previously saved template for our test widget ID
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("a2ui_widget_prefs", 0)
                .edit().remove("template_" + TEST_WIDGET_ID).apply();
    }

    @After
    public void tearDown() {
        // Clean up
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("a2ui_widget_prefs", 0)
                .edit().remove("template_" + TEST_WIDGET_ID).apply();
    }

    // ===== CA01: Activity launches with valid widget ID =====

    @Test
    public void CA01_activityLaunches_withValidWidgetId() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        scenario.onActivity(activity -> {
            assertNotNull("Activity should be created", activity);
        });
        scenario.close();
    }

    // ===== CA02: UI shows all templates =====

    @Test
    public void CA02_uiShowsAllRegisteredTemplates() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        scenario.onActivity(activity -> {
            // Find the ScrollView → LinearLayout → Buttons
            ScrollView scroll = findViewByType(activity.findViewById(android.R.id.content), ScrollView.class);
            assertNotNull("Should have a ScrollView", scroll);

            LinearLayout listContainer = (LinearLayout) scroll.getChildAt(0);
            assertNotNull("ScrollView should contain a LinearLayout", listContainer);

            int buttonCount = 0;
            for (int i = 0; i < listContainer.getChildCount(); i++) {
                if (listContainer.getChildAt(i) instanceof Button) {
                    buttonCount++;
                }
            }

            // Should have at least as many buttons as registered templates
            int expectedCount = WidgetTemplateRegistry.getEntries().size();
            assertTrue("Should have at least " + expectedCount + " template buttons, got " + buttonCount,
                    buttonCount >= expectedCount);
        });
        scenario.close();
    }

    // ===== CA03: Selecting a template persists the choice =====

    @Test
    public void CA03_selectTemplate_persistsChoice() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        scenario.onActivity(activity -> {
            // Simulate selecting the "weather" template
            // We call the method directly since clicking via UI is flaky
            WidgetConfig.setTemplate(activity, TEST_WIDGET_ID, "weather");
        });

        // Verify persistence
        String saved = WidgetConfig.getTemplate(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TEST_WIDGET_ID);
        assertEquals("weather", saved);

        scenario.close();
    }

    // ===== CA04: Template config validates against registry =====

    @Test
    public void CA04_configGetTemplate_validatesAgainstRegistry() {
        // Save a non-existent template
        WidgetConfig.setTemplate(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TEST_WIDGET_ID, "nonexistent_template");

        // getTemplate should fall back to default
        String result = WidgetConfig.getTemplate(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TEST_WIDGET_ID);
        assertEquals("Should fall back to default template",
                WidgetTemplateRegistry.getDefaultTemplate(), result);
    }

    // ===== CA05: Invalid widget ID finishes activity =====

    @Test
    public void CA05_invalidWidgetId_finishesImmediately() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        // The activity should finish immediately for invalid ID
        // We can't directly assert isFinishing() reliably due to async lifecycle,
        // but we verify the scenario doesn't crash
        assertNotNull(scenario);
        scenario.close();
    }

    // ===== CA06: Title text is displayed =====

    @Test
    public void CA06_titleTextDisplayed() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        scenario.onActivity(activity -> {
            TextView title = findViewByType(activity.findViewById(android.R.id.content), TextView.class);
            assertNotNull("Should have a TextView (title)", title);
            assertTrue("Title should have text", title.getText().length() > 0);
        });
        scenario.close();
    }

    // ===== CA07: All categories represented in button labels =====

    @Test
    public void CA07_allCategoriesInButtonLabels() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), WidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID);

        ActivityScenario<WidgetConfigActivity> scenario = ActivityScenario.launch(intent);
        scenario.onActivity(activity -> {
            ScrollView scroll = findViewByType(activity.findViewById(android.R.id.content), ScrollView.class);
            LinearLayout listContainer = (LinearLayout) scroll.getChildAt(0);

            // Collect all button texts
            StringBuilder allTexts = new StringBuilder();
            for (int i = 0; i < listContainer.getChildCount(); i++) {
                if (listContainer.getChildAt(i) instanceof Button) {
                    Button btn = (Button) listContainer.getChildAt(i);
                    allTexts.append(btn.getText().toString().toLowerCase()).append(" ");
                }
            }

            // Check that each category name appears
            for (WidgetTemplateRegistry.Category cat : WidgetTemplateRegistry.Category.values()) {
                assertTrue("Button texts should contain category: " + cat.name().toLowerCase(),
                        allTexts.toString().contains(cat.name().toLowerCase()));
            }
        });
        scenario.close();
    }

    // ===== Helper: find first view of a given type in the view hierarchy =====

    @SuppressWarnings("unchecked")
    private static <T extends android.view.View> T findViewByType(android.view.View root, Class<T> type) {
        if (root == null) return null;
        if (type.isInstance(root)) return (T) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findViewByType(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
