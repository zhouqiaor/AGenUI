package com.amap.agenui.render.component.impl;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.A2UILayoutComponent;
import com.amap.agenui.render.component.impl.list.ComponentAdapter;
import com.amap.agenui.render.component.impl.list.YogaLayoutManager;
import com.amap.agenui.render.style.StyleHelper;

import java.util.Map;

/**
 * List component with virtualized rendering for BOTH directions via RecyclerView.
 *
 * Both vertical and horizontal lists use a RecyclerView backed by
 * {@link YogaLayoutManager} + {@link ComponentAdapter}. Children placement
 * comes from the engine's Yoga layout (absolute x/y/w/h); scrolling and view
 * recycling come from RV's standard machinery.
 *
 * Lazy-load semantics (both directions): List children's createView is deferred
 * until they scroll into the visible window (or RV's prefetch cache window).
 * Two pieces cooperate to realize this:
 *   1. {@link #shouldCreateChildView()} returns false → Surface.handleChildComponent
 *      short-circuits before calling child.createView at dispatch time.
 *   2. {@link #createChildViews(Context)} is overridden to a no-op → when this
 *      List itself is created (e.g. by an outer RV's onBindViewHolder), it does
 *      NOT eagerly walk its own children. The base-class createChildViews would
 *      otherwise recurse into every child, defeating virtualization.
 * The actual createView for a child is triggered later by
 * {@link com.amap.agenui.render.component.impl.list.ComponentAdapter#onBindViewHolder}
 * when a position is bound.
 *
 * Frame source: ListComponent does NOT cache per-child yoga frames.
 * Instead, it hands its live {@code children} list to
 * {@link YogaLayoutManager#setChildren}, and the LayoutManager re-reads each
 * child's current x/y/width/height from {@code child.properties.styles} on every
 * measure/layout/scroll pass. This avoids a staleness bug in DataBinding scenarios
 * where the engine dispatches a card in multiple batches and updates the card's
 * height incrementally (see list-lazy-load-frame-staleness-fix.md).
 *
 * History: Previously vertical lists used eager YogaAbsoluteLayout (no scrolling,
 * no virtualization, O(n) creation). As of R29, vertical lists also use
 * RecyclerView for virtualization — a 100-item vertical list only creates views
 * for visible items (~5-10), not all 100.
 */
public class ListComponent extends A2UILayoutComponent {

    // -- RecyclerView fields (shared by both directions) --
    private RecyclerView recyclerView;
    private YogaLayoutManager layoutManager;
    private ComponentAdapter adapter;
    private final Runnable mDeferredRequestLayout = () -> {
        if (recyclerView != null) recyclerView.requestLayout();
    };
    private final View.OnLayoutChangeListener mClipBoundsListener = (v, left, top, right, bottom,
                                                                      oldLeft, oldTop, oldRight, oldBottom) -> {
        int w = right - left;
        int h = bottom - top;
        Rect current = v.getClipBounds();
        if (current == null || current.right != w || current.bottom != h) {
            v.setClipBounds(new Rect(0, 0, w, h));
        }
    };

    private String direction = "vertical";

    public ListComponent(String id, Map<String, Object> properties) {
        super(id, "List");
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    private boolean isHorizontal() {
        return "horizontal".equals(direction);
    }

    /**
     * Lazy-load gate for List CHILDREN (both directions):
     * Surface.handleChildComponent reads parent.shouldCreateChildView() before
     * deciding whether to create a freshly added child's view at dispatch time.
     * Returning false here means "my children are lazy".
     *
     * About the List itself:
     *   - If the List's own parent is non-lazy, the List is created eagerly
     *     when added (Surface checks THAT parent's shouldCreateChildView,
     *     not ours).
     *   - If the List's own parent is also lazy (e.g. List-of-List), the
     *     List is itself deferred and gets created later — by the outer
     *     RV's adapter.onBindViewHolder, which calls createView on this
     *     List, which runs onCreateView to build the RecyclerView shell.
     *
     * Actual hosting of a child view always happens inside
     * ComponentViewHolder.attach when a position is bound by the RV adapter.
     */
    @Override
    public boolean shouldCreateChildView() {
        return false;
    }

    /**
     * Self-managed child placement (both directions): the RecyclerView's
     * adapter/holder owns where child views go (via
     * ComponentAdapter.onBindViewHolder + ComponentViewHolder.attach).
     * External code (Surface.attachChildView, A2UIComponent.addChild catch-up,
     * createChildViews) must NOT auto-addView our children — RecyclerView
     * rejects external addView calls with UnsupportedOperationException.
     */
    @Override
    public boolean shouldAutoAddChildView() {
        return false;
    }

    /**
     * Both directions: List children are positioned by YogaLayoutManager.layoutDecorated
     * on their shell FrameLayouts. Yoga x/y must NOT be applied as leftMargin/
     * topMargin — that would double-offset the child view (the shell already
     * sits at the yoga position). Width/height are driven by
     * measureChildExactly in onLayoutChildren.
     *
     * Returning false skips the entire applyYogaLayout, including the
     * setLayoutParams → requestLayout chain. To ensure the LM picks up
     * frame changes (e.g. DataBinding card height updates), we request a
     * relayout on the RV. isLayoutRequested() deduplicates: within one
     * onComponentsUpdate batch (N children), only the first call triggers;
     * the resulting onLayoutChildren re-reads all frames via
     * currentPxFrames() in one pass.
     */
    @Override
    public boolean shouldApplyChildYogaLayout(A2UIComponent child) {
        if (layoutManager != null) {
            layoutManager.invalidateFrameCache();
        }
        if (recyclerView != null) {
            if (!recyclerView.isLayoutRequested()) {
                recyclerView.requestLayout();
            } else {
                recyclerView.removeCallbacks(mDeferredRequestLayout);
                recyclerView.post(mDeferredRequestLayout);
            }
        }
        return false;
    }

    @Override
    protected View onCreateView(Context context) {
        Object directionValue = properties.get("direction");
        direction = directionValue != null ? String.valueOf(directionValue) : "vertical";
        return createRecyclerView(context);
    }

    private View createRecyclerView(Context context) {
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Disable item animations: streaming inserts (appendComponents) should
        // not flicker, and animations interfere with RV stability under flings.
        recyclerView.setItemAnimator(null);
        // Disable overscroll edge glow / bounce — when the user drags past the
        // first or last item the LM clamps scrollOffset, but RV's default
        // overscroll exposes a blank strip on the dragged side. Pre-refactor
        // (HorizontalScrollView path) did the same setOverScrollMode(NEVER).
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.addOnLayoutChangeListener(mClipBoundsListener);

        layoutManager = new YogaLayoutManager();
        layoutManager.setDirection(isHorizontal()
                ? YogaLayoutManager.HORIZONTAL
                : YogaLayoutManager.VERTICAL);
        // Hand the live children list to LM. LM reads each child's current
        // yoga frame on every layout pass — no separate frame cache to keep
        // in sync, no staleness window.
        layoutManager.setChildren(children);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ComponentAdapter(children);
        // Disable GapWorker prefetch so that onBindViewHolder only fires when
        // the item is actually needed for display, not speculatively pre-bound.
        layoutManager.setItemPrefetchEnabled(false);
        // Bind == display: report each child when RecyclerView binds it.
        adapter.setOnItemBindListener((position, child) -> {
            if (child.getId() == null) {
                return;
            }
            child.notifyAppeared();
        });

        recyclerView.setAdapter(adapter);
        applyContainerPaddingToRecyclerView(context);
        return recyclerView;
    }

    private void applyContainerPaddingToRecyclerView(Context context) {
        if (recyclerView == null || context == null) {
            return;
        }
        Rect padding = StyleHelper.resolveCSSPaddingPx(extractStyles(properties), context);
        recyclerView.setPadding(0, 0, padding.right, padding.bottom);
        recyclerView.setClipToPadding(false);
    }

    @Override
    public void onUpdateProperties(Map<String, Object> changedProps) {
        super.onUpdateProperties(changedProps);
        if (changedProps != null
                && changedProps.containsKey("styles")) {
            Context context = recyclerView != null ? recyclerView.getContext() : null;
            applyContainerPaddingToRecyclerView(context);
        }
    }

    /**
     * Lazy contract (both directions): RecyclerView's adapter
     * (ComponentAdapter.onBindViewHolder) is the SOLE trigger for materializing
     * List children. The base class createChildViews would eagerly recurse into
     * every child here — for a 100-card List that means 100x onCreateView +
     * full descendant subtree creation up-front, completely defeating
     * virtualization. Override to no-op so children stay deferred until they
     * scroll into view.
     *
     * Why the dispatch-time guard (Surface's parentContainer == null branch)
     * is not enough on its own: when this List is itself a child of another
     * lazy container (e.g. List-of-List), its createView gets triggered by
     * the outer adapter's onBindViewHolder, which in turn calls
     * createChildViews on us — and the base class implementation would
     * eagerly create everything down the tree. This override breaks that
     * recursion at the List boundary.
     */
    @Override
    protected void createChildViews(Context context) {
        return;
    }

    @Override
    public ViewGroup getChildContainer() {
        return null;
    }

    @Override
    public void addChild(A2UIComponent child) {
        // Record tree relationship (always). LM holds a reference to `children`
        // and will re-read frames live — no cache to update here.
        super.addChild(child);

        // Children list changed — invalidate LM's frame cache so the next
        // layout pass re-reads all frames (including the newly added child).
        if (layoutManager != null) {
            layoutManager.invalidateFrameCache();
        }

        // Notify adapter only after onCreateView has wired it up. First-time
        // addChild during dispatch hits this path with adapter == null and
        // just records; setAdapter in onCreateView will pick up children.size()
        // as initial item count and trigger first layout naturally.
        if (adapter != null) {
            adapter.notifyItemInserted(children.size() - 1);
        }
    }

    @Override
    public void removeChild(A2UIComponent child) {
        int index = children.indexOf(child);
        super.removeChild(child);
        if (index < 0) return;

        // Children list changed — invalidate LM's frame cache so stale
        // entries for the removed child are discarded.
        if (layoutManager != null) {
            layoutManager.invalidateFrameCache();
        }

        if (adapter != null) {
            adapter.notifyItemRemoved(index);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach RV from its adapter / layout manager so any RV-internal state
        // (recycler pool, pending GapWorker prefetches) is released alongside
        // this component. Note: A2UIComponent.destroy() calls children.clear()
        // BEFORE invoking this onDestroy() hook, so we are not racing the
        // base-class clear here — both run on the same (UI) thread sequentially.
        // What this guards against is anything queued on the RV that might
        // outlive the component reference (e.g. animator end callbacks).
        if (recyclerView != null) {
            recyclerView.removeOnLayoutChangeListener(mClipBoundsListener);
            recyclerView.removeCallbacks(mDeferredRequestLayout);
            recyclerView.setLayoutManager(null);
            recyclerView.setAdapter(null);
        }
        recyclerView = null;
        layoutManager = null;
        adapter = null;
    }
}
