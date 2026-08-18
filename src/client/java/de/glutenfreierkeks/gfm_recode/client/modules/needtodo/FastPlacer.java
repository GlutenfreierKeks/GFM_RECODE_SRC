package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

public class FastPlacer extends Module {

    private final BoolSetting showFeedback = register(new BoolSetting("ShowFeedback", "Show feedback in chat", false));

    // Items/Blocks die vom FastPlace ausgeschlossen sind
    private static final Set<Item> EXCLUDED_ITEMS = new HashSet<>();
    private static final Set<Block> EXCLUDED_BLOCKS = new HashSet<>();

    static {
        // Items die NICHT fast-placed werden sollen
        EXCLUDED_ITEMS.add(Items.OBSIDIAN);
        EXCLUDED_ITEMS.add(Items.END_CRYSTAL);
        EXCLUDED_ITEMS.add(Items.GLOWSTONE);
        EXCLUDED_ITEMS.add(Items.EXPERIENCE_BOTTLE);
        EXCLUDED_ITEMS.add(Items.RESPAWN_ANCHOR);
        EXCLUDED_ITEMS.add(Items.FIREWORK_ROCKET);
        EXCLUDED_ITEMS.add(Items.SHIELD);
        EXCLUDED_ITEMS.add(Items.ENDER_PEARL);
        EXCLUDED_ITEMS.add(Items.ENDER_CHEST);

        // Blocks die NICHT fast-placed werden sollen
        EXCLUDED_BLOCKS.add(Blocks.OBSIDIAN);
        EXCLUDED_BLOCKS.add(Blocks.GLOWSTONE);
        EXCLUDED_BLOCKS.add(Blocks.RESPAWN_ANCHOR);
        EXCLUDED_BLOCKS.add(Blocks.ENDER_CHEST);
    }

    private final de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting delay = register(new de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting("Placement Delay", "Ticks between placements", 0, 0, 4));
    private final BoolSetting includePvP = register(new BoolSetting("IncludePvP", "Include Obsidian/Crystals in FastPlace", true));

    public FastPlacer() {
        super("FastPlacer", "Makes you place Faster (no pvp items)", Category.PLAYER);
    }
    
    public int getDelay() {
        return delay.getValue();
    }

    /**
     * Prüft ob das aktuelle Item/Block vom FastPlace ausgeschlossen werden soll
     * Diese Methode wird vom Mixin aufgerufen
     */
    public boolean shouldBypassCooldown() {
        if (!isEnabled()) {
            return false;
        }

        if (mc.player == null) {
            return false;
        }

        // Prüfe das Item in der Main Hand
        Item mainHandItem = mc.player.getMainHandStack().getItem();
        
        if (!includePvP.getValue()) {
            if (EXCLUDED_ITEMS.contains(mainHandItem)) {
                return false; // Nicht bypassen, normaler Cooldown
            }

            // Prüfe ob das Item ein Block ist und ob dieser Block ausgeschlossen ist
            if (mainHandItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (EXCLUDED_BLOCKS.contains(block)) {
                    return false; // Nicht bypassen, normaler Cooldown
                }
            }
        }

        return true; // Cooldown bypassen
    }

    /**
     * Hilfsmethode: Gibt zurück ob ein bestimmtes Item ausgeschlossen ist
     */
    public static boolean isItemExcluded(Item item) {
        return EXCLUDED_ITEMS.contains(item);
    }

    /**
     * Hilfsmethode: Gibt zurück ob ein bestimmter Block ausgeschlossen ist
     */
    public static boolean isBlockExcluded(Block block) {
        return EXCLUDED_BLOCKS.contains(block);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public String getDisplayInfo() {
        if (mc.player != null) {
            Item currentItem = mc.player.getMainHandStack().getItem();
            if (EXCLUDED_ITEMS.contains(currentItem)) {
                return "Excluded";
            }
            if (currentItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (EXCLUDED_BLOCKS.contains(block)) {
                    return "Excluded";
                }
            }
            return "Active";
        }
        return "Ready";
    }
}
