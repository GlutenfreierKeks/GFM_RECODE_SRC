package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class Freecam extends Module {

    private Vec3d cameraPos;
    private float cameraYaw;
    private float cameraPitch;
    
    private float playerYawOrig;
    private float playerPitchOrig;
    
    private float mouseSensitivity = 0.12f;
    
    private long lastTime = 0;

    public final DoubleSliderSetting speed = register(new DoubleSliderSetting("Speed", "Movement speed", 1.0, 0.05, 5.0));

    private Vec3d lookTarget = null;
    private boolean lookTargetPressed = false;
    private final de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting lookTargetKey = register(new de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting("Look Target Key", "Press to lock/unlock camera to a point", GLFW.GLFW_KEY_X));

    public Freecam() {
        super("Freecam", "Allows free camera movement", Category.RENDER);
        getKeybindSetting().setValue(GLFW.GLFW_KEY_V);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        if (mc.player != null && mc.mouse != null && mc.world != null) {
            cameraPos = mc.player.getEyePos();
            
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            
            playerYawOrig = mc.player.getYaw();
            playerPitchOrig = mc.player.getPitch();
            
            lastTime = 0;
            lookTarget = null;
        }
    }

    @Override
    protected void onDisable() {
        lookTarget = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        
        // Secondary layer of blocking input
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    public void onMouseMoved(double dx, double dy) {
        if (isEnabled() && mc.currentScreen == null && mc.isWindowFocused()) {
            if (lookTarget == null) {
                cameraYaw += (float) (dx * mouseSensitivity);
                cameraPitch += (float) (dy * mouseSensitivity);
                cameraPitch = Math.max(-90, Math.min(90, cameraPitch));
            }
        }
    }

    public void updateCamera() {
        if (mc.player == null || mc.mouse == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (lastTime == 0) lastTime = now;
        double delta = (now - lastTime) / 1000.0;
        lastTime = now;

        if (delta > 0.1) delta = 0.1;

        // Toggle look target
        boolean isPressed = lookTargetKey.isPressed(mc.getWindow().getHandle());
        if (isPressed) {
            if (!lookTargetPressed) {
                if (lookTarget == null) {
                    HitResult hr = mc.world.raycast(new RaycastContext(
                        cameraPos,
                        cameraPos.add(Vec3d.fromPolar(cameraPitch, cameraYaw).multiply(100)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                    ));
                    if (hr != null && hr.getType() != HitResult.Type.MISS) {
                        lookTarget = hr.getPos();
                    }
                } else {
                    lookTarget = null;
                }
                lookTargetPressed = true;
            }
        } else {
            lookTargetPressed = false;
        }

        // Apply auto-look
        if (lookTarget != null) {
            double dx = lookTarget.x - cameraPos.x;
            double dy = lookTarget.y - cameraPos.y;
            double dz = lookTarget.z - cameraPos.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
            
            cameraYaw = interpolateAngle(cameraYaw, targetYaw, 0.15f);
            cameraPitch = interpolateAngle(cameraPitch, targetPitch, 0.15f);
        }

        double moveSpeed = speed.getValue() * delta * 20.0;
        
        Vec3d forward = Vec3d.fromPolar(0, cameraYaw).normalize();
        Vec3d right = Vec3d.fromPolar(0, cameraYaw + 90).normalize();
        Vec3d move = Vec3d.ZERO;

        long window = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) move = move.add(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) move = move.subtract(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) move = move.subtract(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) move = move.add(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) move = move.add(0, 1, 0);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) move = move.subtract(0, 1, 0);
        
        if (move.lengthSquared() > 0) {
            cameraPos = cameraPos.add(move.normalize().multiply(moveSpeed));
        }

        mc.player.setYaw(playerYawOrig);
        mc.player.setPitch(playerPitchOrig);
        mc.player.setBodyYaw(playerYawOrig);
        mc.player.setHeadYaw(playerYawOrig);
    }

    private float interpolateAngle(float start, float end, float pct) {
        float diff = net.minecraft.util.math.MathHelper.wrapDegrees(end - start);
        return start + diff * pct;
    }

    public HitResult getPlayerInteractionTarget(double reach) {
        if (mc.player == null) return null;
        
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d rotationVec = Vec3d.fromPolar(playerPitchOrig, playerYawOrig);
        Vec3d endPos = eyePos.add(rotationVec.multiply(reach));
        
        return mc.world.raycast(new RaycastContext(
            eyePos,
            endPos,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
    }

    public void onMouseScroll(double vertical) {
        if (isEnabled()) {
            speed.setValue(Math.max(0.05, Math.min(5.0, speed.getValue() + (vertical * 0.1))));
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, net.minecraft.client.render.Camera camera, float tickDelta) {
        updateCamera();
        
        if (lookTarget != null) {
            Vec3d camPos = camera.getCameraPos();
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                lookTarget.x - 0.05, lookTarget.y - 0.05, lookTarget.z - 0.05,
                lookTarget.x + 0.05, lookTarget.y + 0.05, lookTarget.z + 0.05
            ).offset(-camPos.x, -camPos.y, -camPos.z);
            
            de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil.drawFilledBox(posMatrix, box, new java.awt.Color(255, 255, 255, 120));
            de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil.drawBox(posMatrix, box, new java.awt.Color(217, 111, 212), 1.0);
        }
    }

    public Vec3d getCameraPos() { return cameraPos != null ? cameraPos : (mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO); }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    
    public static boolean isFakePlayer(Entity entity) { return false; }
}
