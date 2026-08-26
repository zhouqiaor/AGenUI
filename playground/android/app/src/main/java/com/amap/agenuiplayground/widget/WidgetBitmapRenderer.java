package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.amap.agenui.render.surface.Surface;
import com.amap.agenuiplayground.BuildConfig;
import com.amap.agenuiplayground.R;

/**
 * Renders a {@link Surface}'s container View to a {@link Bitmap} via Canvas.
 *
 * <p>Extracted from {@link AGenUIWidgetRenderService} to isolate the drawing
 * pipeline (measure → layout → draw → optional debug dump) from orchestration.
 *
 * <p>All methods in this class <b>must be called on the main thread</b>.
 */
public final class WidgetBitmapRenderer {

    private static final String TAG = "WidgetBitmapRenderer";

    /** Default widget dimensions in pixels. */
    public static final int WIDGET_WIDTH = WidgetSizeDetector.DEFAULT_WIDTH;
    public static final int WIDGET_HEIGHT = WidgetSizeDetector.DEFAULT_HEIGHT;

    private WidgetBitmapRenderer() { } // utility class

    /**
     * Draws the surface's container View to a Bitmap.
     *
     * @param context Application context (used for debug file paths and theme colors)
     * @param surface  The AGenUI surface containing the mounted view tree
     * @return A new Bitmap, or {@code null} on failure
     */
    public static Bitmap drawSurfaceToBitmap(Context context, Surface surface) {
        return drawSurfaceToBitmap(context, surface, WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    /**
     * Draws the surface's container View to a Bitmap with the specified dimensions.
     *
     * @param context Application context (used for debug file paths and theme colors)
     * @param surface The AGenUI surface containing the mounted view tree
     * @param width   Target bitmap width in pixels
     * @param height  Target bitmap height in pixels
     * @return A new Bitmap, or {@code null} on failure
     */
    public static Bitmap drawSurfaceToBitmap(Context context, Surface surface,
                                             int width, int height) {
        View container = surface.getContainer();
        if (container == null) {
            Log.e(TAG, "Surface container is null");
            return null;
        }

        // Force layout by invalidating
        container.forceLayout();

        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = height;
        Log.d(TAG, "Measured: " + w + "x" + h);

        container.layout(0, 0, w, h);

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Use theme-aware background color (white in light, dark gray in dark)
        canvas.drawColor(context.getColor(R.color.widget_bg));

        drawViewTree(container, canvas);
        Log.d(TAG, "Bitmap: " + w + "x" + h + ", bytes=" + bitmap.getByteCount());

        if (BuildConfig.DEBUG) {
            saveDebugBitmap(context, bitmap, container);
        }

        return bitmap;
    }

    /**
     * Recursively draws a View tree to a canvas.
     * Bypasses View's internal "skip draw if not attached" logic by calling
     * {@link View#draw(Canvas)} directly on each view.
     */
    private static void drawViewTree(View view, Canvas canvas) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        int saveCount = canvas.save();

        float transX = view.getLeft();
        float transY = view.getTop();
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            transX -= parent.getScrollX();
            transY -= parent.getScrollY();
        }
        canvas.translate(transX, transY);
        canvas.clipRect(0, 0, view.getWidth(), view.getHeight());
        view.draw(canvas);

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                drawViewTree(vg.getChildAt(i), canvas);
            }
        }

        canvas.restoreToCount(saveCount);
    }

    /**
     * Saves a debug PNG and dumps the view hierarchy.
     */
    private static void saveDebugBitmap(Context context, Bitmap bitmap, View container) {
        try {
            java.io.File outFile = new java.io.File(context.getExternalFilesDir(null),
                    "widget_render_debug.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.d(TAG, "Debug bitmap saved: " + outFile.getAbsolutePath());
            Log.d(TAG, "View hierarchy:\n" + dumpViewHierarchy(container, 0));
        } catch (Exception e) {
            Log.w(TAG, "Failed to save debug bitmap", e);
        }
    }

    /**
     * Dumps view hierarchy for debugging blank-bitmap issue.
     */
    private static String dumpViewHierarchy(View view, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        sb.append(indent).append(view.getClass().getSimpleName())
                .append(" [").append(view.getWidth()).append("x").append(view.getHeight()).append("]")
                .append(" measured=[").append(view.getMeasuredWidth()).append("x").append(view.getMeasuredHeight()).append("]")
                .append(" vis=").append(view.getVisibility())
                .append(" tag=").append(view.getTag());
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                sb.append("\n").append(dumpViewHierarchy(vg.getChildAt(i), depth + 1));
            }
        }
        return sb.toString();
    }
}
