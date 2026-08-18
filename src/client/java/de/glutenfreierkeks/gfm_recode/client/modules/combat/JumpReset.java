package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class JumpReset extends Module {

    private final DoubleSliderSetting chance = register(new DoubleSliderSetting("Chance", "Chance to jump on hit (%)", 100.0, 1.0, 100.0));
    private final IntSliderSetting cooldownTicks = register(new IntSliderSetting("Cooldown", "Ticks between jump resets", 2, 0, 20));
    private final BoolSetting requireGround = register(new BoolSetting("RequireGround", "Only jump when grounded", true));
    private final BoolSetting onlyStill = register(new BoolSetting("OnlyStill", "Only jump reset while standing mostly still", true));
    private final BoolSetting noStrafe = register(new BoolSetting("NoStrafe", "Skip jump reset while strafing or moving diagonally", true));
    private final BoolSetting noSprint = register(new BoolSetting("NoSprint", "Skip jump reset while sprinting", true));
    private final DoubleSliderSetting maxHorizontalSpeed = register(new DoubleSliderSetting("MaxHorizSpeed", "Maximum horizontal speed for a safe jump reset", 0.07, 0.0, 0.35));

    private int lastHurtTime = 0;
    private int cooldown = 0;

    public JumpReset() {
        super("JumpReset", "Immediately jumps on fresh damage", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
        }

        int currentHurtTime = mc.player.hurtTime;
        boolean freshDamage = currentHurtTime > lastHurtTime;
        if (currentHurtTime == 10 && freshDamage && cooldown <= 0 && Math.random() * 100.0 <= chance.getValue() && canJumpNow()) {
            mc.player.jump();
            cooldown = cooldownTicks.getValue();
        }

        lastHurtTime = currentHurtTime;
    }

    private boolean canJumpNow() {
        if (mc.player == null) {
            return false;
        }
        if (mc.player.isTouchingWater() || mc.player.isClimbing() || mc.player.isSneaking()) {
            return false;
        }
        if (onlyStill.getValue()) {
            double horizontalSpeed = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);
            boolean pressingMove = Math.abs(mc.player.input.getMovementInput().x) > 0.0F || Math.abs(mc.player.input.getMovementInput().y) > 0.0F;
            if (pressingMove || horizontalSpeed > maxHorizontalSpeed.getValue()) {
                return false;
            }
        }
        if (noStrafe.getValue() && Math.abs(mc.player.input.getMovementInput().x) > 0.0F) {
            return false;
        }
        if (noSprint.getValue() && (mc.player.isSprinting() || mc.options.sprintKey.isPressed())) {
            return false;
        }
        if (!requireGround.getValue()) {
            return true;
        }
        return mc.player.isOnGround() && mc.player.getVelocity().y <= 0.0 && mc.player.fallDistance <= 0.0F;
    }

    @Override
    protected void onDisable() {
        lastHurtTime = 0;
        cooldown = 0;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }
}
