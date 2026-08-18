package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import net.minecraft.util.math.MathHelper;

public class ThunderEaseOutBack {
    private int prevTick;
    private int tick;
    private final int maxTick;

    public ThunderEaseOutBack() {
        this(10);
    }

    public ThunderEaseOutBack(int maxTick) {
        this.maxTick = maxTick;
    }

    public static double dropAnimation(double value) {
        return 1 + 2.70158 * Math.pow(value - 1, 3) + 1.70158 * Math.pow(value - 1, 2);
    }

    public void update(boolean update) {
        prevTick = tick;
        tick = MathHelper.clamp(tick + (update ? 1 : -1), 0, maxTick);
    }

    public double getAnimation(double tickDelta) {
        return dropAnimation((prevTick + (tick - prevTick) * tickDelta) / maxTick);
    }

    public void reset() {
        prevTick = 0;
        tick = 0;
    }
}
