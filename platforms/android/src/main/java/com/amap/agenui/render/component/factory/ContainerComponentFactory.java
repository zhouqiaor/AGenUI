package com.amap.agenui.render.component.factory;

import com.amap.agenui.annotation.BuiltInComponent;
import android.content.Context;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.IComponentFactory;
import com.amap.agenui.render.measurement.IMeasurer;

import androidx.annotation.Nullable;
import com.amap.agenui.render.component.impl.ContainerComponent;

import java.util.Map;

/**
 * Factory for the {@link ContainerComponent}.
 *
 * <p>Annotated with {@link BuiltInComponent} so the annotation processor
 * auto-registers it into {@code BuiltInComponentRegistrar} at compile time.
 */
@BuiltInComponent
public class ContainerComponentFactory implements IComponentFactory {

    @Override
    public A2UIComponent createComponent(Context context, String id, Map<String, Object> properties) {
        return new ContainerComponent(context, id, properties);
    }

    @Override
    public String getComponentType() {
        return "Container";
    }

    @Override
    @Nullable
    public IMeasurer getMeasurer() {
        return null;
    }
}
