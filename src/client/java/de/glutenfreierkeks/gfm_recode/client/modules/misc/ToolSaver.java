package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;

public class ToolSaver extends Module {

    private final IntSliderSetting threshold = register(new IntSliderSetting("Threshold", "Minimum durability before saving", 10, 1, 50));

    public ToolSaver() {
        super("ToolSaver", "Prevents tools from breaking", Category.MISC);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    public boolean shouldSave() {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty() || !held.isDamageable()) return false;
        if (!isToolItem(held.getItem())) return false;
        int remaining = held.getMaxDamage() - held.getDamage();
        return remaining <= threshold.getValue();
    }

    private boolean isToolItem(net.minecraft.item.Item item) {
        String name = item.getClass().getSimpleName();
        if (name.contains("Tool") || name.contains("Sword") ||
            name.contains("Weapon") || name.contains("Pickaxe") ||
            name.contains("Shovel") || name.contains("Axe") ||
            name.contains("Hoe") || name.contains("Shears") ||
            name.contains("Mace")) return true;
        String path = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        return path.endsWith("_pickaxe") || path.endsWith("_axe") ||
               path.endsWith("_shovel") || path.endsWith("_hoe") ||
               path.endsWith("_sword");
    }
}
