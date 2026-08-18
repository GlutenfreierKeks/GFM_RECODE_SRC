package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.util.math.MathHelper;

/**
 * A setting that holds an RGBA color.
 * Internally maintains HSV for smoother GUI interaction and RGB for final output.
 */
public class ColorSetting extends Setting<Integer> {

    private float hue = 0, sat = 0, val = 1;
    private int alpha = 255;

    public ColorSetting(String name, String description, int r, int g, int b, int a) {
        super(name, description, 0);
        setRGB(r, g, b, a);
    }

    public ColorSetting(String name, String description) {
        this(name, description, 176, 106, 255, 255);
    }

    public void setRGB(int r, int g, int b, int a) {
        this.alpha = MathHelper.clamp(a, 0, 255);
        float[] hsv = java.awt.Color.RGBtoHSB(r, g, b, null);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        sync();
    }

    public void setHSV(float h, float s, float v, int a) {
        this.hue = h;
        this.sat = s;
        this.val = v;
        this.alpha = MathHelper.clamp(a, 0, 255);
        sync();
    }

    public int getR() { return (getArgb() >> 16) & 0xFF; }
    public int getG() { return (getArgb() >> 8) & 0xFF; }
    public int getB() { return getArgb() & 0xFF; }
    public int getA() { return alpha; }

    public float getHue() { return hue; }
    public float getSat() { return sat; }
    public float getVal() { return val; }

    public void setHue(float h) { this.hue = h; sync(); }
    public void setSat(float s) { this.sat = s; sync(); }
    public void setVal(float v) { this.val = v; sync(); }
    public void setAlpha(int a) { this.alpha = MathHelper.clamp(a, 0, 255); sync(); }

    public int getArgb() { return value; }

    public java.awt.Color getJavaColor() {
        return new java.awt.Color(getR(), getG(), getB(), getA());
    }

    private void sync() {
        int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
        this.value = (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public SettingType getType() { return SettingType.COLOR; }
}
