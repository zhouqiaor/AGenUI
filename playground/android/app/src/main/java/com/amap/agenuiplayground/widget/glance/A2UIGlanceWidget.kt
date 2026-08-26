package com.amap.agenuiplayground.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.unit.ColorProvider
import kotlin.system.measureTimeMillis

/**
 * Phase 4 预研 - Glance Widget PoC.
 *
 * 职责：
 * 1. 验证 Glance 框架能在 AGenUI Playground 中正确显示（Text + Button）
 * 2. 验证 Glance 能接收并显示外部 Bitmap（F2）
 * 3. 提供 actionRunCallback 点击延迟测量基准（F3）
 *
 * 注意：这只是预研 PoC，不替代 RemoteViews 实现。
 */
class A2UIGlanceWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "A2UIGlanceWidget"

        // 用于跨进程传递 Bitmap 的静态引用（PoC 方案，非生产代码）
        @Volatile
        var sharedBitmap: Bitmap? = null

        // 点击延迟测量：记录 actionRunCallback 触发时间戳
        @Volatile
        var lastClickTimestamp: Long = 0L

        // 更新延迟测量：记录请求更新时间戳
        @Volatile
        var updateRequestTimestamp: Long = 0L

        // 模式：显示纯 Text+Button 还是 Bitmap
        const val MODE_POC = "poc"
        const val MODE_BITMAP = "bitmap"
        @Volatile
        var mode: String = MODE_POC
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance: id=$id, mode=$mode")
        updateRequestTimestamp = System.currentTimeMillis()

        val currentMode = mode
        val bitmap = sharedBitmap

        provideContent {
            when (currentMode) {
                MODE_BITMAP -> BitmapContent(bitmap)
                else -> PocContent()
            }
        }
    }

    /**
     * PoC 内容：Text + 可点击区域，验证 Glance 框架能正常显示。
     */
    @Composable
    private fun PocContent() {
        var clickCount by remember { mutableIntStateOf(0) }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AGenUI Glance PoC",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "clicks: $clickCount",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "点击测试延迟",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.clickable {
                        lastClickTimestamp = System.currentTimeMillis()
                        clickCount++
                        Log.d(TAG, "Button clicked: count=$clickCount")
                    }
                )
            }
        }
    }

    /**
     * Bitmap 混合方案：尝试显示外部 Bitmap 到 Image 组件。
     */
    @Composable
    private fun BitmapContent(bitmap: Bitmap?) {
        if (bitmap == null) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bitmap: null\n(Glance Bitmap 验证)",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
            return
        }

        Box(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                Text(
                    text = "Glance + Bitmap",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 10.sp
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp)
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "AGenUI rendered bitmap",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Glance Widget Receiver - 在 AndroidManifest.xml 中注册。
 */
class A2UIGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = A2UIGlanceWidget()

    companion object {
        private const val TAG = "A2UIGlanceReceiver"

        /**
         * 请求更新所有 Glance widget 实例。
         * 用于 F3 延迟测量：调用此方法后记录到 [A2UIGlanceWidget.updateRequestTimestamp]。
         */
        suspend fun updateAll(context: Context) {
            val startMs = System.currentTimeMillis()
            A2UIGlanceWidget.updateRequestTimestamp = startMs
            Log.d(TAG, "updateAll: request at $startMs")
            try {
                A2UIGlanceWidget().updateAll(context)
                val elapsed = System.currentTimeMillis() - startMs
                Log.d(TAG, "updateAll: completed in ${elapsed}ms")
            } catch (e: Exception) {
                Log.e(TAG, "updateAll failed", e)
            }
        }
    }
}
