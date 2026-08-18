package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

public class DoubleAnchor extends Module {

    private final DoubleSliderSetting slotDelay   = new DoubleSliderSetting("SlotDelay",  "Warten nach Slot-Switch (ms)",   0.0, 0.0, 500.0);
    private final DoubleSliderSetting placeDelay  = new DoubleSliderSetting("PlaceDelay", "Warten nach Block-Place (ms)",   0.0, 0.0, 500.0);
    private final DoubleSliderSetting clickDelay  = new DoubleSliderSetting("ClickDelay", "Warten nach Rechtsklick (ms)",   0.0, 0.0, 500.0);
    private final DoubleSliderSetting randomRange = new DoubleSliderSetting("RandomRange","Zufalls-Varianz ± (ms)",          0.0,  0.0, 100.0);
    private final KeybindSetting      triggerKey  = new KeybindSetting("Trigger", "keybind");

    private enum MacroState {
        IDLE,
        PLACE_1, SWITCH_GLOW_1, CHARGE_1, SWITCH_TOTEM_1,
        EXPLODE_1, PLACE_2,
        SWITCH_GLOW_2, CHARGE_2, SWITCH_TOTEM_2, EXPLODE_2,
        DONE
    }

    private MacroState state             = MacroState.IDLE;
    private int        savedSlot         = -1;
    private BlockPos   anchorPos         = null;
    private long       actionCooldownUntil = 0;

    private BlockPos  lockNeighbor = null;
    private Direction lockSide     = null;
    private Vec3d     lockHitVec   = null;

    public DoubleAnchor() {
        super("DoubleAnchor", "Double Respawn Anchor Macro (Explodes and replaces immediately)", Category.PLAYER);
        this.macroAllowed = false;
        register(slotDelay); register(placeDelay); register(clickDelay); register(randomRange);
        register(triggerKey);
    }

    private long randomMs(double base) {
        double r = randomRange.getValue();
        return (long) ThreadLocalRandom.current().nextDouble(Math.max(0, base - r), base + r + 1);
    }

    private boolean onCooldown() {
        return System.currentTimeMillis() < actionCooldownUntil;
    }

    private void cooldownSlot()  { actionCooldownUntil = System.currentTimeMillis() + randomMs(slotDelay.getValue()); }
    private void cooldownPlace() { actionCooldownUntil = System.currentTimeMillis() + randomMs(placeDelay.getValue()); }
    private void cooldownClick() { actionCooldownUntil = System.currentTimeMillis() + randomMs(clickDelay.getValue()); }

    private void resetMacro() {
        if (savedSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
        }
        state            = MacroState.IDLE;
        savedSlot        = -1;
        anchorPos        = null;
        lockNeighbor     = null;
        lockSide         = null;
        lockHitVec       = null;
    }

    @Override
    public void onEnable() { resetMacro(); }

    @Override
    public void onDisable() { resetMacro(); }

    private boolean isTriggerPressed() {
        return triggerKey.isPressed(mc.getWindow().getHandle());
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (state == MacroState.IDLE) {
            if (isTriggerPressed()) {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
                var block = mc.world.getBlockState(bhr.getBlockPos()).getBlock();
                if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                    anchorPos = bhr.getBlockPos();
                } else {
                    anchorPos = bhr.getBlockPos().offset(bhr.getSide());
                }
                var targetState = mc.world.getBlockState(anchorPos);
                if (!targetState.isAir() && targetState.getBlock() != Blocks.FIRE && targetState.getBlock() != Blocks.SOUL_FIRE && targetState.getBlock() != Blocks.RESPAWN_ANCHOR) {
                    return;
                }
                savedSlot = mc.player.getInventory().getSelectedSlot();
                state = MacroState.PLACE_1;
            } else {
                return;
            }
        }

        // Global look check to prevent unintended placement/explosions
        if (state != MacroState.IDLE && anchorPos != null) {
            boolean lookingAtTarget = false;
            if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                BlockPos cp = bhr.getBlockPos();
                if (cp.equals(anchorPos)) lookingAtTarget = true;
                else {
                    for (Direction d : Direction.values()) {
                        if (cp.equals(anchorPos.offset(d))) { lookingAtTarget = true; break; }
                    }
                }
            }
            if (!lookingAtTarget) { resetMacro(); return; }
        }

        if (onCooldown()) return;

        switch (state) {
            case PLACE_1 -> {
                int slot = findInHotbar(Items.RESPAWN_ANCHOR);
                if (slot == -1) { resetMacro(); return; }
                mc.player.getInventory().setSelectedSlot(slot);
                if (doPlace(anchorPos)) {
                    cooldownPlace();
                    state = MacroState.SWITCH_GLOW_1;
                }
            }
            case SWITCH_GLOW_1 -> {
                int slot = findInHotbar(Items.GLOWSTONE);
                if (slot == -1) { resetMacro(); return; }
                mc.player.getInventory().setSelectedSlot(slot);
                cooldownSlot();
                state = MacroState.CHARGE_1;
            }
            case CHARGE_1 -> {
                if (mc.player.getMainHandStack().getItem() != Items.GLOWSTONE) return;
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(anchorPos)) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();
                state = MacroState.SWITCH_TOTEM_1;
            }
            case SWITCH_TOTEM_1 -> {
                int totemSlot = findInHotbar(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) mc.player.getInventory().setSelectedSlot(totemSlot);
                else {
                    int sword = findSword();
                    if (sword != -1) mc.player.getInventory().setSelectedSlot(sword);
                }
                cooldownSlot();
                state = MacroState.EXPLODE_1;
            }
            case EXPLODE_1 -> {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(anchorPos)) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);

                // Reset lock so PLACE_2 re-scans for a valid neighbor on the now-empty anchorPos
                lockNeighbor = null;
                lockSide     = null;
                lockHitVec   = null;

                cooldownClick();
                state = MacroState.PLACE_2;
            }
            case PLACE_2 -> {
                int anchorSlot = findInHotbar(Items.RESPAWN_ANCHOR);
                if (anchorSlot == -1) { state = MacroState.DONE; return; }
                mc.player.getInventory().setSelectedSlot(anchorSlot);
                if (doPlace(anchorPos)) {
                    cooldownPlace();
                    state = MacroState.SWITCH_GLOW_2;
                } else {
                    state = MacroState.DONE;
                }
            }
            case SWITCH_GLOW_2 -> {
                int slot = findInHotbar(Items.GLOWSTONE);
                if (slot == -1) { state = MacroState.DONE; return; }
                mc.player.getInventory().setSelectedSlot(slot);
                cooldownSlot();
                state = MacroState.CHARGE_2;
            }
            case CHARGE_2 -> {
                if (mc.player.getMainHandStack().getItem() != Items.GLOWSTONE) return;
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(anchorPos)) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();
                state = MacroState.SWITCH_TOTEM_2;
            }
            case SWITCH_TOTEM_2 -> {
                int totemSlot = findInHotbar(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) mc.player.getInventory().setSelectedSlot(totemSlot);
                cooldownSlot();
                state = MacroState.EXPLODE_2;
            }
            case EXPLODE_2 -> {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(anchorPos)) return;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();
                state = MacroState.DONE;
            }
            case DONE -> resetMacro();
        }
    }

    private boolean doPlace(BlockPos pos) {
        // 1. Try locked neighbor
        if (lockNeighbor != null && !mc.world.getBlockState(lockNeighbor).isAir()) {
            BlockHitResult hit = new BlockHitResult(lockHitVec, lockSide.getOpposite(), lockNeighbor, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        // 2. Scan for new neighbor
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Direction bestDir = null;
        double bestDot = Double.NEGATIVE_INFINITY;

        Direction[] dirs = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : dirs) {
            BlockPos neighbor = pos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) continue;
            double dot = look.dotProduct(new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()));
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            lockSide     = bestDir;
            lockNeighbor = pos.offset(bestDir);
            double u = 0.2 + rng.nextDouble(0.6);
            double v = 0.2 + rng.nextDouble(0.6);
            lockHitVec = switch (bestDir) {
                case UP    -> new Vec3d(pos.getX() + u, pos.getY() + 1.0, pos.getZ() + v);
                case DOWN  -> new Vec3d(pos.getX() + u, pos.getY(),       pos.getZ() + v);
                case NORTH -> new Vec3d(pos.getX() + u, pos.getY() + v,   pos.getZ());
                case SOUTH -> new Vec3d(pos.getX() + u, pos.getY() + v,   pos.getZ() + 1.0);
                case WEST  -> new Vec3d(pos.getX(),     pos.getY() + u,   pos.getZ() + v);
                case EAST  -> new Vec3d(pos.getX() + 1.0, pos.getY() + u, pos.getZ() + v);
            };
            BlockHitResult hit = new BlockHitResult(lockHitVec, lockSide.getOpposite(), lockNeighbor, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    private int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    private int findSword() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem().getTranslationKey().contains("sword")) return i;
        return -1;
    }
}