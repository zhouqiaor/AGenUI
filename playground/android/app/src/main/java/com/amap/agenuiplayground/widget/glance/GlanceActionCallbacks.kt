package com.amap.agenuiplayground.widget.glance

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback

/**
 * Glance action callbacks for the A2UI Widget.
 *
 * Glance's actionRunCallback<>() executes a suspend function in response to
 * widget clicks — no IPC roundtrip to Activity needed.
 */
object GlanceActionCallbacks {

    private const val TAG = "GlanceActionCallbacks"

    /**
     * Toggle between current and forecast view modes.
     * Uses updateStateViaGlance for atomic, framework-blessed state update.
     */
    class ToggleViewModeAction : ActionCallback {
        override suspend fun onAction(
            context: Context,
            glanceId: GlanceId,
            parameters: ActionParameters
        ) {
            Log.d(TAG, "ToggleViewModeAction: id=$glanceId")

            A2UIGlanceStateDefinition.updateStateViaGlance(context, glanceId) { state ->
                val newMode = if (state.viewMode == A2UIGlanceStateDefinition.VIEW_MODE_CURRENT) {
                    A2UIGlanceStateDefinition.VIEW_MODE_FORECAST
                } else {
                    A2UIGlanceStateDefinition.VIEW_MODE_CURRENT
                }
                Log.d(TAG, "ToggleViewModeAction: ${state.viewMode} -> $newMode")
                state.copy(viewMode = newMode, errorMsg = "")
            }
            GlanceRenderWorker.renderNow(context)
        }
    }

    /**
     * Manually refresh the widget content.
     */
    class RefreshAction : ActionCallback {
        override suspend fun onAction(
            context: Context,
            glanceId: GlanceId,
            parameters: ActionParameters
        ) {
            Log.d(TAG, "RefreshAction: id=$glanceId")
            GlanceRenderWorker.renderNow(context)
        }
    }

    /**
     * Clear widget content and return to empty state.
     * Uses updateStateViaGlance for atomic, framework-blessed state reset.
     */
    class ClearContentAction : ActionCallback {
        override suspend fun onAction(
            context: Context,
            glanceId: GlanceId,
            parameters: ActionParameters
        ) {
            Log.d(TAG, "ClearContentAction: id=$glanceId")
            A2UIGlanceStateDefinition.updateStateViaGlance(context, glanceId) {
                A2UIGlanceState() // reset to default empty state
            }
            GlanceBitmapCache.delete(context, A2UIGlanceWidget.DEFAULT_CACHE_WIDGET_ID)
            A2UIGlanceWidgetReceiver.updateAll(context)
        }
    }
}

/** Convenience factories for use with clickable(Action). */
fun toggleViewModeAction() = actionRunCallback<GlanceActionCallbacks.ToggleViewModeAction>()
fun refreshAction() = actionRunCallback<GlanceActionCallbacks.RefreshAction>()
fun clearContentAction() = actionRunCallback<GlanceActionCallbacks.ClearContentAction>()
