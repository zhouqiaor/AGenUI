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
        private const val WIDGET_WIDTH = 300
        private const val WIDGET_HEIGHT = 400
        private const val PERIODIC_WORK_NAME = "a2ui_glance_render_periodic"
        private const val PERIODIC_INTERVAL_MIN = 15L

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GlanceRenderWorker>(PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
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
            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "renderNow: enqueued one-shot")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: started")
        val context = applicationContext

        try {
            val stateDef = A2UIGlanceStateDefinition
            val state = stateDef.getWidgetState(context)

            val templateName = if (state.template.isNotEmpty()) state.template
                               else WidgetProtocolTemplates.DEFAULT_TEMPLATE

            Log.d(TAG, "doWork: template=$templateName, viewMode=${state.viewMode}")

            val surfaceId = "glance_${System.currentTimeMillis()}"
            val templateJson = WidgetProtocolTemplates.loadTemplate(context, templateName, surfaceId)
            if (templateJson == null) {
                Log.e(TAG, "Template not found: $templateName")
                stateDef.setError(context, "模板未找到: $templateName")
                A2UIGlanceWidgetReceiver.updateAll(context)
                return Result.failure()
            }

            val arr = JSONArray(templateJson)
            val createSurfaceJson = arr.optJSONObject(0)?.toString()
            val updateComponentsJson = arr.optJSONObject(1)?.toString()
            val updateDataModelJson = arr.optJSONObject(2)?.toString()

            // Apply viewMode filtering to updateComponents (weather template only)
            val filteredComponentsJson = filterWeatherComponents(updateComponentsJson, state.viewMode)

            AGenUI.getInstance().initialize(context)
            AGenUI.getInstance().setDebug(BuildConfig.DEBUG)

            val surfaceManager = SurfaceManager(context)
            val surfaceRef = AtomicReference<Surface?>(null)
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
                    Handler(Looper.getMainLooper()).postDelayed({ rootComponentReady.countDown() }, 100)
                }
                override fun onError(surface: Surface?, code: Int, message: String?) {
                    Log.e(TAG, "Surface error: code=$code, msg=$message")
                    surfaceCreated.countDown()
                    rootComponentReady.countDown()
                }
                override fun onBlankCheckResult(surface: Surface?, isBlank: Boolean) {}
                override fun onComponentAppeared(
                    surface: Surface?, parentComponentId: String?,
                    parentType: String?, properties: Map<String, Any>?
                ) {}
                override fun surfaceSize(sid: String): SurfaceSize {
                    return SurfaceSize(WIDGET_WIDTH.toFloat(), WIDGET_HEIGHT.toFloat())
                }
            }
            surfaceManager.addListener(surfaceManagerListener)

            surfaceManager.beginTextStream()
            createSurfaceJson?.let { surfaceManager.receiveTextChunk(it) }
            filteredComponentsJson?.let { surfaceManager.receiveTextChunk(it) }
            updateDataModelJson?.let {
                if (!it.contains("\"value\":{}")) surfaceManager.receiveTextChunk(it)
            }
            surfaceManager.endTextStream()

            val created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!created || surfaceRef.get() == null) {
                Log.e(TAG, "Surface creation timeout")
                cleanup(surfaceManager)
                return Result.retry()
            }
            rootComponentReady.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            val surface = surfaceRef.get()!!
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
                cleanup(surfaceManager)
                return Result.retry()
            }

            val newState = A2UIGlanceState(
                template = templateName,
                bitmapPath = bitmapPath,
                viewMode = state.viewMode,
                hasContent = true,
                lastUpdateTs = System.currentTimeMillis()
            )
            stateDef.setWidgetState(context, newState)
            stateDef.clearError(context)

            Log.d(TAG, "doWork: render complete, bitmap=${bitmap.width}x${bitmap.height}")

            A2UIGlanceWidgetReceiver.updateAll(context)
            cleanup(surfaceManager)
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "doWork failed", e)
            val stateDef = A2UIGlanceStateDefinition
            stateDef.setError(applicationContext, e.message ?: "未知错误")
            A2UIGlanceWidgetReceiver.updateAll(applicationContext)
            return Result.retry()
        }
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
        if (h <= 0) h = WIDGET_HEIGHT
        Log.d(TAG, "Measured: ${w}x${h}")

        container.layout(0, 0, w, h)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        drawViewTree(container, canvas)
        Log.d(TAG, "Bitmap: ${w}x${h}, bytes=${bitmap.byteCount}")

        return bitmap
    }

    private fun drawViewTree(view: View?, canvas: Canvas) {
        if (view == null || view.width <= 0 || view.height <= 0) return

        val saveCount = canvas.save()
        val parent = view.parent
        val scrollX = if (parent is android.view.ViewGroup) parent.scrollX else 0
        val scrollY = if (parent is android.view.ViewGroup) parent.scrollY else 0
        canvas.translate(
            (view.left - scrollX).toFloat(),
            (view.top - scrollY).toFloat()
        )
        canvas.clipRect(0, 0, view.width, view.height)
        view.draw(canvas)

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                drawViewTree(view.getChildAt(i), canvas)
            }
        }
        canvas.restoreToCount(saveCount)
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
            val rootObj = org.json.JSONObject(updateComponentsJson)
            val components = rootObj.optJSONArray("components") ?: return updateComponentsJson

            // Find root_body's children array
            var rootBodyChildren: org.json.JSONArray? = null
            for (i in 0 until components.length()) {
                val comp = components.optJSONObject(i)
                if (comp?.optString("id") == "root_body") {
                    rootBodyChildren = comp.optJSONArray("children")
                    break
                }
            }
            if (rootBodyChildren == null) return updateComponentsJson

            val isForecast = viewMode == A2UIGlanceStateDefinition.VIEW_MODE_FORECAST
            val weatherChildIds = setOf("root_c0", "root_c1", "root_c2", "root_c3")
            val toRemove = if (isForecast) setOf("root_c0", "root_c1", "root_c2") else setOf("root_c3")

            val filtered = org.json.JSONArray()
            for (i in 0 until rootBodyChildren.length()) {
                val childId = rootBodyChildren.optString(i)
                // Only filter weather-specific child IDs; pass through everything else
                if (childId in weatherChildIds) {
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
