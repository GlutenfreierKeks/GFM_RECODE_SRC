package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlatformBuilder extends Module {

    private static PlatformBuilder activeInstance = null;

    public static boolean onWandClick(BlockPos pos) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return false;
        if (activeInstance == null || activeInstance.phase != Phase.SELECTING) return false;
        if (!mc.player.getMainHandStack().isOf(Items.STICK)) return false;
        return activeInstance.handleWandClick(pos);
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private final BoolSetting fastMode    = register(new BoolSetting("FastBridge", "Faster bridging logic", false));
    private final BoolSetting autoDisable = register(new BoolSetting("AutoOff", "Turn off after completion", true));
    private final BoolSetting silentSwitch = register(new BoolSetting("SilentSwitch", "Switch back to previous slot", false));
    private final EnumSetting<RotationMode> rotationMode = register(new EnumSetting<>("RotationMode", "Rotation style", RotationMode.SMOOTH));
    private final IntSliderSetting placeCd     = register(new IntSliderSetting("Cooldown", "Ticks between placements", 4, 1, 15));
    private final ColorSetting espColor = register(new ColorSetting("ESP Color", "Color of the area highlight", 100, 220, 255, 180));

    private enum RotationMode { SMOOTH, SNAPPY }

    // ── Region ────────────────────────────────────────────────────────────────

    private static final class Region {
        final int minX, minZ, maxX, maxZ, y;
        Region(BlockPos a, BlockPos b) {
            minX = Math.min(a.getX(), b.getX()); maxX = Math.max(a.getX(), b.getX());
            minZ = Math.min(a.getZ(), b.getZ()); maxZ = Math.max(a.getZ(), b.getZ());
            y    = a.getY();
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private enum Phase      { SELECTING, BUILDING, DONE }
    private enum BuildState { WALKING, ROTATING, PLACING }

    private Phase      phase      = Phase.SELECTING;
    private BuildState buildState = BuildState.WALKING;
    private Region     region     = null;
    private BlockPos   selPos1    = null;
    private BlockPos   selPos2    = null;

    private BlockPos  currentTarget  = null;
    private PlaceInfo currentPlace   = null;
    private int       cooldownTicks  = 0;
    private int       prevSlot       = -1;
    private boolean   isSneaking     = false;
    private boolean   isWalking      = false;
    private boolean   isSprinting    = false;

    // Stuck-Detection (Normal + FastBridge)
    private Vec3d lastStuckCheckPos  = null;
    private int   stuckTicks         = 0;
    private static final int STUCK_THRESHOLD = 60; // 3 Sekunden bei 20 TPS
    private static final double STUCK_MIN_MOVE = 0.02;

    // Skipped targets – werden nach einem Stuck übersprungen
    private final Set<BlockPos> skippedTargets = new HashSet<>();

    // FastBridge
    private int fbSneakTimer   = 0;
    private int edgeStuckTimer = 0;

    private static final int   FB_SNEAK_MAX   = 6;
    private static final int   EDGE_STUCK_MAX = 12;
    private static final float FB_PITCH       = 80.0f;
    private static final float MAX_YAW_STEP   = 25.0f;

    private static final double EDGE_OFFSET  = 0.21;
    private static final double WALK_STOP_SQ = 0.05 * 0.05;
    private static final double REACH_SQ     = 4.3 * 4.3;

    private static final Random RNG = new Random();

    // ── Constructor / Lifecycle ───────────────────────────────────────────────

    public PlatformBuilder() {
        super("PlatformBuilder", "Builds a platform in a marked area.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        resetAll();
        activeInstance = this;
        msg("§a[PlatformBuilder] §fEnabled! Stick -> Right Click §ePos1 §fthen §ePos2§f.");
    }

    @Override
    public void onDisable() {
        setSneaking(false);
        setWalking(false);
        setSprinting(false);
        restoreSlot();
        currentTarget  = null;
        currentPlace   = null;
        activeInstance = null;
    }

    private void resetAll() {
        phase = Phase.SELECTING; buildState = BuildState.WALKING;
        region = null; selPos1 = selPos2 = null;
        currentTarget = null; currentPlace = null;
        cooldownTicks = 0; prevSlot = -1;
        isSneaking = false; isWalking = false; isSprinting = false;
        fbSneakTimer = 0; edgeStuckTimer = 0;
        stuckTicks = 0; lastStuckCheckPos = null;
        skippedTargets.clear();
    }

    // ── Wand ──────────────────────────────────────────────────────────────────

    private boolean handleWandClick(BlockPos pos) {
        if (selPos1 == null) {
            selPos1 = pos;
            msg("§a[PlatformBuilder] §fPos1: §e" + fmt(pos));
        } else {
            selPos2 = pos;
            region  = new Region(selPos1, selPos2);
            msg("§a[PlatformBuilder] §fPos2: §e" + fmt(pos) +
                    " §7| " + (region.maxX - region.minX + 1) + "×" + (region.maxZ - region.minZ + 1) + " Blocks");
            phase = Phase.BUILDING;
        }
        return true;
    }

    // ── Main Tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        RotationUtil.onTick();

        if (phase == Phase.SELECTING) return;
        if (phase == Phase.DONE) { msg("§a[PlatformBuilder] §fDone!"); this.disable(); return; }
        if (cooldownTicks > 0) { cooldownTicks--; return; }

        int blockSlot = findBlockSlot();
        if (blockSlot < 0) { msg("§c[PlatformBuilder] §fNo blocks in hotbar!"); this.disable(); return; }

        List<BlockPos> missing = getMissingBlocks();
        if (missing.isEmpty()) { phase = Phase.DONE; return; }

        // ── Globale Stuck-Detection ───────────────────────────────────────────
        tickStuckDetection(missing);

        if (fastMode.getValue()) {
            tickFastBridge(missing, blockSlot);
        } else {
            tickNormal(missing, blockSlot);
        }
    }

    // ── Stuck Detection ───────────────────────────────────────────────────────

    private void tickStuckDetection(List<BlockPos> missing) {
        if (!isWalking) {
            stuckTicks        = 0;
            lastStuckCheckPos = mc.player.getEyePos();
            return;
        }

        Vec3d curPos = mc.player.getEyePos();

        if (lastStuckCheckPos == null) {
            lastStuckCheckPos = curPos;
            return;
        }

        if (curPos.distanceTo(lastStuckCheckPos) < STUCK_MIN_MOVE) {
            stuckTicks++;
            if (stuckTicks >= STUCK_THRESHOLD) {
                if (currentTarget != null) {
                    skippedTargets.add(currentTarget);
                    msg("§c[PlatformBuilder] §fStuck! Block skipped: §e" + fmt(currentTarget));
                }
                currentTarget  = null;
                currentPlace   = null;
                buildState     = BuildState.WALKING;
                stuckTicks     = 0;
                fbSneakTimer   = 0;
                edgeStuckTimer = 0;
                lastStuckCheckPos = curPos;

                List<BlockPos> remaining = getMissingBlocks();
                remaining.removeAll(skippedTargets);
                if (remaining.isEmpty()) {
                    msg("§e[PlatformBuilder] §fAll targets skipped - restarting.");
                    skippedTargets.clear();
                }
            }
        } else {
            stuckTicks        = 0;
            lastStuckCheckPos = curPos;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  NORMAL MODE
    // ════════════════════════════════════════════════════════════════════════════

    private void tickNormal(List<BlockPos> missing, int blockSlot) {
        setSprinting(false);

        List<BlockPos> available = new ArrayList<>(missing);
        available.removeAll(skippedTargets);
        if (available.isEmpty()) { skippedTargets.clear(); return; }

        if (currentTarget == null || !available.contains(currentTarget)) {
            pickTarget(available);
            if (currentTarget == null) return;
            buildState = BuildState.WALKING;
            stuckTicks = 0;
            lastStuckCheckPos = mc.player.getEyePos();
        }

        switch (buildState) {
            case WALKING  -> tickNormalWalking();
            case ROTATING -> tickNormalRotating();
            case PLACING  -> tickNormalPlacing(blockSlot);
        }
    }

    private void tickNormalWalking() {
        Vec3d  stand = currentPlace.standPos;
        double dx    = stand.x - mc.player.getX();
        double dz    = stand.z - mc.player.getZ();

        if (dx * dx + dz * dz <= WALK_STOP_SQ) {
            setWalking(false);
            setSneaking(true);
            stuckTicks = 0;
            buildState = BuildState.ROTATING;
            return;
        }

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff      = wrapDeg(targetYaw - mc.player.getYaw());
        float step      = Math.max(-MAX_YAW_STEP, Math.min(MAX_YAW_STEP, diff * 0.4f));
        mc.player.setYaw(mc.player.getYaw() + step);

        setSneaking(true);
        setWalking(true);
    }

    private void tickNormalRotating() {
        setWalking(false);
        Vec3d look = getFaceLookTarget(currentPlace.support, currentPlace.face);

        if (RotationUtil.isRotating()) return;
        if (isLookingAt(look, 2f)) { buildState = BuildState.PLACING; return; }
        applyRotation(look);
    }

    private void tickNormalPlacing(int blockSlot) {
        setWalking(false);
        setSneaking(true);

        PlaceInfo p = currentPlace;
        if (!reachable(p)) { buildState = BuildState.WALKING; return; }

        doPlace(p, blockSlot);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  FASTBRIDGE MODE
    // ════════════════════════════════════════════════════════════════════════════

    private void tickFastBridge(List<BlockPos> missing, int blockSlot) {
        if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(blockSlot);

        List<BlockPos> available = new ArrayList<>(missing);
        available.removeAll(skippedTargets);
        if (available.isEmpty()) { skippedTargets.clear(); return; }

        BlockPos fbTarget = pickFastBridgeTarget(available);
        if (fbTarget == null) {
            setSneaking(false); setSprinting(true); setWalking(true);
            return;
        }
        currentTarget = fbTarget;

        BlockPos   support    = fbTarget.down();
        BlockState supportSt  = mc.world.getBlockState(support);
        boolean    hasSupport = !supportSt.isAir() && !supportSt.isReplaceable() && supportSt.isOpaque();

        if (!hasSupport) {
            PlaceInfo side = buildPlaceInfo(fbTarget, false);
            if (side == null) {
                edgeStuckTimer++;
                if (edgeStuckTimer > EDGE_STUCK_MAX) {
                    skippedTargets.add(fbTarget);
                    currentTarget  = null;
                    edgeStuckTimer = 0;
                    msg("§c[PlatformBuilder] §fEdge-Block skipped: §e" + fmt(fbTarget));
                }
                return;
            }

            currentPlace   = side;
            edgeStuckTimer = 0;

            double sdx  = side.standPos.x - mc.player.getX();
            double sdz  = side.standPos.z - mc.player.getZ();

            if (sdx * sdx + sdz * sdz > WALK_STOP_SQ) {
                float ty   = (float) Math.toDegrees(Math.atan2(-sdx, sdz));
                float diff = wrapDeg(ty - mc.player.getYaw());
                float step = Math.max(-MAX_YAW_STEP, Math.min(MAX_YAW_STEP, diff * 0.5f));
                mc.player.setYaw(mc.player.getYaw() + step);
                setSneaking(true); setSprinting(false); setWalking(true);
            } else {
                setSneaking(true); setWalking(false); setSprinting(false);

                float[] rot   = RotationUtil.getRotations(getFaceLookTarget(side.support, side.face));
                float   yDiff = wrapDeg(rot[0] - mc.player.getYaw());
                float   pDiff = rot[1] - mc.player.getPitch();
                mc.player.setYaw(mc.player.getYaw()    + Math.max(-20f, Math.min(20f, yDiff * 0.7f)));
                mc.player.setPitch(mc.player.getPitch() + Math.max(-20f, Math.min(20f, pDiff * 0.7f)));

                if (isLookingAt(getFaceLookTarget(side.support, side.face), 5f)) {
                    edgeStuckTimer = 0;
                    doPlace(side, blockSlot);
                } else {
                    edgeStuckTimer++;
                    if (edgeStuckTimer > EDGE_STUCK_MAX * 2) {
                        skippedTargets.add(fbTarget);
                        currentTarget  = null;
                        edgeStuckTimer = 0;
                        msg("§c[PlatformBuilder] §fEdge-Block skipped: §e" + fmt(fbTarget));
                    }
                }
            }
            return;
        }

        double dx = (fbTarget.getX() + 0.5) - mc.player.getX();
        double dz = (fbTarget.getZ() + 0.5) - mc.player.getZ();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawDiff   = wrapDeg(targetYaw - mc.player.getYaw());
        float yawStep   = Math.max(-MAX_YAW_STEP, Math.min(MAX_YAW_STEP, yawDiff * 0.45f));
        mc.player.setYaw(mc.player.getYaw() + yawStep);

        mc.player.setPitch(mc.player.getPitch() + (FB_PITCH - mc.player.getPitch()) * 0.25f);

        boolean needSneak = fbNeedsSneak();

        if (needSneak) {
            setSneaking(true);
            setSprinting(false);

            Vec3d fc = new Vec3d(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(fc) <= REACH_SQ) {
                Vec3d hit = new Vec3d(
                        support.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 0.06,
                        support.getY() + 1.0,
                        support.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 0.06
                );
                doPlace(new PlaceInfo(fbTarget, support, Direction.UP, Vec3d.of(support), hit), blockSlot);
            }
        } else {
            fbSneakTimer = 0;
            setSneaking(false);
            setSprinting(true);
            setWalking(true);
        }
    }

    private boolean fbNeedsSneak() {
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double mx = Math.floor(px);
        double mz = Math.floor(pz);

        boolean atEdgeX = (px - mx < EDGE_OFFSET) || (px - mx > 1.0 - EDGE_OFFSET);
        boolean atEdgeZ = (pz - mz < EDGE_OFFSET) || (pz - mz > 1.0 - EDGE_OFFSET);

        if (atEdgeX || atEdgeZ) {
            fbSneakTimer++;
            return fbSneakTimer >= 2;
        }
        fbSneakTimer = 0;
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  CORE UTILITIES
    // ════════════════════════════════════════════════════════════════════════════

    private void doPlace(PlaceInfo p, int slot) {
        if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        BlockHitResult bhr = new BlockHitResult(p.hitVec, p.face, p.support, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);

        cooldownTicks = placeCd.getValue();
        currentTarget = null;
        currentPlace  = null;
        buildState    = BuildState.WALKING;
        edgeStuckTimer = 0;
    }

    private void pickTarget(List<BlockPos> missing) {
        missing.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX()+0.5, p.getY(), p.getZ()+0.5)));

        for (BlockPos target : missing) {
            PlaceInfo info = buildPlaceInfo(target, true);
            if (info != null) {
                currentTarget = target;
                currentPlace  = info;
                return;
            }
        }
        currentTarget = null;
        currentPlace  = null;
    }

    private BlockPos pickFastBridgeTarget(List<BlockPos> missing) {
        missing.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX()+0.5, p.getY(), p.getZ()+0.5)));
        return missing.get(0);
    }

    private PlaceInfo buildPlaceInfo(BlockPos target, boolean needsStand) {
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            BlockPos support = target.offset(d);
            BlockState st    = mc.world.getBlockState(support);
            if (st.isAir() || st.isReplaceable()) continue;

            Direction face = d.getOpposite();
            Vec3d stand = Vec3d.ofBottomCenter(target).add(d.getOffsetX() * 1.2, 0, d.getOffsetZ() * 1.2);
            Vec3d hit   = getFaceLookTarget(support, face);

            PlaceInfo info = new PlaceInfo(target, support, face, stand, hit);
            if (!needsStand || reachable(info)) return info;
        }
        return null;
    }

    private boolean reachable(PlaceInfo p) {
        double dx = mc.player.getX() - p.standPos.x;
        double dz = mc.player.getZ() - p.standPos.z;
        if (dx*dx + dz*dz > 1.5 * 1.5) return false;
        return mc.player.getEyePos().squaredDistanceTo(p.hitVec) <= REACH_SQ;
    }

    private Vec3d getFaceLookTarget(BlockPos pos, Direction face) {
        Vec3d center = Vec3d.ofCenter(pos);
        return center.add(Vec3d.of(face.getVector()).multiply(0.5));
    }

    private List<BlockPos> getMissingBlocks() {
        List<BlockPos> list = new ArrayList<>();
        if (region == null) return list;
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int z = region.minZ; z <= region.maxZ; z++) {
                BlockPos p = new BlockPos(x, region.y, z);
                if (mc.world.getBlockState(p).isReplaceable()) list.add(p);
            }
        }
        return list;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private void applyRotation(Vec3d target) {
        float[] rots = RotationUtil.getRotations(target);
        if (rotationMode.getValue() == RotationMode.SNAPPY) {
            mc.player.setYaw(rots[0]);
            mc.player.setPitch(rots[1]);
        } else {
            float dy = wrapDeg(rots[0] - mc.player.getYaw());
            float dp = rots[1] - mc.player.getPitch();
            mc.player.setYaw(mc.player.getYaw() + dy * 0.4f);
            mc.player.setPitch(mc.player.getPitch() + dp * 0.4f);
        }
    }

    private boolean isLookingAt(Vec3d target, float tolerance) {
        float[] rots = RotationUtil.getRotations(target);
        return Math.abs(wrapDeg(rots[0] - mc.player.getYaw())) <= tolerance &&
               Math.abs(rots[1] - mc.player.getPitch()) <= tolerance;
    }

    private void restoreSlot() {
        if (silentSwitch.getValue() && prevSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private void setSneaking(boolean s) {
        if (isSneaking == s) return;
        isSneaking = s;
        mc.options.sneakKey.setPressed(s);
    }

    private void setWalking(boolean w) {
        if (isWalking == w) return;
        isWalking = w;
        mc.options.forwardKey.setPressed(w);
    }

    private void setSprinting(boolean s) {
        if (isSprinting == s) return;
        isSprinting = s;
        mc.options.sprintKey.setPressed(s);
    }

    private float wrapDeg(float d) {
        while (d >  180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    private String fmt(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    private static record PlaceInfo(BlockPos target, BlockPos support, Direction face, Vec3d standPos, Vec3d hitVec) {}
}
