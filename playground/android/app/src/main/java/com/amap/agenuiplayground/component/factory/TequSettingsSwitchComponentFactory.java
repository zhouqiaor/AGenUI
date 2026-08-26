package com.amap.agenuiplayground.component.factory;

import android.content.Context;

import androidx.annotation.Nullable;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.IComponentFactory;
import com.amap.agenui.render.component.impl.CheckBoxComponent;
import com.amap.agenui.render.measurement.IMeasurer;

import java.util.Map;

/**
 * TequSettingsSwitch - 4K 适配的设置开关组件工厂
 *
 * <p>用于 settings-panel 的 switch 类型设置项。
 * 当前版本委托给内置 CheckBoxComponent，后续迭代将替换为
 * 专门的 Switch 样式组件（4K 圆角轨道+滑块）。
 *
 * <p>注册方式：
 * <pre>
 * AGenUI.getInstance().registerComponent("TequSettingsSwitch", new TequSettingsSwitchComponentFactory());
 * </pre>
 */
public class TequSettingsSwitchComponentFactory implements IComponentFactory {

    @Override
    public A2UIComponent createComponent(Context context, String id, Map<String, Object> properties) {
        // Phase 1: 委托给内置 CheckBoxComponent
        return new CheckBoxComponent(context, id, properties);
    }

    @Override
    public String getComponentType() {
        return "TequSettingsSwitch";
    }

    @Nullable
    @Override
    public IMeasurer getMeasurer() {
        return null;
    }
}
