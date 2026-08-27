package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.agenuiplayground.R;

import java.util.List;

/**
 * Template list picker Activity — HarmonyOS half-modal (Sheet) pattern.
 *
 * <p>Shows a right-side panel (~40% screen width) with a scrollable list of
 * all registered templates. Tapping a row switches the widget template.
 * Tapping the dimmed background or close button dismisses the panel.
 *
 * <p>Replaces the old full-screen Activity with a HarmonyOS-style right Sheet
 * that slides in from the right edge.
 */
public class WidgetTemplateListActivity extends Activity {

    private static final String TAG = "WidgetTemplateList";
    public static final String EXTRA_APPWIDGET_ID = A2UIWidgetProvider.EXTRA_APPWIDGET_ID;

    private int appWidgetId;
    private View panelView;
    private View dimBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_template_list);

        appWidgetId = getIntent().getIntExtra(EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w(TAG, "Invalid appWidgetId, finishing");
            finish();
            return;
        }

        // Find panel and dim background
        panelView = findViewById(R.id.rvTemplateList).getParent();
        dimBackground = (View) panelView.getParent();

        // Slide-in animation from right
        panelView.post(() -> {
            TranslateAnimation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f);
            slideIn.setDuration(250);
            slideIn.setInterpolator(new android.view.animation.DecelerateInterpolator());
            panelView.startAnimation(slideIn);
        });

        // Dim background click → dismiss
        dimBackground.setOnClickListener(v -> finishWithAnimation());

        // Current template (to highlight)
        String currentTemplate = WidgetConfig.getTemplate(this, appWidgetId);

        RecyclerView rv = findViewById(R.id.rvTemplateList);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<WidgetTemplateRegistry.TemplateEntry> entries = WidgetTemplateRegistry.getEntries();
        TemplateListAdapter adapter = new TemplateListAdapter(entries, currentTemplate,
                this::onTemplateSelected);
        rv.setAdapter(adapter);

        // Close button
        findViewById(R.id.btnListClose).setOnClickListener(v -> finishWithAnimation());
    }

    @Override
    public void onBackPressed() {
        finishWithAnimation();
    }

    private void finishWithAnimation() {
        TranslateAnimation slideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        slideOut.setDuration(200);
        slideOut.setInterpolator(new android.view.animation.AccelerateInterpolator());
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationRepeat(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                finish();
                overridePendingTransition(0, 0); // no default fade
            }
        });
        panelView.startAnimation(slideOut);
    }

    private void onTemplateSelected(String templateName) {
        Log.d(TAG, "Selected template: " + templateName);

        // Save and broadcast the switch
        WidgetProtocolCache.saveTemplate(this, appWidgetId, templateName);

        // Trigger render via broadcast
        Intent switchIntent = new Intent(this, A2UIWidgetProvider.class);
        switchIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, templateName);
        sendBroadcast(switchIntent);

        Toast.makeText(this, "已切换: " + templateName, Toast.LENGTH_SHORT).show();
        finishWithAnimation();
    }

    // ---- Adapter ----

    private static class TemplateListAdapter
            extends RecyclerView.Adapter<TemplateListAdapter.ViewHolder> {

        private final List<WidgetTemplateRegistry.TemplateEntry> entries;
        private final String currentTemplate;
        private final OnTemplateSelected listener;

        interface OnTemplateSelected {
            void onSelected(String templateName);
        }

        TemplateListAdapter(List<WidgetTemplateRegistry.TemplateEntry> entries,
                            String currentTemplate,
                            OnTemplateSelected listener) {
            this.entries = entries;
            this.currentTemplate = currentTemplate;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = (TextView) android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            WidgetTemplateRegistry.TemplateEntry entry = entries.get(position);
            String displayName = holder.itemView.getContext()
                    .getString(entry.getDisplayNameRes());

            // Show category badge
            String category = entry.getCategory().name();
            String label = displayName + "  [" + category + "]";

            ((TextView) holder.itemView).setText(label);
            ((TextView) holder.itemView).setTextSize(16);
            ((TextView) holder.itemView).setPadding(32, 24, 32, 24);

            // Highlight current
            if (entry.getName().equals(currentTemplate)) {
                ((TextView) holder.itemView).setTextColor(
                        ContextCompat.getColor(holder.itemView.getContext(),
                                R.color.widget_template_active));
                ((TextView) holder.itemView).setTypeface(
                        ((TextView) holder.itemView).getTypeface(),
                        android.graphics.Typeface.BOLD);
            } else {
                ((TextView) holder.itemView).setTextColor(
                        ContextCompat.getColor(holder.itemView.getContext(),
                                R.color.widget_template_inactive));
                ((TextView) holder.itemView).setTypeface(
                        ((TextView) holder.itemView).getTypeface(),
                        android.graphics.Typeface.NORMAL);
            }

            holder.itemView.setOnClickListener(v ->
                    listener.onSelected(entry.getName()));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
