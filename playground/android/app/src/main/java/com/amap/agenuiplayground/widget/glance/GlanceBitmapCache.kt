package com.amap.agenuiplayground.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Bitmap file cache for Glance Widget.
 *
 * Solves the PoC's [A2UIGlanceWidget.sharedBitmap] problem:
 * - PoC used a volatile static variable -> lost when process dies
 * - This cache writes Bitmap to a file -> survives process death
 *
 * Improvements over initial PoC:
 * - Atomic save via temp file + rename (prevents partial writes)
 * - WEBP compression on API 30+ (50% smaller than PNG for photos)
 * - BitmapFactory.Options with inSampleSize to prevent OOM on large bitmaps
 * - Cleanup utility for stale cache files
 */
object GlanceBitmapCache {

    private const val TAG = "GlanceBitmapCache"
    private const val DIR_NAME = "glance_bitmaps"
    private const val FILE_PREFIX = "widget_"
    private const val FILE_SUFFIX = ".webp"
    private const val TEMP_SUFFIX = ".tmp"
    private const val WEBP_QUALITY = 90
    private const val MAX_CACHE_FILES = 10
    private const val MAX_CACHE_SIZE_BYTES = 20L * 1024 * 1024 // 20 MB

    private fun getDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getFile(context: Context, appWidgetId: Int): File {
        return File(getDir(context), "$FILE_PREFIX$appWidgetId$FILE_SUFFIX")
    }

    private fun getTempFile(context: Context, appWidgetId: Int): File {
        return File(getDir(context), "$FILE_PREFIX$appWidgetId$TEMP_SUFFIX")
    }

    fun save(context: Context, appWidgetId: Int, bitmap: Bitmap): String? {
        return try {
            val file = getFile(context, appWidgetId)
            val tempFile = getTempFile(context, appWidgetId)

            FileOutputStream(tempFile).use { fos ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, WEBP_QUALITY, fos)
                fos.flush()
                try { fos.fd.sync() } catch (e: Exception) { /* best-effort fsync */ }
            }

            if (file.exists()) {
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }

            Log.d(TAG, "save: widget=$appWidgetId, file=${file.absolutePath}, size=${file.length()}")
            evictIfNeeded(context)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "save failed: widget=$appWidgetId", e)
            null
        }
    }

    fun load(
        context: Context,
        appWidgetId: Int,
        maxWidth: Int = 0,
        maxHeight: Int = 0
    ): Bitmap? {
        return try {
            val file = getFile(context, appWidgetId)
            if (!file.exists()) {
                Log.d(TAG, "load: no cache file for widget=$appWidgetId")
                return null
            }

            if (maxWidth > 0 && maxHeight > 0) {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                opts.inSampleSize = calculateSampleSize(
                    opts.outWidth, opts.outHeight, maxWidth, maxHeight
                )
                opts.inJustDecodeBounds = false
                opts.inPreferredConfig = Bitmap.Config.RGB_565 // 50% memory vs ARGB_8888 for widget display
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bitmap != null) {
                    Log.d(TAG, "load: widget=$appWidgetId, ${bitmap.width}x${bitmap.height}, sample=${opts.inSampleSize}")
                }
                bitmap
            } else {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    Log.d(TAG, "load: widget=$appWidgetId, ${bitmap.width}x${bitmap.height}")
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "load failed: widget=$appWidgetId", e)
            null
        }
    }

    fun delete(context: Context, appWidgetId: Int) {
        try {
            val file = getFile(context, appWidgetId)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "delete: widget=$appWidgetId")
            }
            val tempFile = getTempFile(context, appWidgetId)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "delete failed: widget=$appWidgetId", e)
        }
    }

    fun exists(context: Context, appWidgetId: Int): Boolean {
        return getFile(context, appWidgetId).exists()
    }

    /**
     * Returns total cache size in bytes across all widget cache files.
     */
    fun getCacheSize(context: Context): Long {
        return try {
            val dir = getDir(context)
            dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Returns the number of cache files.
     */
    fun getCacheFileCount(context: Context): Int {
        return try {
            val dir = getDir(context)
            dir.listFiles()?.filter { it.isFile }?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getPath(context: Context, appWidgetId: Int): String {
        return getFile(context, appWidgetId).absolutePath
    }

    fun getDimensions(context: Context, appWidgetId: Int): Pair<Int, Int>? {
        return try {
            val file = getFile(context, appWidgetId)
            if (!file.exists()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0) Pair(opts.outWidth, opts.outHeight) else null
        } catch (e: Exception) {
            null
        }
    }

    fun clearAll(context: Context) {
        try {
            val dir = getDir(context)
            dir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "clearAll: done")
        } catch (e: Exception) {
            Log.e(TAG, "clearAll failed", e)
        }
    }

    /**
     * Evicts oldest cache files if count or total size exceeds limits.
     * Sorts by lastModified ascending; deletes oldest first until under both thresholds.
     */
    private fun evictIfNeeded(context: Context) {
        try {
            val dir = getDir(context)
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            if (files.isEmpty()) return

            val sorted = files.sortedBy { it.lastModified() }
            var totalSize = sorted.sumOf { it.length() }

            // Delete oldest until under both count and size limits
            for (f in sorted) {
                if (files.size <= MAX_CACHE_FILES && totalSize <= MAX_CACHE_SIZE_BYTES) break
                val sz = f.length()
                if (f.delete()) {
                    totalSize -= sz
                    Log.d(TAG, "evictIfNeeded: deleted ${f.name} (${sz} bytes)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "evictIfNeeded failed", e)
        }
    }

    private fun calculateSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sampleSize = 1
        // Halve until the next step would be smaller than the target
        while (srcWidth / (sampleSize * 2) >= targetWidth &&
               srcHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
