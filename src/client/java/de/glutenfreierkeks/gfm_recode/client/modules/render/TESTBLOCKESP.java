package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderBlockAnimationUtility;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderRender2D;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderRender3D;
import net.minecraft.block.Block;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TESTBLOCKESP extends Module {
    private final ItemSetting targetBlock = register(new ItemSetting("Target", "Block to find", Items.DIAMOND_ORE));
    private final EnumSetting<Style> style = register(new EnumSetting<>("Style", "Thunder block style", Style.THUNDER_ANIMATED));
    private final EnumSetting<ThunderBlockAnimationUtility.BlockAnimationMode> animation = register(new EnumSetting<>("Animation", "Thunder animation", ThunderBlockAnimationUtility.BlockAnimationMode.Fade));
    private final EnumSetting<ThunderBlockAnimationUtility.BlockRenderMode> renderMode = register(new EnumSetting<>("Render", "Render mode", ThunderBlockAnimationUtility.BlockRenderMode.All));
    private final ColorSetting color = register(new ColorSetting("Color", "Block ESP color", 80, 185, 255, 230));
    private final ColorSetting fillColor = register(new ColorSetting("Fill", "Fill color", 80, 185, 255, 48));
    private final IntSliderSetting radius = register(new IntSliderSetting("Radius", "Scan range", 20, 4, 96));
    private final IntSliderSetting limit = register(new IntSliderSetting("Limit", "Max rendered blocks", 180, 20, 600));
    private final IntSliderSetting lineWidth = register(new IntSliderSetting("Line Width", "Thunder line width", 4, 1, 8));
    private final BoolSetting fade = register(new BoolSetting("Fade Box", "Render fade box", true));
    private final BoolSetting side = register(new BoolSetting("Side Fill", "Render top side", true));
    private final BoolSetting crosses = register(new BoolSetting("Crosses", "Render Thunder crosses", false));
    private final BoolSetting tracers = register(new BoolSetting("Tracers", "Render tracer lines", false));

    private final List<BlockPos> blocks = new ArrayList<>();
    private int tickCounter;
    private boolean scanning;

    public enum Style { THUNDER_ANIMATED, STATIC_THUNDER, FADE_GLOW, FULL }

    public TESTBLOCKESP() {
        super("TESTBLOCKESP", "ThunderHack styled block ESP test", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            return;
        }
        ThunderRender3D.updateTargetESP();
        if (tickCounter++ % 30 == 0 && !scanning) {
            new Thread(this::updateBlocks).start();
        }
        if (style.getValue() == Style.THUNDER_ANIMATED || style.getValue() == Style.FULL) {
            Color line = new Color(color.getArgb(), true);
            Color fill = new Color(fillColor.getArgb(), true);
            synchronized (blocks) {
                for (BlockPos pos : blocks) {
                    ThunderBlockAnimationUtility.renderBlock(pos, line, lineWidth.getValue(), fill, animation.getValue(), renderMode.getValue());
                }
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        Color line = new Color(color.getArgb(), true);
        Color fill = new Color(fillColor.getArgb(), true);
        List<BlockPos> current;
        synchronized (blocks) {
            current = new ArrayList<>(blocks);
        }

        VertexConsumer vc = RenderUtil.beginBatch();
        if (style.getValue() == Style.THUNDER_ANIMATED || style.getValue() == Style.FULL) {
            ThunderBlockAnimationUtility.onRender(vc, posMatrix, camPos, renderMode.getValue());
        }

        if (style.getValue() != Style.THUNDER_ANIMATED) {
            for (BlockPos pos : current) {
                Box box = new Box(pos).offset(-camPos.x, -camPos.y, -camPos.z);
                if (fade.getValue() || style.getValue() == Style.FADE_GLOW || style.getValue() == Style.FULL) {
                    ThunderRender3D.drawFilledFadeBox(vc, posMatrix, box.expand(0.02), fill, ThunderRender2D.injectAlpha(line, 100));
                }
                ThunderRender3D.drawBoxOutline(vc, posMatrix, box.expand(0.035), line, lineWidth.getValue());
                ThunderRender3D.drawBoxOutline(vc, posMatrix, box.expand(0.095), ThunderRender2D.injectAlpha(line, 85), lineWidth.getValue() + 1);
                if (side.getValue()) {
                    ThunderRender3D.drawFilledSide(vc, posMatrix, box, ThunderRender2D.injectAlpha(line, 55), Direction.UP);
                }
                if (crosses.getValue()) {
                    ThunderRender3D.renderCrosses(vc, posMatrix, box, line, lineWidth.getValue());
                }
            }
        }
        RenderUtil.endBatch();

        if (tracers.getValue()) {
            Vec3d start = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.5);
            for (BlockPos pos : current) {
                Vec3d end = new Vec3d(pos.getX() - camPos.x + 0.5, pos.getY() - camPos.y + 0.5, pos.getZ() - camPos.z + 0.5);
                RenderUtil.drawTracer(posMatrix, start, end, line);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return style.getValue().name();
    }

    private void updateBlocks() {
        scanning = true;
        try {
            List<net.minecraft.item.Item> items = targetBlock.getValue();
            List<Block> targets = new ArrayList<>();
            for (net.minecraft.item.Item item : items) {
                if (item instanceof BlockItem blockItem) {
                    targets.add(blockItem.getBlock());
                }
            }
            if (targets.isEmpty() || mc.player == null || mc.world == null) {
                synchronized (blocks) {
                    blocks.clear();
                }
                return;
            }

            BlockPos playerPos = mc.player.getBlockPos();
            int rad = radius.getValue();
            int max = limit.getValue();
            List<BlockPos> found = new ArrayList<>();
            for (int x = -rad; x <= rad && found.size() < max; x++) {
                for (int y = -rad; y <= rad && found.size() < max; y++) {
                    for (int z = -rad; z <= rad && found.size() < max; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (mc.world.getBlockState(pos) != null && targets.contains(mc.world.getBlockState(pos).getBlock())) {
                            found.add(pos);
                        }
                    }
                }
            }
            synchronized (blocks) {
                blocks.clear();
                blocks.addAll(found);
            }
        } catch (Exception ignored) {
        } finally {
            scanning = false;
        }
    }
}
