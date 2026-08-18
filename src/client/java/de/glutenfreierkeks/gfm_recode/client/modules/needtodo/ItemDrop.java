package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.joml.Matrix4f;

public class ItemDrop extends Module {

    private final IntSliderSetting cooldown = register(new IntSliderSetting("Cooldown", "Delay in ms", 250, 0, 2000));
    private final ItemSetting targetItems = register(new ItemSetting("Drop Items", "Items to drop from container", Items.WHEAT));

    private long lastActionTime = 0L;

    public ItemDrop() {
        super("ItemDrop", "Drops items from GUI that were held in hand", Category.FARM);
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

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty() && targetItems.contains(stack.getItem())) {
                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                lastActionTime = System.currentTimeMillis();
            }
        }
    }
}
