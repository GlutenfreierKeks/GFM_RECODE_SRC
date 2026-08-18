package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private static boolean rotating;
    private static float targetYaw;
    private static float targetPitch;
    private static boolean hasTarget;

    private RotationUtil() {
    }

    public static void onTick() {
        rotating = false;
        hasTarget = false;
    }

    public static boolean isRotating() {
        return rotating;
    }

    public static void rotateToBlock(BlockPos pos) {
        rotateToPos(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    public static void rotateToBlock(BlockPos pos, boolean instant) {
        if (instant) {
            rotateToPosInstant(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        } else {
            rotateToBlock(pos);
        }
    }

    public static void rotateToEntity(Entity entity) {
        rotateToPos(entity.getEyePos());
    }

    public static void rotateToPos(Vec3d target) {
        rotateToPos(target, 10.0f); // Default speed factor
    }

    public static void rotateToPos(Vec3d target, float speedFactor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float[] rot = getRotations(target);
        rotateSmooth(rot[0], rot[1], speedFactor * 3.0f);
        hasTarget = true;
    }

    public static void rotateSmooth(float targetYawValue, float targetPitchValue, float speed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float yawDiff = MathHelper.wrapDegrees(targetYawValue - mc.player.getYaw());
        float pitchDiff = targetPitchValue - mc.player.getPitch();

        float stepYaw = MathHelper.clamp(yawDiff, -speed, speed);
        float stepPitch = MathHelper.clamp(pitchDiff, -speed, speed);

        mc.player.setYaw(mc.player.getYaw() + stepYaw);
        mc.player.setPitch(mc.player.getPitch() + stepPitch);
        
        targetYaw = targetYawValue;
        targetPitch = targetPitchValue;
        rotating = true;
    }

    public static void rotateToPosInstant(Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float[] rot = getRotations(target);
        mc.player.setYaw(rot[0]);
        mc.player.setPitch(rot[1]);
        rotating = true;
    }

    public static float[] getRotations(Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[]{0f, 0f};

        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));
        
        return new float[]{
                mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()),
                mc.player.getPitch() + MathHelper.wrapDegrees(pitch - mc.player.getPitch())
        };
    }
}
