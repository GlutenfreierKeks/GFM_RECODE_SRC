package de.glutenfreierkeks.gfm_recode.client.modules.world;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import org.joml.Matrix4f;

/**
 * Breaks all blocks in a configurable radius around the player.
 */
public class Nuker extends Module {

    public final ItemSetting targetBlock = register(
        new ItemSetting("Target Block", "The block type to break (Any = all blocks)",
            Items.OBSIDIAN,
            Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL,
            Items.OBSIDIAN, Items.NETHERRACK, Items.COBBLESTONE,
            Items.OAK_LOG, Items.OAK_PLANKS
        )
    );

    public final IntSliderSetting radius = register(
        new IntSliderSetting("Radius", "Break radius in blocks", 3, 1, 6)
    );

    public final IntSliderSetting delay = register(
        new IntSliderSetting("Delay", "Ticks between breaks", 0, 0, 10)
    );

    public final BoolSetting onlyExposed = register(
        new BoolSetting("Only Exposed", "Only break blocks with exposed faces", true)
    );

    public final BoolSetting legit = register(
        new BoolSetting("Legit Mode", "Simulate normal break speed", false)
    );

    public final EnumSetting<Shape> shape = register(
        new EnumSetting<>("Shape", "Break shape", Shape.SPHERE)
    );

    public enum Shape {
        SPHERE,
        CUBE,
        FLAT
    }

    public Nuker() {
        super("Nuker", "Breaks blocks around you", Category.WORLD);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        // Add nuker logic here using radius.getValue(), targetBlock.getValue(), shape.getValue()
    }
}
