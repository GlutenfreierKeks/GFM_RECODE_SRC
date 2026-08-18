package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class CropBreaker extends Module {

    private static final Random RNG = new Random();

    // ── Settings ──────────────────────────────────────────────────────────────
    private final IntSliderSetting breakDelay    = register(new IntSliderSetting("BreakDelay", "Delay between breaking crops", 3,  1, 10));
    private final IntSliderSetting range         = register(new IntSliderSetting("Range", "Scan range", 4,  1,  6));
    private final IntSliderSetting rotSpeed      = register(new IntSliderSetting("RotSpeed", "Rotation speed", 8,  1, 10));
    private final BoolSetting breakWheat    = register(new BoolSetting("BreakWheat", "Break wheat", true));
    private final BoolSetting breakCarrots  = register(new BoolSetting("BreakCarrots", "Break carrots", true));
    private final BoolSetting breakPotatoes = register(new BoolSetting("BreakPotatoes", "Break potatoes", true));
    private final BoolSetting breakBeetroot = register(new BoolSetting("BreakBeetroot", "Break beetroot", true));
    private final BoolSetting breakNWart    = register(new BoolSetting("BreakNetherWart", "Break nether wart", true));
    private final BoolSetting autoReplant   = register(new BoolSetting("AutoReplant", "Automatically replant crops", true));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show crop ESP", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed", "ESP Red", 255, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen", "ESP Green", 200, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue", "ESP Blue", 0, 0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha", "ESP Alpha", 200, 0, 255));

    // ── State ─────────────────────────────────────────────────────────────────
    private enum Phase { IDLE, ROTATING, SLOT_CHANGE_WAIT, BREAKING, REPLANT_WAIT, REPLANTING }

    private Phase    phase         = Phase.IDLE;
    private BlockPos currentTarget = null;
    private int      rotatingTicks = 0;
    private int      tickDelay     = 0;
    private int      brokenCount   = 0;
    private int      prevSlot      = -1;
    private int      slotChangeTicks = 0; // Warten nach Slot-Wechsel

    // Nach dem Break 1 Tick warten bevor Replant → Server hat Zeit den Block zu entfernen
    private BlockPos replantTarget = null;
    private Block    replantBlock  = null;
    private int      replantWait   = 0;

    private static final int ROT_SETTLE_TICKS  = 2;
    private static final int SLOT_CHANGE_TICKS = 2; // Ticks nach Slot-Wechsel warten (fix Post-flag)
    private static final int REPLANT_WAIT_TICKS = 2; // Ticks nach Break warten bevor Replant

    public CropBreaker() {
        super("CropBreaker", "Harvest fully grown crops", Category.FARM);
    }

    @Override
    public void onEnable() {
        resetAll();
    }

    @Override
    public void onDisable() {
        restoreSlot();
        currentTarget = null;
        if (mc.player != null && brokenCount > 0)
            mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§a[CropBreaker] Broke " + brokenCount + " crops!"), false);
        brokenCount = 0;
    }

    private void resetAll() {
        phase         = Phase.IDLE;
        currentTarget = null;
        rotatingTicks = 0;
        tickDelay     = 0;
        brokenCount   = 0;
        prevSlot      = -1;
        slotChangeTicks = 0;
        replantTarget = null;
        replantBlock  = null;
        replantWait   = 0;
    }

    // ── Main Tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        RotationUtil.onTick();

        if (tickDelay > 0) { tickDelay--; return; }

        switch (phase) {
            case IDLE           -> tickIdle();
            case ROTATING       -> tickRotating();
            case SLOT_CHANGE_WAIT -> tickSlotChangeWait();
            case BREAKING       -> tickBreaking();
            case REPLANT_WAIT   -> tickReplantWait();
            case REPLANTING     -> tickReplanting();
        }
    }

    // ── IDLE: Nächstes Target suchen ──────────────────────────────────────────

    private void tickIdle() {
        currentTarget = findBestCrop();
        if (currentTarget == null) return;
        rotatingTicks = 0;
        phase = Phase.ROTATING;
    }

    // ── ROTATING: Rotieren + Settle ───────────────────────────────────────────

    private void tickRotating() {
        if (currentTarget == null) { phase = Phase.IDLE; return; }

        // Target noch gültig?
        if (!isFullyGrownAt(currentTarget)) { currentTarget = null; phase = Phase.IDLE; return; }

        // Reach prüfen
        if (!inReach(currentTarget)) { currentTarget = null; phase = Phase.IDLE; return; }

        Vec3d   center = blockCenter(currentTarget);
        float[] tRot   = RotationUtil.getRotations(center);
        float   factor = Math.min(0.95f, rotSpeed.getValue() * 0.1f);
        mc.player.setYaw(mc.player.getYaw()    + wrapDeg(tRot[0] - mc.player.getYaw())    * factor);
        mc.player.setPitch(mc.player.getPitch() + (tRot[1]        - mc.player.getPitch()) * factor);

        if (!isLookingAt(currentTarget)) { rotatingTicks = 0; return; }

        rotatingTicks++;
        if (rotatingTicks < ROT_SETTLE_TICKS) return;
        rotatingTicks = 0;

        // Slot-Wechsel NUR wenn AutoReplant aktiviert
        if (autoReplant.getValue()) {
            Block block = mc.world.getBlockState(currentTarget).getBlock();
            int seedSlot = findSeedSlot(block);
            if (seedSlot >= 0) {
                if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(seedSlot);
                slotChangeTicks = SLOT_CHANGE_TICKS;
                phase = Phase.SLOT_CHANGE_WAIT;
                return;
            }
        }

        // Kein Slot-Wechsel nötig → direkt breaken
        phase = Phase.BREAKING;
    }

    // ── SLOT_CHANGE_WAIT: Nach Slot-Wechsel kurz warten (fix Post-flag) ───────

    private void tickSlotChangeWait() {
        if (slotChangeTicks > 0) { slotChangeTicks--; return; }
        phase = Phase.BREAKING;
    }

    // ── BREAKING: Block brechen via interactBlock (kein Destroy-Packet) ───────

    private void tickBreaking() {
        if (currentTarget == null) { phase = Phase.IDLE; return; }

        // KRITISCH: nochmal prüfen ob Block noch da ist bevor wir brechen
        // → verhindert AirLiquidBreak
        if (!isFullyGrownAt(currentTarget)) {
            restoreSlot();
            currentTarget = null;
            phase = Phase.IDLE;
            return;
        }

        if (!inReach(currentTarget)) {
            restoreSlot();
            currentTarget = null;
            phase = Phase.IDLE;
            return;
        }

        // Crops sind "weich" (hardness=0) → interactBlock reicht NICHT zum Brechen.
        // Wir nutzen attackBlock statt START/STOP_DESTROY_BLOCK Packets.
        // attackBlock ist die normale Linksklick-Interaktion → Grim-konform.
        Direction face = bestFace(currentTarget);
        mc.interactionManager.attackBlock(currentTarget, face);
        mc.player.swingHand(Hand.MAIN_HAND);

        brokenCount++;
        replantTarget = currentTarget;
        replantBlock  = mc.world.getBlockState(currentTarget).getBlock();
        currentTarget = null;

        if (autoReplant.getValue() && findSeedSlot(replantBlock) >= 0) {
            replantWait = REPLANT_WAIT_TICKS;
            phase = Phase.REPLANT_WAIT;
        } else {
            restoreSlot();
            tickDelay = breakDelay.getValue() + RNG.nextInt(2);
            phase = Phase.IDLE;
        }
    }

    // ── REPLANT_WAIT: Kurz warten bis Server den Block entfernt hat ───────────

    private void tickReplantWait() {
        if (replantWait > 0) { replantWait--; return; }
        phase = Phase.REPLANTING;
    }

    // ── REPLANTING: Seed pflanzen ─────────────────────────────────────────────

    private void tickReplanting() {
        if (replantTarget == null || replantBlock == null) {
            restoreSlot();
            tickDelay = breakDelay.getValue() + RNG.nextInt(2);
            phase = Phase.IDLE;
            return;
        }

        // Prüfen ob Platz frei ist (Block wurde tatsächlich gebrochen)
        BlockState st = mc.world.getBlockState(replantTarget);
        if (!st.isAir() && !st.isReplaceable()) {
            // Block noch da → nächsten Tick nochmal versuchen, max 3x
            replantWait++;
            if (replantWait > 3) {
                restoreSlot();
                replantTarget = null;
                replantBlock  = null;
                tickDelay = breakDelay.getValue();
                phase = Phase.IDLE;
            }
            return;
        }

        // Seed-Slot sicherstellen
        int seedSlot = findSeedSlot(replantBlock);
        if (seedSlot < 0) {
            restoreSlot();
            replantTarget = null;
            replantBlock  = null;
            tickDelay = breakDelay.getValue();
            phase = Phase.IDLE;
            return;
        }

        // Sicherstellen dass wir noch den richtigen Slot haben
        if (mc.player.getInventory().getSelectedSlot() != seedSlot) {
            mc.player.getInventory().setSelectedSlot(seedSlot);
        }

        // Farmland ist direkt unter dem Crop (replantTarget = Crop-Position = Farmland+1)
        BlockPos farmland = replantTarget.down();
        BlockState farmlandSt = mc.world.getBlockState(farmland);
        if (farmlandSt.getBlock() != Blocks.FARMLAND) {
            // Kein Farmland mehr → überspringen
            restoreSlot();
            replantTarget = null;
            replantBlock  = null;
            tickDelay = breakDelay.getValue();
            phase = Phase.IDLE;
            return;
        }

        Vec3d hitVec = new Vec3d(
                replantTarget.getX() + 0.5 + (RNG.nextDouble()-0.5)*0.1,
                replantTarget.getY(),
                replantTarget.getZ() + 0.5 + (RNG.nextDouble()-0.5)*0.1
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.UP, farmland, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        restoreSlot();
        replantTarget = null;
        replantBlock  = null;
        tickDelay = breakDelay.getValue() + RNG.nextInt(2);
        phase = Phase.IDLE;
    }

    // ── Block-Suche ───────────────────────────────────────────────────────────

    private BlockPos findBestCrop() {
        int r = range.getValue();
        BlockPos player = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterateOutwards(player, r, 3, r)) {
            if (!inReach(pos)) continue;
            if (!isFullyGrownAt(pos)) continue;
            found.add(pos.toImmutable());
        }

        if (found.isEmpty()) return null;
        found.sort(Comparator.comparingDouble(p ->
                mc.player.getEyePos().squaredDistanceTo(p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5)));
        return found.get(0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isFullyGrownAt(BlockPos pos) {
        BlockState st = mc.world.getBlockState(pos);
        Block b = st.getBlock();
        if (!shouldBreakCrop(b)) return false;
        return isFullyGrown(b, st);
    }

    private boolean shouldBreakCrop(Block b) {
        if (breakWheat.getValue()    && b == Blocks.WHEAT)       return true;
        if (breakCarrots.getValue()  && b == Blocks.CARROTS)     return true;
        if (breakPotatoes.getValue() && b == Blocks.POTATOES)    return true;
        if (breakBeetroot.getValue() && b == Blocks.BEETROOTS)   return true;
        if (breakNWart.getValue()    && b == Blocks.NETHER_WART) return true;
        return false;
    }

    private boolean isFullyGrown(Block b, BlockState st) {
        if (b == Blocks.NETHER_WART)   return st.get(net.minecraft.block.NetherWartBlock.AGE) >= 3;
        if (b instanceof CropBlock cb) return st.get(CropBlock.AGE) >= cb.getMaxAge();
        return false;
    }

    private int findSeedSlot(Block b) {
        net.minecraft.item.Item seed = null;
        if (b == Blocks.WHEAT)           seed = Items.WHEAT_SEEDS;
        else if (b == Blocks.CARROTS)    seed = Items.CARROT;
        else if (b == Blocks.POTATOES)   seed = Items.POTATO;
        else if (b == Blocks.BEETROOTS)  seed = Items.BEETROOT_SEEDS;
        else if (b == Blocks.NETHER_WART) seed = Items.NETHER_WART;
        if (seed == null) return -1;
        for (int s = 0; s < 9; s++)
            if (mc.player.getInventory().getStack(s).getItem() == seed) return s;
        return -1;
    }

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(
                pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) <= 4.5*4.5;
    }

    private boolean isLookingAt(BlockPos pos) {
        Vec3d center = blockCenter(pos);
        double dist = mc.player.getEyePos().distanceTo(center);
        if (dist < 0.01) return true;
        float halfAngle = (float) Math.toDegrees(Math.atan2(0.5, dist));
        float[] rot = RotationUtil.getRotations(center);
        return Math.abs(wrapDeg(rot[0] - mc.player.getYaw()))   <= halfAngle
                && Math.abs(wrapDeg(rot[1] - mc.player.getPitch())) <= halfAngle;
    }

    private Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5);
    }

    private Direction bestFace(BlockPos pos) {
        Vec3d diff = mc.player.getEyePos().subtract(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5);
        Direction best = Direction.UP; double bestDot = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            double dot = diff.x*d.getOffsetX() + diff.y*d.getOffsetY() + diff.z*d.getOffsetZ();
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
    }

    private void restoreSlot() {
        if (prevSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private static float wrapDeg(float d) {
        while (d >  180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    // ── ESP ───────────────────────────────────────────────────────────────────

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
