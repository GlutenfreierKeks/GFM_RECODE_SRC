package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

public class AimAssist extends Module {

    // Range & Targeting
    private final DoubleSliderSetting range            = new DoubleSliderSetting("Range",        "Max Distanz zum Ziel",                       4.0,  1.0,  100.0);
    private final DoubleSliderSetting fov              = new DoubleSliderSetting("FOV",          "Sichtfeld für Targeting",                    90.0, 10.0, 180.0);
    private final EnumSetting<TargetMode> targetMode   = new EnumSetting<>("TargetMode",         "Zielauswahl",                                TargetMode.Crosshair);

    // Raven-Style Speed
    private final DoubleSliderSetting speedYaw         = new DoubleSliderSetting("Speed1 Yaw",   "Haupt-Yaw-Geschwindigkeit",                  45.0, 5.0,  100.0);
    private final DoubleSliderSetting complimentYaw    = new DoubleSliderSetting("Speed2 Yaw",   "Komplementär-Yaw-Geschwindigkeit",           15.0, 2.0,  97.0);
    private final DoubleSliderSetting speedPitch       = new DoubleSliderSetting("Speed1 Pitch", "Haupt-Pitch-Geschwindigkeit",                45.0, 5.0,  100.0);
    private final DoubleSliderSetting complimentPitch  = new DoubleSliderSetting("Speed2 Pitch", "Komplementär-Pitch-Geschwindigkeit",         15.0, 2.0,  97.0);

    // Body Aim
    private final DoubleSliderSetting bodyAimHeight    = new DoubleSliderSetting("BodyAimHeight","Zielhöhe (0=Füße, 0.6=Brust, 1=Kopf)",      0.6,  0.0,  1.0);
    private final DoubleSliderSetting pitchOffset      = new DoubleSliderSetting("PitchOffset",  "Pitch-Versatz",                              0.0, -2.0,  2.0);

    // Smoothing
    private final DoubleSliderSetting smoothing        = new DoubleSliderSetting("Smoothing",    "Smoothness pro Frame (höher=smoother)",      0.92, 0.50, 0.99);
    private final DoubleSliderSetting maxTurnSpeed     = new DoubleSliderSetting("MaxTurnSpeed", "Max Grad/Frame",                             20.0,  0.01, 150.0);
    private final DoubleSliderSetting deadzone         = new DoubleSliderSetting("Deadzone",     "Mindestwinkel bevor Assist aktiv",           0.3,  0.0,  3.0);
    private final DoubleSliderSetting accelerationTime = new DoubleSliderSetting("AccelTime",    "Frames bis volle Stärke",                    40.0, 1.0,  120.0);

    // Sticky Aim
    private final BoolSetting stickyAim               = new BoolSetting("StickyAim",            "Schwerer aus Hitbox zu gehen",               true);
    private final DoubleSliderSetting stickyRange      = new DoubleSliderSetting("StickyRange",  "Grad bis Sticky aktiv",                      5.0,  1.0,  15.0);
    private final DoubleSliderSetting stickyStrength   = new DoubleSliderSetting("StickyStr",    "Stärke des Sticky",                          0.4,  0.0,  1.0);

    // Conditions
    private final BoolSetting onlyWhenClicking        = new BoolSetting("OnlyClicking",         "Nur beim Angreifen aktiv",                   false);
    private final BoolSetting onlyWhenHoldingWeapon   = new BoolSetting("OnlyWeapon",           "Nur mit Waffe aktiv",                        true);
    private final BoolSetting ignoreTeammates         = new BoolSetting("IgnoreTeams",          "Keine Teammates targeten",                   true);
    private final BoolSetting aimPitch                = new BoolSetting("AimPitch",             "Pitch ebenfalls korrigieren",                true);

    // State – wird in render3D benutzt (läuft mit voller FPS)
    private double smoothYawVel    = 0;
    private double smoothPitchVel  = 0;
    private int    framesOnTarget  = 0;

    // Wird in onTick gesetzt, in render3D gelesen (thread-safe genug für selben Thread)
    private volatile PlayerEntity currentTarget = null;

    public enum TargetMode { Nearest, Crosshair }

    public AimAssist() {
        super("AimAssist", "Ultrasmooth Aim Assist (Frame-basiert)", Category.PLAYER);
        this.macroAllowed = false;
        register(range); register(fov); register(targetMode);
        register(speedYaw); register(complimentYaw);
        register(speedPitch); register(complimentPitch);
        register(bodyAimHeight); register(pitchOffset);
        register(smoothing); register(maxTurnSpeed); register(deadzone); register(accelerationTime);
        register(stickyAim); register(stickyRange); register(stickyStrength);
        register(onlyWhenClicking); register(onlyWhenHoldingWeapon);
        register(ignoreTeammates); register(aimPitch);
    }

    // ── onTick: nur Target-Suche (20/s reicht dafür) ─────────────
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) { currentTarget = null; return; }
        if (onlyWhenClicking.getValue() && !mc.options.attackKey.isPressed()) { currentTarget = null; return; }
        if (onlyWhenHoldingWeapon.getValue() && !isHoldingWeapon())           { currentTarget = null; return; }
        currentTarget = findTarget();
    }

    // ── render3D: Rotation mit voller FPS → kein Ruckeln ─────────
    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.player == null || mc.currentScreen != null) return;

        PlayerEntity target = currentTarget;

        if (target == null || target.isDead() || !target.isAlive()) {
            // Sanfter Fade-out
            smoothYawVel   *= 0.85;
            smoothPitchVel *= 0.85;
            framesOnTarget  = 0;

            if (Math.abs(smoothYawVel) > 0.0001 || Math.abs(smoothPitchVel) > 0.0001) {
                applyRotation((float) smoothYawVel, (float) smoothPitchVel);
            } else {
                smoothYawVel  = 0;
                smoothPitchVel = 0;
            }
            return;
        }

        framesOnTarget++;
        applyAimAssist(target);
    }

    // ─────────────────────────────────────────────────────────────
    //  Kernlogik (läuft jetzt mit voller FPS)
    // ─────────────────────────────────────────────────────────────

    private void applyAimAssist(PlayerEntity target) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to   = getTargetPos(target);

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double targetYaw   = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        double horizDist   = Math.sqrt(dx * dx + dz * dz);
        double targetPitch = -Math.toDegrees(Math.atan2(dy, horizDist));

        double diffYaw   = MathHelper.wrapDegrees(targetYaw   - mc.player.getYaw());
        double diffPitch = MathHelper.wrapDegrees(targetPitch - mc.player.getPitch() + pitchOffset.getValue());

        // ── Deadzone ──────────────────────────────────────────────
        double dz2 = deadzone.getValue();
        if (Math.abs(diffYaw)   < dz2) diffYaw   = 0;
        if (Math.abs(diffPitch) < dz2) diffPitch = 0;

        if (diffYaw == 0 && diffPitch == 0) {
            smoothYawVel   *= 0.7;
            smoothPitchVel *= 0.7;
            if (Math.abs(smoothYawVel)   < 0.00003) smoothYawVel   = 0;
            if (Math.abs(smoothPitchVel) < 0.00003) smoothPitchVel = 0;
            return;
        }

        // ── Acceleration (frame-basiert, ease-in³ für noch weicheren Start) ──
        double accelMult = Math.min(framesOnTarget / accelerationTime.getValue(), 1.0);
        accelMult = accelMult * accelMult * accelMult; // kubisch = sehr sanfter Einstieg

        // ── Sticky Aim ────────────────────────────────────────────
        double angleDist   = Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
        double stickyBoost = 0;
        if (stickyAim.getValue() && angleDist < stickyRange.getValue()) {
            double stickyT = 1.0 - (angleDist / stickyRange.getValue());
            stickyBoost = stickyStrength.getValue() * stickyT * 0.05; // Frame-skaliert klein halten
        }

        // ── Raven-Formel (frame-skaliert klein) ───────────────────
        // Skalierungsfaktor /20.0 weil die Formel für Ticks ausgelegt war,
        // wir aber jetzt pro Frame arbeiten (~60-144 fps statt 20 tps)
        double frameScale = 1.0 / 20.0;

        double rawYawVel = 0;
        if (Math.abs(diffYaw) > dz2) {
            double rndComp  = ThreadLocalRandom.current().nextDouble(
                    complimentYaw.getValue() - 1.473,
                    complimentYaw.getValue() + 2.483);
            double rndSpeed = ThreadLocalRandom.current().nextDouble(
                    speedYaw.getValue() - 4.724,
                    speedYaw.getValue());

            double complSpeed = diffYaw * (rndComp / 100.0);
            rawYawVel = (complSpeed + (diffYaw / (101.0 - rndSpeed))) * frameScale * accelMult;

            if (stickyBoost > 0 && Math.abs(rawYawVel) < stickyBoost)
                rawYawVel = Math.signum(diffYaw) * stickyBoost * accelMult;
        }

        double rawPitchVel = 0;
        if (aimPitch.getValue() && Math.abs(diffPitch) > dz2) {
            double rndComp  = ThreadLocalRandom.current().nextDouble(
                    complimentPitch.getValue() - 1.473,
                    complimentPitch.getValue() + 2.483);
            double rndSpeed = ThreadLocalRandom.current().nextDouble(
                    speedPitch.getValue() - 4.724,
                    speedPitch.getValue());

            double complSpeed = diffPitch * (rndComp / 100.0);
            rawPitchVel = (complSpeed + (diffPitch / (101.0 - rndSpeed))) * frameScale * accelMult;

            if (stickyBoost > 0 && Math.abs(rawPitchVel) < stickyBoost)
                rawPitchVel = Math.signum(diffPitch) * stickyBoost * accelMult;
        }

        // ── Overshoot verhindern ──────────────────────────────────
        rawYawVel   = clampAbs(rawYawVel,   Math.abs(diffYaw));
        rawPitchVel = clampAbs(rawPitchVel, Math.abs(diffPitch));

        // ── Hochfrequentes Smoothing ───────────────────────────────
        // Bei 60fps und s=0.92 braucht der Aim ~12 Frames zum Einpendeln
        // Bei 144fps und s=0.92 ist er noch smoother → kein Ruckeln mehr
        double s = smoothing.getValue();
        smoothYawVel   = smoothYawVel   * s + rawYawVel   * (1.0 - s);
        smoothPitchVel = smoothPitchVel * s + rawPitchVel * (1.0 - s);

        // Threshold: unter 0.00003 Grad passiert nichts – kein Mikrozittern
        if (Math.abs(smoothYawVel)   < 0.00003) smoothYawVel   = 0;
        if (Math.abs(smoothPitchVel) < 0.00003) smoothPitchVel = 0;

        // ── Overshoot nach Smooth ─────────────────────────────────
        smoothYawVel   = clampAbs(smoothYawVel,   Math.abs(diffYaw));
        smoothPitchVel = clampAbs(smoothPitchVel, Math.abs(diffPitch));

        // ── MaxTurnSpeed (pro Frame, nicht pro Tick) ──────────────
        double maxSpeed   = maxTurnSpeed.getValue();
        float  yawDelta   = (float) MathHelper.clamp(smoothYawVel,   -maxSpeed, maxSpeed);
        float  pitchDelta = (float) MathHelper.clamp(smoothPitchVel, -maxSpeed, maxSpeed);

        applyRotation(yawDelta, pitchDelta);
    }

    // ─────────────────────────────────────────────────────────────
    //  Hilfsmethoden
    // ─────────────────────────────────────────────────────────────

    private Vec3d getTargetPos(PlayerEntity target) {
        double height = target.getHeight() * bodyAimHeight.getValue();
        return new Vec3d(target.getX(), target.getY() + height, target.getZ());
    }

    private double clampAbs(double value, double limit) {
        return MathHelper.clamp(value, -limit, limit);
    }

    private void applyRotation(float yawDelta, float pitchDelta) {
        mc.player.setYaw(mc.player.getYaw() + yawDelta);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchDelta, -90.0f, 90.0f));
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        var stack = mc.player.getMainHandStack();
        return !stack.isEmpty() && stack.getMaxDamage() > 0;
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.isFriend(player.getName().getString())) continue;
            if (player.isDead() || !player.isAlive()) continue;
            if (ignoreTeammates.getValue() && isTeammate(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range.getValue()) continue;

            double angle = getAngleTo(player);
            if (angle > fov.getValue() / 2.0) continue;

            double score = targetMode.getValue() == TargetMode.Crosshair ? angle : dist;
            if (score < bestScore) { bestScore = score; best = player; }
        }

        return best;
    }

    private boolean isTeammate(PlayerEntity player) {
        return mc.player.getScoreboardTeam() != null &&
                mc.player.getScoreboardTeam().equals(player.getScoreboardTeam());
    }

    private double getAngleTo(PlayerEntity target) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to   = getTargetPos(target);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double yawDiff   = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90 - mc.player.getYaw());
        double pitchDiff = MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) - mc.player.getPitch());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private void resetState() {
        smoothYawVel   = 0;
        smoothPitchVel = 0;
        framesOnTarget = 0;
        currentTarget  = null;
    }

    @Override public void onEnable()  { resetState(); }
    @Override public void onDisable() { resetState(); }
}