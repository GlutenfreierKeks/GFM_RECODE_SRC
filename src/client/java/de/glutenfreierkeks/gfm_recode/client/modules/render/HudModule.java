package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import org.joml.Matrix4f;

public class HudModule extends Module {

    public enum ThemePreset {
        APPLE,
        AMOLED,
        VALORANT,
        CYBERPUNK,
        VANILLA_PLUS,
        GLASS_NEON,
        KRYPTON
    }

    public final BoolSetting showWatermark = register(new BoolSetting("Watermark", "Show client name", true));
    public final BoolSetting showArrayList = register(new BoolSetting("ArrayList", "Show enabled modules", true));
    public final BoolSetting showCoords    = register(new BoolSetting("Coordinates", "Show current position", true));
    public final BoolSetting showFps       = register(new BoolSetting("FPS", "Show frames per second", true));
    public final BoolSetting showSpeed     = register(new BoolSetting("Speed", "Show blocks per second", true));
    public final BoolSetting showPing      = register(new BoolSetting("Ping", "Show server latency", true));
    public final BoolSetting showServer    = register(new BoolSetting("Server", "Show current server IP", true));
    public final BoolSetting showBiome     = register(new BoolSetting("Biome", "Show current biome", true));
    public final BoolSetting showDir       = register(new BoolSetting("Direction", "Show facing direction", true));
    public final BoolSetting showTime      = register(new BoolSetting("Time", "Show world time", true));
    public final BoolSetting showTPS       = register(new BoolSetting("TPS", "Show server TPS", true));
    public final BoolSetting showDurability= register(new BoolSetting("Durability", "Show item durability", true));
    public final BoolSetting showMemory    = register(new BoolSetting("Memory", "Show RAM usage", true));
    public final BoolSetting showHotbar    = register(new BoolSetting("Hotbar", "Show custom hotbar", true));
    public final BoolSetting showScoreboard= register(new BoolSetting("Scoreboard", "Show custom scoreboard", true));
    public final BoolSetting showEffects   = register(new BoolSetting("Effects", "Show potion effects panel", true));
    public final BoolSetting showVitals    = register(new BoolSetting("Vitals", "Show health, armor and food panel", true));
    public final BoolSetting showSpeedPanel= register(new BoolSetting("Speedometer", "Show the speedometer panel", true));
    public final BoolSetting showRadar     = register(new BoolSetting("Radar", "Show entity radar", true));
    public final BoolSetting showCombatPanel = register(new BoolSetting("Combat Panel", "Show combat stats and nearby pressure", true));
    public final BoolSetting showInventoryPanel = register(new BoolSetting("Inventory Panel", "Show important item counts", true));
    public final BoolSetting showSessionPanel = register(new BoolSetting("Session Panel", "Show session and world meta", true));
    public final EnumSetting<ThemePreset> theme = register(new EnumSetting<>("Theme", "Theme preset for HUD and ClickGUI", ThemePreset.KRYPTON));

    // Additional settings like in old UI
    public final ItemSetting itemPicker = register(new ItemSetting("Item Picker", "Select items to monitor", Items.DIAMOND_SWORD));
    public final InventorySlotSetting slotPicker = register(new InventorySlotSetting("Slot Picker", "Select inventory slots to monitor", InventorySlotSetting.Layout.PLAYER_INVENTORY, true));

    public HudModule() {
        super("HUD", "Controls the in-game overlay", Category.RENDER);
        this.setEnabled(true);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        // HUD is rendered via HTML in HudBrowserRenderer
    }
}
