package de.glutenfreierkeks.gfm_recode.client.modules.world;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class HypixelPumpkin extends Module {

    private State currentState = State.LOOK_1;
    private int timer = 0;
    private int stuckTimer = 0;
    private float targetPitchVariation = 0;

    private enum State {
        LOOK_1,  // yaw -150, pitch 30
        MINE_1,  // hold attack, hold A (left)
        MOVE_W1, // hold W for 2 sec
        LOOK_2,  // yaw 30, pitch 35
        MINE_2,  // hold attack, hold A (left)
        LOOK_3,  // yaw 135, pitch 20
        MOVE_W2  // hold W for 1 sec
    }

    public HypixelPumpkin() {
        super("HypixelPumpkin", "Automates pumpkin farming on Hypixel.", Category.WORLD);
    }

    @Override
    protected void onEnable() {
        currentState = State.LOOK_1;
        timer = 0;
        targetPitchVariation = (float) (Math.random() * 2.0 - 1.0);
    }

    @Override
    protected void onDisable() {
        resetKeys();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        switch (currentState) {
            case LOOK_1:
                rotateTo(-150f, 30f + targetPitchVariation);
                if (isRotated(-150f, 30f + targetPitchVariation)) {
                    currentState = State.MINE_1;
                    stuckTimer = 20; // Wait 1 sec before checking for stuck
                }
                break;

            case MINE_1:
                mc.options.attackKey.setPressed(true);
                mc.options.leftKey.setPressed(true);
                
                if (stuckTimer > 0) stuckTimer--;
                
                if (stuckTimer <= 0 && isNotMoving()) {
                    resetKeys();
                    timer = 40; // 2 sec
                    currentState = State.MOVE_W1;
                }
                break;

            case MOVE_W1:
                mc.options.leftKey.setPressed(true);
                if (--timer <= 0) {
                    resetKeys();
                    targetPitchVariation = (float) (Math.random() * 2.0 - 1.0);
                    currentState = State.LOOK_2;
                }
                break;

            case LOOK_2:
                rotateTo(30f, 35f + targetPitchVariation);
                if (isRotated(30f, 35f + targetPitchVariation)) {
                    currentState = State.MINE_2;
                    stuckTimer = 20;
                }
                break;

            case MINE_2:
                mc.options.attackKey.setPressed(true);
                mc.options.leftKey.setPressed(true);
                
                if (stuckTimer > 0) stuckTimer--;
                
                if (stuckTimer <= 0 && isNotMoving()) {
                    resetKeys();
                    currentState = State.LOOK_3;
                }
                break;

            case LOOK_3:
                rotateTo(135f, 20f);
                if (isRotated(135f, 20f)) {
                    timer = 20; // 1 sec
                    currentState = State.MOVE_W2;
                }
                break;

            case MOVE_W2:
                mc.options.leftKey.setPressed(true);
                if (--timer <= 0) {
                    resetKeys();
                    targetPitchVariation = (float) (Math.random() * 2.0 - 1.0);
                    currentState = State.LOOK_1;
                }
                break;
        }
    }

    private void rotateTo(float yaw, float pitch) {
        RotationUtil.rotateSmooth(yaw, pitch, 5.0f);
    }

    private boolean isRotated(float yaw, float pitch) {
        float yawDiff = Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(pitch - mc.player.getPitch());
        return yawDiff < 1f && pitchDiff < 1f;
    }

    private boolean isNotMoving() {
        return mc.player.getVelocity().horizontalLengthSquared() < 0.0001;
    }

    private void resetKeys() {
        mc.options.attackKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
