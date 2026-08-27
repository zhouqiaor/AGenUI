package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetRenderMetrics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * R196-R200 (continued): WidgetRenderMetrics tests.
 *
 * Tests per-template render time tracking, cache hit rate, min/max/avg computation,
 * summary formatting, enable/disable toggle, and reset.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderMetricsTest {

    @Before
    public void setUp() {
        WidgetRenderMetrics.reset();
        WidgetRenderMetrics.setEnabled(true);
    }

    @After
    public void tearDown() {
        WidgetRenderMetrics.reset();
    }

    // ===== Basic recording =====

    @Test
    public void RM01_recordRender_cacheHit() {
        WidgetRenderMetrics.recordRender("weather", 5, true);
        String summary = WidgetRenderMetrics.getSummary();
        assertNotNull(summary);
        assertTrue("Summary should contain 'weather'", summary.contains("weather"));
        assertTrue("Summary should show 1 hit", summary.contains("1"));
    }

    @Test
    public void RM02_recordRender_fullRender() {
        WidgetRenderMetrics.recordRender("agenda", 1500, false);
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("Summary should contain 'agenda'", summary.contains("agenda"));
        assertTrue("Summary should contain 1500", summary.contains("1500"));
    }

    @Test
    public void RM03_recordRender_multipleTemplates() {
        WidgetRenderMetrics.recordRender("weather", 800, false);
        WidgetRenderMetrics.recordRender("agenda", 1200, false);
        WidgetRenderMetrics.recordRender("todo", 500, true);
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue(summary.contains("weather"));
        assertTrue(summary.contains("agenda"));
        assertTrue(summary.contains("todo"));
    }

    // ===== Min/Max tracking =====

    @Test
    public void RM04_minMaxTrackedAcrossMultipleRenders() {
        WidgetRenderMetrics.recordRender("weather", 500, false);
        WidgetRenderMetrics.recordRender("weather", 200, false);
        WidgetRenderMetrics.recordRender("weather", 800, false);
        String summary = WidgetRenderMetrics.getSummary();
        // Min should be 200, Max should be 800
        assertTrue("Summary should contain min (200)", summary.contains("200"));
        assertTrue("Summary should contain max (800)", summary.contains("800"));
    }

    // ===== Cache hit rate =====

    @Test
    public void RM05_cacheHitRate_reflected() {
        WidgetRenderMetrics.recordRender("todo", 1, true);
        WidgetRenderMetrics.recordRender("todo", 1, true);
        WidgetRenderMetrics.recordRender("todo", 1, true);
        WidgetRenderMetrics.recordRender("todo", 500, false);
        String summary = WidgetRenderMetrics.getSummary();
        // 3 hits out of 4 total = 75%
        assertTrue("Should show hit rate", summary.contains("75"));
    }

    // ===== Enable/Disable =====

    @Test
    public void RM06_disabled_doesNotRecord() {
        WidgetRenderMetrics.setEnabled(false);
        WidgetRenderMetrics.recordRender("weather", 100, false);
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("No renders should be recorded when disabled",
                summary.contains("No renders") || summary.isEmpty());
    }

    @Test
    public void RM07_enabled_recordsNormally() {
        WidgetRenderMetrics.setEnabled(true);
        WidgetRenderMetrics.recordRender("weather", 100, false);
        String summary = WidgetRenderMetrics.getSummary();
        assertFalse("Summary should contain data when enabled",
                summary.contains("No renders"));
    }

    // ===== Reset =====

    @Test
    public void RM08_reset_clearsAllStats() {
        WidgetRenderMetrics.recordRender("weather", 100, false);
        WidgetRenderMetrics.recordRender("agenda", 200, false);
        WidgetRenderMetrics.reset();
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("After reset, summary should be empty",
                summary.contains("No renders") || summary.isEmpty());
    }

    // ===== Summary format =====

    @Test
    public void RM09_summary_hasHeaderRow() {
        WidgetRenderMetrics.recordRender("weather", 100, false);
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("Summary should have 'Template' header",
                summary.contains("Template"));
        assertTrue("Summary should have 'Total' header",
                summary.contains("Total"));
        assertTrue("Summary should have 'Hits' header",
                summary.contains("Hits"));
    }

    // ===== Edge cases =====

    @Test
    public void RM10_emptySummary_whenNoRenders() {
        String summary = WidgetRenderMetrics.getSummary();
        assertNotNull(summary);
        assertTrue("Should indicate no renders", summary.contains("No renders"));
    }

    @Test
    public void RM11_avgCalculation_multipleRenders() {
        // 2 renders: 300ms + 500ms → avg = 400ms
        WidgetRenderMetrics.recordRender("test_avg", 300, false);
        WidgetRenderMetrics.recordRender("test_avg", 500, false);
        String summary = WidgetRenderMetrics.getSummary();
        assertTrue("Summary should contain avg (400)", summary.contains("400"));
    }

    private static void assertFalse(String msg, boolean condition) {
        org.junit.Assert.assertFalse(msg, condition);
    }
}
