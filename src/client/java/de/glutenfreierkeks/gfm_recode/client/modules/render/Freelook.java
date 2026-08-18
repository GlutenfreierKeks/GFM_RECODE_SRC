package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class Freelook extends Module {

    private float orbitYaw;
    private float orbitPitch;

    private float lockedYaw;
    private float lockedPitch;

    private boolean active = false;

    // Aktuell berechnete Kamera-Werte (direkt aus lerpedPos, kein extra Smoothing)
    private Vec3d cachedCameraPos = null;
    private float lookYaw;
    private float lookPitch;

    public final KeybindSetting holdKey       = register(new KeybindSetting("Hold Key", "a"));
    public final DoubleSliderSetting distance = register(new DoubleSliderSetting("Camera Distance", "Distance from player", 5.0, 1.0, 20.0, 1));

    public Freelook() {
        super("Freelook", "Hold a key to freely orbit the camera around your character", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        orbitYaw        = mc.player.getYaw();
        orbitPitch      = 20f;
        lockedYaw       = mc.player.getYaw();
        lockedPitch     = mc.player.getPitch();
        cachedCameraPos = mc.player.getEyePos();
        lookYaw         = orbitYaw + 180f;
        lookPitch       = -orbitPitch;
        active          = false;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setYaw(lockedYaw);
            mc.player.setPitch(lockedPitch);
        }
        active          = false;
        cachedCameraPos = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean keyDown = GLFW.glfwGetKey(mc.getWindow().getHandle(), holdKey.getValue()) == GLFW.GLFW_PRESS;

        if (keyDown) {
            if (!active) {
                orbitYaw    = mc.player.getYaw();
                orbitPitch  = 20f;
                lockedYaw   = mc.player.getYaw();
                lockedPitch = mc.player.getPitch();
                active      = true;
            }
            mc.player.setYaw(lockedYaw);
            mc.player.setPitch(lockedPitch);
            mc.player.setHeadYaw(lockedYaw);
            mc.player.setBodyYaw(lockedYaw);
        } else {
            if (active) {
                mc.player.setYaw(lockedYaw);
                mc.player.setPitch(lockedPitch);
                active = false;
            }
        }
    }

    public void onMouseMoved(double dx, double dy) {
        if (!active) return;
        double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double factor      = sensitivity * sensitivity * sensitivity * 8.0;
        orbitYaw   += (float) (dx * factor * 0.15);
        orbitPitch += (float) (dy * factor * 0.15);
        orbitPitch  = MathHelper.clamp(orbitPitch, -89f, 89f);
    }

    /**
     * Berechnet die Kameraposition direkt aus getLerpedPos(tickDelta) –
     * kein manuelles Smoothing, Minecraft interpoliert bereits frame-genau.
     */
    public void updateCamera(float tickDelta) {
        if (!active || mc.player == null) return;

        double dist     = distance.getValue();
        double yawRad   = Math.toRadians(orbitYaw);
        double pitchRad = Math.toRadians(orbitPitch);

        // Spherical → Cartesian um den Spieler
        double offX =  Math.sin(yawRad) * Math.cos(pitchRad) * dist;
        double offY =  Math.sin(pitchRad) * dist;
        double offZ = -Math.cos(yawRad) * Math.cos(pitchRad) * dist;

        // getLerpedPos(tickDelta) gibt die frame-interpolierte Spielerposition –
        // das ist die einzige Interpolation die wir brauchen, kein extra Smoothing
        Vec3d eye = mc.player.getLerpedPos(tickDelta)
                .add(0, mc.player.getStandingEyeHeight(), 0);

        cachedCameraPos = new Vec3d(eye.x + offX, eye.y + offY, eye.z + offZ);

        // Richtung von Kamera zurück zum Spielerauge → Rotation
        double dx        = eye.x - cachedCameraPos.x;
        double dy        = eye.y - cachedCameraPos.y;
        double dz        = eye.z - cachedCameraPos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        lookYaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        lookPitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        updateCamera(tickDelta);
    }

    public boolean isActive()     { return isEnabled() && active; }
    public float getCameraYaw()   { return lookYaw; }
    public float getCameraPitch() { return lookPitch; }
    public Vec3d getCameraPos()   {
        if (cachedCameraPos != null) return cachedCameraPos;
        return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
    }
}