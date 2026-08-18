package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ItemSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class BlockESP extends Module {

    public final EnumSetting<RenderFx.VisualMode> visualMode = register(new EnumSetting<>("Visual Mode", "Simple or shader visuals", RenderFx.VisualMode.SHADER));
    public final ItemSetting targetBlock = register(new ItemSetting("Target", "Block to find", Items.DIAMOND_ORE));
    public final ColorSetting color = register(new ColorSetting("Color", "Box color", 0, 255, 255, 255));
    public final IntSliderSetting radius = register(new IntSliderSetting("Radius", "Range", 20, 5, 100));
    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw lines to blocks", true));
    public final BoolSetting bloom = register(new BoolSetting("Bloom", "Soft inner bloom", true));
    public final BoolSetting strongGlow = register(new BoolSetting("Strong Glow", "Extra wide glow layers", true));
    public final BoolSetting scannerFx = register(new BoolSetting("Scanner", "Animated scan plane", true));
    public final BoolSetting pulse = register(new BoolSetting("Pulse", "Animated pulse shell", true));
    public final BoolSetting halo = register(new BoolSetting("Halo", "Extra halo shells", true));

    private final List<BlockPos> blocks = new ArrayList<>();
    private int tickCounter = 0;
    private boolean scanning = false;

    public ChunkFinder chunkFinder; // Placeholder just in case

    public BlockESP() {
        super("BlockESP", "Highlights blocks", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (blocks.isEmpty()) return;

        Color c = new Color(color.getArgb(), true);
        Vec3d camPos = camera.getCameraPos();
        RenderFx.ShaderOptions shaderOptions = new RenderFx.ShaderOptions(1.0f, bloom.getValue(), strongGlow.getValue(), scannerFx.getValue(), pulse.getValue(), halo.getValue());

        List<BlockPos> currentBlocks;
        synchronized (blocks) {
            currentBlocks = new ArrayList<>(blocks);
        }

        VertexConsumer vc = RenderUtil.beginBatch();
        for (BlockPos pos : currentBlocks) {
            double dx = pos.getX() - camPos.x;
            double dy = pos.getY() - camPos.y;
            double dz = pos.getZ() - camPos.z;
            Box box = new Box(dx, dy, dz, dx + 1.0, dy + 1.0, dz + 1.0);

            if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                RenderFx.renderSimpleBox(vc, posMatrix, box, c);
            } else {
                RenderFx.renderShaderBox(vc, posMatrix, box, c, shaderOptions, pos.asLong() * 0.013);
            }
        }
        RenderUtil.endBatch();

        if (tracers.getValue()) {
            Vec3d start = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.5);
            for (BlockPos pos : currentBlocks) {
                double dx = pos.getX() - camPos.x;
                double dy = pos.getY() - camPos.y;
                double dz = pos.getZ() - camPos.z;
                RenderUtil.drawTracer(posMatrix, start, new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5), c);
            }
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (tickCounter++ % 40 == 0 && !scanning) {
            new Thread(this::updateBlocks).start();
        }
    }

    private void updateBlocks() {
        scanning = true;
        try {
            List<net.minecraft.item.Item> items = targetBlock.getValue();
            List<Block> targets = new ArrayList<>();
            for (net.minecraft.item.Item item : items) {
                if (item instanceof BlockItem bi) targets.add(bi.getBlock());
            }
            if (targets.isEmpty()) {
                synchronized (blocks) {
                    blocks.clear();
                }
                return;
            }

            BlockPos pPos = mc.player.getBlockPos();
            int rad = radius.getValue();
            List<BlockPos> found = new ArrayList<>();

            for (int x = -rad; x <= rad; x++) {
                for (int y = -rad; y <= rad; y++) {
                    for (int z = -rad; z <= rad; z++) {
                        BlockPos pos = pPos.add(x, y, z);
                        if (mc.world == null) return;
                        if (targets.contains(mc.world.getBlockState(pos).getBlock())) {
                            found.add(pos);
                            if (found.size() > 500) break;
                        }
                    }
                    if (found.size() > 500) break;
                }
                if (found.size() > 500) break;
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
