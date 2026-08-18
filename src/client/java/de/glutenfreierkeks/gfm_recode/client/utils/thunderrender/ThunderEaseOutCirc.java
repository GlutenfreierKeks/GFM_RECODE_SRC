package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import net.minecraft.util.math.MathHelper;

public class ThunderEaseOutCirc {
    private int prevTick;
    private int tick;
    private final int maxTick;

    public ThunderEaseOutCirc() {
        this(10);
    }

    public ThunderEaseOutCirc(int maxTick) {
        this.maxTick = maxTick;
    }

    public void update(boolean update) {
        prevTick = tick;
        tick = MathHelper.clamp(tick + (update ? 1 : -1), 0, maxTick);
    }

    public double getAnimation(double tickDelta) {
        double value = (prevTick + (tick - prevTick) * tickDelta) / maxTick;
        return Math.sqrt(1 - Math.pow(value - 1, 2));
    }

    public void reset() {
        prevTick = 0;
        tick = 0;
    }
}
