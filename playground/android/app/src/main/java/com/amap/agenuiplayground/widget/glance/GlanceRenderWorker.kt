package com.amap.agenuiplayground.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amap.agenui.AGenUI
import com.amap.agenui.render.surface.ISurfaceManagerListener
import com.amap.agenui.render.surface.Surface
import com.amap.agenui.render.surface.SurfaceManager
import com.amap.agenui.render.surface.SurfaceSize
import com.amap.agenuiplayground.BuildConfig
import com.amap.agenuiplayground.widget.WidgetProtocolTemplates
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Background render worker for Glance Widget.
 *
 * Mirrors [AGenUIWidgetRenderService] but outputs to Glance state + file cache
 * instead of RemoteViews.
 *
 * Flow:
 * 1. Read template JSON from state (or default)
 * 2. Initialize AGenUI + SurfaceManager
 * 3. Apply viewMode filtering to updateComponents (weather template)
 * 4. Stream template -> SurfaceManager -> wait for root component
 * 5. Draw Surface container View to Bitmap on main thread
 * 6. Save Bitmap to GlanceBitmapCache
 * 7. Update GlanceStateDefinition (bitmapPath, hasContent, lastUpdateTs)
 * 8. Trigger Glance widget update
 */
class GlanceRenderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GlanceRenderWorker"
        private const val SURFACE_TIMEOUT_MS = 5000L
        // Render dimensions: match the largest SizeMode.Responsive breakpoint (280x300)
        // plus margin for high-density displays. Bitmap is scaled down at load time.
        private const val WIDGET_WIDTH = 300
        private const val WIDGET_HEIGHT = 400
        private const val PERIODIC_WORK_NAME = "a2ui_glance_render_periodic"
        private const val ONE_SHOT_WORK_NAME = "a2ui_glance_render_oneshot"
        private const val PERIODIC_INTERVAL_MIN = 15L
        private const val WORKER_TOTAL_TIMEOUT_MS = 30_000L
        private const val ROOT_COMPONENT_SETTLE_DELAY_MS = 100L
        private val WEATHER_CHILD_IDS = setOf("root_c0", "root_c1", "root_c2", "root_c3")
        private val FORECAST_REMOVE_IDS = setOf("root_c0", "root_c1", "root_c2")
        private val CURRENT_REMOVE_IDS = setOf("root_c3")
        private val surfaceIdCounter = java.util.concurrent.atomic.AtomicLong(0)

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GlanceRenderWorker>(PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "schedulePeriodic: every ${PERIODIC_INTERVAL_MIN}min")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d(TAG, "cancelPeriodic")
        }

        fun renderNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<GlanceRenderWorker>()
                .addTag(TAG)
                .build()
            // Use unique work to coalesce rapid renderNow calls into one execution
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "renderNow: enqueued one-shot")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: started, cache=${GlanceBitmapCache.getCacheInfo(applicationContext)}")
        val context = applicationContext

        return try {
            withTimeout(WORKER_TOTAL_TIMEOUT_MS) {
                doRenderWork(context)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "doWork: total timeout exceeded ${WORKER_TOTAL_TIMEOUT_MS}ms", e)
            // Set error state + update widget BEFORE returning retry.
            // WorkManager will re-enqueue after backoff; the error state
            // ensures the widget shows ErrorContent immediately.
            A2UIGlanceStateDefinition.setError(applicationContext, "渲染超时")
            A2UIGlanceWidgetReceiver.updateAll(applicationContext)
            Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Don't catch CancellationException — let it propagate for proper coroutine cancellation
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "doWork failed", e)
            A2UIGlanceStateDefinition.setError(applicationContext, e.message ?: "未知错误")
            A2UIGlanceWidgetReceiver.updateAll(applicationContext)
            Result.retry()
        }
    }

    private suspend fun doRenderWork(context: Context): Result {
        val renderStartMs = System.currentTimeMillis()
        val state = A2UIGlanceStateDefinition.getWidgetState(context)

        // Clean up stale bitmap cache files older than 24 hours
        GlanceBitmapCache.deleteStale(context, 24L * 60 * 60 * 1000)

        // Skip if content was rendered within the periodic interval (avoids redundant work)
        if (state.isFresh(PERIODIC_INTERVAL_MIN * 60 * 1000L) && !state.hasError) {
            Log.d(TAG, "doWork: skipping, content fresh (lastUpdate=${state.lastUpdateTs})")
            return Result.success()
        }

        val templateName = if (state.template.isNotEmpty()) state.template
                           else WidgetProtocolTemplates.DEFAULT_TEMPLATE

        Log.d(TAG, "doWork: template=$templateName, viewMode=${state.viewMode}")

        val surfaceId = "glance_${System.currentTimeMillis()}_${surfaceIdCounter.incrementAndGet()}"
        val templateJson = WidgetProtocolTemplates.loadTemplate(context, templateName, surfaceId)
        if (templateJson == null) {
            Log.e(TAG, "Template not found: $templateName")
            A2UIGlanceStateDefinition.setError(context, "模板未找到: $templateName")
            A2UIGlanceWidgetReceiver.updateAll(context)
            return Result.failure()
        }

        val arr = JSONArray(templateJson)
        val createSurfaceJson = arr.optJSONObject(0)?.toString()
        val updateComponentsJson = arr.optJSONObject(1)?.toString()
        val updateDataModelJson = arr.optJSONObject(2)?.toString()

        // Apply viewMode filtering to updateComponents (weather template only)
        val filteredComponentsJson = filterWeatherComponents(updateComponentsJson, state.viewMode)

        // AGenUI.initialize has internal isInitialized guard, but calling
        // isInitialized first avoids entering the synchronized block.
        if (!AGenUI.getInstance().isInitialized) {
            AGenUI.getInstance().initialize(context)
        }
        AGenUI.getInstance().setDebug(BuildConfig.DEBUG)

        val surfaceManager = SurfaceManager(context)
        val surfaceRef = AtomicReference<Surface?>(null)
        val surfaceErrorRef = AtomicReference<String?>(null)
        val surfaceCreated = CountDownLatch(1)
        val rootComponentReady = CountDownLatch(1)

        surfaceManagerListener = object : ISurfaceManagerListener {
            override fun onCreateSurface(surface: Surface) {
                Log.d(TAG, "onCreateSurface: ${surface.surfaceId}")
                surfaceRef.set(surface)
                surfaceCreated.countDown()
            }
            override fun onDeleteSurface(surface: Surface) {}
            override fun onReceiveActionEvent(event: String?) {}
            override fun onRootComponentUpdate(surface: Surface, props: Map<String, String>) {
                Log.d(TAG, "onRootComponentUpdate: ${surface.surfaceId}")
                surfaceRef.set(surface)
                Handler(Looper.getMainLooper()).postDelayed({ rootComponentReady.countDown() }, ROOT_COMPONENT_SETTLE_DELAY_MS)
            }
            override fun onError(surface: Surface?, code: Int, message: String?) {
                Log.e(TAG, "Surface error: code=$code, msg=$message")
                // Unblock any waiting latches so Worker can proceed to error handling
                surfaceCreated.countDown()
                rootComponentReady.countDown()
                // Capture error for doRenderWork to report
                surfaceErrorRef.set("Surface error: $code/${message ?: "unknown"}")
            }
            override fun onBlankCheckResult(surface: Surface?, isBlank: Boolean) {}
            override fun onComponentAppeared(
                surface: Surface?, parentComponentId: String?,
                parentType: String?, properties: Map<String, Any>?
            ) {}
            override fun surfaceSize(sid: String): SurfaceSize {
                // TODO R87: query per-widget dimensions from GlanceAppWidgetManager
                // to support multi-instance with different sizes. Currently all
                // instances share the same render dimensions.
                return SurfaceSize(WIDGET_WIDTH.toFloat(), WIDGET_HEIGHT.toFloat())
            }
        }
        surfaceManager.addListener(surfaceManagerListener)

        try {
            surfaceManager.beginTextStream()
            createSurfaceJson?.let { surfaceManager.receiveTextChunk(it) }
            filteredComponentsJson?.let { surfaceManager.receiveTextChunk(it) }
            updateDataModelJson?.let {
                // Skip empty data model (value:{} means no data)
                val dataObj = JSONObject(it)
                val value = dataObj.optJSONObject("value")
                if (value != null && value.length() > 0) {
                    surfaceManager.receiveTextChunk(it)
                }
            }
        } finally {
            surfaceManager.endTextStream()
        }

        val created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!created || surfaceRef.get() == null) {
            Log.e(TAG, "Surface creation timeout")
            cleanup(surfaceManager)
            return Result.retry()
        }
        rootComponentReady.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // Check if SurfaceManager reported an error during creation/streaming
        surfaceErrorRef.get()?.let { errMsg ->
            Log.e(TAG, "Surface error during render: $errMsg")
            A2UIGlanceStateDefinition.setError(context, errMsg)
            A2UIGlanceWidgetReceiver.updateAll(context)
            cleanup(surfaceManager)
            return Result.failure()
        }

        val surface = surfaceRef.get()
        if (surface == null) {
            Log.e(TAG, "Surface became null after creation await")
            cleanup(surfaceManager)
            return Result.retry()
        }
        val bitmapResult = AtomicReference<Bitmap?>(null)
        val drawDone = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                bitmapResult.set(drawSurfaceToBitmap(surface))
            } catch (e: Exception) {
                Log.e(TAG, "Draw failed", e)
            } finally {
                drawDone.countDown()
            }
        }

        drawDone.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        val bitmap = bitmapResult.get()

        if (bitmap == null) {
            Log.e(TAG, "Bitmap render returned null")
            cleanup(surfaceManager)
            return Result.retry()
        }

        val appWidgetId = A2UIGlanceWidget.DEFAULT_CACHE_WIDGET_ID
        val bitmapPath = GlanceBitmapCache.save(context, appWidgetId, bitmap)
        if (bitmapPath == null) {
            Log.e(TAG, "Bitmap cache save failed")
            bitmap.recycle() // free bitmap on save failure
            cleanup(surfaceManager)
            return Result.retry()
        }
        // Record dimensions BEFORE recycle (accessing after recycle throws)
        val bitmapW = bitmap.width
        val bitmapH = bitmap.height
        // Free the in-memory bitmap now that it's persisted to file cache
        bitmap.recycle()

        val newState = A2UIGlanceState(
            template = templateName,
            bitmapPath = bitmapPath,
            viewMode = state.viewMode,
            hasContent = true,
            lastUpdateTs = System.currentTimeMillis()
        )
        // setWidgetState already writes errorMsg="" (data class default), so no separate clearError needed
        A2UIGlanceStateDefinition.setWidgetState(context, newState)

        Log.d(TAG, "doWork: render complete, bitmap=${bitmapW}x${bitmapH}, elapsed=${System.currentTimeMillis() - renderStartMs}ms")

        A2UIGlanceWidgetReceiver.updateAll(context)
        // Note: cleanup must be called before every return path in doRenderWork.
        // Consider refactoring to try-finally in a future round for safety.
        cleanup(surfaceManager)
        return Result.success()
    }

    private fun drawSurfaceToBitmap(surface: Surface): Bitmap? {
        val container = surface.container ?: run {
            Log.e(TAG, "Surface container is null")
            return null
        }

        container.forceLayout()

        val widthSpec = View.MeasureSpec.makeMeasureSpec(WIDGET_WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(WIDGET_HEIGHT, View.MeasureSpec.AT_MOST)
        container.measure(widthSpec, heightSpec)

        val w = container.measuredWidth
        var h = container.measuredHeight
        if (w <= 0) {
            Log.w(TAG, "Measured width is 0, falling back to WIDGET_WIDTH")
        }
        if (h <= 0) h = WIDGET_HEIGHT
        val renderW = if (w <= 0) WIDGET_WIDTH else w
        Log.d(TAG, "Measured: ${renderW}x${h}")

        container.layout(0, 0, renderW, h)

        val bitmap = Bitmap.createBitmap(renderW, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // White background — AGenUI content is rendered on top.
        // RGB_565 (used at load time) doesn't support alpha, so transparent would become black.
        canvas.drawColor(android.graphics.Color.WHITE)
        // ViewGroup.draw() already dispatches to children via dispatchDraw();
        // manual recursion would double-draw each child.
        container.draw(canvas)
        Log.d(TAG, "Bitmap: ${renderW}x${h}, bytes=${bitmap.byteCount}")

        return bitmap
    }

    private fun cleanup(surfaceManager: SurfaceManager) {
        try {
            surfaceManagerListener?.let { surfaceManager.removeListener(it) }
        } catch (e: Exception) {
            Log.w(TAG, "removeListener failed", e)
        }
        try {
            surfaceManager.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "SurfaceManager destroy failed", e)
        }
        surfaceManagerListener = null
    }

    private var surfaceManagerListener: ISurfaceManagerListener? = null

    /**
     * Filter weather template components based on viewMode.
     * current mode: keep root_c0/c1/c2 (city/temp/forecast), remove root_c3 (forecast detail)
     * forecast mode: keep root_c3 only, remove root_c0/c1/c2
     * Non-weather components (not root_cN pattern) are always kept.
     */
    private fun filterWeatherComponents(updateComponentsJson: String?, viewMode: String): String? {
        if (updateComponentsJson == null) return null
        return try {
            val rootObj = JSONObject(updateComponentsJson)
            val components = rootObj.optJSONArray("components") ?: return updateComponentsJson

            // Find root_body's children array
            var rootBodyChildren: JSONArray? = null
            for (i in 0 until components.length()) {
                val comp = components.optJSONObject(i)
                if (comp?.optString("id") == "root_body") {
                    rootBodyChildren = comp.optJSONArray("children")
                    break
                }
            }
            if (rootBodyChildren == null) return updateComponentsJson

            val isForecast = viewMode == A2UIGlanceStateDefinition.VIEW_MODE_FORECAST
            val toRemove = if (isForecast) FORECAST_REMOVE_IDS else CURRENT_REMOVE_IDS

            val filtered = JSONArray()
            for (i in 0 until rootBodyChildren.length()) {
                val childId = rootBodyChildren.optString(i)
                // Only filter weather-specific child IDs; pass through everything else
                if (childId in WEATHER_CHILD_IDS) {
                    if (childId !in toRemove) {
                        filtered.put(childId)
                    }
                } else {
                    filtered.put(childId)
                }
            }

            // Replace children array in root_body
            for (i in 0 until components.length()) {
                val comp = components.optJSONObject(i)
                if (comp?.optString("id") == "root_body") {
                    comp.put("children", filtered)
                    break
                }
            }

            rootObj.toString()
        } catch (e: Exception) {
            Log.w(TAG, "filterWeatherComponents failed, returning unfiltered", e)
            updateComponentsJson
        }
    }
}
