package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;

public class ShieldBreaker extends Module {

    private int originalSlot = -1;
    private int axeSlot = -1;
    private int breakTicks = 0;
    private boolean breaking = false;

    public ShieldBreaker() {
        super("ShieldBreaker", "Automatically breaks enemy shields with axe", Category.PLAYER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        
        var triggerBot = de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient.modules.getByName("TriggerBot");
        if (triggerBot != null && triggerBot.isEnabled()) return;
        HitResult hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity target = entityHit.getEntity();
            
            if (target instanceof PlayerEntity player && target != mc.player) {
                // Check if player is blocking with shield
                if (player.isBlocking()) {
                    ItemStack offhand = player.getOffHandStack();
                    ItemStack mainhand = player.getMainHandStack();
                    
                    // Check if holding shield in either hand
                    if (offhand.getItem() == Items.SHIELD || mainhand.getItem() == Items.SHIELD) {
                        // Found shield blocker! Start breaking in 1 tick
                        breakShield(target);
                    }
                }
            }
        }
    }

    private void breakShield(Entity target) {
        // Find axe in hotbar (slots 0-8)
        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                axeSlot = i;
                break;
            }
        }
        
        // No axe found, abort
        if (axeSlot == -1) return;
        
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        if (axeSlot == originalSlot) {
            // Already holding it, just attack
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }

        // 1. Swap to axe
        mc.player.getInventory().setSelectedSlot(axeSlot);
        
        // 2. Attack!
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // 3. Swap back immediately
        mc.player.getInventory().setSelectedSlot(originalSlot);
    }

    @Override
    public void onEnable() {
        breaking = false;
        originalSlot = -1;
        axeSlot = -1;
        breakTicks = 0;
    }

    @Override
    public void onDisable() {
        // If disabled mid-break, restore original slot
        if (breaking && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        breaking = false;
    }
}
