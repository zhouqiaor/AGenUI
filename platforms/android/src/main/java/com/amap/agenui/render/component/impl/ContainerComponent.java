package com.amap.agenui.render.component.impl;

import android.content.Context;
import android.view.View;

import com.amap.agenui.render.component.A2UILayoutComponent;
import com.amap.agenui.render.layout.YogaAbsoluteLayout;

import java.util.Map;

/**
 * Container is a generic Yoga-backed layout container.
 *
 * <p>Unlike {@link ColumnComponent} (which forces flex-direction: column) or
 * {@link RowComponent} (flex-direction: row), Container imposes no direction —
 * children are positioned absolutely via Yoga. It is the A2UI equivalent of a
 * bare {@code div} or {@code FrameLayout} with absolute positioning.
 *
 * <p>Templates use {@code "component": "Container"} for decorative elements
 * such as circular icon backgrounds, progress bar tracks, pill-shaped badges,
 * and gradient overlays — anywhere a styled box with children is needed without
 * a forced layout direction.
 */
public class ContainerComponent extends A2UILayoutComponent {

    private YogaAbsoluteLayout container;

    public ContainerComponent(Context context, String id, Map<String, Object> properties) {
        super(id, "Container");
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    protected View onCreateView(Context context) {
        container = new YogaAbsoluteLayout(context);
        if (!properties.isEmpty()) {
            onUpdateProperties(this.properties);
        }
        return container;
    }
}
