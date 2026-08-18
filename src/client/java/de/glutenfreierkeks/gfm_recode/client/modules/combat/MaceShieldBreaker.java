package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class MaceShieldBreaker extends Module {

    public MaceShieldBreaker() {
        super("MaceShieldBreaker", "Breaks shield with a density mace if player falls > 10 blocks", Category.PLAYER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public void onTick() {}
}
