package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class NoRender extends Module {

    public final BoolSetting fire = register(new BoolSetting("Fire", "Don't render fire", true));
    public final BoolSetting blind = register(new BoolSetting("Blindness", "Don't render blindness", true));
    public final BoolSetting nausea = register(new BoolSetting("Nausea", "Don't render nausea", true));
    public final BoolSetting fog = register(new BoolSetting("Fog", "Don't render fog", true));

    public NoRender() {
        super("NoRender", "Prevents certain things from rendering.", Category.RENDER);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
