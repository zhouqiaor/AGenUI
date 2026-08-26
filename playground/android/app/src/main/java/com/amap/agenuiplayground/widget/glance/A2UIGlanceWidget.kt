package com.amap.agenuiplayground.widget.glance

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Glance Widget for AGenUI — Evolution version.
 *
 * Architecture: "Glance 管壳 + AGenUI Bitmap 管内容"
 * - Glance provides the declarative shell, click handling, responsive layout
 * - AGenUI renders template JSON to Bitmap via SurfaceManager (in Worker)
 * - Bitmap loaded from file in provideGlance, displayed via ImageProvider
 *
 * SizeMode.Responsive: 3 layout breakpoints (compact/standard/expanded).
 * Framework picks the best-matching layout from the set, avoiding per-resize rebuilds.
 */
class A2UIGlanceWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "A2UIGlanceWidget"
        private const val COMPACT_HEIGHT_DP = 80
        private const val EXPANDED_HEIGHT_DP = 140
        const val DEFAULT_CACHE_WIDGET_ID = 0
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 100.dp),  // compact: 2x1 cells
            DpSize(180.dp, 220.dp),  // standard: 2x3 cells
            DpSize(280.dp, 300.dp),  // expanded: 4x4 cells
        )
    )
    override val stateDefinition: androidx.glance.state.GlanceStateDefinition<*> = A2UIGlanceStateDefinition.delegate

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance: id=$id")
        // Load initial state BEFORE provideContent (heavy work goes here)
        val initialState = A2UIGlanceStateDefinition.getWidgetState(context)
        val initialBitmap = if (initialState.hasBitmap) {
            GlanceBitmapCache.load(context, DEFAULT_CACHE_WIDGET_ID)
        } else {
            null
        }

        provideContent {
            // Observe state changes within composition for live updates
            val state by remember { A2UIGlanceStateDefinition.getStateFlow(context) }.collectAsState(initial = initialState)
            val bitmap = if (state.hasBitmap) {
                remember(state.bitmapPath, state.lastUpdateTs) {
                    GlanceBitmapCache.load(context, DEFAULT_CACHE_WIDGET_ID)
                }
            } else null

            // Use freshly loaded bitmap; fall back to initial only if path hasn't changed
            val displayBitmap = bitmap ?: initialBitmap?.takeIf {
                state.bitmapPath == initialState.bitmapPath
            }
            GlanceContent(state, displayBitmap)
        }
    }

    @Composable
    private fun GlanceContent(state: A2UIGlanceState, bitmap: android.graphics.Bitmap?) {
        val size = LocalSize.current
        val heightDp = size.height
        val isCompact = heightDp.value < COMPACT_HEIGHT_DP
        val isExpanded = heightDp.value >= EXPANDED_HEIGHT_DP

        when {
            state.hasError -> ErrorContent(state)
            !state.hasContent -> EmptyContent()
            bitmap == null -> LoadingContent()
            isCompact -> CompactContent(state, bitmap)
            isExpanded -> ExpandedContent(state, bitmap)
            else -> StandardContent(state, bitmap)
        }
    }

    @Composable
    private fun CompactContent(state: A2UIGlanceState, bitmap: android.graphics.Bitmap) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "AGenUI widget",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }

    @Composable
    private fun StandardContent(state: A2UIGlanceState, bitmap: android.graphics.Bitmap) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                Text(
                    text = if (state.viewMode == A2UIGlanceStateDefinition.VIEW_MODE_FORECAST)
                        "预报" else "实况",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp)
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "AGenUI rendered content",
                    modifier = GlanceModifier.fillMaxWidth().height(120.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    @Composable
    private fun ExpandedContent(state: A2UIGlanceState, bitmap: android.graphics.Bitmap) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                Text(
                    text = if (state.viewMode == A2UIGlanceStateDefinition.VIEW_MODE_FORECAST)
                        "天气预报" else "天气实况",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "AGenUI rendered content",
                    modifier = GlanceModifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "切换 ${if (state.viewMode == A2UIGlanceStateDefinition.VIEW_MODE_CURRENT) "预报" else "实况"}",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.clickable(toggleViewModeAction())
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "刷新",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.clickable(refreshAction())
                )
            }
        }
    }

    @Composable
    private fun EmptyContent() {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AGenUI\nWidget 未配置",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "点击生成",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.clickable(refreshAction())
                )
            }
        }
    }

    @Composable
    private fun LoadingContent() {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "加载中...",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }

    /**
     * Error state: render failed. Shows error message + retry button.
     */
    @Composable
    private fun ErrorContent(state: A2UIGlanceState) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "渲染错误",
                    style = TextStyle(
                        color = GlanceTheme.colors.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = state.errorMsg.take(50),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "重试",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.clickable(refreshAction())
                )
            }
        }
    }
}

/**
 * Glance Widget Receiver — manages WorkManager lifecycle.
 */
class A2UIGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = A2UIGlanceWidget()

    companion object {
        private const val TAG = "A2UIGlanceReceiver"

        /**
         * Updates all placed widget instances using GlanceAppWidgetManager
         * to enumerate GlanceIds (multi-instance safe).
         */
        suspend fun updateAll(context: Context) {
            val startMs = System.currentTimeMillis()
            Log.d(TAG, "updateAll: request at $startMs")
            try {
                A2UIGlanceWidget().updateAll(context)
                val elapsed = System.currentTimeMillis() - startMs
                Log.d(TAG, "updateAll: completed in ${elapsed}ms")
            } catch (e: Exception) {
                Log.e(TAG, "updateAll failed", e)
            }
        }

        /**
         * Returns all GlanceIds for this widget (multi-instance support).
         */
        suspend fun getGlanceIds(context: Context): List<GlanceId> {
            return GlanceAppWidgetManager(context).getGlanceIds(A2UIGlanceWidget::class.java)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled: scheduling periodic render")
        GlanceRenderWorker.schedulePeriodic(context)
        GlanceRenderWorker.renderNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "onDisabled: cancelling periodic render")
        GlanceRenderWorker.cancelPeriodic(context)
        GlanceBitmapCache.clearAll(context)
    }
}
