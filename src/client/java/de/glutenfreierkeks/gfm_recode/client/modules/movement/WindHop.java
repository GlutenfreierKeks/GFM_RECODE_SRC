package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class WindHop extends Module {

    private final BoolSetting silent = register(new BoolSetting("Silent", "Hide the rotation from yourself", true));
    private final DoubleSliderSetting waitTicks = register(new DoubleSliderSetting("Wait Ticks", "How many ticks to wait while looking down", 1, 0, 5, 0));
    private final BoolSetting auto   = register(new BoolSetting("Auto Disable", "Disable the module after one jump", true));
    
    private boolean active = false;
    private float originalPitch;
    private int step = 0;
    private int waitCounter = 0;
    private int oldSlot = -1;

    public WindHop() {
        super("WindHop", "Automated Wind Charge jump when on ground", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        switch (step) {
            case 0: // Idle, looking for ground
                if (mc.player.isOnGround()) {
                    int slot = findWindCharge();
                    if (slot != -1) {
                        oldSlot = mc.player.getInventory().getSelectedSlot();
                        originalPitch = mc.player.getPitch();
                        
                        // Switch & Rotate
                        mc.player.getInventory().setSelectedSlot(slot);
                        mc.player.setPitch(90);
                        active = true;
                        
                        waitCounter = waitTicks.getValue().intValue();
                        step = 1;
                    }
                }
                break;

            case 1: // Waiting phase
                if (waitCounter > 0) {
                    waitCounter--;
                } else {
                    step = 2;
                }
                break;

            case 2: // Throw charge
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                step = 3;
                break;

            case 3: // Restoration
                if (oldSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                }
                mc.player.setPitch(originalPitch);
                active = false;
                step = 0;
                
                if (auto.getValue()) {
                    setEnabled(false);
                }
                break;
        }
    }

    private int findWindCharge() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.WIND_CHARGE)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isSilentActive() { 
        return active && isEnabled() && silent.getValue(); 
    }
    
    public float getOriginalYaw() { return mc.player != null ? mc.player.getYaw() : 0; }
    public float getOriginalPitch() { return originalPitch; }

    @Override
    protected void onDisable() {
        if (active && mc.player != null) {
            mc.player.setPitch(originalPitch);
            if (oldSlot != -1) mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        active = false;
        step = 0;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
