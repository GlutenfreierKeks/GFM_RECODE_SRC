package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

public class ESP extends Module {

    public final EnumSetting<Target> target = register(new EnumSetting<>("Target", "What to highlight", Target.PLAYERS));
    public final EnumSetting<RenderFx.VisualMode> visualMode = register(new EnumSetting<>("Visual Mode", "Simple or shader visuals", RenderFx.VisualMode.SHADER));
    public final ColorSetting playerColor = register(new ColorSetting("Player Color", "Color for players", 100, 200, 255, 220));
    public final ColorSetting mobColor = register(new ColorSetting("Mob Color", "Color for mobs", 255, 100, 100, 220));
    public final DoubleSliderSetting lineWidth = register(new DoubleSliderSetting("Line Width", "Thickness", 1.2, 0.5, 3.0, 1));
    public final BoolSetting showName = register(new BoolSetting("Show Name", "Display name tag above entity", true));
    public final BoolSetting throughWalls = register(new BoolSetting("Through Walls", "Show through walls", true));
    public final BoolSetting bloom = register(new BoolSetting("Bloom", "Soft inner bloom", true));
    public final BoolSetting strongGlow = register(new BoolSetting("Strong Glow", "Extra wide glow layers", true));
    public final BoolSetting scannerFx = register(new BoolSetting("Scanner", "Animated model scan band", false));
    public final BoolSetting pulse = register(new BoolSetting("Pulse", "Animated pulse ring", true));
    public final BoolSetting halo = register(new BoolSetting("Halo", "Extra orbit rings", true));

    public enum Target { PLAYERS, MOBS, ALL }

    public ESP() {
        super("ESP", "Highlights entities through walls", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        RenderFx.ShaderOptions shaderOptions = new RenderFx.ShaderOptions(
                lineWidth.getValue().floatValue(),
                bloom.getValue(),
                strongGlow.getValue(),
                scannerFx.getValue(),
                pulse.getValue(),
                halo.getValue()
        );

        VertexConsumer vc = RenderUtil.beginBatch();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !shouldHighlight(entity)) {
                continue;
            }

            Color color = getEntityColor(entity);
            double x = entity.lastX + (entity.getX() - entity.lastX) * tickDelta;
            double y = entity.lastY + (entity.getY() - entity.lastY) * tickDelta;
            double z = entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta;

            double rx = x - camPos.x;
            double ry = y - camPos.y;
            double rz = z - camPos.z;

            float width = entity.getWidth();
            float height = entity.getHeight();
            double radius = (width / 2.0) * Math.sqrt(2.0) + 0.05;
            Box box = new Box(
                    rx - width / 2.0, ry, rz - width / 2.0,
                    rx + width / 2.0, ry + height, rz + width / 2.0
            );

            if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                RenderFx.renderSimpleEntity(vc, posMatrix, box, color);
            } else {
                RenderFx.renderShaderEntity(vc, posMatrix, rx, ry, rz, height, radius, color, shaderOptions, entity.getId() * 0.173);
            }
        }
        RenderUtil.endBatch();
    }

    private boolean shouldHighlight(Entity entity) {
        return switch (target.getValue()) {
            case PLAYERS -> entity instanceof PlayerEntity;
            case MOBS -> entity instanceof MobEntity;
            case ALL -> entity instanceof PlayerEntity || entity instanceof MobEntity;
        };
    }

    private Color getEntityColor(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return new Color(playerColor.getArgb(), true);
        }
        return new Color(mobColor.getArgb(), true);
    }
}
