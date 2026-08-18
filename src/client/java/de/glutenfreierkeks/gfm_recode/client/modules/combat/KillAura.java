package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {

    private LivingEntity currentTarget;
    private float visualYaw;
    private float visualPitch;
    private boolean isRotating = false;
    private boolean isJumping = false;

    // ── Settings ──────────────────────────────────────────────────
    public final DoubleSliderSetting range = register(new DoubleSliderSetting("Range", "Attack range", 3.8, 1.0, 6.0, 1));
    public final BoolSetting perfectCooldown = register(new BoolSetting("Perfect Cooldown", "Attack at exactly 1.0 cooldown", true));
    public final BoolSetting onlyPlayers = register(new BoolSetting("Only Players", "Target only players", true));
    public final BoolSetting silent = register(new BoolSetting("Silent", "Hide rotation from your view while keeping free mouse movement", true));
    public final ColorSetting targetColor = register(new ColorSetting("Target Color", "Highlight color", 255, 80, 80, 180));

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities with silent rotation", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            visualYaw = mc.player.getYaw();
            visualPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            currentTarget = null;
            isRotating = false;
            return;
        }

        findTarget();

        if (currentTarget != null) {
            // Calculate rotation to target
            float[] rotations = RotationUtil.getRotations(currentTarget.getEyePos());
            
            // Set player rotation (Server sees this)
            mc.player.setYaw(rotations[0]);
            mc.player.setPitch(rotations[1]);
            mc.player.setHeadYaw(rotations[0]);
            mc.player.setBodyYaw(rotations[0]);
            isRotating = true;

            // Attack logic
            boolean cooldownReady = !perfectCooldown.getValue() || mc.player.getAttackCooldownProgress(0) >= 1.0f;

            if (cooldownReady) {
                attack(currentTarget);
            }
        } else {
            // When no target, sync server rotation with client view to avoid snapping/glitching
            mc.player.setYaw(visualYaw);
            mc.player.setPitch(visualPitch);
            mc.player.setHeadYaw(visualYaw);
            mc.player.setBodyYaw(visualYaw);
            
            isRotating = false;
            isJumping = false;
        }
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;
        
        MaceSwap maceSwap = Gfm_recodeClient.modules.getModuleByClass(MaceSwap.class);
        if (maceSwap != null && maceSwap.isEnabled()) {
            if (maceSwap.onAttack(target)) return;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void findTarget() {
        double r = range.getValue();
        List<LivingEntity> targets = mc.world.getEntitiesByClass(LivingEntity.class, 
            mc.player.getBoundingBox().expand(r), 
            e -> e != mc.player && e.isAlive() && mc.player.distanceTo(e) <= r && (!onlyPlayers.getValue() || e instanceof PlayerEntity)
                 && !(e instanceof PlayerEntity && de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.isFriend(e.getName().getString()))
        );

        currentTarget = targets.stream()
            .min(Comparator.comparingDouble(mc.player::distanceTo))
            .orElse(null);
    }

    public void onMouseMoved(double dx, double dy) {
        if (isEnabled() && silent.getValue()) {
            // Adjust sensitivity similarly to how Minecraft does it
            double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double factor = sensitivity * sensitivity * sensitivity * 8.0;
            
            visualYaw += (float) (dx * factor * 0.15);
            visualPitch += (float) (dy * factor * 0.15);
            visualPitch = MathHelper.clamp(visualPitch, -90, 90);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setYaw(visualYaw);
            mc.player.setPitch(visualPitch);
        }
        isRotating = false;
        currentTarget = null;
        isJumping = false;
    }

    public boolean isSilentActive() { return isEnabled() && silent.getValue(); }
    public float getVisualYaw() { return visualYaw; }
    public float getVisualPitch() { return visualPitch; }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (currentTarget == null || !currentTarget.isAlive()) return;
        Color color = targetColor.getJavaColor();
        Vec3d camPos = camera.getCameraPos();
        Box box = currentTarget.getBoundingBox().offset(-camPos.x, -camPos.y, -camPos.z);
        RenderUtil.drawBox(posMatrix, box, color, 1.5);
        RenderUtil.drawFilledBox(posMatrix, box, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
    }
}
