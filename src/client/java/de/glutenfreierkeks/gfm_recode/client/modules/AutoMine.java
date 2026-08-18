package de.glutenfreierkeks.gfm_recode.client.modules;

import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class AutoMine extends Module {

    public AutoMine() {
        super("AutoMine", "Automatically mines blocks", Category.WORLD);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        
        // Simulate holding left-click (attack key)
        mc.options.attackKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        // Stop mining when disabled
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
        }
    }
}
