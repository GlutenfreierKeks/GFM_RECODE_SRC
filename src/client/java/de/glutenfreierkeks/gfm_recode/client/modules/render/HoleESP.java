package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class HoleESP extends Module {

    private final IntSliderSetting radius = register(new IntSliderSetting("Radius", "Scan radius", 24, 1, 64));
    private final IntSliderSetting minLevel = register(new IntSliderSetting("Start Y", "Start height of scan", 0, -64, 320));
    private final IntSliderSetting maxLevel = register(new IntSliderSetting("Open Y", "Height for open tunnels", 200, -64, 320));
    private final EnumSetting<RenderFx.VisualMode> visualMode = register(new EnumSetting<>("Visual Mode", "Simple or shader visuals", RenderFx.VisualMode.SHADER));
    
    private final ColorSetting openColor = register(new ColorSetting("Open Color", "Continuous to Max Y", 255, 0, 0, 180));
    private final ColorSetting coveredColor = register(new ColorSetting("Covered Color", "Roofed tunnels", 0, 255, 0, 180));
    
    private final BoolSetting ignoreWater = register(new BoolSetting("Ignore Water", "Don't render if water is above cover", true));
    private final BoolSetting fill = register(new BoolSetting("Fill", "Fill the box", true));
    private final DoubleSliderSetting lineWidth = register(new DoubleSliderSetting("Line Width", "", 1.0, 0.1, 3.0));
    private final BoolSetting bloom = register(new BoolSetting("Bloom", "Soft inner bloom", true));
    private final BoolSetting strongGlow = register(new BoolSetting("Strong Glow", "Extra wide glow layers", true));
    private final BoolSetting scannerFx = register(new BoolSetting("Scanner", "Animated scan plane", true));
    private final BoolSetting pulse = register(new BoolSetting("Pulse", "Animated pulse shell", true));
    private final BoolSetting halo = register(new BoolSetting("Halo", "Extra halo shells", true));

    private final List<Tunnel> tunnels = new ArrayList<>();

    public HoleESP() {
        super("HoleESP", "Detects vertical tunnels and covered holes", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        tunnels.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.getValue();
        int yStart = minLevel.getValue();
        int yEnd = maxLevel.getValue();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int worldX = playerPos.getX() + x;
                int worldZ = playerPos.getZ() + z;

                int currentY = yStart;
                boolean validTunnel = true;
                
                // Scan the air column
                while (currentY < 320) {
                    BlockPos p = new BlockPos(worldX, currentY, worldZ);
                    BlockState state = mc.world.getBlockState(p);
                    
                    if (!state.isAir()) break;
                    
                    if (!isTunnelPart(p)) {
                        validTunnel = false;
                        break;
                    }
                    currentY++;
                }

                int height = currentY - yStart;
                if (validTunnel && height > 0) {
                    boolean isFullyOpen = currentY >= yEnd;
                    
                    if (!isFullyOpen) {
                        // Check for water above the cover block
                        if (ignoreWater.getValue()) {
                            BlockPos aboveCover = new BlockPos(worldX, currentY + 1, worldZ);
                            if (mc.world.getFluidState(aboveCover).getFluid() == Fluids.WATER || 
                                mc.world.getFluidState(aboveCover).getFluid() == Fluids.FLOWING_WATER) {
                                continue; 
                            }
                        }
                    }

                    Color color = isFullyOpen ? openColor.getJavaColor() : coveredColor.getJavaColor();
                    tunnels.add(new Tunnel(new BlockPos(worldX, yStart, worldZ), height, color));
                }
            }
        }
    }

    private boolean isTunnelPart(BlockPos pos) {
        boolean xWalls = isSolid(pos.east()) && isSolid(pos.west());
        boolean zWalls = isSolid(pos.north()) && isSolid(pos.south());
        return xWalls || zWalls;
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOpaque() && !state.isAir();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (tunnels.isEmpty()) return;
        RenderFx.ShaderOptions shaderOptions = new RenderFx.ShaderOptions(
                lineWidth.getValue().floatValue(),
                bloom.getValue(),
                strongGlow.getValue(),
                scannerFx.getValue(),
                pulse.getValue(),
                halo.getValue()
        );
        VertexConsumer vc = RenderUtil.beginBatch();

        for (Tunnel tunnel : tunnels) {
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                tunnel.basePos.getX(), tunnel.basePos.getY(), tunnel.basePos.getZ(),
                tunnel.basePos.getX() + 1, tunnel.basePos.getY() + tunnel.height, tunnel.basePos.getZ() + 1
            ).offset(-camera.getCameraPos().x, -camera.getCameraPos().y, -camera.getCameraPos().z);

            if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                if (fill.getValue()) {
                    RenderFx.renderSimpleBox(vc, posMatrix, box, tunnel.color);
                } else {
                    Color outlineOnly = new Color(tunnel.color.getRed(), tunnel.color.getGreen(), tunnel.color.getBlue(), Math.max(80, tunnel.color.getAlpha()));
                    RenderUtil.batchOutlineBox(vc, posMatrix, box,
                            outlineOnly.getRed() / 255f,
                            outlineOnly.getGreen() / 255f,
                            outlineOnly.getBlue() / 255f,
                            outlineOnly.getAlpha() / 255f,
                            lineWidth.getValue().floatValue());
                }
            } else {
                RenderFx.renderShaderBox(vc, posMatrix, box, tunnel.color, shaderOptions, tunnel.basePos.asLong() * 0.021);
            }
        }
        RenderUtil.endBatch();
    }

    private static class Tunnel {
        final BlockPos basePos;
        final int height;
        final Color color;

        Tunnel(BlockPos basePos, int height, Color color) {
            this.basePos = basePos;
            this.height = height;
            this.color = color;
        }
    }
}
