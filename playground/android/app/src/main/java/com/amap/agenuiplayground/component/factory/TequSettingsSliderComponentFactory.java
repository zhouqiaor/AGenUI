package com.amap.agenuiplayground.component.factory;

import android.content.Context;

import androidx.annotation.Nullable;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.IComponentFactory;
import com.amap.agenui.render.component.impl.SliderComponent;
import com.amap.agenui.render.measurement.IMeasurer;

import java.util.Map;

/**
 * TequSettingsSlider - 4K 适配的设置滑块组件工厂
 *
 * <p>用于 settings-panel 的 slider 类型设置项。
 * 当前版本委托给内置 SliderComponent，后续迭代将替换为
 * 专门的 Slider 样式组件（4K 轨道高度+数值显示）。
 */
public class TequSettingsSliderComponentFactory implements IComponentFactory {

    @Override
    public A2UIComponent createComponent(Context context, String id, Map<String, Object> properties) {
        // Phase 1: 委托给内置 SliderComponent
        return new SliderComponent(context, id, properties);
    }

    @Override
    public String getComponentType() {
        return "TequSettingsSlider";
    }

    @Nullable
    @Override
    public IMeasurer getMeasurer() {
        return null;
    }
}
