package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;

public class Tracers extends Module {

    private final EnumSetting<Target> target = register(new EnumSetting<>("Target", "Who to draw tracers to", Target.PLAYERS));
    private final EnumSetting<StartPos> startPos = register(new EnumSetting<>("Start Pos", "Where tracers start from", StartPos.CROSSHAIR));
    private final ColorSetting playerColor = register(new ColorSetting("Player Color", "Tracer color for players", 255, 255, 255, 150));
    private final ColorSetting mobColor = register(new ColorSetting("Mob Color", "Tracer color for mobs", 255, 100, 100, 150));

    public enum Target { PLAYERS, MOBS, ALL }
    public enum StartPos { CROSSHAIR, BOTTOM, TOP }

    public Tracers() {
        super("Tracers", "Draws lines to entities", Category.RENDER);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = camera.getCameraPos();
        
        // Start position relative to camera in world space
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d start;
        switch (startPos.getValue()) {
            case BOTTOM -> {
                Vec3d down = Vec3d.fromPolar(camera.getPitch() + 10, camera.getYaw());
                start = down.multiply(0.5);
            }
            case TOP -> {
                Vec3d up = Vec3d.fromPolar(camera.getPitch() - 10, camera.getYaw());
                start = up.multiply(0.5);
            }
            default -> start = forward.multiply(0.5);
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!shouldDraw(entity)) continue;

            // Interpolated entity position relative to camera
            double x = entity.lastX + (entity.getX() - entity.lastX) * tickDelta - camPos.x;
            double y = entity.lastY + (entity.getY() - entity.lastY) * tickDelta - camPos.y + (entity.getHeight() / 2.0);
            double z = entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta - camPos.z;

            Vec3d end = new Vec3d(x, y, z);
            Color color = (entity instanceof PlayerEntity) ? new Color(playerColor.getArgb(), true) : new Color(mobColor.getArgb(), true);
            
            RenderUtil.drawTracer(posMatrix, start, end, color);
        }
    }

    private boolean shouldDraw(Entity entity) {
        return switch (target.getValue()) {
            case PLAYERS -> entity instanceof PlayerEntity;
            case MOBS -> entity instanceof MobEntity;
            case ALL -> entity instanceof PlayerEntity || entity instanceof MobEntity;
        };
    }
}
