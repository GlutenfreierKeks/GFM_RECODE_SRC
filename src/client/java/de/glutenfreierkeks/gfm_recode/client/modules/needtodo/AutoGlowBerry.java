package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class AutoGlowBerry extends Module {

    private static final Random RANDOM = new Random();

    // ── Core ────────────────────────────────────────────────────────────────
    private final DoubleSliderSetting range = register(new DoubleSliderSetting("Range", "Scan range", 4.0, 1.0, 6.0));
    private final BoolSetting requireBonemeal = register(new BoolSetting("RequireBonemeal", "Only work if holding bonemeal", true));

    // ── Anti-Cheat ──────────────────────────────────────────────────────────
    private final BoolSetting rotateToTarget = register(new BoolSetting("RotateToTarget", "Rotate toward target", true));
    private final BoolSetting requireLookAngle = register(new BoolSetting("RequireLookAngle", "Only interact when looking at block", true));
    private final DoubleSliderSetting lookAngleTolerance = register(new DoubleSliderSetting("LookTolerance", "Angle tolerance for looking at block", 30.0, 5.0, 90.0));
    private final IntSliderSetting interactDelay = register(new IntSliderSetting("InteractDelay", "Ticks to wait between interactions", 1, 0, 10));
    private final IntSliderSetting delayRandomness = register(new IntSliderSetting("DelayRandom", "Randomize interaction delay", 1, 0, 5));
    private final BoolSetting spreadClicks = register(new BoolSetting("SpreadClicks", "Cycle through different vine blocks", true));

    // ── Internal state ───────────────────────────────────────────────────────
    private int tickCooldown = 0;
    private int nextDelay    = 0;

    public AutoGlowBerry() {
        super("AutoGlowBerry", "Spams bonemeal on glow berries and harvests them.", Category.FARM);
        resetDelay();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Always advance smooth rotation
        RotationUtil.onTick();

        // Bonemeal check
        if (requireBonemeal.getValue() && mc.player.getMainHandStack().getItem() != Items.BONE_MEAL) return;

        // Delay between interactions
        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        // --- Scan nearby cave vines ---
        List<BlockPos> targets = new ArrayList<>();
        int r   = (int) Math.ceil(range.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        double rangeSq = range.getValue() * range.getValue();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() instanceof CaveVinesHeadBlock || state.getBlock() instanceof CaveVinesBodyBlock) {
                        targets.add(pos);
                    }
                }
            }
        }

        if (targets.isEmpty()) return;

        // Sort by distance (closest first)
        targets.sort(Comparator.comparingDouble(pos ->
                mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
        ));

        // Pick target (spread across ticks if enabled)
        int targetIndex = spreadClicks.getValue() ? (int)((System.currentTimeMillis() / 50) % targets.size()) : 0;
        BlockPos targetPos = targets.get(targetIndex);

        // Start rotation only if not already rotating and not already looking at target.
        if (rotateToTarget.getValue()) {
            if (!RotationUtil.isRotating() && !isLookingAt(targetPos)) {
                float[] rots = RotationUtil.getRotations(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
                mc.player.setYaw(rots[0]);
                mc.player.setPitch(rots[1]);
            }
        }

        // HARD BLOCK: never interact while rotation is in progress.
        if (RotationUtil.isRotating()) return;

        // Final angle sanity check
        if (requireLookAngle.getValue() && !isLookingAt(targetPos)) {
            return;
        }

        // GRIM: exactly ONE interactBlock per tick
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, createBlockHitResult(targetPos));
        mc.player.swingHand(Hand.MAIN_HAND);

        // Reset cooldown
        resetDelay();
        tickCooldown = nextDelay;
    }

    private boolean isLookingAt(BlockPos pos) {
        if (mc.player == null) return false;
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        float[] needed  = RotationUtil.getRotations(center);
        float   curYaw   = mc.player.getYaw();
        float   curPitch = mc.player.getPitch();

        float dyaw   = Math.abs(wrapDeg(needed[0] - curYaw));
        float dpitch = Math.abs(wrapDeg(needed[1] - curPitch));
        double tol   = lookAngleTolerance.getValue();
        return dyaw <= tol && dpitch <= tol;
    }

    private static float wrapDeg(float d) {
        while (d >  180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    private BlockHitResult createBlockHitResult(BlockPos pos) {
        Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2,
                pos.getY() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2,
                pos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2
        );
        return new BlockHitResult(hitVec, Direction.DOWN, pos, false);
    }

    private void resetDelay() {
        int base   = interactDelay.getValue();
        int jitter = delayRandomness.getValue();
        nextDelay  = base + (jitter > 0 ? RANDOM.nextInt(jitter * 2 + 1) - jitter : 0);
        if (nextDelay < 0) nextDelay = 0;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public String getDisplayInfo() {
        return "d:" + interactDelay.getValue() + " r:" + String.format("%.1f", range.getValue());
    }
}
