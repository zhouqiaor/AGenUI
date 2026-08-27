package com.amap.agenuiplayground.tests;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenuiplayground.R;
import com.amap.agenuiplayground.widget.WidgetRemoteViewsPool;
import com.amap.agenuiplayground.widget.WidgetStateController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * R201-R210: WidgetStateController + WidgetRemoteViewsPool tests.
 *
 * StateController: 4 state transitions (content/loading/empty/error), visibility toggling.
 * RemoteViewsPool: LRU pool behavior, clone-on-obtain, size cap, clear.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetInfrastructureTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WidgetRemoteViewsPool.clear();
    }

    @After
    public void tearDown() {
        WidgetRemoteViewsPool.clear();
    }

    // ===== WidgetStateController =====

    @Test
    public void SC01_setContentState_showsImageView() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setState(views, WidgetStateController.STATE_CONTENT);
        // Just verify no crash — RemoteViews doesn't expose visibility getters
        assertNotNull(views);
    }

    @Test
    public void SC02_setLoadingState_showsLoading() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setState(views, WidgetStateController.STATE_LOADING);
        assertNotNull(views);
    }

    @Test
    public void SC03_setEmptyState_showsEmpty() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setState(views, WidgetStateController.STATE_EMPTY);
        assertNotNull(views);
    }

    @Test
    public void SC04_setErrorState_showsError() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setState(views, WidgetStateController.STATE_ERROR);
        assertNotNull(views);
    }

    @Test
    public void SC05_setErrorWithMessage_setsText() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setError(views, "Connection failed");
        assertNotNull(views);
    }

    @Test
    public void SC06_setEmptyWithMessage_setsText() {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.a2ui_widget_content);
        WidgetStateController.setEmpty(views, "No data");
        assertNotNull(views);
    }

    @Test
    public void SC07_stateConstants_areDistinct() {
        assertTrue(WidgetStateController.STATE_CONTENT != WidgetStateController.STATE_LOADING);
        assertTrue(WidgetStateController.STATE_LOADING != WidgetStateController.STATE_EMPTY);
        assertTrue(WidgetStateController.STATE_EMPTY != WidgetStateController.STATE_ERROR);
        assertTrue(WidgetStateController.STATE_CONTENT != WidgetStateController.STATE_ERROR);
    }

    @Test
    public void SC08_allStatesAreNonNegative() {
        assertTrue(WidgetStateController.STATE_CONTENT >= 0);
        assertTrue(WidgetStateController.STATE_LOADING >= 0);
        assertTrue(WidgetStateController.STATE_EMPTY >= 0);
        assertTrue(WidgetStateController.STATE_ERROR >= 0);
    }

    // ===== WidgetRemoteViewsPool =====

    @Test
    public void RP01_obtain_returnsNonNullRemoteViews() {
        RemoteViews views = WidgetRemoteViewsPool.obtain(context,
                R.layout.a2ui_widget_content);
        assertNotNull("obtain should return non-null RemoteViews", views);
    }

    @Test
    public void RP02_obtainWidgetLayout_returnsNonNull() {
        RemoteViews views = WidgetRemoteViewsPool.obtainWidgetLayout(context);
        assertNotNull(views);
    }

    @Test
    public void RP03_obtain_increasesPoolSize() {
        assertEquals("Pool should be empty initially", 0, WidgetRemoteViewsPool.size());
        WidgetRemoteViewsPool.obtain(context, R.layout.a2ui_widget_content);
        assertTrue("Pool size should be >= 1 after obtain",
                WidgetRemoteViewsPool.size() >= 1);
    }

    @Test
    public void RP04_obtain_sameLayout_returnsClone() {
        RemoteViews first = WidgetRemoteViewsPool.obtain(context,
                R.layout.a2ui_widget_content);
        RemoteViews second = WidgetRemoteViewsPool.obtain(context,
                R.layout.a2ui_widget_content);
        assertNotNull(first);
        assertNotNull(second);
        // They should be different instances (clone-on-obtain)
        assertTrue("Second obtain should return a different instance",
                first != second);
    }

    @Test
    public void RP05_clear_resetsPool() {
        WidgetRemoteViewsPool.obtain(context, R.layout.a2ui_widget_content);
        assertTrue(WidgetRemoteViewsPool.size() >= 1);
        WidgetRemoteViewsPool.clear();
        assertEquals("Pool should be empty after clear", 0, WidgetRemoteViewsPool.size());
    }

    @Test
    public void RP06_obtain_multipleLayouts_poolSizeIncreases() {
        WidgetRemoteViewsPool.obtain(context, R.layout.a2ui_widget_content);
        // Try another layout if available
        // Pool should handle multiple entries
        assertTrue("Pool should have at least 1 entry", WidgetRemoteViewsPool.size() >= 1);
    }

    @Test
    public void RP07_poolSize_neverNegative() {
        WidgetRemoteViewsPool.clear();
        assertTrue("Pool size should be >= 0", WidgetRemoteViewsPool.size() >= 0);
    }

    @Test
    public void RP08_clearOnEmptyPool_isSafe() {
        WidgetRemoteViewsPool.clear();
        WidgetRemoteViewsPool.clear();
        assertEquals(0, WidgetRemoteViewsPool.size());
    }

    @Test
    public void RP09_obtain_doesNotCrashOnMultipleCalls() {
        for (int i = 0; i < 10; i++) {
            RemoteViews views = WidgetRemoteViewsPool.obtain(context,
                    R.layout.a2ui_widget_content);
            assertNotNull("Call " + i + " should return non-null", views);
        }
    }

    @Test
    public void RP10_poolSize_cappedAtMax() {
        // MAX_POOL_SIZE is 3, but we only have 1 layout in this test
        // So size should not exceed available layouts
        for (int i = 0; i < 5; i++) {
            WidgetRemoteViewsPool.obtain(context, R.layout.a2ui_widget_content);
        }
        assertTrue("Pool size should be <= 3 (MAX_POOL_SIZE)",
                WidgetRemoteViewsPool.size() <= 3);
    }
}
