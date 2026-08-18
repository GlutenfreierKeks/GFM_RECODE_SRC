package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;

public class AutoAttack extends Module {

    // --- SETTINGS ---
    private final DoubleSliderSetting range = register(new DoubleSliderSetting("Range", "Attack range", 3.0, 1.0, 6.0));
    private final BoolSetting throughWalls = register(new BoolSetting("ThroughWalls", "Attack through walls", false));
    private final BoolSetting players = register(new BoolSetting("Players", "Attack players", true));
    private final BoolSetting mobs = register(new BoolSetting("Mobs", "Attack mobs", true));
    private final BoolSetting animals = register(new BoolSetting("Animals", "Attack animals", false));
    private final BoolSetting rotate = register(new BoolSetting("Rotate", "Rotate to target", true));
    private final BoolSetting onlyWhileHoldingWeapon = register(new BoolSetting("OnlyWhileHoldingWeapon", "Only attack with weapon", false));

    private Entity currentTarget = null;

    public AutoAttack() {
        super("AutoAttack", "Automatically attacks entities with perfect hit cooldown", Category.MISC);
    }

    @Override
    public void onEnable() {
        currentTarget = null;
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Check if holding weapon requirement
        if (onlyWhileHoldingWeapon.getValue() && !isHoldingWeapon()) {
            return;
        }

        // Find target
        currentTarget = findTarget();

        if (currentTarget == null) return;

        // Check attack cooldown (perfect timing)
        if (!canAttack()) return;

        // Rotate to target if enabled
        if (rotate.getValue()) {
            RotationUtil.rotateToPos(currentTarget.getEyePos(), 10.0f);
        }

        // Only attack if looking at target (Grim-friendly)
        if (!isLookingAt(currentTarget)) return;

        // Check wall detection
        if (!throughWalls.getValue() && !canSeeEntity(currentTarget)) {
            return;
        }

        // Attack
        attackEntity(currentTarget);
    }

    private boolean isLookingAt(Entity entity) {
        if (mc.player == null || entity == null) return false;
        Vec3d target = entity.getEyePos();
        double dist = mc.player.getEyePos().distanceTo(target);
        if (dist < 0.01) return true;
        float halfAngle = (float) Math.toDegrees(Math.atan2(0.4, dist));
        float[] rot = RotationUtil.getRotations(target);
        return Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw())) <= halfAngle
                && Math.abs(MathHelper.wrapDegrees(rot[1] - mc.player.getPitch())) <= halfAngle;
    }

    private Entity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        List<Entity> entities = mc.world.getEntitiesByClass(
                Entity.class,
                new Box(mc.player.getBlockPos()).expand(range.getValue()),
                this::isValidTarget
        );

        if (entities.isEmpty()) return null;

        // Sort by distance (closest first)
        return entities.stream()
                .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (entity.isSpectator()) return false;
        if (mc.player.squaredDistanceTo(entity) > range.getValue() * range.getValue()) return false;

        // Check entity type
        if (entity instanceof PlayerEntity) {
            return players.getValue() && entity != mc.player && !de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.isFriend(entity.getName().getString());
        }
        if (entity instanceof Monster) {
            return mobs.getValue();
        }
        if (entity instanceof PassiveEntity) {
            return animals.getValue();
        }

        return false;
    }

    private boolean canAttack() {
        if (mc.player == null) return false;

        // Check if attack cooldown is ready (1.0 = 100% charged)
        return mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
    }

    private boolean canSeeEntity(Entity entity) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d entityPos = entity.getBoundingBox().getCenter();

        RaycastContext context = new RaycastContext(
                playerPos,
                entityPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        HitResult result = mc.world.raycast(context);

        // If raycast hits a block before reaching entity, can't see through walls
        return result.getType() == HitResult.Type.MISS;
    }

    private void attackEntity(Entity entity) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Attack the entity
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;

        var item = mc.player.getMainHandStack().getItem();
        String itemName = item.toString().toLowerCase();

        // Check for common weapon names
        return itemName.contains("sword") ||
                itemName.contains("axe") ||
                itemName.contains("trident") ||
                itemName.contains("mace");
    }

    public String getDisplayInfo() {
        if (currentTarget != null) {
            return currentTarget.getName().getString();
        }
        return "No Target";
    }
}
