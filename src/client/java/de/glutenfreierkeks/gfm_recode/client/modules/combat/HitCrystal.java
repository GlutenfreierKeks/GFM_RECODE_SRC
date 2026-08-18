package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class HitCrystal extends Module {

    private final DoubleSliderSetting smoothing      = new DoubleSliderSetting("Smoothing",      "Aim Smoothness",                    0.0,  0.0, 0.99);
    private final DoubleSliderSetting maxTurn        = new DoubleSliderSetting("MaxTurn",        "Max degrees per frame",             180.0, 1.0, 180.0);
    private final DoubleSliderSetting aimThresh      = new DoubleSliderSetting("AimThresh",      "Angle needed before place",          5.0, 0.5, 45.0);
    private final DoubleSliderSetting slotDelay      = new DoubleSliderSetting("SlotDelay",      "Warten nach Slot-Switch (ms)",       0.0, 0.0, 500.0);
    private final DoubleSliderSetting placeDelay     = new DoubleSliderSetting("PlaceDelay",     "Warten nach Block-Place (ms)",       0.0, 0.0, 500.0);
    private final DoubleSliderSetting randomRange    = new DoubleSliderSetting("RandomRange",    "Zufalls-Varianz +- (ms)",            0.0, 0.0, 100.0);
    private final DoubleSliderSetting cps            = new DoubleSliderSetting("CPS",            "Crystals pro Sekunde",              20.0, 1.0, 20.0);
    private final DoubleSliderSetting postPlaceDelay = new DoubleSliderSetting("PostPlaceDelay", "Delay before crystal phase (ms)",    0.0, 0.0, 200.0);
    private final DoubleSliderSetting postObiSnap    = new DoubleSliderSetting("PostObiSnap",    "Extra snap speed after obsidian",    5.0, 1.0, 10.0);
    private final DoubleSliderSetting crystalYOffset = new DoubleSliderSetting("CrystalY",       "Post-place aim height",             1.06, 0.8, 1.5);
    private final DoubleSliderSetting placeSpamDelay = new DoubleSliderSetting("PlaceSpamDelay", "Min ms zwischen Crystal-Places",     0.0, 0.0, 200.0);

    private enum State {
        IDLE,
        EXTINGUISH_FIRE,
        SWITCH_OBSIDIAN, PLACE_OBSIDIAN,
        SWITCH_CRYSTAL,  AIM_CRYSTAL_POS,
        CRYSTAL_LOOP
    }

    public static boolean isRunning = false;

    private State          state            = State.IDLE;
    private BlockPos       obsidianPos      = null;
    private BlockPos       firePos          = null;   // Position des Feuers
    private BlockHitResult initialHitResult = null;
    private boolean        fireMode         = false;

    private long actionCooldownUntil = 0;
    private long lastCrystalAction   = 0;
    private long lastPlaceTime       = 0;

    private double targetYaw         = 0;
    private double targetPitch       = 0;
    private double smoothYawVel      = 0;
    private double smoothPitchVel    = 0;

    private int     originalSlot     = -1;
    private boolean crystalAimLocked = false;

    public HitCrystal() {
        super("HitCrystal", "Rechtsklick+Schwert -> Obsidian+Crystal platzieren", Category.PLAYER);
        register(smoothing); register(maxTurn); register(aimThresh);
        register(slotDelay); register(placeDelay); register(randomRange);
        register(cps); register(postPlaceDelay); register(postObiSnap);
        register(crystalYOffset); register(placeSpamDelay);
        this.macroAllowed = false;
    }

    private long randomMs(double base) {
        double r = randomRange.getValue();
        return (long) ThreadLocalRandom.current().nextDouble(Math.max(0, base - r), base + r + 1);
    }

    private boolean onCooldown() {
        return System.currentTimeMillis() < actionCooldownUntil;
    }

    private void cooldownSlot() { actionCooldownUntil = System.currentTimeMillis() + randomMs(slotDelay.getValue()); }

    private boolean holdingRightClick() {
        return mc.options.useKey.isPressed();
    }

    private boolean isGroundLook(BlockHitResult hitResult) {
        return hitResult != null && hitResult.getSide() == Direction.UP
                && mc.player != null && mc.player.getPitch() >= 35.0F;
    }

    private boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getMaxDamage() > 0 && stack.getItem().getTranslationKey().contains("sword");
    }

    private int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    private double wrapDeg(double d) {
        d %= 360;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    private void aimAt(Vec3d target) {
        Vec3d from  = mc.player.getEyePos();
        double dx   = target.x - from.x;
        double dy   = target.y - from.y;
        double dz   = target.z - from.z;
        targetYaw   = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        targetPitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private void returnToOriginalSlot() {
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }

    private void resetState() {
        state               = State.IDLE;
        isRunning           = false;
        obsidianPos         = null;
        firePos             = null;
        initialHitResult    = null;
        fireMode            = false;
        actionCooldownUntil = 0;
        lastCrystalAction   = 0;
        lastPlaceTime       = 0;
        crystalAimLocked    = false;
        smoothYawVel        = 0;
        smoothPitchVel      = 0;
    }

    private double getVanillaReach() {
        return mc.player.isCreative() ? 5.0 : 4.5;
    }

    @Override public void onEnable()  { resetState(); originalSlot = -1; }
    @Override public void onDisable() { returnToOriginalSlot(); resetState(); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (!holdingRightClick()) {
            if (state != State.IDLE) { returnToOriginalSlot(); resetState(); }
            return;
        }

        // Aim-Target berechnen
        if (state != State.IDLE && obsidianPos != null) {
            Vec3d targetVec = (state == State.SWITCH_OBSIDIAN || state == State.PLACE_OBSIDIAN
                    || state == State.EXTINGUISH_FIRE)
                    ? (initialHitResult != null ? initialHitResult.getPos() : Vec3d.ofCenter(obsidianPos.down()))
                    : Vec3d.of(obsidianPos).add(0.5, crystalYOffset.getValue(), 0.5);
            aimAt(targetVec);
        }

        // Crosshair-Abweichungs-Check (lockerer im fireMode)
        if (state != State.IDLE && state != State.EXTINGUISH_FIRE) {
            boolean lookingAtTarget = false;
            if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                BlockPos cp = bhr.getBlockPos();
                if (obsidianPos != null && (cp.equals(obsidianPos)
                        || (initialHitResult != null && cp.equals(initialHitResult.getBlockPos()))))
                    lookingAtTarget = true;
            } else if (mc.crosshairTarget instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof EndCrystalEntity crystal) {
                if (obsidianPos != null && crystal.getBlockPos().down().equals(obsidianPos))
                    lookingAtTarget = true;
            }
            if (!lookingAtTarget) {
                double limit = fireMode ? 45.0 : 30.0;
                double diff = Math.sqrt(
                        Math.pow(wrapDeg(targetYaw   - mc.player.getYaw()),   2) +
                                Math.pow(wrapDeg(targetPitch - mc.player.getPitch()), 2));
                if (diff > limit) { returnToOriginalSlot(); resetState(); return; }
            }
        }

        if (state == State.AIM_CRYSTAL_POS) {
            double diff = Math.sqrt(
                    Math.pow(wrapDeg(targetYaw   - mc.player.getYaw()),   2) +
                            Math.pow(wrapDeg(targetPitch - mc.player.getPitch()), 2));
            if (diff < aimThresh.getValue()) {
                state = State.CRYSTAL_LOOP;
            } else {
                return;
            }
        }

        if (onCooldown()) return;

        switch (state) {
            case IDLE -> {
                if (mc.crosshairTarget == null) return;
                if (!isSword(mc.player.getMainHandStack())) return;
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;

                var block = mc.world.getBlockState(bhr.getBlockPos()).getBlock();
                boolean isFire = block == Blocks.FIRE || block == Blocks.SOUL_FIRE;

                if (isFire) {
                    if (mc.player.getPitch() < 35.0F) return;
                } else {
                    if (!isGroundLook(bhr)) return;
                }

                if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                    obsidianPos  = bhr.getBlockPos();
                    originalSlot = mc.player.getInventory().getSelectedSlot();
                    isRunning    = true;
                    fireMode     = false;
                    state        = State.SWITCH_CRYSTAL;

                } else if (isFire) {
                    // Feuer: erst löschen, dann Obsidian an diese Stelle
                    firePos          = bhr.getBlockPos();
                    obsidianPos      = bhr.getBlockPos(); // Obsidian kommt an Feuerstelle
                    initialHitResult = bhr;
                    originalSlot     = mc.player.getInventory().getSelectedSlot();
                    isRunning        = true;
                    fireMode         = true;
                    state            = State.EXTINGUISH_FIRE;

                } else {
                    BlockPos placePos = bhr.getBlockPos().up();
                    if (!mc.world.getBlockState(placePos).isAir()) return;
                    obsidianPos      = placePos;
                    initialHitResult = bhr;
                    originalSlot     = mc.player.getInventory().getSelectedSlot();
                    isRunning        = true;
                    fireMode         = false;
                    state            = State.SWITCH_OBSIDIAN;
                }
            }

            case EXTINGUISH_FIRE -> {
                // Linksklick auf den Feuer-Block um es zu löschen (Schwert in Hand)
                if (firePos == null) { resetState(); return; }

                // Aim auf Feuer-Block
                aimAt(Vec3d.ofCenter(firePos));

                // Left-Click = attackBlock
                mc.interactionManager.attackBlock(firePos, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);

                // Kurz warten bis Feuer weg, dann Obsidian platzieren
                actionCooldownUntil = System.currentTimeMillis() + 100;
                state = State.SWITCH_OBSIDIAN;
            }

            case SWITCH_OBSIDIAN -> {
                int slot = findInHotbar(Items.OBSIDIAN);
                if (slot == -1) { resetState(); return; }
                mc.player.getInventory().setSelectedSlot(slot);
                cooldownSlot();
                state = State.PLACE_OBSIDIAN;
            }

            case PLACE_OBSIDIAN -> {
                if (initialHitResult == null) { resetState(); return; }
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, initialHitResult);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPlaceTime = System.currentTimeMillis();
                long pd = randomMs(postPlaceDelay.getValue());
                actionCooldownUntil = lastPlaceTime + pd;
                crystalAimLocked = true;
                aimAt(Vec3d.of(obsidianPos).add(0.5, crystalYOffset.getValue(), 0.5));
                state = State.SWITCH_CRYSTAL;
            }

            case SWITCH_CRYSTAL -> {
                int slot = findInHotbar(Items.END_CRYSTAL);
                if (slot == -1) { returnToOriginalSlot(); resetState(); return; }
                mc.player.getInventory().setSelectedSlot(slot);
                crystalAimLocked = true;
                long sd = randomMs(slotDelay.getValue());
                if (sd > 0) actionCooldownUntil = System.currentTimeMillis() + sd;
                state = State.AIM_CRYSTAL_POS;
            }

            case CRYSTAL_LOOP -> {
                if (obsidianPos == null) { resetState(); return; }

                if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
                    state = State.SWITCH_CRYSTAL;
                    return;
                }

                long now = System.currentTimeMillis();
                long msPerCrystal = (long) (1000.0 / cps.getValue());
                if (now - lastCrystalAction < msPerCrystal) return;

                double reach = getVanillaReach();
                if (mc.player.getEyePos().distanceTo(Vec3d.of(obsidianPos).add(0.5, 1.0, 0.5)) > reach) return;

                EndCrystalEntity nearby = findCrystalAt(obsidianPos);
                if (nearby != null) {
                    mc.interactionManager.attackEntity(mc.player, nearby);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastCrystalAction = now;
                    return; // WICHTIG: nie Break+Place im selben Tick
                }

                double spamGuard = placeSpamDelay.getValue();
                if (spamGuard > 0 && now - lastPlaceTime < spamGuard) return;

                BlockHitResult hit = getRealBlockHit(obsidianPos);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalAction = now;
                lastPlaceTime     = now;
            }
        }
    }

    private BlockHitResult getRealBlockHit(BlockPos targetBlock) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double x = 0.3 + rng.nextDouble(0.4);
        double z = 0.3 + rng.nextDouble(0.4);
        double y = 0.99 + rng.nextDouble(0.01);
        return new BlockHitResult(Vec3d.of(targetBlock).add(x, y, z), Direction.UP, targetBlock, false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (state == State.IDLE || mc.player == null) {
            smoothYawVel   *= 0.8;
            smoothPitchVel *= 0.8;
            return;
        }

        double diffYaw   = wrapDeg(targetYaw   - mc.player.getYaw());
        double diffPitch = wrapDeg(targetPitch - mc.player.getPitch());

        double s = smoothing.getValue();
        boolean fastSnap     = state == State.AIM_CRYSTAL_POS || state == State.SWITCH_CRYSTAL || crystalAimLocked;
        double snapMultiplier = fastSnap ? postObiSnap.getValue() : 1.0;
        double frameScale    = 0.05;

        double rawYawVel   = diffYaw   * (1.0 - s) * frameScale * snapMultiplier;
        double rawPitchVel = diffPitch * (1.0 - s) * frameScale * snapMultiplier;

        smoothYawVel   = smoothYawVel   * s + rawYawVel   * (1.0 - s);
        smoothPitchVel = smoothPitchVel * s + rawPitchVel * (1.0 - s);

        double maxDeg = fastSnap ? 180.0 : maxTurn.getValue();
        float dy = (float) MathHelper.clamp(smoothYawVel,   -maxDeg, maxDeg);
        float dp = (float) MathHelper.clamp(smoothPitchVel, -maxDeg, maxDeg);

        mc.player.setYaw(mc.player.getYaw() + dy);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dp, -90, 90));

        if (crystalAimLocked && Math.abs(diffPitch) < Math.max(aimThresh.getValue(), 5.0)) {
            crystalAimLocked = false;
        }
    }

    private EndCrystalEntity findCrystalAt(BlockPos base) {
        if (mc.world == null) return null;
        Box box = new Box(base).expand(0.1, 2.5, 0.1);
        List<EndCrystalEntity> list = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, e -> true);
        return list.isEmpty() ? null : list.get(0);
    }
}