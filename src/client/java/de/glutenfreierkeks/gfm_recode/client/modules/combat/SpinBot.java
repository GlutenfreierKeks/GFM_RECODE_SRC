package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class SpinBot extends Module {

    private final DoubleSliderSetting speed = register(new DoubleSliderSetting("Speed", "Degrees per tick", 45.0, 1.0, 180.0, 1));
    private final DoubleSliderSetting pitch = register(new DoubleSliderSetting("Pitch", "Look down angle", 90.0, -90.0, 90.0, 1));

    private float currentSpinYaw = 0;

    public SpinBot() {
        super("SpinBot", "Spins your model on the server while keeping your view stable", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        currentSpinYaw = (currentSpinYaw + speed.getValue().floatValue()) % 360;
    }

    public boolean isSilentActive() {
        return isEnabled();
    }

    public float getSpinYaw() {
        return currentSpinYaw;
    }

    public float getSpinPitch() {
        return pitch.getValue().floatValue();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
