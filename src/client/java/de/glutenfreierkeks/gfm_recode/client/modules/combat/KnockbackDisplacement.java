package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

public class KnockbackDisplacement extends Module {

    public enum Side {
        LEFT,
        RIGHT,
        RANDOM
    }

    private final EnumSetting<Side> side = register(
        new EnumSetting<>("Side", "Direction of the silent displacement look", Side.LEFT)
    );

    private final DoubleSliderSetting angle = register(
        new DoubleSliderSetting("Angle", "Silent side look angle", 90.0, 10.0, 180.0, 1)
    );

    private final BoolSetting onlyPlayers = register(
        new BoolSetting("Only Players", "Only run on player targets", true)
    );

    private final de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting chance = register(
        new de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting("Chance", "Probability of the displacement triggering", 100, 0, 100)
    );

    private boolean active;
    private float originalYaw;
    private float originalPitch;
    
    private Entity queuedTarget;
    private int tickCounter = 0;
    private boolean isProgrammaticAttack = false;

    public KnockbackDisplacement() {
        super("Knockback Displacement", "Silently looks sideways for one tick before attacking", Category.PLAYER);
        this.macroAllowed = false;
    }

    /**
     * Called when the user attempts an attack.
     * @return true if the attack should be canceled (to handle it in our own sequence)
     */
    public boolean onAttack(Entity target) {
        if (mc.player == null || target == null) return false;
        if (onlyPlayers.getValue() && !(target instanceof PlayerEntity)) return false;
        if (isProgrammaticAttack) return false; // Don't intercept ourselves

        // Chance check
        if (chance.getValue() < 100) {
            if (ThreadLocalRandom.current().nextInt(100) >= chance.getValue()) {
                return false; // Roll failed, do normal attack
            }
        }

        // Start the sequence
        originalYaw = mc.player.getYaw();
        originalPitch = mc.player.getPitch();
        queuedTarget = target;
        tickCounter = 2; // Increased to 2 to compensate for handleInputEvents timing
        active = true;

        applySideRotation();
        return true; // Cancel original attack
    }

    private void applySideRotation() {
        if (mc.player == null) return;
        
        float signedAngle = angle.getValue().floatValue();
        Side selected = side.getValue();
        if (selected == Side.RANDOM) {
            selected = ThreadLocalRandom.current().nextBoolean() ? Side.LEFT : Side.RIGHT;
        }
        if (selected == Side.LEFT) signedAngle = -signedAngle;

        mc.player.setYaw(originalYaw + signedAngle);
    }

    @Override
    public void onTick() {
        if (!active || mc.player == null) return;

        if (tickCounter > 0) {
            tickCounter--;
            if (tickCounter <= 0) {
                // Time to attack!
                mc.player.setYaw(originalYaw);
                mc.player.setPitch(originalPitch);
                
                if (queuedTarget != null && queuedTarget.isAlive()) {
                    isProgrammaticAttack = true;
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackEntity(mc.player, queuedTarget);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    isProgrammaticAttack = false;
                }
                
                active = false;
                queuedTarget = null;
            }
        }
    }

    public boolean isSilentActive() {
        return active;
    }

    public float getOriginalYaw() { return originalYaw; }
    public float getOriginalPitch() { return originalPitch; }

    @Override
    protected void onDisable() {
        if (active && mc.player != null) {
            mc.player.setYaw(originalYaw);
            active = false;
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
