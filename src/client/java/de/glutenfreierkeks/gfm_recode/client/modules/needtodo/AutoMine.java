package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

public class AutoMine extends Module {

    // ══════════════════════════════════════════════════════════════════════════
    //  WAND HOOK
    // ══════════════════════════════════════════════════════════════════════════

    private static AutoMine activeInstance = null;

    public static boolean onWandClick(BlockPos pos) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return false;
        if (activeInstance == null) return false;
        if (activeInstance.phase != Phase.SELECTING) return false;
        if (!mc.player.getMainHandStack().isOf(Items.STICK)) return false;
        return activeInstance.handleWandClick(pos);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SETTINGS
    // ══════════════════════════════════════════════════════════════════════════

    private final BoolSetting autoOff      = register(new BoolSetting("AutoOff",      "Turn off after finishing", true));
    private final EnumSetting<MineMode> mineMode = register(new EnumSetting<>("Mode", "Mining mode", MineMode.NORMAL));
    private final BoolSetting smartCheck   = register(new BoolSetting("SmartCheck",   "Build platforms to avoid falling", true));
    private final BoolSetting autoTool     = register(new BoolSetting("AutoTool",     "Automatically switch to best tool", true));
    private final BoolSetting losCheck     = register(new BoolSetting("LOSCheck",     "Line of sight check", true));
    private final BoolSetting doNavigate   = register(new BoolSetting("Navigate",     "Automatically walk to targets", true));
    private final BoolSetting coverFluids  = register(new BoolSetting("CoverFluids",  "Cover fluids with blocks", true));
    private final IntSliderSetting eatAt        = register(new IntSliderSetting("EatAt",        "Food level to start eating", 14, 1, 20));
    private final IntSliderSetting breakDelaySt = register(new IntSliderSetting("BreakDelay",   "Delay between breaking blocks", 2,  0, 10));
    private final IntSliderSetting rotSpeed     = register(new IntSliderSetting("RotSpeed",     "Rotation speed", 8,  1, 10));
    private final BoolSetting silentSwitch = register(new BoolSetting("SilentSwitch", "Switch back to previous slot", false));
    private final EnumSetting<RotationMode> rotationMode = register(new EnumSetting<>("RotationMode", "Rotation mode", RotationMode.SMOOTH));
    private final IntSliderSetting durabilityThreshold = register(new IntSliderSetting("LowDurability", "Low durability threshold", 20, 1, 100));
    private final ColorSetting espColor = register(new ColorSetting("ESP Color", "ESP color", 255, 100, 0, 200));

    private enum RotationMode { SMOOTH, SNAPPY }
    private enum MineMode { NORMAL, DRILL }

    // ══════════════════════════════════════════════════════════════════════════
    //  KONSTANTEN
    // ══════════════════════════════════════════════════════════════════════════

    private static final double MINE_REACH       = 3.5;
    private static final double MINE_REACH_SQ    = MINE_REACH * MINE_REACH;
    private static final double NAV_STOP_DIST    = 1.8;
    private static final int    STUCK_TICKS      = 80;
    private static final int    MAX_SKIP         = 3;
    private static final int    MAX_DEPTH        = 64;
    private static final int    ROT_SETTLE_TICKS = 3;

    // ══════════════════════════════════════════════════════════════════════════
    //  REGION
    // ══════════════════════════════════════════════════════════════════════════

    private static final class Region {
        final int minX, maxX, minZ, maxZ;
        final int regionMinY, regionMaxY;
        final MineMode mode;

        Region(BlockPos a, BlockPos b, MineMode mode) {
            this.mode  = mode;
            minX       = Math.min(a.getX(), b.getX());
            maxX       = Math.max(a.getX(), b.getX());
            minZ       = Math.min(a.getZ(), b.getZ());
            maxZ       = Math.max(a.getZ(), b.getZ());
            regionMinY = Math.min(a.getY(), b.getY());
            regionMaxY = Math.min(Math.max(a.getY(), b.getY()), regionMinY + MAX_DEPTH - 1);
        }

        int width()       { return maxX - minX + 1; }
        int depth()       { return maxZ - minZ + 1; }
        int totalHeight() { return regionMaxY - regionMinY + 1; }

        int layerHeight() {
            return mode == MineMode.DRILL ? 3 : 2;
        }

        int layerCount() {
            return (totalHeight() + layerHeight() - 1) / layerHeight();
        }

        int layerTopY(int layerIdx) {
            return regionMaxY - layerIdx * layerHeight();
        }

        int layerBottomY(int layerIdx) {
            return Math.max(regionMinY, layerTopY(layerIdx) - (layerHeight() - 1));
        }

        int layerTargetY(int layerIdx) {
            if (mode == MineMode.DRILL) {
                int target = layerTopY(layerIdx) - 1;
                return Math.max(regionMinY, target);
            }
            return layerTopY(layerIdx);
        }

        boolean containsXZ(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean inLayer(BlockPos p, int layerIdx) {
            return containsXZ(p.getX(), p.getZ())
                    && p.getY() >= layerBottomY(layerIdx)
                    && p.getY() <= layerTopY(layerIdx);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FLUID COVER INFO
    // ══════════════════════════════════════════════════════════════════════════

    private static final class FluidCoverTask {
        final BlockPos  fluidPos;
        final BlockPos  supportPos;
        final Direction face;

        FluidCoverTask(BlockPos fluid, BlockPos support, Direction face) {
            this.fluidPos   = fluid;
            this.supportPos = support;
            this.face       = face;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASEN
    // ══════════════════════════════════════════════════════════════════════════

    private enum Phase {
        SELECTING,
        IDLE,
        COVERING_FLUID,
        NAVIGATING,
        ROTATING,
        MINING,
        WAIT_GO_UP,
        DONE
    }

    // ── Haupt-State ──────────────────────────────────────────────────────────
    private Phase    phase   = Phase.SELECTING;
    private Region   region  = null;
    private BlockPos selPos1 = null;
    private BlockPos selPos2 = null;

    private int currentLayer = 0;

    // Break-State
    private BlockPos breakTarget   = null;
    private BlockPos lastBroken    = null;
    private int      breakTicks    = 0;
    private int      breakSeq      = 0;
    private int      breakDelay    = 0;
    private int      rotatingTicks = 0;

    // Navigation-State
    private BlockPos navTarget  = null;
    private Vec3d    lastNavPos = null;
    private int      stuckTicks = 0;
    private int      skipCount  = 0;
    private final Set<BlockPos> skippedTargets = new HashSet<>();

    // Fluid-Covering
    private FluidCoverTask fluidTask     = null;
    private int            fluidRotTicks = 0;

    // Inventory
    private int prevSlot      = -1;
    private int prevSlotFluid = -1;

    // Statistik
    private int totalBlocks  = 0;

    // Sneaking
    private boolean isSneaking = false;

    // ══════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR / LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    public AutoMine() {
        super("AutoMine", "Mines all blocks in a marked area, layer by layer.", Category.FARM);
    }

    @Override
    public void onEnable() {
        resetAll();
        activeInstance = this;
        msg("§a[AutoMine] §fEnabled. §eStick → Pos1 §fthen §ePos2§f.");
    }

    @Override
    public void onDisable() {
        abortBreak();
        stopNavigation();
        restoreSlot();
        restoreSlotFluid();
        activeInstance = null;
    }

    private void resetAll() {
        phase         = Phase.SELECTING;
        region        = null;
        selPos1 = selPos2 = null;
        currentLayer  = 0;
        breakTarget   = null;
        lastBroken    = null;
        navTarget     = null;
        lastNavPos    = null;
        stuckTicks    = 0;
        skipCount     = 0;
        breakTicks    = 0;
        breakSeq      = 0;
        breakDelay    = 0;
        rotatingTicks = 0;
        prevSlot      = -1;
        prevSlotFluid = -1;
        totalBlocks   = 0;
        isSneaking    = false;
        fluidTask     = null;
        fluidRotTicks = 0;
        skippedTargets.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WAND
    // ══════════════════════════════════════════════════════════════════════════

    private boolean handleWandClick(BlockPos pos) {
        if (selPos1 == null) {
            selPos1 = pos;
            msg("§a[AutoMine] §fPos1: §e" + fmt(pos));
        } else {
            selPos2      = pos;
            region       = new Region(selPos1, selPos2, mineMode.getValue());
            currentLayer = 0;
            skippedTargets.clear();
            totalBlocks  = countTotalBreakable();

            int layers = region.layerCount();
            msg("§a[AutoMine] §fPos2: §e" + fmt(pos)
                    + " §7| §f" + region.width() + "×" + region.totalHeight() + "×" + region.depth()
                    + " §7(§f" + layers + " §7layers, §f" + totalBlocks + " §7blocks)");

            if (mc.player != null && mc.player.getBlockPos().getY() < region.layerTopY(0)) {
                phase = Phase.WAIT_GO_UP;
                msg("§c[AutoMine] §fYou are below the top layer (Y=" + region.layerTopY(0)
                        + ")! §ePlease go up §fand re-select.");
            } else {
                phase = Phase.IDLE;
            }
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DURABILITY CHECK
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Prüft ob das aktuell gehaltene Tool noch genug Durability hat.
     * Wenn nicht → versuche zu einem anderen Tool zu wechseln.
     * Wenn kein anderes Tool vorhanden → abbrechen mit Nachricht.
     *
     * @param pos der Block der abgebaut werden soll (für Speed-Vergleich)
     * @return true wenn ein brauchbares Tool verfügbar ist, false wenn abgebrochen werden soll
     */
    private boolean checkAndHandleDurability(BlockPos pos) {
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        ItemStack currentStack = mc.player.getInventory().getStack(currentSlot);

        // Nur prüfen wenn Item überhaupt Durability hat
        if (!hasDurability(currentStack)) return true;

        int remaining = getRemainingDurability(currentStack);
        if (remaining > durabilityThreshold.getValue()) return true;

        // Aktuelles Tool hat niedrige Durability → suche Ersatz
        msg("§e[AutoMine] §fLow durability on current tool (" + remaining + " left), switching...");

        int fallbackSlot = findBestToolExcluding(pos, currentSlot);
        if (fallbackSlot >= 0) {
            if (prevSlot < 0) prevSlot = currentSlot;
            mc.player.getInventory().setSelectedSlot(fallbackSlot);
            msg("§a[AutoMine] §fSwitched to slot §e" + (fallbackSlot + 1) + "§f.");
            return true;
        }

        // Kein Ersatz-Tool gefunden → abbrechen
        msg("§c[AutoMine] §fLow durability – take ore repair your pickaxe!");
        abortBreak();
        restoreSlot();
        this.setEnabled(false);
        return false;
    }

    /**
     * Hat das Item überhaupt eine Durability-Komponente?
     */
    private boolean hasDurability(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getComponents().contains(DataComponentTypes.DAMAGE)
                && stack.getComponents().contains(DataComponentTypes.MAX_DAMAGE);
    }

    /**
     * Verbleibende Haltbarkeit = MaxDamage - aktuellerDamage.
     */
    private int getRemainingDurability(ItemStack stack) {
        if (!hasDurability(stack)) return Integer.MAX_VALUE;
        Integer maxDmg = stack.get(DataComponentTypes.MAX_DAMAGE);
        Integer curDmg = stack.get(DataComponentTypes.DAMAGE);
        if (maxDmg == null || curDmg == null) return Integer.MAX_VALUE;
        return maxDmg - curDmg;
    }

    /**
     * Sucht das beste Tool im Hotbar das:
     * a) nicht der ausgeschlossene Slot ist
     * b) noch genug Durability hat (> threshold)
     * c) die höchste Mining-Speed für den Block hat
     */
    private int findBestToolExcluding(BlockPos pos, int excludeSlot) {
        if (pos == null) return -1;
        BlockState st = mc.world.getBlockState(pos);

        int   bestSlot  = -1;
        float bestSpeed = -1f;

        for (int s = 0; s < 9; s++) {
            if (s == excludeSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (stack.isEmpty()) continue;

            // Durability prüfen
            if (hasDurability(stack) && getRemainingDurability(stack) <= durabilityThreshold.getValue()) continue;

            float spd = stack.getMiningSpeedMultiplier(st);
            if (spd > bestSpeed) {
                bestSpeed = spd;
                bestSlot  = s;
            }
        }
        return bestSlot;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAIN TICK
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        RotationUtil.onTick();

        if (phase == Phase.SELECTING) return;

        if (phase == Phase.DONE) {
            int remaining = countTotalBreakable();
            int broken = Math.max(0, totalBlocks - remaining);
            msg("§a[AutoMine] §fDone! §7(" + broken + "/" + totalBlocks + " blocks mined)");
            if (autoOff.getValue()) this.setEnabled(false);
            return;
        }

        if (phase == Phase.WAIT_GO_UP) {
            if (region != null && mc.player.getBlockPos().getY() >= region.layerTopY(0)) {
                currentLayer = 0;
                lastBroken   = null;
                skippedTargets.clear();
                phase = Phase.IDLE;
                msg("§a[AutoMine] §fStarting from the top (Layer 1/" + region.layerCount() + ").");
                return;
            }
            if (mc.world.getTime() % 40 == 0) {
                int targetY = region != null ? region.layerTopY(0) : 0;
                msg("§c[AutoMine] §fGo to Y=" + targetY + " §fto start mining.");
            }
            return;
        }

        if (mc.player.getHungerManager().getFoodLevel() <= eatAt.getValue()) {
            pauseForEating();
            return;
        } else {
            mc.options.useKey.setPressed(false);
        }

        if (breakDelay > 0) { breakDelay--; return; }

        // Durability-Check jeden Tick während Mining/Rotating
        if (phase == Phase.MINING || phase == Phase.ROTATING) {
            if (!checkAndHandleDurability(breakTarget)) return;
        }

        if (phase != Phase.MINING && coverFluids.getValue()) {
            FluidCoverTask task = findFluidToCover();
            if (task != null) {
                fluidTask     = task;
                fluidRotTicks = 0;
                phase         = Phase.COVERING_FLUID;
            }
        }

        switch (phase) {
            case IDLE           -> tickIdle();
            case COVERING_FLUID -> tickCoveringFluid();
            case NAVIGATING     -> tickNavigating();
            case ROTATING       -> tickRotating();
            case MINING         -> tickMining();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE: IDLE
    // ══════════════════════════════════════════════════════════════════════════

    private void tickIdle() {
        if (region == null) { phase = Phase.SELECTING; return; }

        // Durability auch im IDLE prüfen (vor dem nächsten Block)
        if (autoTool.getValue()) {
            int slot = mc.player.getInventory().getSelectedSlot();
            ItemStack cur = mc.player.getInventory().getStack(slot);
            if (hasDurability(cur) && getRemainingDurability(cur) <= durabilityThreshold.getValue()) {
                int fallback = findBestToolExcluding(null, slot);
                if (fallback >= 0) {
                    if (prevSlot < 0) prevSlot = slot;
                    mc.player.getInventory().setSelectedSlot(fallback);
                } else {
                    msg("§c[AutoMine] §fLow durability – take ore repair your pickaxe!");
                    this.setEnabled(false);
                    return;
                }
            }
        }

        if (isLayerClear(currentLayer)) {
            int nextLayer = currentLayer + 1;
            if (nextLayer >= region.layerCount()) {
                phase = Phase.DONE;
                return;
            }
            currentLayer = nextLayer;
            lastBroken   = null;
            skippedTargets.clear();
            msg("§a[AutoMine] §fLayer §e" + (currentLayer + 1) + "/" + region.layerCount()
                    + " §f(Y " + region.layerBottomY(currentLayer) + "-" + region.layerTopY(currentLayer) + ")");

            if (mc.player.getBlockPos().getY() <= region.layerBottomY(currentLayer)) {
                phase = Phase.WAIT_GO_UP;
                return;
            }
        }

        BlockPos reachable = findBestReachableInLayer(currentLayer);
        if (reachable != null) {
            breakTarget   = reachable;
            navTarget     = null;
            rotatingTicks = 0;
            phase         = Phase.ROTATING;
            return;
        }

        if (!doNavigate.getValue()) return;

        BlockPos navGoal = findBestNavTargetInLayer(currentLayer);
        if (navGoal == null) return;

        navTarget  = navGoal;
        lastNavPos = mc.player.getEyePos();
        stuckTicks = 0;
        phase      = Phase.NAVIGATING;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE: COVERING_FLUID
    // ══════════════════════════════════════════════════════════════════════════

    private void tickCoveringFluid() {
        if (fluidTask == null) { phase = Phase.IDLE; return; }

        BlockState fluidSt = mc.world.getBlockState(fluidTask.fluidPos);
        if (!isFluid(fluidSt)) {
            fluidTask = null;
            restoreSlotFluid();
            phase = Phase.IDLE;
            return;
        }

        int blockSlot = findBlockSlot();
        if (blockSlot < 0) {
            fluidTask = null;
            phase = Phase.IDLE;
            return;
        }
        if (prevSlotFluid < 0) prevSlotFluid = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(blockSlot);

        Vec3d faceCenter = new Vec3d(
                fluidTask.supportPos.getX() + 0.5 + fluidTask.face.getOffsetX() * 0.5,
                fluidTask.supportPos.getY() + 0.5 + fluidTask.face.getOffsetY() * 0.5,
                fluidTask.supportPos.getZ() + 0.5 + fluidTask.face.getOffsetZ() * 0.5
        );

        applyRotation(faceCenter);

        fluidRotTicks++;

        if (isLookingAtFace(faceCenter) || fluidRotTicks >= 5) {
            Vec3d hitVec = new Vec3d(
                    fluidTask.supportPos.getX() + 0.5 + fluidTask.face.getOffsetX() * 0.45,
                    fluidTask.supportPos.getY() + 0.5 + fluidTask.face.getOffsetY() * 0.45,
                    fluidTask.supportPos.getZ() + 0.5 + fluidTask.face.getOffsetZ() * 0.45
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hitVec, fluidTask.face, fluidTask.supportPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            fluidTask     = null;
            fluidRotTicks = 0;
            restoreSlotFluid();
            breakDelay = 1;
            phase = Phase.IDLE;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE: NAVIGATING
    // ══════════════════════════════════════════════════════════════════════════

    private void tickNavigating() {
        if (navTarget == null) { phase = Phase.IDLE; return; }

        BlockState navSt = mc.world.getBlockState(navTarget);
        if (navSt.isAir() || navSt.isReplaceable()) {
            lastBroken = navTarget;
            stopNavigation();
            phase = Phase.IDLE;
            return;
        }

        if (smartCheck.getValue()) {
            BlockPos below = mc.player.getBlockPos().down();
            BlockState belowSt = mc.world.getBlockState(below);
            if (belowSt.isAir() || belowSt.isReplaceable() || belowSt.getBlock() instanceof FluidBlock) {
                int blockSlot = findBlockSlot();
                if (blockSlot >= 0) {
                    if (prevSlotFluid < 0) prevSlotFluid = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(blockSlot);
                    applyRotation(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5));
                    if (mc.world.getTime() % 3 == 0) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                                new BlockHitResult(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    mc.options.forwardKey.setPressed(false);
                    return;
                }
            } else {
                restoreSlotFluid();
            }
        }

        double dx     = navTarget.getX() + 0.5 - mc.player.getX();
        double dz     = navTarget.getZ() + 0.5 - mc.player.getZ();
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        double eyeDist = mc.player.getEyePos().distanceTo(blockCenter(navTarget));

        if (eyeDist <= MINE_REACH || dist2d <= NAV_STOP_DIST) {
            stopNavigation();
            breakTarget   = navTarget;
            navTarget     = null;
            rotatingTicks = 0;
            phase         = Phase.ROTATING;
            return;
        }

        Vec3d curPos = mc.player.getEyePos();
        if (lastNavPos != null) {
            if (curPos.distanceTo(lastNavPos) < 0.02) {
                stuckTicks++;
                if (stuckTicks >= STUCK_TICKS) {
                    skipCount++;
                    if (skipCount >= MAX_SKIP) {
                        skippedTargets.add(navTarget);
                        msg("§c[AutoMine] §fBlock skipped: §e" + fmt(navTarget));
                        skipCount = 0;
                    }
                    stopNavigation();
                    phase = Phase.IDLE;
                    return;
                }
            } else {
                stuckTicks = 0;
            }
        }
        lastNavPos = curPos;

        float tyaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        RotationUtil.rotateToPos(new Vec3d(navTarget.getX() + 0.5, mc.player.getEyePos().y - 0.2, navTarget.getZ() + 0.5), 4.0f);
        setSneaking(true);
        mc.options.forwardKey.setPressed(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE: ROTATING
    // ══════════════════════════════════════════════════════════════════════════

    private void tickRotating() {
        if (breakTarget == null) { phase = Phase.IDLE; return; }

        BlockState st = mc.world.getBlockState(breakTarget);
        if (st.isAir() || st.isReplaceable()) {
            lastBroken    = breakTarget;
            breakTarget   = null;
            rotatingTicks = 0;
            phase         = Phase.IDLE;
            return;
        }

        double eyeDist = mc.player.getEyePos().distanceTo(blockCenter(breakTarget));
        if (eyeDist > MINE_REACH * 1.2) {
            rotatingTicks = 0;
            if (doNavigate.getValue()) {
                navTarget  = breakTarget;
                lastNavPos = mc.player.getEyePos();
                stuckTicks = 0;
                phase      = Phase.NAVIGATING;
            } else {
                breakTarget = null;
                phase       = Phase.IDLE;
            }
            return;
        }

        mc.options.forwardKey.setPressed(false);

        applyRotation(blockCenter(breakTarget));

        if (!isLookingAtHitbox(breakTarget)) {
            rotatingTicks = 0;
            return;
        }

        rotatingTicks++;
        if (rotatingTicks < ROT_SETTLE_TICKS) return;
        rotatingTicks = 0;

        if (autoTool.getValue()) selectBestTool(breakTarget);
        breakSeq++;
        mc.interactionManager.attackBlock(breakTarget, bestFace(breakTarget));
        mc.player.swingHand(Hand.MAIN_HAND);
        breakTicks = 0;
        phase      = Phase.MINING;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE: MINING
    // ══════════════════════════════════════════════════════════════════════════

    private void tickMining() {
        if (breakTarget == null) { phase = Phase.IDLE; return; }

        BlockState st = mc.world.getBlockState(breakTarget);
        if (st.isAir() || st.isReplaceable()) {
            lastBroken  = breakTarget;
            restoreSlot();
            breakTarget = null;
            breakTicks  = 0;
            breakDelay  = breakDelaySt.getValue();

            if (coverFluids.getValue()) {
                FluidCoverTask task = findFluidToCover();
                if (task != null) {
                    fluidTask     = task;
                    fluidRotTicks = 0;
                    phase         = Phase.COVERING_FLUID;
                    return;
                }
            }
            phase = Phase.IDLE;
            return;
        }

        double eyeDist = mc.player.getEyePos().distanceTo(blockCenter(breakTarget));
        if (eyeDist > MINE_REACH * 1.1) {
            abortBreak();
            if (doNavigate.getValue()) {
                navTarget   = breakTarget;
                lastNavPos  = mc.player.getEyePos();
                stuckTicks  = 0;
                breakTarget = null;
                phase       = Phase.NAVIGATING;
            } else {
                breakTarget = null;
                phase       = Phase.IDLE;
            }
            return;
        }

        if (!isLookingAtHitbox(breakTarget)) {
            applyRotation(blockCenter(breakTarget));
        }

        mc.interactionManager.updateBlockBreakingProgress(breakTarget, bestFace(breakTarget));
        mc.player.swingHand(Hand.MAIN_HAND);
        breakTicks++;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FLUID COVERING
    // ══════════════════════════════════════════════════════════════════════════

    private FluidCoverTask findFluidToCover() {
        if (region == null) return null;
        if (findBlockSlot() < 0) return null;

        int maxY = region.regionMaxY + 1;

        for (int y = maxY; y >= region.regionMinY; y--) {
            for (int x = region.minX; x <= region.maxX; x++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    BlockPos   p  = new BlockPos(x, y, z);
                    BlockState st = mc.world.getBlockState(p);
                    if (!isFluid(st)) continue;

                    for (Direction d : Direction.values()) {
                        BlockPos   nb   = p.offset(d);
                        BlockState nbSt = mc.world.getBlockState(nb);
                        if (nbSt.isAir() || nbSt.isReplaceable() || !nbSt.isOpaque()) continue;

                        Direction face = d.getOpposite();
                        Vec3d faceCenter = new Vec3d(
                                nb.getX() + 0.5 + face.getOffsetX() * 0.5,
                                nb.getY() + 0.5 + face.getOffsetY() * 0.5,
                                nb.getZ() + 0.5 + face.getOffsetZ() * 0.5
                        );

                        if (mc.player.getEyePos().squaredDistanceTo(faceCenter) > MINE_REACH_SQ) continue;
                        return new FluidCoverTask(p, nb, face);
                    }
                }
            }
        }
        return null;
    }

    private boolean isFluid(BlockState st) {
        return st.getBlock() instanceof FluidBlock;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BLOCK-SUCHE
    // ══════════════════════════════════════════════════════════════════════════

    private BlockPos findBestReachableInLayer(int layerIdx) {
        if (region == null) return null;
        BlockPos ref = effectiveRef();

        BlockPos bestLos = null; double bestLosScore = Double.MAX_VALUE;
        BlockPos bestAny = null; double bestAnyScore = Double.MAX_VALUE;

        int topY    = region.layerTopY(layerIdx);
        int bottomY = region.layerBottomY(layerIdx);
        int targetY = region.layerTargetY(layerIdx);

        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (skippedTargets.contains(p)) continue;
                    if (!isBreakable(p))             continue;

                    // Im Drill-Mode: nur die Ziel-Ebene und Zentren bevorzugen
                    if (mineMode.getValue() == MineMode.DRILL) {
                        if (y != targetY) continue;
                        if (!isDrillCenter(p)) continue;
                    }

                    double eyeDistSq = mc.player.getEyePos().squaredDistanceTo(blockCenter(p));
                    if (eyeDistSq > MINE_REACH_SQ) continue;

                    double score = squaredDist(p, ref);
                    if (score < bestAnyScore) { bestAnyScore = score; bestAny = p; }
                    if (losCheck.getValue() && !hasLineOfSight(p)) continue;
                    if (score < bestLosScore) { bestLosScore = score; bestLos = p; }
                }

        // Fallback für Drill-Mode: wenn keine Zentren erreichbar sind, nimm IRGENDEINEN erreichbaren Block
        if (mineMode.getValue() == MineMode.DRILL && bestAny == null) {
            for (int x = region.minX; x <= region.maxX; x++)
                for (int z = region.minZ; z <= region.maxZ; z++)
                    for (int y = bottomY; y <= topY; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (skippedTargets.contains(p)) continue;
                        if (!isBreakable(p)) continue;
                        double eyeDistSq = mc.player.getEyePos().squaredDistanceTo(blockCenter(p));
                        if (eyeDistSq > MINE_REACH_SQ) continue;
                        double score = squaredDist(p, ref);
                        if (score < bestAnyScore) { bestAnyScore = score; bestAny = p; }
                        if (losCheck.getValue() && !hasLineOfSight(p)) continue;
                        if (score < bestLosScore) { bestLosScore = score; bestLos = p; }
                    }
        }

        return bestLos != null ? bestLos : bestAny;
    }

    private BlockPos findBestNavTargetInLayer(int layerIdx) {
        if (region == null) return null;
        BlockPos ref = effectiveRef();
        BlockPos best = null; double bestScore = Double.MAX_VALUE;

        int topY    = region.layerTopY(layerIdx);
        int bottomY = region.layerBottomY(layerIdx);
        int targetY = region.layerTargetY(layerIdx);

        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (skippedTargets.contains(p)) continue;
                    if (!isBreakable(p))             continue;

                    if (mineMode.getValue() == MineMode.DRILL) {
                        if (y != targetY) continue;
                        if (!isDrillCenter(p)) continue;
                    }

                    double score = squaredDist(p, ref);
                    if (score < bestScore) { bestScore = score; best = p; }
                }

        if (mineMode.getValue() == MineMode.DRILL && best == null) {
            for (int x = region.minX; x <= region.maxX; x++)
                for (int z = region.minZ; z <= region.maxZ; z++)
                    for (int y = bottomY; y <= topY; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (skippedTargets.contains(p)) continue;
                        if (!isBreakable(p)) continue;
                        double score = squaredDist(p, ref);
                        if (score < bestScore) { bestScore = score; best = p; }
                    }
        }

        return best;
    }

    private boolean isDrillCenter(BlockPos p) {
        if (region == null) return true;
        int dx = p.getX() - region.minX;
        int dz = p.getZ() - region.minZ;
        
        int offX = Math.min(1, region.width() - 1);
        int offZ = Math.min(1, region.depth() - 1);
        
        return (dx % 3 == offX) && (dz % 3 == offZ);
    }

    private boolean isLayerClear(int layerIdx) {
        if (region == null) return true;
        int topY    = region.layerTopY(layerIdx);
        int bottomY = region.layerBottomY(layerIdx);

        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (skippedTargets.contains(p)) continue;
                    if (isBreakable(p)) return false;
                }
        return true;
    }

    private int countTotalBreakable() {
        if (region == null) return 0;
        int count = 0;
        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = region.regionMinY; y <= region.regionMaxY; y++)
                    if (isBreakable(new BlockPos(x, y, z))) count++;
        return count;
    }

    private int countRemainingInCurrentLayer() {
        if (region == null) return 0;
        int count   = 0;
        int topY    = region.layerTopY(currentLayer);
        int bottomY = region.layerBottomY(currentLayer);
        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!skippedTargets.contains(p) && isBreakable(p)) count++;
                }
        return count;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HILFSMETHODEN
    // ══════════════════════════════════════════════════════════════════════════

    private boolean isBreakable(BlockPos p) {
        BlockState st = mc.world.getBlockState(p);
        if (st.isAir() || st.isReplaceable()) return false;
        if (st.getBlock() instanceof FluidBlock) return false;
        if (st.getHardness(mc.world, p) < 0)    return false;
        return true;
    }

    private BlockPos effectiveRef() {
        return lastBroken != null ? lastBroken : mc.player.getBlockPos();
    }

    private boolean hasLineOfSight(BlockPos target) {
        Vec3d  eye    = mc.player.getEyePos();
        Vec3d  center = blockCenter(target);
        Vec3d  dir    = center.subtract(eye);
        double len    = dir.length();
        Vec3d  step   = dir.multiply(1.0 / 32.0);
        for (int i = 1; i < 32; i++) {
            Vec3d pt = eye.add(step.multiply(i));
            if (eye.distanceTo(pt) >= len) break;
            BlockPos  chk = BlockPos.ofFloored(pt);
            if (chk.equals(target)) break;
            BlockState st = mc.world.getBlockState(chk);
            if (!st.isAir() && !st.isReplaceable() && st.getHardness(mc.world, chk) >= 0)
                return false;
        }
        return true;
    }

    private int findBlockSlot() {
        for (int s = 0; s < 9; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                    && bi.getBlock().getDefaultState().isOpaque()) return s;
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EATING
    // ══════════════════════════════════════════════════════════════════════════

    private void pauseForEating() {
        mc.options.forwardKey.setPressed(false);
        int slot = findFoodSlot();
        if (slot < 0) return;
        if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
    }

    private int findFoodSlot() {
        for (int s = 0; s < 9; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (!stack.isEmpty() && stack.getComponents().contains(DataComponentTypes.FOOD)) return s;
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INVENTORY
    // ══════════════════════════════════════════════════════════════════════════

    private void selectBestTool(BlockPos pos) {
        BlockState st        = mc.world.getBlockState(pos);
        int        bestSlot  = -1;
        float      bestSpeed = -1f;

        for (int s = 0; s < 9; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (stack.isEmpty()) continue;
            // Tools mit zu niedriger Durability überspringen
            if (hasDurability(stack) && getRemainingDurability(stack) <= durabilityThreshold.getValue()) continue;

            float spd = stack.getMiningSpeedMultiplier(st);
            if (spd > bestSpeed) { bestSpeed = spd; bestSlot = s; }
        }

        int current = mc.player.getInventory().getSelectedSlot();
        if (bestSlot >= 0 && bestSlot != current) {
            if (prevSlot < 0) prevSlot = current;
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private void restoreSlot() {
        mc.options.useKey.setPressed(false);
        if (silentSwitch.getValue() && prevSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private void restoreSlotFluid() {
        if (silentSwitch.getValue() && prevSlotFluid >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlotFluid);
            prevSlotFluid = -1;
        }
    }

    private void applyRotation(Vec3d target) {
        if (rotationMode.getValue() == RotationMode.SNAPPY) {
            RotationUtil.rotateToPosInstant(target);
            return;
        }
        RotationUtil.rotateToPos(target, (float) rotSpeed.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BREAK / NAVIGATION CONTROL
    // ══════════════════════════════════════════════════════════════════════════

    private void abortBreak() {
        if (breakTarget == null) return;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    breakTarget, Direction.DOWN, breakSeq));
        } catch (Exception ignored) {}
        breakTarget = null;
        breakTicks  = 0;
    }

    private void stopNavigation() {
        mc.options.forwardKey.setPressed(false);
        setSneaking(false);
        navTarget  = null;
        stuckTicks = 0;
    }

    private void setSneaking(boolean sneak) {
        if (isSneaking == sneak) return;
        isSneaking = sneak;
        mc.player.setSneaking(sneak);
        mc.options.sneakKey.setPressed(sneak);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ROTATION / GEOMETRY
    // ══════════════════════════════════════════════════════════════════════════

    private boolean isLookingAtHitbox(BlockPos pos) {
        Vec3d  eye       = mc.player.getEyePos();
        Vec3d  center    = blockCenter(pos);
        double dist      = eye.distanceTo(center);
        if (dist < 0.01) return true;
        float  halfAngle = (float) Math.toDegrees(Math.atan2(0.5, dist));
        float[] rot      = RotationUtil.getRotations(center);
        return Math.abs(wrapDeg(rot[0] - mc.player.getYaw()))   <= halfAngle
                && Math.abs(wrapDeg(rot[1] - mc.player.getPitch())) <= halfAngle;
    }

    private boolean isLookingAtFace(Vec3d faceCenter) {
        float[] rot = RotationUtil.getRotations(faceCenter);
        return Math.abs(wrapDeg(rot[0] - mc.player.getYaw()))   <= 8f
                && Math.abs(wrapDeg(rot[1] - mc.player.getPitch())) <= 8f;
    }

    private Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private Direction bestFace(BlockPos pos) {
        Vec3d diff = mc.player.getEyePos()
                .subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction best = Direction.UP; double bestDot = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            double dot = diff.x * d.getOffsetX() + diff.y * d.getOffsetY() + diff.z * d.getOffsetZ();
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
    }

    private static float wrapDeg(float d) {
        while (d >  180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private static double squaredDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ESP
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.player == null) return;

        int base = espColor.getValue();
        int r = (base >> 16) & 255;
        int g = (base >> 8) & 255;
        int b = base & 255;
        int a = (base >> 24) & 255;
        if (a == 0) a = 200;
        Color mainColor  = new Color(r, g, b, a);
        Color navColor   = new Color(255, 165, 0, a);
        Color fluidColor = new Color(0, 200, 255, a);
        Color dimColor   = new Color(r, g, b, Math.max(0, a - 150));

        if (selPos1 != null && selPos2 == null)
           // RenderUtil.drawBox(posMatrix, selPos1, new Color(0, 255, 0, 200), 1.5, false, true);

        if (region == null) return;

        int topY    = region.layerTopY(currentLayer);
        int bottomY = region.layerBottomY(currentLayer);

        for (int x = region.minX; x <= region.maxX; x++)
            for (int z = region.minZ; z <= region.maxZ; z++)
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isBreakable(p)) continue;
                   // if (p.equals(breakTarget)) { RenderUtil.drawBox(posMatrix, p, mainColor,  2.0, true,  true); continue; }
                  //  if (p.equals(navTarget))   { RenderUtil.drawBox(posMatrix, p, navColor,   1.5, false, true); continue; }
                   // RenderUtil.drawBox(posMatrix, p, dimColor, 0.8, false, false);
                }

        if (fluidTask != null)
            System.out.println("hi");
            //RenderUtil.drawBox(posMatrix, fluidTask.fluidPos, fluidColor, 1.5, false, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HUD
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public String getDisplayInfo() {
        if (phase == Phase.SELECTING)
            return selPos1 == null ? "§ePos1 §fset (Stick)" : "§ePos2 §fset (Stick)";
        if (region == null) return "no region";

        // Durability des aktuellen Tools im HUD anzeigen
        int slot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : 0;
        ItemStack cur = mc.player != null ? mc.player.getInventory().getStack(slot) : ItemStack.EMPTY;
        String durStr = "";
        if (hasDurability(cur)) {
            int rem = getRemainingDurability(cur);
            String color = rem <= durabilityThreshold.getValue() ? "§c" : rem <= durabilityThreshold.getValue() * 3 ? "§e" : "§a";
            durStr = " | " + color + rem + "§f dur";
        }

        int    layers   = region.layerCount();
        String layerStr = "L" + (currentLayer + 1) + "/" + layers
                + " §7(Y" + region.layerBottomY(currentLayer) + "-" + region.layerTopY(currentLayer) + ")";
        int    remInLayer = countRemainingInCurrentLayer();
        int    remaining  = countTotalBreakable();
        int    broken     = Math.max(0, totalBlocks - remaining);
        int    pct        = totalBlocks > 0 ? (broken * 100 / totalBlocks) : 0;
        String progress   = broken + "/" + totalBlocks + " §7(" + pct + "%)";

        return switch (phase) {
            case IDLE           -> layerStr + " | " + progress + " | §7" + remInLayer + " left" + durStr;
            case COVERING_FLUID -> "§bfluid cover §f| " + layerStr;
            case NAVIGATING     -> "§6nav §f| " + layerStr + (stuckTicks > 20 ? " §c[stuck]" : "") + durStr;
            case ROTATING       -> "§erotating §f(" + rotatingTicks + "/" + ROT_SETTLE_TICKS + ") | " + layerStr + durStr;
            case MINING         -> "§amining §f(" + breakTicks + "t) | " + layerStr + " | " + progress + durStr;
            case WAIT_GO_UP     -> "§cGo to the TOP! §7Layer done.";
            case DONE           -> "§aDone d";
            default             -> phase.name();
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTIL
    // ══════════════════════════════════════════════════════════════════════════

    private void msg(String t) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(t), false);
    }

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + "," + p.getY() + "," + p.getZ() + ")";
    }
}