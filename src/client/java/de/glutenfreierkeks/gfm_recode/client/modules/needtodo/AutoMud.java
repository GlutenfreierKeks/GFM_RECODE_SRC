package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;

import java.util.Optional;
import java.util.Random;

public class AutoMud extends Module {

    private static final Random RANDOM = new Random();

    // ── Core ────────────────────────────────────────────────────────────────
    private final BoolSetting autoFill = register(
            new BoolSetting("AutoFillBottles", "Automatically fill empty bottles at water sources", true)
    );
    private final DoubleSliderSetting range = register(
            new DoubleSliderSetting("Range", "Interaction range", 4.5, 1.0, 6.0)
    );

    // ── Anti-Cheat ──────────────────────────────────────────────────────────
    private final BoolSetting rotateToTarget = register(
            new BoolSetting("RotateToTarget", "Rotate toward target block", true)
    );
    private final BoolSetting requireLookAngle = register(
            new BoolSetting("RequireLookAngle", "Only interact when looking at block", true)
    );
    private final DoubleSliderSetting lookAngleTolerance = register(
            new DoubleSliderSetting("LookTolerance", "Angle tolerance for looking at block", 30.0, 5.0, 90.0)
    );

    /**
     * Ticks to wait between each action.
     * Grim 1.21.5: keep at least 1.
     */
    private final IntSliderSetting interactDelay = register(
            new IntSliderSetting("InteractDelay", "Ticks to wait between actions", 2, 0, 10)
    );
    private final IntSliderSetting delayRandomness = register(
            new IntSliderSetting("DelayRandom", "Randomize interaction delay", 1, 0, 5)
    );

    // ── Debug ────────────────────────────────────────────────────────────────
    private final BoolSetting debugMode = register(
            new BoolSetting("DebugMode", "Show debug info in chat", false)
    );

    // ── Internal state ───────────────────────────────────────────────────────
    private State currentState = State.IDLE;
    private int tickCooldown   = 0;

    // Pending action – set when rotation starts, executed once aimed
    private BlockPos  pendingPos  = null;
    private PendingAction pendingAction = null;
    private int       pendingSlot  = -1;
    private int       pendingOldSlot = -1;

    private enum PendingAction { FILL, CONVERT }

    public AutoMud() {
        super("AutoMud", "Fill up Bottles and use them on dirt to make mud", Category.FARM);
    }

    @Override
    public void onEnable() {
        currentState    = State.IDLE;
        pendingPos      = null;
        pendingAction   = null;
        tickCooldown    = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Tick cooldown between actions
        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        // ── If we have a pending action, wait for rotation then execute ──────
        if (pendingPos != null && pendingAction != null) {

            // Still rotating – wait
            if (RotationUtil.isRotating()) return;

            // Rotation done but not aimed yet
            if (requireLookAngle.getValue() && !isLookingAt(pendingPos)) {
                if (rotateToTarget.getValue()) RotationUtil.rotateToBlock(pendingPos);
                return;
            }

            // Aimed – execute the queued action
            executePending();
            return;
        }

        // ── Decide next action ───────────────────────────────────────────────

        // Priority 1: Fill empty bottles
        if (autoFill.getValue()) {
            int emptySlot = findEmptyBottleInHotbar();
            if (emptySlot != -1) {
                BlockPos waterPos = findNearestWaterSource();
                if (waterPos != null) {
                    debug("Queuing fill at " + waterPos);
                    queueAction(waterPos, PendingAction.FILL, emptySlot);
                    return;
                }
            }
        }

        // Priority 2: Convert dirt to mud
        int waterBottleSlot = findWaterBottleInHotbar();
        if (waterBottleSlot != -1) {
            BlockPos dirtPos = findNearbyDirt();
            if (dirtPos != null) {
                debug("Queuing convert at " + dirtPos);
                queueAction(dirtPos, PendingAction.CONVERT, waterBottleSlot);
                return;
            }
        }

        currentState = State.IDLE;
    }

    // ── Action queueing ───────────────────────────────────────────────────────

    private void queueAction(BlockPos pos, PendingAction action, int slot) {
        pendingPos    = pos;
        pendingAction = action;
        pendingSlot   = slot;
        pendingOldSlot = mc.player.getInventory().getSelectedSlot();

        currentState = action == PendingAction.FILL ? State.FILLING : State.CONVERTING;

        // Switch hotbar slot immediately
        mc.player.getInventory().setSelectedSlot(slot);

        // Start rotation – will NOT interact until rotation is complete
        if (rotateToTarget.getValue()) {
            RotationUtil.rotateToBlock(pos);
        }
    }

    private void executePending() {
        if (mc.player == null || mc.interactionManager == null || pendingPos == null) {
            clearPending();
            return;
        }

        Vec3d hitVec = new Vec3d(
                pendingPos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2,
                pendingPos.getY() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2,
                pendingPos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2
        );
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pendingPos, false);

        // ONE interaction per tick
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        debug((pendingAction == PendingAction.FILL ? "Bottle filled" : "Dirt converted") + " at " + pendingPos);

        // Restore old slot
        mc.player.getInventory().setSelectedSlot(pendingOldSlot);

        // Apply cooldown with jitter
        int base   = interactDelay.getValue();
        int jitter = delayRandomness.getValue();
        tickCooldown = base + (jitter > 0 ? RANDOM.nextInt(jitter * 2 + 1) - jitter : 0);
        if (tickCooldown < 0) tickCooldown = 0;

        clearPending();
    }

    private void clearPending() {
        pendingPos    = null;
        pendingAction = null;
        pendingSlot   = -1;
        currentState  = State.IDLE;
    }

    // ── Look helpers ─────────────────────────────────────────────────────────

    private boolean isLookingAt(BlockPos pos) {
        if (mc.player == null) return false;
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        float[] needed = RotationUtil.getRotations(center);
        float dyaw   = Math.abs(wrapDeg(needed[0] - mc.player.getYaw()));
        float dpitch = Math.abs(wrapDeg(needed[1] - mc.player.getPitch()));
        double tol   = lookAngleTolerance.getValue();
        return dyaw <= tol && dpitch <= tol;
    }

    private static float wrapDeg(float d) {
        while (d >  180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    // ── Block / item finders ─────────────────────────────────────────────────

    private BlockPos findNearestWaterSource() {
        if (mc.player == null || mc.world == null) return null;
        PlayerEntity player = mc.player;
        BlockPos origin = player.getBlockPos();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        double maxDist = range.getValue();

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 4, 2, 4)) {
            try {
                FluidState fluid = mc.world.getFluidState(pos);
                if (fluid.getFluid() == Fluids.WATER && fluid.isStill()) {
                    double dist = player.getEyePos().distanceTo(pos.toCenterPos());
                    if (dist <= maxDist && dist < closestDist) {
                        closest = pos.toImmutable();
                        closestDist = dist;
                    }
                }
            } catch (Exception ignored) {}
        }
        return closest;
    }

    private BlockPos findNearbyDirt() {
        if (mc.player == null || mc.world == null) return null;
        PlayerEntity player = mc.player;
        BlockPos origin = player.getBlockPos();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        double maxDist = range.getValue();

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 4, 2, 4)) {
            try {
                if (mc.world.getBlockState(pos).getBlock() == Blocks.DIRT) {
                    double dist = player.getEyePos().distanceTo(pos.toCenterPos());
                    if (dist <= maxDist && dist < closestDist) {
                        closest = pos.toImmutable();
                        closestDist = dist;
                    }
                }
            } catch (Exception ignored) {}
        }
        return closest;
    }

    private int findEmptyBottleInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            try {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == Items.GLASS_BOTTLE) return i;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private int findWaterBottleInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            try {
                if (isStackWaterBottle(mc.player.getInventory().getStack(i))) return i;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private boolean isStackWaterBottle(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.POTION) return false;
        try {
            PotionContentsComponent comp = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (comp == null) return true;
            Optional<RegistryEntry<Potion>> potionOpt = comp.potion();
            if (potionOpt.isEmpty()) return true;
            return potionOpt.get().value() == Potions.WATER;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    private void debug(String message) {
        if (debugMode.getValue() && mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§7[AutoMud] " + message), false);
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public String getDisplayInfo() {
        return currentState.name();
    }

    private enum State { IDLE, FILLING, CONVERTING }
}
