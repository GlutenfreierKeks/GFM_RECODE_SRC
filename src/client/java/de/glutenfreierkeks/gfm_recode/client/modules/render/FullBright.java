package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Matrix4f;

public class FullBright extends Module {

    private double oldGamma;
    private boolean hadNightVision;

    public FullBright() {
        super("FullBright", "Makes everything bright.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        oldGamma = mc.options.getGamma().getValue();
        mc.options.getGamma().setValue(1.0);

        if (mc.player != null) {
            hadNightVision = mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION);
            mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false
            ));
        }
    }

    @Override
    public void onDisable() {
        mc.options.getGamma().setValue(oldGamma);

        if (mc.player != null && !hadNightVision) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.options.getGamma().getValue() < 1.0) {
            mc.options.getGamma().setValue(1.0);
        }
        if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false
            ));
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
