package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class FastPlace extends Module {

    public FastPlace() {
        super("FastPlace", "Removes place delay.", Category.PLAYER);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        // In modern Minecraft, we might need a mixin to access itemUseCheck, 
        // but for now we'll leave it as a placeholder if the field is not directly accessible.
        // mc.itemUseCheck = 0;
    }
}
