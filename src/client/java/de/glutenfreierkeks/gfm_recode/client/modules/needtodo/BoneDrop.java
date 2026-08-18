package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.joml.Matrix4f;

public class BoneDrop extends Module {

    private final IntSliderSetting cooldown = register(new IntSliderSetting("Cooldown", "Delay between drops in ms", 250, 0, 2000));

    private long lastActionTime = 0L;

    public BoneDrop() {
        super("BoneDrop", "Drops bone from GUI", Category.FARM);
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
            if (stack.getItem() == Items.BONE) {
                // Knochen gefunden → Drop aus GUI
                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                lastActionTime = System.currentTimeMillis();
            }
        }
    }
}
