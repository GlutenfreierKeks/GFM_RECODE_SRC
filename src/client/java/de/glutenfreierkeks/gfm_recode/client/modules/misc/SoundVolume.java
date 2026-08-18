package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class SoundVolume extends Module {

    public final DoubleSliderSetting volume = register(new DoubleSliderSetting("Volume", "Sound volume multiplier", 0.3, 0.0, 1.0));

    public SoundVolume() {
        super("SoundVolume", "Control clickgui sound volume", Category.MISC);
    }

    public static Double getVolume() {
        SoundVolume module = Gfm_recodeClient.modules.getModuleByClass(SoundVolume.class);
        if (module == null || !module.isEnabled()) return 1.0;
        return module.volume.getValue();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
