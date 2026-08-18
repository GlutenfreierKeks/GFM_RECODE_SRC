package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.util.math.MathHelper;

public class DoubleSliderSetting extends Setting<Double> {

    public final double min;
    public final double max;
    public final int    decimalPlaces;

    public DoubleSliderSetting(String name, String description, double defaultValue, double min, double max, int decimalPlaces) {
        super(name, description, defaultValue);
        this.min           = min;
        this.max           = max;
        this.decimalPlaces = decimalPlaces;
    }

    public DoubleSliderSetting(String name, String description, double defaultValue, double min, double max) {
        this(name, description, defaultValue, min, max, 2);
    }

    @Override
    public void setValue(Double v) {
        double factor = Math.pow(10, decimalPlaces);
        this.value = Math.round(MathHelper.clamp(v, min, max) * factor) / factor;
    }

    public double getPercent() {
        return (value - min) / (max - min);
    }

    public void setFromPercent(double percent) {
        setValue(min + (max - min) * MathHelper.clamp((float)percent, 0f, 1f));
    }

    public String getFormatted() {
        return String.format("%." + decimalPlaces + "f", value);
    }

    public double getMin() { return min; }
    public double getMax() { return max; }

    @Override
    public SettingType getType() { return SettingType.SLIDER_DOUBLE; }
}
