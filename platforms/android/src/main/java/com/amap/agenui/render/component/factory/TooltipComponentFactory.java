package com.amap.agenui.render.component.factory;

import android.content.Context;
import com.amap.agenui.annotation.BuiltInComponent;
import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.ComponentEventDispatcher;
import com.amap.agenui.render.component.ComponentState;
import com.amap.agenui.render.component.impl.TooltipComponent;

@BuiltInComponent("Tooltip")
public class TooltipComponentFactory implements IComponentFactory {
    @Override
    public A2UIComponent createComponent(Context context, String componentId,
                                         String componentType,
                                         ComponentEventDispatcher eventDispatcher,
                                         ComponentState componentState) {
        return new TooltipComponent(context, componentId, componentType,
                eventDispatcher, componentState);
    }
}
