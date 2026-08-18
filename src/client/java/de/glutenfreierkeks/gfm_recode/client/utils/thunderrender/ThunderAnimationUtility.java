package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

public final class ThunderAnimationUtility {
    private ThunderAnimationUtility() {}

    public static float deltaTime() {
        return 0.016f;
    }

    public static float fast(float end, float start, float multiple) {
        float clampedDelta = Math.max(0f, Math.min(1f, deltaTime() * multiple));
        return (1f - clampedDelta) * end + clampedDelta * start;
    }
}
