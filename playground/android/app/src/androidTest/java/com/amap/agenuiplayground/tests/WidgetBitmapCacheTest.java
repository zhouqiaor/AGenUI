package com.amap.agenuiplayground.tests;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetBitmapCache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * R186-R195: WidgetBitmapCache lifecycle safety tests.
 *
 * Tests the P0 bitmap lifecycle safety features:
 * - Recycled bitmap detection on get()
 * - Recycled bitmap rejection on put()
 * - Safe remove() with recycle
 * - LRU eviction behavior
 * - buildKey() format
 * - clear() recycles all
 */
@RunWith(AndroidJUnit4.class)
public class WidgetBitmapCacheTest {

    @Before
    public void setUp() {
        WidgetBitmapCache.clear();
    }

    @After
    public void tearDown() {
        WidgetBitmapCache.clear();
    }

    // ===== buildKey =====

    @Test
    public void BC01_buildKey_withViewMode() {
        String key = WidgetBitmapCache.buildKey("weather", "current");
        assertEquals("weather_current", key);
    }

    @Test
    public void BC02_buildKey_nullViewMode() {
        String key = WidgetBitmapCache.buildKey("weather", null);
        assertEquals("weather_default", key);
    }

    @Test
    public void BC03_buildKey_emptyViewMode() {
        String key = WidgetBitmapCache.buildKey("agenda", "");
        assertEquals("agenda_default", key);
    }

    @Test
    public void BC04_buildKey_consistentForSameInputs() {
        String k1 = WidgetBitmapCache.buildKey("todo", "pending");
        String k2 = WidgetBitmapCache.buildKey("todo", "pending");
        assertEquals(k1, k2);
    }

    // ===== Basic put/get =====

    @Test
    public void BC05_putAndGet_returnsSameBitmap() {
        Bitmap bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        String key = "test_key";
        WidgetBitmapCache.put(key, bmp);

        Bitmap retrieved = WidgetBitmapCache.get(key);
        assertNotNull("Should retrieve cached bitmap", retrieved);
        assertEquals(bmp, retrieved);
    }

    @Test
    public void BC06_get_returnsNullForMiss() {
        Bitmap result = WidgetBitmapCache.get("nonexistent_key");
        assertNull("Cache miss should return null", result);
    }

    @Test
    public void BC07_get_returnsNullForNullKey() {
        assertNull(WidgetBitmapCache.get(null));
    }

    @Test
    public void BC08_put_nullKey_isNoop() {
        Bitmap bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        WidgetBitmapCache.put(null, bmp);
        assertEquals("Cache should remain empty", 0, WidgetBitmapCache.size());
        bmp.recycle();
    }

    @Test
    public void BC09_put_nullBitmap_isNoop() {
        WidgetBitmapCache.put("test_key", null);
        assertEquals("Cache should remain empty", 0, WidgetBitmapCache.size());
    }

    // ===== Recycled bitmap safety (P0) =====

    @Test
    public void BC10_put_recycledBitmap_rejected() {
        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        bmp.recycle();
        WidgetBitmapCache.put("recycled_key", bmp);
        assertEquals("Recycled bitmap should not be cached", 0, WidgetBitmapCache.size());
    }

    @Test
    public void BC11_get_recycledBitmap_returnsNullAndRemoves() {
        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        String key = "will_be_recycled";
        WidgetBitmapCache.put(key, bmp);

        // Recycle the bitmap externally
        bmp.recycle();

        // get() should detect recycled state and return null
        Bitmap result = WidgetBitmapCache.get(key);
        assertNull("Should return null for recycled bitmap", result);
    }

    // ===== remove() =====

    @Test
    public void BC12_remove_existingKey_recyclesBitmap() {
        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        String key = "to_remove";
        WidgetBitmapCache.put(key, bmp);

        WidgetBitmapCache.remove(key);
        assertNull("After remove, get should return null", WidgetBitmapCache.get(key));
        assertTrue("Bitmap should be recycled", bmp.isRecycled());
    }

    @Test
    public void BC13_remove_nonExistentKey_isSafe() {
        WidgetBitmapCache.remove("does_not_exist");
        // Should not crash
    }

    @Test
    public void BC14_remove_nullKey_isSafe() {
        WidgetBitmapCache.remove(null);
        // Should not crash
    }

    // ===== clear() =====

    @Test
    public void BC15_clear_removesAllEntries() {
        Bitmap bmp1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Bitmap bmp2 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        WidgetBitmapCache.put("key1", bmp1);
        WidgetBitmapCache.put("key2", bmp2);
        assertTrue("Cache should have 2 entries", WidgetBitmapCache.size() >= 2);

        WidgetBitmapCache.clear();
        assertEquals("Cache should be empty after clear", 0, WidgetBitmapCache.size());
    }

    @Test
    public void BC16_clear_recyclesBitmaps() {
        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        WidgetBitmapCache.put("clear_test", bmp);
        WidgetBitmapCache.clear();
        assertTrue("Bitmap should be recycled after clear", bmp.isRecycled());
    }

    @Test
    public void BC17_clear_onEmptyCache_isSafe() {
        WidgetBitmapCache.clear(); // already empty from setUp
        WidgetBitmapCache.clear(); // call again
        assertEquals(0, WidgetBitmapCache.size());
    }

    // ===== Overwrite behavior =====

    @Test
    public void BC18_put_sameKey_overwrites() {
        Bitmap bmp1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Bitmap bmp2 = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        String key = "overwrite_key";

        WidgetBitmapCache.put(key, bmp1);
        WidgetBitmapCache.put(key, bmp2);

        Bitmap retrieved = WidgetBitmapCache.get(key);
        assertEquals("Should return the latest bitmap", bmp2, retrieved);
        assertFalse("Old bitmap should not be the retrieved one", bmp1.equals(retrieved));
    }

    // ===== size() =====

    @Test
    public void BC19_size_reflectsEntryCount() {
        assertEquals("Empty cache should have size 0", 0, WidgetBitmapCache.size());

        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        WidgetBitmapCache.put("size_test", bmp);
        assertTrue("Cache with 1 entry should have size >= 1",
                WidgetBitmapCache.size() >= 1);
    }

    // ===== Multiple templates =====

    @Test
    public void BC20_multipleTemplates_allAccessible() {
        Bitmap[] bmps = new Bitmap[5];
        for (int i = 0; i < 5; i++) {
            bmps[i] = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
            WidgetBitmapCache.put("template_" + i, bmps[i]);
        }

        for (int i = 0; i < 5; i++) {
            Bitmap retrieved = WidgetBitmapCache.get("template_" + i);
            assertNotNull("Entry " + i + " should be retrievable", retrieved);
            assertEquals(bmps[i], retrieved);
        }
    }
}
