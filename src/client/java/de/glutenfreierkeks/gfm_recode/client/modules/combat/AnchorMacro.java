
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

public class AnchorMacro extends Module {

    // ── Settings ─────────────────────────────────────────────────────────────
    // Normale Sequenz: Delays können beliebig eingestellt werden
    private final DoubleSliderSetting slotDelay    = new DoubleSliderSetting("SlotDelay",     "Slot-Switch Delay (ms)",          0.0,  0.0, 200.0);
    private final DoubleSliderSetting placeDelay   = new DoubleSliderSetting("PlaceDelay",    "Block-Place Delay (ms)",          0.0,  0.0, 200.0);
    private final DoubleSliderSetting clickDelay   = new DoubleSliderSetting("ClickDelay",    "Rechtsklick Delay (ms)",          0.0,  0.0, 200.0);
    private final DoubleSliderSetting randomRange  = new DoubleSliderSetting("RandomRange",   "Zufalls-Varianz ± (ms)",          0.0,  0.0,  50.0);
    private final DoubleSliderSetting doublePressMs = new DoubleSliderSetting("DoublePressMs","Double-Press Fenster (ms)",      250.0, 50.0, 600.0);
    private final KeybindSetting      triggerKey   = new KeybindSetting("Trigger", "keybind");

    // ── State-Machine ────────────────────────────────────────────────────────
    private enum MacroState {
        IDLE,
        // Erste Sequenz (normal, mit Delays)
        PLACE_ANCHOR,
        SWITCH_GLOWSTONE,
        CHARGE_ANCHOR,
        SWITCH_TOTEM,
        EXPLODE,
        // Zweite Sequenz (Replant, so schnell wie möglich, mit Air-Place)
        REPLANT_AWAIT_GONE,     // warten bis Anchor-Block verschwunden
        REPLANT_PLACE,          // sofort platzieren (Air-Place erlaubt)
        REPLANT_CHARGE,         // Glowstone rein (slot-switch + interact im selben Tick)
        REPLANT_EXPLODE,        // Totem + explodieren (slot-switch + interact im selben Tick)
        DONE
    }

    private MacroState state        = MacroState.IDLE;
    private int        savedSlot    = -1;
    private BlockPos   anchorPos    = null;
    private boolean    anchorPlaced = false;

    private long actionCooldownUntil = 0;

    // Place-Cache für normalen Place (kein Air-Place)
    private BlockPos  lastPlacedPos = null;
    private Direction lastPlacedDir = null;
    private Vec3d     lastHitVec    = null;

    // Auto-trigger via Rechtsklick
    private boolean lastUseKeyState  = false;
    private BlockPos pendingCheckPos = null;

    // Double-press detection
    private boolean lastTriggerState   = false;
    private long    lastTriggerPressMs = 0;
    private boolean doublePressPending = false;

    // Replant: Timeout-Schutz falls Anchor nie verschwindet
    private long replantAwaitTimeout = 0;

    public AnchorMacro() {
        super("AnchorMacro", "Respawn Anchor Macro (Always on, uses Trigger Key)", Category.PLAYER);
        this.macroAllowed = false;
        register(slotDelay);
        register(placeDelay);
        register(clickDelay);
        register(randomRange);
        register(doublePressMs);
        register(triggerKey);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long randomMs(double base) {
        double r = randomRange.getValue();
        return (long) ThreadLocalRandom.current().nextDouble(Math.max(0, base - r), base + r + 1);
    }

    private boolean onCooldown() { return System.currentTimeMillis() < actionCooldownUntil; }

    private void cooldownSlot()  { actionCooldownUntil = System.currentTimeMillis() + randomMs(slotDelay.getValue()); }
    private void cooldownPlace() { actionCooldownUntil = System.currentTimeMillis() + randomMs(placeDelay.getValue()); }
    private void cooldownClick() { actionCooldownUntil = System.currentTimeMillis() + randomMs(clickDelay.getValue()); }

    private void resetMacro() {
        if (savedSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(savedSlot);
        state              = MacroState.IDLE;
        savedSlot          = -1;
        anchorPlaced       = false;
        anchorPos          = null;
        lastPlacedPos      = null;
        lastPlacedDir      = null;
        lastHitVec         = null;
        pendingCheckPos    = null;
        doublePressPending = false;
        replantAwaitTimeout = 0;
    }

    @Override public void onEnable()  { resetMacro(); lastUseKeyState = false; lastTriggerState = false; }
    @Override public void onDisable() { resetMacro(); }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    private boolean isTriggerPressed() {
        return triggerKey.isPressed(mc.getWindow().getHandle());
    }

    // ── Look-Check ────────────────────────────────────────────────────────────

    /** Prüft ob der Spieler auf anchorPos schaut (oder Nachbar bei Place-States) */
    private boolean isLookingAtTarget() {
        if (anchorPos == null) return false;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return false;
        BlockPos cur = bhr.getBlockPos();
        boolean isPlaceState = state == MacroState.PLACE_ANCHOR || state == MacroState.REPLANT_PLACE;
        if (isPlaceState) {
            if (cur.equals(anchorPos)) return true;
            for (Direction dir : Direction.values())
                if (cur.equals(anchorPos.offset(dir))) return true;
            return false;
        }
        return cur.equals(anchorPos);
    }

    // ── Main Tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // ── Double-Press tracking (läuft immer, auch im IDLE) ──────────────
        {
            boolean pressedNow  = isTriggerPressed();
            boolean risingEdge  = pressedNow && !lastTriggerState;
            lastTriggerState    = pressedNow;

            if (risingEdge) {
                long now    = System.currentTimeMillis();
                Double window = doublePressMs.getValue();
                if ((now - lastTriggerPressMs) <= window) {
                    doublePressPending = true;   // Double-Press → Replant vormerken
                }
                lastTriggerPressMs = now;
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // IDLE: Trigger-Erkennung
        // ══════════════════════════════════════════════════════════════════════
        if (state == MacroState.IDLE) {

            // Auto-Trigger: Rechtsklick mit Anchor in der Hand
            boolean useKeyNow       = mc.options.useKey.isPressed();
            boolean useKeyRisingEdge = useKeyNow && !lastUseKeyState;
            lastUseKeyState = useKeyNow;

            if (useKeyRisingEdge) {
                boolean holdingAnchor =
                        mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR ||
                                mc.player.getOffHandStack().getItem() == Items.RESPAWN_ANCHOR;

                if (holdingAnchor && mc.crosshairTarget instanceof BlockHitResult bhr) {
                    var block = mc.world.getBlockState(bhr.getBlockPos()).getBlock();
                    pendingCheckPos = (block == Blocks.FIRE || block == Blocks.SOUL_FIRE)
                            ? bhr.getBlockPos()
                            : bhr.getBlockPos().offset(bhr.getSide());
                }
            }

            if (pendingCheckPos != null) {
                if (mc.world.getBlockState(pendingCheckPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
                    anchorPos    = pendingCheckPos;
                    savedSlot    = mc.player.getInventory().getSelectedSlot();
                    anchorPlaced = true;
                    state        = MacroState.SWITCH_GLOWSTONE;
                    pendingCheckPos = null;
                } else if (!useKeyNow) {
                    pendingCheckPos = null;
                }
            }

            // Manueller Trigger-Key
            if (state == MacroState.IDLE && isTriggerPressed()) {
                if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;

                BlockPos targetPos = bhr.getBlockPos();
                var block = mc.world.getBlockState(targetPos).getBlock();

                if (block == Blocks.RESPAWN_ANCHOR) {
                    anchorPos    = targetPos;
                    savedSlot    = mc.player.getInventory().getSelectedSlot();
                    anchorPlaced = true;
                    state        = MacroState.SWITCH_GLOWSTONE;
                } else {
                    anchorPos = (block == Blocks.FIRE || block == Blocks.SOUL_FIRE)
                            ? targetPos
                            : targetPos.offset(bhr.getSide());

                    var targetState = mc.world.getBlockState(anchorPos);
                    if (!targetState.isAir()
                            && targetState.getBlock() != Blocks.FIRE
                            && targetState.getBlock() != Blocks.SOUL_FIRE
                            && targetState.getBlock() != Blocks.RESPAWN_ANCHOR)
                        return;

                    savedSlot = mc.player.getInventory().getSelectedSlot();
                    state     = MacroState.PLACE_ANCHOR;
                }
            }

            if (state == MacroState.IDLE) return;
        }

        // ══════════════════════════════════════════════════════════════════════
        // Look-Check (nicht für AWAIT_GONE – da ist der Block weg)
        // ══════════════════════════════════════════════════════════════════════
        if (state != MacroState.REPLANT_AWAIT_GONE && state != MacroState.DONE) {
            if (!isLookingAtTarget()) { resetMacro(); return; }
        }

        // ══════════════════════════════════════════════════════════════════════
        // REPLANT_AWAIT_GONE: kein Cooldown, kein Look-Check → sofort auf Block warten
        // ══════════════════════════════════════════════════════════════════════
        if (state == MacroState.REPLANT_AWAIT_GONE) {
            // Timeout nach 2s
            if (System.currentTimeMillis() > replantAwaitTimeout) { resetMacro(); return; }
            // Schaut der Spieler noch ungefähr in die Richtung?
            // (optional, aber gut gegen Fehlplatzierungen)
            if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) { resetMacro(); return; }
            {
                BlockPos cur = bhr.getBlockPos();
                boolean near = cur.equals(anchorPos);
                if (!near) for (Direction dir : Direction.values())
                    if (cur.equals(anchorPos.offset(dir))) { near = true; break; }
                if (!near) { resetMacro(); return; }
            }
            // Warten bis Anchor-Block weg ist
            if (mc.world.getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
                // Sofort im selben Tick: Slot auf Anchor switchen + platzieren
                state = MacroState.REPLANT_PLACE;
                doReplantPlaceImmediate();
            }
            return; // kein weiteres Processing in diesem Tick
        }

        if (onCooldown()) return;

        // ══════════════════════════════════════════════════════════════════════
        // State-Machine
        // ══════════════════════════════════════════════════════════════════════
        switch (state) {

            // ── Erste Sequenz (mit normalen Delays) ───────────────────────────

            case PLACE_ANCHOR -> {
                if (!anchorPlaced) {
                    int slot = findInHotbar(Items.RESPAWN_ANCHOR);
                    if (slot == -1) { resetMacro(); return; }
                    mc.player.getInventory().setSelectedSlot(slot);
                    placeBlockAt(anchorPos, false); // kein Air-Place
                    anchorPlaced = true;
                    cooldownPlace();
                } else {
                    anchorPlaced = false;
                    state = MacroState.SWITCH_GLOWSTONE;
                    // kein extra Delay → direkt im nächsten onTick-Durchlauf weiter
                }
            }

            case SWITCH_GLOWSTONE -> {
                int slot = findInHotbar(Items.GLOWSTONE);
                if (slot == -1) { resetMacro(); return; }
                mc.player.getInventory().setSelectedSlot(slot);
                cooldownSlot();
                state = MacroState.CHARGE_ANCHOR;
            }

            case CHARGE_ANCHOR -> {
                rightClickAt(anchorPos);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();
                state = MacroState.SWITCH_TOTEM;
            }

            case SWITCH_TOTEM -> {
                int totemSlot = findInHotbar(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) mc.player.getInventory().setSelectedSlot(totemSlot);
                else { int sw = findSword(); if (sw != -1) mc.player.getInventory().setSelectedSlot(sw); }
                cooldownSlot();
                state = MacroState.EXPLODE;
            }

            case EXPLODE -> {
                rightClickAt(anchorPos);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();

                if (doublePressPending) {
                    // Zweite Sequenz starten: auf Block-Verschwinden warten
                    doublePressPending  = false;
                    replantAwaitTimeout = System.currentTimeMillis() + 2000;
                    state = MacroState.REPLANT_AWAIT_GONE;
                } else {
                    state = MacroState.DONE;
                }
            }

            // ── Zweite Sequenz (Replant, ultra-schnell) ───────────────────────
            // REPLANT_AWAIT_GONE: oben separat behandelt

            case REPLANT_PLACE -> {
                // Fallback falls doReplantPlaceImmediate() schon gecalled wurde
                // aber Cooldown noch läuft (placeDelay > 0)
                state = MacroState.REPLANT_CHARGE;
            }

            case REPLANT_CHARGE -> {
                // Slot-Switch + Interact im selben Tick, kein Slot-Delay
                int glowSlot = findInHotbar(Items.GLOWSTONE);
                if (glowSlot == -1) { resetMacro(); return; }
                mc.player.getInventory().setSelectedSlot(glowSlot);
                rightClickAt(anchorPos);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick(); // nur Click-Delay, kein Slot-Delay
                state = MacroState.REPLANT_EXPLODE;
            }

            case REPLANT_EXPLODE -> {
                // Totem in die Hand + explodieren im selben Tick
                int totemSlot = findInHotbar(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) mc.player.getInventory().setSelectedSlot(totemSlot);
                else { int sw = findSword(); if (sw != -1) mc.player.getInventory().setSelectedSlot(sw); }
                rightClickAt(anchorPos);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownClick();
                state = MacroState.DONE;
            }

            case DONE -> resetMacro();
        }
    }

    /**
     * Sofort (noch im selben Tick) Anchor-Slot holen und Air-Place ausführen.
     * Danach PlaceDelay setzen und in REPLANT_CHARGE wechseln.
     */
    private void doReplantPlaceImmediate() {
        int slot = findInHotbar(Items.RESPAWN_ANCHOR);
        if (slot == -1) { resetMacro(); return; }
        mc.player.getInventory().setSelectedSlot(slot);
        // Place-Cache invalidieren → fresh Air-Place
        lastPlacedPos = null;
        lastPlacedDir = null;
        lastHitVec    = null;
        placeBlockAt(anchorPos, true); // Air-Place erlaubt
        cooldownPlace();               // nur Place-Delay, Slot war kein extra Cooldown
        state = MacroState.REPLANT_CHARGE;
    }

    // ── Block-Interaktion ────────────────────────────────────────────────────

    /**
     * Platziert einen Block an pos.
     * @param allowAirPlace  true → auch ohne soliden Nachbarn platzieren (direkt auf pos)
     */
    private void placeBlockAt(BlockPos pos, boolean allowAirPlace) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vec3d look = mc.player.getRotationVec(1.0f);

        Direction bestDir = null;
        Vec3d     hitVec  = null;

        // Cache nutzen wenn noch gültig
        if (pos.equals(lastPlacedPos) && lastPlacedDir != null && lastHitVec != null) {
            bestDir = lastPlacedDir;
            hitVec  = lastHitVec;
        } else {
            // 1. Versuch: solider Nachbar (normal)
            double bestDot = Double.NEGATIVE_INFINITY;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);
                if (!mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) continue;
                Vec3d faceNormal = new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ());
                double dot = look.dotProduct(faceNormal);
                if (dot > bestDot) { bestDot = dot; bestDir = dir; }
            }

            // 2. Versuch (nur wenn Air-Place erlaubt): beste Richtung auch ohne soliden Nachbar
            if (bestDir == null && allowAirPlace) {
                double bestDot2 = Double.NEGATIVE_INFINITY;
                for (Direction dir : Direction.values()) {
                    Vec3d faceNormal = new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ());
                    double dot = look.dotProduct(faceNormal);
                    if (dot > bestDot2) { bestDot2 = dot; bestDir = dir; }
                }
            }

            if (bestDir != null) {
                double u = 0.15 + rng.nextDouble(0.70);
                double v = 0.15 + rng.nextDouble(0.70);
                hitVec = switch (bestDir) {
                    case UP    -> new Vec3d(pos.getX() + u, pos.getY() + 1.0, pos.getZ() + v);
                    case DOWN  -> new Vec3d(pos.getX() + u, pos.getY(),       pos.getZ() + v);
                    case NORTH -> new Vec3d(pos.getX() + u, pos.getY() + v,   pos.getZ());
                    case SOUTH -> new Vec3d(pos.getX() + u, pos.getY() + v,   pos.getZ() + 1.0);
                    case WEST  -> new Vec3d(pos.getX(),     pos.getY() + u,   pos.getZ() + v);
                    case EAST  -> new Vec3d(pos.getX() + 1.0, pos.getY() + u, pos.getZ() + v);
                };
            }
            lastPlacedPos = pos;
            lastPlacedDir = bestDir;
            lastHitVec    = hitVec;
        }

        if (bestDir == null || hitVec == null) return;

        BlockPos neighbor = pos.offset(bestDir);
        // Beim Air-Place: als Nachbar die gleiche pos verwenden (vanilla-konform genug für die meisten Server)
        BlockPos hitTarget = allowAirPlace && !mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)
                ? pos
                : neighbor;

        BlockHitResult hit = new BlockHitResult(hitVec, bestDir.getOpposite(), hitTarget, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private void rightClickAt(BlockPos pos) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double u = 0.4 + rng.nextDouble(0.2);
        double v = 0.4 + rng.nextDouble(0.2);
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(pos.getX() + u, pos.getY() + 1.0, pos.getZ() + v),
                Direction.UP, pos, false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    // ── Hotbar-Suche ─────────────────────────────────────────────────────────

    private int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    private int findSword() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem().getTranslationKey().contains("sword")) return i;
        return -1;
    }
}
