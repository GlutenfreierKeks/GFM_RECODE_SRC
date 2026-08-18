package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import java.awt.Color;

public final class ThunderRender2D {
    private ThunderRender2D() {}

    public static double interpolate(double oldValue, double newValue, double interpolationValue) {
        return oldValue + (newValue - oldValue) * interpolationValue;
    }

    public static Color injectAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
    }

    public static Color applyOpacity(Color color, float opacity) {
        return injectAlpha(color, (int) (color.getAlpha() * Math.max(0f, Math.min(1f, opacity))));
    }

    public static Color astolfo(int offset, int alpha) {
        float hue = ((System.currentTimeMillis() + offset * 18L) % 3600L) / 3600f;
        int rgb = Color.HSBtoRGB(hue, 0.62f, 1f);
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, clamp(alpha));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
