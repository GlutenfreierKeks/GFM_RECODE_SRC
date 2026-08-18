package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class ViewModel extends Module {

    private final DoubleSliderSetting swingSpeed = register(new DoubleSliderSetting("Swing Speed", "Animation speed multiplier", 1.35, 0.25, 3.5));
    private final DoubleSliderSetting mainSize = register(new DoubleSliderSetting("Main Size", "Scale for the main hand item", 1.0, 0.5, 1.8));
    private final DoubleSliderSetting offSize = register(new DoubleSliderSetting("Off Size", "Scale for the offhand item", 1.0, 0.5, 1.8));
    private final DoubleSliderSetting mainX = register(new DoubleSliderSetting("Main X", "Main hand horizontal offset", 0.0, -1.0, 1.0));
    private final DoubleSliderSetting mainY = register(new DoubleSliderSetting("Main Y", "Main hand vertical offset", 0.0, -1.0, 1.0));
    private final DoubleSliderSetting mainZ = register(new DoubleSliderSetting("Main Z", "Main hand depth offset", 0.0, -1.0, 1.0));
    private final DoubleSliderSetting offX = register(new DoubleSliderSetting("Off X", "Offhand horizontal offset", 0.0, -1.0, 1.0));
    private final DoubleSliderSetting offY = register(new DoubleSliderSetting("Off Y", "Offhand vertical offset", 0.0, -1.0, 1.0));
    private final DoubleSliderSetting offZ = register(new DoubleSliderSetting("Off Z", "Offhand depth offset", 0.0, -1.0, 1.0));
    private final BoolSetting itemOutline = register(new BoolSetting("Item Outline", "Adds a subtle second pass to the held item", false));
    private final BoolSetting handGlow = register(new BoolSetting("Hand Glow", "Makes hands and items glow in first person", false));

    public ViewModel() {
        super("ViewModel", "Customize hand offsets, size and swing feel", Category.RENDER);
        setEnabled(true);
    }

    public float getSwingSpeed() {
        return swingSpeed.getValue().floatValue();
    }

    public float getScale(boolean mainHand) {
        return mainHand ? mainSize.getValue().floatValue() : offSize.getValue().floatValue();
    }

    public float getOffsetX(boolean mainHand) {
        return (mainHand ? mainX : offX).getValue().floatValue();
    }

    public float getOffsetY(boolean mainHand) {
        return (mainHand ? mainY : offY).getValue().floatValue();
    }

    public float getOffsetZ(boolean mainHand) {
        return (mainHand ? mainZ : offZ).getValue().floatValue();
    }

    public boolean isItemOutlineEnabled() {
        return itemOutline.getValue();
    }

    public boolean isHandGlowEnabled() {
        return handGlow.getValue();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }
}
