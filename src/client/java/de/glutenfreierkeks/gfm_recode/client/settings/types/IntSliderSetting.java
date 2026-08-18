package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.util.math.MathHelper;

public class IntSliderSetting extends Setting<Integer> {

    public final int min;
    public final int max;

    public IntSliderSetting(String name, String description, int defaultValue, int min, int max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
    }

    @Override
    public void setValue(Integer v) {
        this.value = MathHelper.clamp(v, min, max);
    }

    /** Returns 0.0 - 1.0 progress for slider rendering. */
    public double getPercent() {
        return (double)(value - min) / (max - min);
    }

    public void setFromPercent(double percent) {
        setValue((int) Math.round(min + (max - min) * MathHelper.clamp((float)percent, 0f, 1f)));
    }

    public int getMin() { return min; }
    public int getMax() { return max; }

    @Override
    public SettingType getType() { return SettingType.SLIDER_INT; }
}
