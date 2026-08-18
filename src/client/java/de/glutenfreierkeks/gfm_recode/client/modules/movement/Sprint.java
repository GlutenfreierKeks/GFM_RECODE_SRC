package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class Sprint extends Module {

    public final EnumSetting<Mode> mode = register(
        new EnumSetting<>("Mode", "Sprint mode", Mode.OMNI)
    );

    public final BoolSetting noSlowdown = register(
        new BoolSetting("No Slowdown", "Sprint without slowing down when attacking", false)
    );

    public final BoolSetting inAir = register(
        new BoolSetting("In Air", "Continue sprinting while in the air", true)
    );

    public enum Mode {
        OMNI,    // Always sprint in all directions
        FORWARD  // Only sprint when moving forward
    }

    public Sprint() {
        super("Sprint", "Automatically sprints", Category.MISC);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        de.glutenfreierkeks.gfm_recode.client.modules.combat.WTap wTap =
                de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient.modules == null ? null :
                        de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.WTap.class);
        if (wTap != null && wTap.isEnabled() && wTap.isSuppressingSprint()) {
            mc.player.setSprinting(false);
            return;
        }

        switch (mode.getValue()) {
            case OMNI:
                mc.player.setSprinting(true);
                break;
            case FORWARD:
                if (mc.player.forwardSpeed > 0) {
                    mc.player.setSprinting(true);
                }
                break;
        }
    }
}
