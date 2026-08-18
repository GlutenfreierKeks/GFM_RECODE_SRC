package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Gemeinsame Basis für alle Farm-Module.
 */
public abstract class BaseFarmModule extends Module {

    protected static final Random RNG = new Random();
    private   static final double VANILLA_REACH_SQ = 4.5 * 4.5;

    // ── Gemeinsame Settings ───────────────────────────────────────────────────
    protected final IntSliderSetting range    = register(new IntSliderSetting("Range",    "Scan range", 4,  1,  6));
    protected final IntSliderSetting rotSpeed = register(new IntSliderSetting("RotSpeed", "Rotation speed", 8,  1, 10));
    protected final IntSliderSetting minDelay = register(new IntSliderSetting("MinDelay", "Minimum delay between actions", 0,  0, 10));
    protected final IntSliderSetting maxDelay = register(new IntSliderSetting("MaxDelay", "Maximum delay between actions", 2,  0, 20));
    protected final BoolSetting silentSwitch  = register(new BoolSetting("SilentSwitch", "Switch back to previous slot", false));

    /** Rotation Jitter: zufällige kleine Abweichung vom exakten Block-Mittelpunkt. */
    protected final DoubleSliderSetting rotJitter   = register(new DoubleSliderSetting("RotJitter",  "Rotation jitter amount", 0.08, 0.0, 0.3));
    /** Wie oft der Block-Scan gecacht wird (in Ticks). 0 = jeden Tick scannen. */
    protected final IntSliderSetting scanInterval   = register(new IntSliderSetting("ScanInterval", "How often to scan for blocks", 3, 0, 10));

    // ── Shared State ──────────────────────────────────────────────────────────
    protected BlockPos       currentTarget  = null;
    protected int            rotatingTicks  = 0;
    protected int            tickDelay      = 0;
    protected int            prevSlot       = -1;

    // Scan Cache
    private List<BlockPos>   cachedTargets  = new ArrayList<>();
    private int              scanTimer      = 0;

    private static final int ROT_SETTLE = 1;

    protected BaseFarmModule(String name, String desc, Category cat) {
        super(name, desc, cat);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    protected void resetBase() {
        currentTarget = null;
        rotatingTicks = 0;
        tickDelay     = 0;
        prevSlot      = -1;
        cachedTargets.clear();
        scanTimer     = 0;
    }

    @Override
    public void onDisable() {
        restoreSlot();
        currentTarget = null;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    // ── Haupt-Tick-Logik ──────────────────────────────────────────────────────

    /**
     * Rufe dies am Anfang von onTick() auf.
     * Gibt true zurück wenn der Frame verarbeitet werden soll.
     */
    protected boolean baseTick() {
        if (mc.player == null || mc.world == null) return false;
        RotationUtil.onTick();
        if (tickDelay > 0) { tickDelay--; return false; }
        return true;
    }

    /**
     * Rotiert zum aktuellen Target und gibt true zurück wenn fertig (settled).
     * Baut Rotation Jitter ein.
     */
    protected boolean rotateToTarget() {
        if (currentTarget == null) return false;

        // Jitter: kleiner zufälliger Offset vom Block-Mittelpunkt
        double jit = rotJitter.getValue();
        Vec3d center = new Vec3d(
                currentTarget.getX() + 0.5 + (RNG.nextDouble()-0.5) * jit,
                currentTarget.getY() + 0.5 + (RNG.nextDouble()-0.5) * jit * 0.5,
                currentTarget.getZ() + 0.5 + (RNG.nextDouble()-0.5) * jit
        );

        // Rotation ausführen über RotationUtil
        RotationUtil.rotateToPos(center, (float) rotSpeed.getValue());

        if (!isLookingAt(currentTarget)) { rotatingTicks = 0; return false; }

        rotatingTicks++;
        if (rotatingTicks < ROT_SETTLE) return false;
        rotatingTicks = 0;
        return true;
    }

    /**
     * Wie rotateToTarget() aber mit angegebenem Look-Punkt (z.B. Oberseite des Blocks).
     */
    protected boolean rotateToPoint(Vec3d lookAt) {
        double jit = rotJitter.getValue();
        Vec3d jittered = new Vec3d(
                lookAt.x + (RNG.nextDouble()-0.5) * jit,
                lookAt.y + (RNG.nextDouble()-0.5) * jit * 0.3,
                lookAt.z + (RNG.nextDouble()-0.5) * jit
        );

        // Rotation ausführen über RotationUtil
        RotationUtil.rotateToPos(jittered, (float) rotSpeed.getValue());

        if (!isLookingAtPoint(lookAt)) { rotatingTicks = 0; return false; }

        rotatingTicks++;
        if (rotatingTicks < ROT_SETTLE) return false;
        rotatingTicks = 0;
        return true;
    }

    /**
     * Randomisierter Delay nach einer Aktion.
     */
    protected void applyRandomDelay() {
        int lo = minDelay.getValue();
        int hi = Math.max(lo, maxDelay.getValue());
        tickDelay = lo + (hi > lo ? RNG.nextInt(hi - lo + 1) : 0);
    }

    // ── Reach-Check ──────────────────────────────────────────────────────────

    /**
     * Prüft ob der Block innerhalb Vanilla-Reach liegt.
     */
    protected boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(
                pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) <= VANILLA_REACH_SQ;
    }

    // ── Scan Cache ────────────────────────────────────────────────────────────

    /**
     * Gibt gecachte oder frisch gescannte Target-Liste zurück.
     * Subklassen implementieren scanTargets().
     */
    protected List<BlockPos> getTargets() {
        if (scanTimer <= 0 || cachedTargets.isEmpty()) {
            cachedTargets = scanTargets();
            // Nach Erreichbarkeit priorisieren
            cachedTargets.sort(Comparator.comparingDouble(p ->
                    mc.player.getEyePos().squaredDistanceTo(p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5)));
            scanTimer = scanInterval.getValue();
        } else {
            scanTimer--;
            // Gecachte Targets validieren – nicht mehr gültige entfernen
            cachedTargets.removeIf(p -> !isValidTarget(p));
        }
        return cachedTargets;
    }

    protected abstract boolean isTargetBlock(BlockPos pos, BlockState state);

    /** Scannt alle gültigen Targets in Range. Subklassen implementieren dies. */
    protected abstract List<BlockPos> scanTargets();

    /** Prüft ob ein gecachter Target noch gültig ist. */
    protected abstract boolean isValidTarget(BlockPos pos);

    // ── Slot-Management ───────────────────────────────────────────────────────

    protected void switchToSlot(int slot) {
        if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
    }

    protected void restoreSlot() {
        if (silentSwitch.getValue() && prevSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    // ── Rotation Helpers ──────────────────────────────────────────────────────

    protected boolean isLookingAt(BlockPos pos) {
        return isLookingAtPoint(new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5));
    }

    protected boolean isLookingAtPoint(Vec3d target) {
        double dist = mc.player.getEyePos().distanceTo(target);
        if (dist < 0.01) return true;
        float halfAngle = (float) Math.toDegrees(Math.atan2(0.5, dist));
        float[] rot = RotationUtil.getRotations(target);
        return Math.abs(wrapDeg(rot[0] - mc.player.getYaw())) <= halfAngle
                && Math.abs(wrapDeg(rot[1] - mc.player.getPitch())) <= halfAngle;
    }

    protected static float wrapDeg(float d) {
        while (d >  180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    // ── ESP Helpers ───────────────────────────────────────────────────────────

    /** Subklassen können onRender3D überschreiben und diese Methode nutzen. */
    protected BlockPos getCurrentTarget() { return currentTarget; }
}
