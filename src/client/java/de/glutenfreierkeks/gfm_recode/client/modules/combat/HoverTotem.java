package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.joml.Matrix4f;

public class HoverTotem extends Module {

    public HoverTotem() {
        super("HoverTotem", "Automatically moves totems to offhand when hovering over them in inventory", Category.PLAYER);
    }

    private long lastActionTime = 0;

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 250L) return;

        // Only works if a screen is open
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        // Find the slot currently being hovered by the mouse
        Slot hoveredSlot = ((de.glutenfreierkeks.gfm_recode.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
        
        if (hoveredSlot != null && hoveredSlot.hasStack()) {
            var stack = hoveredSlot.getStack();
            
            // Check if it's a totem
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                // Check if offhand already has a totem to avoid infinite spam/waste
                if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

                // Use the 'Swap' action (button 40 = offhand swap in Minecraft's clickSlot)
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, hoveredSlot.id, 40, SlotActionType.SWAP, mc.player);
                lastActionTime = now;
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
