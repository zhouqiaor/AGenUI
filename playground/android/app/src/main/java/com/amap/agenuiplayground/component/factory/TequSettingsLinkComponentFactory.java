package com.amap.agenuiplayground.component.factory;

import android.content.Context;

import androidx.annotation.Nullable;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.IComponentFactory;
import com.amap.agenui.render.component.impl.ButtonComponent;
import com.amap.agenui.render.measurement.IMeasurer;

import java.util.Map;

/**
 * TequSettingsLink - 4K 适配的设置链接组件工厂
 *
 * <p>用于 settings-panel 的 link 类型设置项（带右箭头的可点击行）。
 * 当前版本委托给内置 ButtonComponent，后续迭代将替换为
 * 专门的 Link 样式组件（4K 行高+右箭头+标题+副标题布局）。
 */
public class TequSettingsLinkComponentFactory implements IComponentFactory {

    @Override
    public A2UIComponent createComponent(Context context, String id, Map<String, Object> properties) {
        // Phase 1: 委托给内置 ButtonComponent
        return new ButtonComponent(context, id, properties);
    }

    @Override
    public String getComponentType() {
        return "TequSettingsLink";
    }

    @Nullable
    @Override
    public IMeasurer getMeasurer() {
        return null;
    }
}
