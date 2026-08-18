package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.utils.InventoryUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Clutch extends Module {

    private float   visualYaw;
    private float   visualPitch;

    private static final float IDLE_PITCH  = 80f;
    private static final float PLACE_PITCH = 80f;

    private int     lastSlot       = -1;
    private long    lastPlaceTime  = 0;
    private int     placedCount    = 0;
    private boolean sneakQueued    = false;
    private long    sneakUntil     = 0;

    // A/D-Wiggle – begrenzt damit es nicht zu wild schwankt
    private long    lastWiggleTime = 0;
    private boolean wiggleLeft     = true;
    // 120ms pro Seite → subtiles Wackeln, kein Flaggen
    private static final long WIGGLE_INTERVAL = 120;

    // RightClick-Spam – KEIN eigenständiger Spam mehr, nur beim echten Place
    // (verhindert MultiPlace + AirLiquidPlace)

    public Clutch() {
        super("Clutch", "Bridge-Bot: platziert Blöcke an Kanten, schaut still nach unten", Category.PLAYER);
    }

    @Override
    public void render3D(org.joml.Matrix4f p, org.joml.Matrix4f pr,
                         net.minecraft.client.render.Camera c, float td) {}

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            visualYaw   = mc.player.getYaw();
            visualPitch = mc.player.getPitch();
        }
        lastSlot       = -1;
        placedCount    = 0;
        sneakQueued    = false;
        sneakUntil     = 0;
        lastWiggleTime = 0;
        lastPlaceTime  = 0;
    }

    @Override
    protected void onDisable() {
        restoreSlot();
        safeUnpress();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // ── Sprint sperren ────────────────────────────────────────────────
        mc.player.setSprinting(false);
        safeSetPressed(mc.options.sprintKey, false);

        // ── Silent-Rotation ───────────────────────────────────────────────
        applyIdleSilentRotation();

        // ── Sneak-Pulse alle 8 Blöcke ────────────────────────────────────
        handleSneakPulse();

        // ── A/D-Wiggle (subtil) ───────────────────────────────────────────
        handleWiggle();

        // ── Nur bridgen wenn auf dem Boden ───────────────────────────────
        if (!mc.player.isOnGround()) return;

        Direction moveDir = getMovementDirection();
        if (moveDir == null) return;

        BlockPos nextFloor = getNextGroundPos(moveDir);
        if (nextFloor == null) return;

        int blockSlot = InventoryUtil.findBlockInHotbar();
        if (blockSlot == -1) return;

        long now = System.currentTimeMillis();
        // 100ms Mindestabstand zwischen Places → verhindert MultiPlace-Flag
        if (now - lastPlaceTime < 100) return;

        BlockPos standingBlock = mc.player.getBlockPos().down();

        silentRotate(visualYaw + 180f, PLACE_PITCH);

        if (lastSlot == -1) lastSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(blockSlot);

        // Hit-Result: randomisierter Cursor auf der Oberseite des Stand-Blocks
        // → verhindert dass GrimAC immer denselben cursor=0.1,1.0,0.5 sieht
        double cx = 0.2 + Math.random() * 0.6; // 0.2–0.8
        double cz = 0.2 + Math.random() * 0.6;
        Vec3d hitVec = Vec3d.of(standingBlock).add(cx, 1.0, cz);
        BlockHitResult bhr = new BlockHitResult(hitVec, Direction.UP, standingBlock, false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastPlaceTime = now;

        placedCount++;
        if (placedCount % 8 == 0) sneakQueued = true;

        restoreSlot();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void applyIdleSilentRotation() {
        silentRotate(visualYaw + 180f, IDLE_PITCH);
    }

    private void silentRotate(float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.setHeadYaw(yaw);
        mc.player.setBodyYaw(yaw);
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** Invertierte Inputs: W=rückwärts, S=vorwärts, A=rechts, D=links */
    private Direction getMovementDirection() {
        if (mc.player == null) return null;

        boolean w = mc.options.forwardKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        if (!w && !s && !a && !d) return null;

        float  yaw     = MathHelper.wrapDegrees(visualYaw);
        double moveYaw = yaw;

        if      (w && !s) moveYaw = yaw + 180;
        else if (s && !w) moveYaw = yaw;
        else if (d && !a) moveYaw = yaw - 90;
        else if (a && !d) moveYaw = yaw + 90;

        moveYaw = MathHelper.wrapDegrees((float) moveYaw);

        if (moveYaw >= -45 && moveYaw < 45)   return Direction.SOUTH;
        if (moveYaw >= 45  && moveYaw < 135)  return Direction.WEST;
        if (moveYaw >= 135 || moveYaw < -135) return Direction.NORTH;
        return Direction.EAST;
    }

    private BlockPos getNextGroundPos(Direction dir) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos feet      = mc.player.getBlockPos();
        BlockPos nextFeet  = feet.offset(dir);
        BlockPos nextFloor = nextFeet.down();

        if (!mc.world.getBlockState(nextFeet).isAir())  return null; // Wand
        if (!mc.world.getBlockState(nextFloor).isAir()) return null; // Boden existiert

        return nextFloor;
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Subtiles A/D-Wackeln – nur wenn Taste gebunden (keyCode != -1).
     * Längeres Interval = weniger Flaggen, weniger Drift.
     */
    private void handleWiggle() {
        long now = System.currentTimeMillis();
        if (now - lastWiggleTime < WIGGLE_INTERVAL) return;
        lastWiggleTime = now;

        wiggleLeft = !wiggleLeft;
        // Sicher prüfen ob Taste überhaupt gebunden ist
        if (mc.options.leftKey.getDefaultKey().getCode()  != -1)
            safeSetPressed(mc.options.leftKey,  wiggleLeft);
        if (mc.options.rightKey.getDefaultKey().getCode() != -1)
            safeSetPressed(mc.options.rightKey, !wiggleLeft);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void handleSneakPulse() {
        long now = System.currentTimeMillis();
        if (sneakQueued && sneakUntil == 0) {
            sneakUntil  = now + 200;
            sneakQueued = false;
        }
        if (sneakUntil > 0) {
            boolean active = now < sneakUntil;
            safeSetPressed(mc.options.sneakKey, active);
            if (!active) sneakUntil = 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Kapselt setPressed – überspringt Tasten mit keyCode -1 (nicht gebunden)
     * um den "Invalid key -1" GL-Error zu verhindern.
     */
    private void safeSetPressed(net.minecraft.client.option.KeyBinding key, boolean pressed) {
        try {
            if (key != null) key.setPressed(pressed);
        } catch (Exception ignored) {}
    }

    private void safeUnpress() {
        safeSetPressed(mc.options.sneakKey,  false);
        safeSetPressed(mc.options.leftKey,   false);
        safeSetPressed(mc.options.rightKey,  false);
        safeSetPressed(mc.options.useKey,    false);
        safeSetPressed(mc.options.sprintKey, false);
    }

    private void restoreSlot() {
        if (lastSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(lastSlot);
            lastSlot = -1;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    public void onMouseMoved(double dx, double dy) {
        if (!isEnabled()) return;
        double sens   = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double factor = sens * sens * sens * 8.0;
        visualYaw   += (float) (dx * factor * 0.15);
        visualPitch += (float) (dy * factor * 0.15);
        visualPitch  = MathHelper.clamp(visualPitch, -90, 90);
    }

    public boolean isSilentActive() { return isEnabled(); }
    public float   getVisualYaw()   { return visualYaw;   }
    public float   getVisualPitch() { return visualPitch; }
}