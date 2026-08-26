package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.amap.agenuiplayground.R;

import java.util.List;

/**
 * Widget configuration activity — shown when the user places a new widget.
 *
 * <p>Allows the user to choose which template to display in the new widget
 * instance. The selected template is persisted via {@link WidgetConfig} and
 * the widget is rendered immediately.
 *
 * <p>Registered in AndroidManifest.xml with
 * {@code android:configure="...WidgetConfigActivity"} on the widget provider.
 *
 * <p>This matches the 2025-2026 industry best practice: "Let the user choose
 * what the widget represents at placement time."
 */
public class WidgetConfigActivity extends Activity {

    private static final String TAG = "WidgetConfigActivity";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED); // default: cancel if user backs out

        // Get widget ID from intent extras
        mAppWidgetId = getIntent().getExtras().getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid appWidgetId, finishing");
            finish();
            return;
        }

        // Load intent config (ensure keywords are available for display)
        WidgetIntentMatcher.loadConfig(this);

        // Build a simple template picker UI
        setContentView(buildConfigUI());
    }

    private View buildConfigUI() {
        // Title
        TextView title = new TextView(this);
        title.setText(R.string.widget_config_title);
        title.setTextSize(16f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 24, 0, 24);

        // Template list container
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 0, 16, 16);

        List<WidgetTemplateRegistry.TemplateEntry> entries =
                WidgetTemplateRegistry.getEntries();
        for (WidgetTemplateRegistry.TemplateEntry entry : entries) {
            Button btn = new Button(this);
            btn.setText(getString(entry.getDisplayNameRes())
                    + " (" + entry.getCategory().name().toLowerCase() + ")");
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setPadding(16, 12, 16, 12);
            btn.setOnClickListener(v -> onTemplateSelected(entry.getName()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 4, 0, 4);
            listContainer.addView(btn, params);
        }

        // Wrap in ScrollView
        ScrollView scroll = new ScrollView(this);
        scroll.addView(listContainer);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.addView(title);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private void onTemplateSelected(@NonNull String template) {
        Log.d(TAG, "Template selected: " + template + " for widget " + mAppWidgetId);

        // Persist the selection
        WidgetConfig.setTemplate(this, mAppWidgetId, template);

        // Render the widget immediately
        AGenUIWidgetRenderService.renderAsync(this, mAppWidgetId, template);

        Toast.makeText(this,
                getString(R.string.widget_config_selected, template),
                Toast.LENGTH_SHORT).show();

        // Return the widget ID as result
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
