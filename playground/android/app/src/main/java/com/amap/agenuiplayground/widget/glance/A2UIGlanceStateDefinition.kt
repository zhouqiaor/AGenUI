package com.amap.agenuiplayground.widget.glance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Glance State Definition for A2UI Widget.
 *
 * Wraps the official [PreferencesGlanceStateDefinition] singleton as the
 * framework state store, while exposing typed accessors on top of the same
 * DataStore so the widget composition and render worker can read/write
 * structured state.
 *
 * State keys:
 * - template JSON (widget content spec)
 * - bitmap file path (rendered Bitmap cache location)
 * - viewMode (current / forecast toggle)
 * - hasContent (whether any content has been rendered yet)
 * - lastUpdateTs (epoch millis of last successful render)
 */
object A2UIGlanceStateDefinition {

    /** Delegate to the official Preferences-based state definition. */
    val delegate: PreferencesGlanceStateDefinition = PreferencesGlanceStateDefinition

    private const val PREFS_NAME = "a2ui_glance_widget_state"

    val KEY_TEMPLATE = stringPreferencesKey("template_json")
    val KEY_BITMAP_PATH = stringPreferencesKey("bitmap_path")
    val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
    val KEY_HAS_CONTENT = booleanPreferencesKey("has_content")
    val KEY_LAST_UPDATE = longPreferencesKey("last_update_ts")
    val KEY_ERROR_MSG = stringPreferencesKey("error_msg")

    const val VIEW_MODE_CURRENT = "current"
    const val VIEW_MODE_FORECAST = "forecast"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREFS_NAME)

    /** Convert raw Preferences to typed A2UIGlanceState. */
    private fun prefsToState(prefs: Preferences): A2UIGlanceState {
        return A2UIGlanceState(
            template = prefs[KEY_TEMPLATE] ?: "",
            bitmapPath = prefs[KEY_BITMAP_PATH] ?: "",
            viewMode = prefs[KEY_VIEW_MODE] ?: VIEW_MODE_CURRENT,
            hasContent = prefs[KEY_HAS_CONTENT] ?: false,
            lastUpdateTs = prefs[KEY_LAST_UPDATE] ?: 0L,
            errorMsg = prefs[KEY_ERROR_MSG] ?: ""
        )
    }

    suspend fun getWidgetState(context: Context): A2UIGlanceState {
        val prefs = context.dataStore.data.first()
        return prefsToState(prefs)
    }

    suspend fun setWidgetState(context: Context, state: A2UIGlanceState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEMPLATE] = state.template
            prefs[KEY_BITMAP_PATH] = state.bitmapPath
            prefs[KEY_VIEW_MODE] = state.viewMode
            prefs[KEY_HAS_CONTENT] = state.hasContent
            prefs[KEY_LAST_UPDATE] = state.lastUpdateTs
            prefs[KEY_ERROR_MSG] = state.errorMsg
        }
    }

    /**
     * Writes an error message to widget state. Triggers widget refresh to show ErrorContent.
     */
    suspend fun setError(context: Context, msg: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ERROR_MSG] = msg
        }
    }

    /**
     * Clears the error message. Called after a successful render or user action.
     */
    suspend fun clearError(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ERROR_MSG] = ""
        }
    }

    /**
     * Sets the view mode and atomically clears any previous error.
     * Single DataStore edit ensures both changes succeed or fail together.
     */
    suspend fun setViewMode(context: Context, viewMode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIEW_MODE] = viewMode
            prefs[KEY_ERROR_MSG] = ""  // atomic: clear error on mode switch
        }
    }

    /**
     * Sets the widget template name. Called when user selects a different template.
     */
    suspend fun setTemplate(context: Context, template: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEMPLATE] = template
        }
    }

    /**
     * Clears all widget state. Used for testing or full reset.
     */
    suspend fun clearAll(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    /**
     * Checks if widget state has been initialized (at least one key written).
     */
    suspend fun isInitialized(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_HAS_CONTENT] == true ||
               prefs[KEY_TEMPLATE]?.isNotEmpty() == true ||
               prefs[KEY_LAST_UPDATE] != null
    }

    /**
     * Exposes widget state as a Flow for composition observation.
     * Use collectAsState() inside provideContent to react to state changes.
     */
    fun getStateFlow(context: Context): Flow<A2UIGlanceState> {
        return context.dataStore.data.map { prefs -> prefsToState(prefs) }
    }

    /**
     * Uses the official Glance [updateAppWidgetState] to atomically update widget
     * state within a single DataStore transaction. This is the recommended pattern
     * for writing state from ActionCallbacks and Workers.
     *
     * After calling this, invoke [A2UIGlanceWidget.updateAll] to trigger recomposition.
     */
    suspend fun updateStateViaGlance(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        update: (A2UIGlanceState) -> A2UIGlanceState
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefsToState(prefs)
            val newState = update(current)
            prefs[KEY_TEMPLATE] = newState.template
            prefs[KEY_BITMAP_PATH] = newState.bitmapPath
            prefs[KEY_VIEW_MODE] = newState.viewMode
            prefs[KEY_HAS_CONTENT] = newState.hasContent
            prefs[KEY_LAST_UPDATE] = newState.lastUpdateTs
            prefs[KEY_ERROR_MSG] = newState.errorMsg
        }
    }
}

data class A2UIGlanceState(
    val template: String = "",
    val bitmapPath: String = "",
    val viewMode: String = A2UIGlanceStateDefinition.VIEW_MODE_CURRENT,
    val hasContent: Boolean = false,
    val lastUpdateTs: Long = 0L,
    val errorMsg: String = ""
) {
    val hasBitmap: Boolean get() = bitmapPath.isNotEmpty()
    val hasError: Boolean get() = errorMsg.isNotEmpty()

    /**
     * True if the state was updated within the given freshness window.
     * Use to decide whether a periodic Worker should skip rendering.
     */
    fun isFresh(maxAgeMs: Long): Boolean {
        if (lastUpdateTs <= 0L) return false
        return System.currentTimeMillis() - lastUpdateTs < maxAgeMs
    }

    override fun toString(): String {
        return "A2UIGlanceState(template='$template', bitmapPath='$bitmapPath', viewMode='$viewMode', " +
               "hasContent=$hasContent, lastUpdateTs=$lastUpdateTs, hasError=$hasError)"
    }
}
