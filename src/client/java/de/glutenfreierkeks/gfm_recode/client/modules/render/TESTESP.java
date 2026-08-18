package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderBlockAnimationUtility;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderRender2D;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderRender3D;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

public class TESTESP extends Module {
    private final EnumSetting<Target> target = register(new EnumSetting<>("Target", "Entities to render", Target.PLAYERS));
    private final EnumSetting<ThunderMode> mode = register(new EnumSetting<>("Mode", "Main Thunder ESP mode", ThunderMode.THUNDER_TARGET));
    private final EnumSetting<BoxMode> boxMode = register(new EnumSetting<>("Box Mode", "Box render style", BoxMode.FADE));
    private final EnumSetting<BlockMode> blockMode = register(new EnumSetting<>("Block Mode", "Block animation source", BlockMode.RING));
    private final EnumSetting<ThunderBlockAnimationUtility.BlockAnimationMode> blockAnimation = register(new EnumSetting<>("Block Animation", "Thunder block animation", ThunderBlockAnimationUtility.BlockAnimationMode.Fade));
    private final EnumSetting<ThunderBlockAnimationUtility.BlockRenderMode> blockRender = register(new EnumSetting<>("Block Render", "Thunder block render type", ThunderBlockAnimationUtility.BlockRenderMode.All));
    private final ColorSetting playerColor = register(new ColorSetting("Player Color", "Player ESP color", 120, 210, 255, 210));
    private final ColorSetting mobColor = register(new ColorSetting("Mob Color", "Mob ESP color", 255, 95, 95, 205));
    private final ColorSetting itemColor = register(new ColorSetting("Item Color", "Item ESP color", 255, 220, 120, 205));
    private final ColorSetting fillColor = register(new ColorSetting("Fill Color", "Fill color", 120, 180, 255, 42));
    private final ColorSetting fadeTopColor = register(new ColorSetting("Fade Top", "Fade top color", 255, 255, 255, 115));
    private final BoolSetting astolfo = register(new BoolSetting("Astolfo", "Use animated Thunder colors", false));
    private final BoolSetting boxes = register(new BoolSetting("Boxes", "Render Thunder boxes", true));
    private final BoolSetting targetEsp = register(new BoolSetting("Target ESP", "Render Thunder target spiral", true));
    private final BoolSetting oldTarget = register(new BoolSetting("Old Target", "Render old moving ring", true));
    private final BoolSetting ghosts = register(new BoolSetting("Ghosts", "Render orbiting ghost particles", true));
    private final BoolSetting circle = register(new BoolSetting("Circle", "Render circle line", true));
    private final BoolSetting crosses = register(new BoolSetting("Crosses", "Render bottom crosses", false));
    private final BoolSetting sphere = register(new BoolSetting("Sphere", "Render Thunder sphere", false));
    private final BoolSetting cylinder = register(new BoolSetting("Cylinder", "Render Thunder cylinder", false));
    private final BoolSetting sideFill = register(new BoolSetting("Side Fill", "Render one filled side", false));
    private final BoolSetting blockAnimations = register(new BoolSetting("Block Animations", "Render Thunder block animations around player", true));
    private final BoolSetting throughWalls = register(new BoolSetting("Through Walls", "Use see-through batch layer", true));
    private final DoubleSliderSetting range = register(new DoubleSliderSetting("Range", "Max render range", 80.0, 8.0, 180.0, 1));
    private final DoubleSliderSetting lineWidth = register(new DoubleSliderSetting("Line Width", "Line thickness", 1.5, 0.5, 6.0, 1));
    private final DoubleSliderSetting boxExpand = register(new DoubleSliderSetting("Box Expand", "Box expansion", 0.04, 0.0, 0.4, 2));
    private final DoubleSliderSetting circleRadius = register(new DoubleSliderSetting("Circle Radius", "Circle radius multiplier", 0.85, 0.3, 2.5, 2));
    private final IntSliderSetting ghostLength = register(new IntSliderSetting("Ghost Length", "Ghost trail length", 12, 3, 42));
    private final IntSliderSetting ghostFactor = register(new IntSliderSetting("Ghost Factor", "Ghost orbit factor", 4, 1, 12));
    private final DoubleSliderSetting ghostShake = register(new DoubleSliderSetting("Ghost Shake", "Ghost wave divisor", 2.5, 0.5, 8.0, 1));
    private final DoubleSliderSetting ghostAmplitude = register(new DoubleSliderSetting("Ghost Amp", "Ghost wave amplitude", 1.2, 0.1, 5.0, 1));
    private final IntSliderSetting blockRadius = register(new IntSliderSetting("Block Radius", "Block animation radius", 2, 1, 5));

    public enum Target { PLAYERS, MOBS, ITEMS, LIVING, ALL }
    public enum ThunderMode { THUNDER_TARGET, OLD_RING, GHOSTS, BOXES, FULL }
    public enum BoxMode { OUTLINE, FILL, FADE, BOTH, SIDE }
    public enum BlockMode { PLAYER_POS, RING, CROSS }

    public TESTESP() {
        super("TESTESP", "ThunderHack render logic test module", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void onTick() {
        ThunderRender3D.updateTargetESP();
        if (mc.player != null && blockAnimations.getValue()) {
            queueBlockAnimations();
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        VertexConsumer vc = RenderUtil.beginBatch();
        Vec3d camPos = camera.getCameraPos();
        double maxDistanceSq = range.getValue() * range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !shouldRender(entity) || entity.squaredDistanceTo(mc.player) > maxDistanceSq) {
                continue;
            }

            Color color = getColor(entity);
            Box box = getEntityBox(entity, camPos, tickDelta).expand(boxExpand.getValue());
            Vec3d center = ThunderRender3D.interpolate(entity, camera, tickDelta, entity.getHeight() * 0.5);

            if (mode.getValue() == ThunderMode.BOXES || mode.getValue() == ThunderMode.FULL || boxes.getValue()) {
                renderBoxMode(vc, posMatrix, box, color);
            }
            if ((mode.getValue() == ThunderMode.THUNDER_TARGET || mode.getValue() == ThunderMode.FULL) && targetEsp.getValue()) {
                ThunderRender3D.drawTargetEsp(vc, posMatrix, entity, camera, tickDelta, color);
            }
            if ((mode.getValue() == ThunderMode.OLD_RING || mode.getValue() == ThunderMode.FULL) && oldTarget.getValue()) {
                ThunderRender3D.drawOldTargetEsp(vc, posMatrix, entity, camera, tickDelta, color);
            }
            if ((mode.getValue() == ThunderMode.GHOSTS || mode.getValue() == ThunderMode.FULL) && ghosts.getValue()) {
                ThunderRender3D.renderGhosts(vc, posMatrix, camera, tickDelta, ghostLength.getValue(), ghostFactor.getValue(), ghostShake.getValue().floatValue(), ghostAmplitude.getValue().floatValue(), entity, color);
            }
            if (circle.getValue()) {
                ThunderRender3D.drawCircle3D(vc, posMatrix, entity, camera, tickDelta, entity.getWidth() * circleRadius.getValue().floatValue(), color, 56, astolfo.getValue(), 8);
            }
            if (crosses.getValue()) {
                ThunderRender3D.renderCrosses(vc, posMatrix, box, color, lineWidth.getValue().floatValue());
            }
            if (sphere.getValue()) {
                ThunderRender3D.drawSphere(vc, posMatrix, center, entity.getWidth() * 0.75f, 24, 12, ThunderRender2D.injectAlpha(color, 125));
            }
            if (cylinder.getValue()) {
                ThunderRender3D.drawCylinder(vc, posMatrix, new Vec3d(center.x, box.minY, center.z), entity.getWidth() * 0.75f, entity.getHeight(), 32, 5, ThunderRender2D.injectAlpha(color, 135));
            }
            if (sideFill.getValue()) {
                ThunderRender3D.drawFilledSide(vc, posMatrix, box, ThunderRender2D.injectAlpha(color, 75), Direction.UP);
            }
        }

        if (blockAnimations.getValue()) {
            ThunderBlockAnimationUtility.onRender(vc, posMatrix, camPos, blockRender.getValue());
        }
        RenderUtil.endBatch();
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }

    private void renderBoxMode(VertexConsumer vc, Matrix4f matrix, Box box, Color color) {
        Color fill = new Color(fillColor.getArgb(), true);
        Color fadeTop = astolfo.getValue() ? ThunderRender2D.astolfo(35, fadeTopColor.getA()) : new Color(fadeTopColor.getArgb(), true);
        switch (boxMode.getValue()) {
            case OUTLINE -> ThunderRender3D.drawBoxOutline(vc, matrix, box, color, lineWidth.getValue().floatValue());
            case FILL -> ThunderRender3D.drawFilledBox(vc, matrix, box, fill);
            case FADE -> ThunderRender3D.drawFilledFadeBox(vc, matrix, box, fill, fadeTop);
            case BOTH -> {
                ThunderRender3D.drawFilledFadeBox(vc, matrix, box, fill, fadeTop);
                ThunderRender3D.drawBoxOutline(vc, matrix, box, color, lineWidth.getValue().floatValue());
            }
            case SIDE -> ThunderRender3D.drawFilledSide(vc, matrix, box, fill, Direction.UP);
        }
    }

    private void queueBlockAnimations() {
        BlockPos base = mc.player.getBlockPos();
        Color line = astolfo.getValue() ? ThunderRender2D.astolfo(0, playerColor.getA()) : new Color(playerColor.getArgb(), true);
        Color fill = new Color(fillColor.getArgb(), true);
        switch (blockMode.getValue()) {
            case PLAYER_POS -> ThunderBlockAnimationUtility.renderBlock(base.down(), line, lineWidth.getValue().intValue(), fill, blockAnimation.getValue(), blockRender.getValue());
            case RING -> {
                int radius = blockRadius.getValue();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.abs(x) == radius || Math.abs(z) == radius) {
                            ThunderBlockAnimationUtility.renderBlock(base.add(x, -1, z), line, lineWidth.getValue().intValue(), fill, blockAnimation.getValue(), blockRender.getValue());
                        }
                    }
                }
            }
            case CROSS -> {
                int radius = blockRadius.getValue();
                for (int i = -radius; i <= radius; i++) {
                    ThunderBlockAnimationUtility.renderBlock(base.add(i, -1, 0), line, lineWidth.getValue().intValue(), fill, blockAnimation.getValue(), blockRender.getValue());
                    ThunderBlockAnimationUtility.renderBlock(base.add(0, -1, i), line, lineWidth.getValue().intValue(), fill, blockAnimation.getValue(), blockRender.getValue());
                }
            }
        }
    }

    private boolean shouldRender(Entity entity) {
        return switch (target.getValue()) {
            case PLAYERS -> entity instanceof PlayerEntity;
            case MOBS -> entity instanceof MobEntity;
            case ITEMS -> entity instanceof ItemEntity;
            case LIVING -> entity instanceof PlayerEntity || entity instanceof MobEntity;
            case ALL -> entity instanceof PlayerEntity || entity instanceof MobEntity || entity instanceof ItemEntity;
        };
    }

    private Color getColor(Entity entity) {
        if (astolfo.getValue()) {
            return ThunderRender2D.astolfo(entity.getId() * 7, getBaseColor(entity).getAlpha());
        }
        return getBaseColor(entity);
    }

    private Color getBaseColor(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return new Color(playerColor.getArgb(), true);
        }
        if (entity instanceof ItemEntity) {
            return new Color(itemColor.getArgb(), true);
        }
        return new Color(mobColor.getArgb(), true);
    }

    private Box getEntityBox(Entity entity, Vec3d camPos, float tickDelta) {
        double x = entity.lastX + (entity.getX() - entity.lastX) * tickDelta;
        double y = entity.lastY + (entity.getY() - entity.lastY) * tickDelta;
        double z = entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta;
        double rx = x - camPos.x;
        double ry = y - camPos.y;
        double rz = z - camPos.z;
        float width = entity.getWidth();
        float height = entity.getHeight();
        return new Box(rx - width / 2.0, ry, rz - width / 2.0, rx + width / 2.0, ry + height, rz + width / 2.0);
    }
}
