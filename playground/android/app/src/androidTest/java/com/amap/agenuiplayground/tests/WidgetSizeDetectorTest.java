package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.widget.WidgetSizeDetector;
import com.amap.agenuiplayground.widget.WidgetSizeDetector.SizeCategory;
import com.amap.agenuiplayground.widget.WidgetSizeDetector.WidgetDimensions;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * R196-R200: WidgetSizeDetector + WidgetStateController + WidgetRenderMetrics tests.
 *
 * Tests responsive layout breakpoints, state transitions, and metrics collection.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetSizeDetectorTest {

    // ===== categorize() =====

    @Test
    public void SD01_categorize_smallWidth() {
        assertEquals(SizeCategory.SMALL, WidgetSizeDetector.categorize(100));
        assertEquals(SizeCategory.SMALL, WidgetSizeDetector.categorize(0));
        assertEquals(SizeCategory.SMALL, WidgetSizeDetector.categorize(179));
    }

    @Test
    public void SD02_categorize_mediumWidth() {
        assertEquals(SizeCategory.MEDIUM, WidgetSizeDetector.categorize(180));
        assertEquals(SizeCategory.MEDIUM, WidgetSizeDetector.categorize(250));
        assertEquals(SizeCategory.MEDIUM, WidgetSizeDetector.categorize(319));
    }

    @Test
    public void SD03_categorize_largeWidth() {
        assertEquals(SizeCategory.LARGE, WidgetSizeDetector.categorize(320));
        assertEquals(SizeCategory.LARGE, WidgetSizeDetector.categorize(500));
        assertEquals(SizeCategory.LARGE, WidgetSizeDetector.categorize(1000));
    }

    // ===== getWidgetSize() =====

    @Test
    public void SD04_getWidgetSize_returnsNonNullArray() {
        int[] size = WidgetSizeDetector.getWidgetSize(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 1);
        assertNotNull(size);
        assertEquals(2, size.length);
        assertTrue("Width should be > 0", size[0] > 0);
        assertTrue("Height should be > 0", size[1] > 0);
    }

    @Test
    public void SD05_getWidgetSize_defaultDimensionsAreReasonable() {
        int[] size = WidgetSizeDetector.getWidgetSize(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 1);
        // Either the actual API size or the default (300x400)
        assertTrue("Width should be >= 100", size[0] >= 100);
        assertTrue("Height should be >= 100", size[1] >= 100);
    }

    // ===== resolve() =====

    @Test
    public void SD06_resolve_returnsValidDimensions() {
        WidgetDimensions dims = WidgetSizeDetector.resolve(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 1);
        assertNotNull(dims);
        assertTrue("Width > 0", dims.width > 0);
        assertTrue("Height > 0", dims.height > 0);
        assertNotNull("Category not null", dims.category);
    }

    // ===== WidgetDimensions =====

    @Test
    public void SD07_widgetDimensions_isSmall() {
        WidgetDimensions dims = new WidgetDimensions(100, 200, SizeCategory.SMALL);
        assertTrue(dims.isSmall());
        assertFalse(dims.isMedium());
        assertFalse(dims.isLarge());
    }

    @Test
    public void SD08_widgetDimensions_isMedium() {
        WidgetDimensions dims = new WidgetDimensions(250, 300, SizeCategory.MEDIUM);
        assertFalse(dims.isSmall());
        assertTrue(dims.isMedium());
        assertFalse(dims.isLarge());
    }

    @Test
    public void SD09_widgetDimensions_isLarge() {
        WidgetDimensions dims = new WidgetDimensions(400, 500, SizeCategory.LARGE);
        assertFalse(dims.isSmall());
        assertFalse(dims.isMedium());
        assertTrue(dims.isLarge());
    }

    @Test
    public void SD10_widgetDimensions_toString() {
        WidgetDimensions dims = new WidgetDimensions(300, 400, SizeCategory.MEDIUM);
        String str = dims.toString();
        assertTrue("toString should contain width", str.contains("300"));
        assertTrue("toString should contain height", str.contains("400"));
        assertTrue("toString should contain category", str.contains("MEDIUM"));
    }

    // ===== Constants =====

    @Test
    public void SD11_defaultWidth_is300() {
        assertEquals(300, WidgetSizeDetector.DEFAULT_WIDTH);
    }

    @Test
    public void SD12_defaultHeight_is400() {
        assertEquals(400, WidgetSizeDetector.DEFAULT_HEIGHT);
    }

    @Test
    public void SD13_sizeBreakpoints_areOrdered() {
        assertTrue("SMALL < MEDIUM",
                WidgetSizeDetector.SIZE_SMALL <= WidgetSizeDetector.SIZE_MEDIUM);
    }
}
