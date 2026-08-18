package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.mixin.client.MinecraftClientAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class SpearSwap extends Module {

    private final KeybindSetting triggerKey = register(new KeybindSetting("Trigger Key", "Hold this key to perform the spear swap action.", GLFW.GLFW_KEY_V));
    private final IntSliderSetting waitTicks = register(new IntSliderSetting("Wait Ticks", "Ticks to wait after swapping before attacking.", 0, 0, 5));
    private final IntSliderSetting stayTicks = register(new IntSliderSetting("Stay Ticks", "Ticks to stay on the spear after attacking.", 2, 0, 10));
    
    private boolean wasPressed = false;
    private int state = 0; // 0 = idle, 1 = waiting to attack, 2 = staying on spear
    private int timer = -1;
    private int originalSlot = -1;
    private int cooldown = 0;

    public SpearSwap() {
        super("SpearSwap", "Swaps to spear, attacks, and stays for a few ticks", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (cooldown > 0) cooldown--;

        boolean isPressed = triggerKey.isPressed(mc.getWindow().getHandle());
        
        // State Machine
        switch (state) {
            case 0 -> { // Idle
                if (isPressed && !wasPressed && cooldown <= 0) {
                    startSequence();
                }
            }
            case 1 -> { // Waiting to attack (Wait Ticks)
                if (timer > 0) {
                    timer--;
                } else {
                    executeAttack();
                    state = 2;
                    timer = stayTicks.getValue();
                }
            }
            case 2 -> { // Staying on spear (Stay Ticks)
                if (timer > 0) {
                    timer--;
                } else {
                    returnToOriginal();
                    state = 0;
                    cooldown = 5; // Prevent immediate re-trigger to stay safe
                }
            }
        }
        
        wasPressed = isPressed;
    }

    private void startSequence() {
        int spearSlot = findSpear();
        if (spearSlot == -1) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (spearSlot == currentSlot) {
            // If already on spear, just attack and stay
            executeAttack();
            state = 2;
            timer = stayTicks.getValue();
            return;
        }

        originalSlot = currentSlot;
        
        // 1. Swap to Spear (setSelectedSlot handles internal state)
        mc.player.getInventory().setSelectedSlot(spearSlot);
        
        // 2. Determine next step
        if (waitTicks.getValue() > 0) {
            state = 1;
            timer = waitTicks.getValue();
        } else {
            // Immediate attack
            executeAttack();
            state = 2;
            timer = stayTicks.getValue();
        }
    }

    private void executeAttack() {
        if (mc instanceof MinecraftClientAccessor accessor) {
            boolean wasAttackPressed = mc.options.attackKey.isPressed();
            mc.options.attackKey.setPressed(true);
            accessor.invokeDoAttack();
            mc.options.attackKey.setPressed(wasAttackPressed);
        } else {
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    private void returnToOriginal() {
        if (originalSlot != -1) {
            // Double check if we are still on the spear before returning
            int spearSlot = findSpear();
            if (mc.player.getInventory().getSelectedSlot() == spearSlot) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
        }
        originalSlot = -1;
    }

    private int findSpear() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TRIDENT || 
                stack.getName().getString().toLowerCase().contains("spear")) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        if (state != 0) {
            returnToOriginal();
        }
        state = 0;
        timer = -1;
        cooldown = 0;
    }

    @Override
    public void render3D(org.joml.Matrix4f posMatrix, org.joml.Matrix4f projMatrix, net.minecraft.client.render.Camera camera, float tickDelta) {}
}
