package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.joml.Matrix4f;

public class ItemSteal extends Module {

    private final IntSliderSetting cooldown = register(new IntSliderSetting("Cooldown", "Delay in ms", 250, 0, 2000));
    private final ItemSetting targetItems = register(new ItemSetting("Steal Items", "Items to move into inventory", Items.WHEAT));

    private long lastActionTime = 0L;

    public ItemSteal() {
        super("ItemSteal", "Takes items from GUI container into inventory", Category.FARM);
    }

    @Override
    public void onEnable() {
        lastActionTime = 0;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen == null) return;
        if (System.currentTimeMillis() - lastActionTime < cooldown.getValue()) return;

        Screen screen = mc.currentScreen;
        if (!(screen instanceof HandledScreen<?> handledScreen)) return;
        if (handledScreen.getScreenHandler() == null) return;

        var handler = handledScreen.getScreenHandler();

        // Suche nach dem gemerkten Item im Container (obere Slots)
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.slots.get(i).getStack();

            if (!stack.isEmpty() && targetItems.contains(stack.getItem()) && !isPlayerInventorySlot(handler, i)) {
                // Shift-Click um Item aus Container in Spieler-Inventar zu verschieben
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                lastActionTime = System.currentTimeMillis();
                return; // Nur einen pro Tick verschieben
            }
        }
    }

    private boolean isPlayerInventorySlot(ScreenHandler handler, int slotIndex) {
        // Die ersten Slots gehören normalerweise zum Container (Chest etc.)
        // Die unteren Slots (ab einer bestimmten Anzahl) gehören zum Spieler-Inventar
        // Typischerweise: Chest hat 27 Slots (0-26), danach kommt Spieler-Inventar (27+)

        int totalSlots = handler.slots.size();
        int playerInventorySize = 36; // 27 Inventar + 9 Hotbar

        // Slots vom Ende her zählen (letzte 36 Slots sind Spieler-Inventar)
        return slotIndex >= (totalSlots - playerInventorySize);
    }
}
