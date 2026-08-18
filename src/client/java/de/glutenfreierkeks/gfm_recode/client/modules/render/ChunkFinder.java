package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.fluid.FluidState;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;

public class ChunkFinder extends Module {

    public enum Mode { Chat, Toast, Both }

    // Detection
    public final BoolSetting detectDeepslate = register(new BoolSetting("Detect Deepslate", "Find deepslate blocks", false));
    public final BoolSetting detectCobbledDeepslate = register(new BoolSetting("Detect Cobbled Deepslate", "Find cobbled deepslate blocks", true));
    public final BoolSetting detectRotatedDeepslate = register(new BoolSetting("Detect Rotated Deepslate", "Find rotated deepslate blocks", true));
    public final BoolSetting detectEndStone = register(new BoolSetting("Detect End Stone", "Find end stone blocks (disabled in The End)", true));
    public final BoolSetting ignoreExposed = register(new BoolSetting("Ignore Exposed", "Ignore blocks exposed to air/fluid", true));
    public final BoolSetting ignoreTrialChambers = register(new BoolSetting("Ignore Trial Chambers", "Ignore chunks with trial chambers", true));
    public final IntSliderSetting trialChamberThreshold = register(new IntSliderSetting("Trial Chamber Threshold", "Min copper/tuff blocks", 50, 1, 50));
    public final IntSliderSetting deepslateThreshold = register(new IntSliderSetting("Deepslate Threshold", "Min deepslate to flag", 1, 1, 15));
    public final IntSliderSetting cobbledDeepslateThreshold = register(new IntSliderSetting("Cobbled Threshold", "Min cobbled to flag", 4, 1, 15));
    public final IntSliderSetting rotatedDeepslateThreshold = register(new IntSliderSetting("Rotated Threshold", "Min rotated to flag", 3, 1, 20));
    public final IntSliderSetting endStoneThreshold = register(new IntSliderSetting("End Stone Threshold", "Min end stone to flag", 2, 1, 15));
    
    // Tunnel Settings (Only for coloring)
    public final IntSliderSetting tunnelMinY = register(new IntSliderSetting("Tunnel Min Y", "Start height for tunnel check", 50, -64, 320));
    public final IntSliderSetting tunnelMaxY = register(new IntSliderSetting("Tunnel Max Y", "Height for open tunnels", 200, -64, 320));
    public final IntSliderSetting minTunnelHeight = register(new IntSliderSetting("Min Tunnel Length", "Minimum blocks for a tunnel", 3, 1, 10));

    // Render
    public final EnumSetting<RenderFx.VisualMode> visualMode = register(new EnumSetting<>("Visual Mode", "Simple or shader visuals", RenderFx.VisualMode.SHADER));
    public final DoubleSliderSetting renderY = register(new DoubleSliderSetting("Render Height", "Height to render highlights", 64.0, -64.0, 320.0));
    public final DoubleSliderSetting thickness = register(new DoubleSliderSetting("Thickness", "Thickness of highlight box", 0.3, 0.1, 2.0));
    public final ColorSetting chunkColor = register(new ColorSetting("Chunk Color", "Yellow/Default Color", 255, 215, 0, 120));
    public final ColorSetting openTunnelColor = register(new ColorSetting("Open Tunnel Color", "Red", 255, 0, 0, 150));
    public final ColorSetting coveredTunnelColor = register(new ColorSetting("Covered Tunnel Color", "Green", 0, 255, 0, 150));
    public final BoolSetting bloom = register(new BoolSetting("Bloom", "Soft inner bloom", true));
    public final BoolSetting strongGlow = register(new BoolSetting("Strong Glow", "Extra wide glow layers", true));
    public final BoolSetting scannerFx = register(new BoolSetting("Scanner", "Animated scan plane", true));
    public final BoolSetting pulse = register(new BoolSetting("Pulse", "Animated pulse shell", true));
    public final BoolSetting halo = register(new BoolSetting("Halo", "Extra halo shells", true));

    // Performance & Others
    public final BoolSetting useMultiThreading = register(new BoolSetting("Threading", "Use background threads", true));
    public final IntSliderSetting threadCount = register(new IntSliderSetting("Thread Count", "Number of workers", 2, 1, 4));
    public final IntSliderSetting scanInterval = register(new IntSliderSetting("Scan Delay", "MS between scans", 100, 50, 2000));
    public final IntSliderSetting maxConcurrentScans = register(new IntSliderSetting("Max Concurrent", "Max chunks scanned at once", 3, 1, 8));
    public final BoolSetting highlightBlocks = register(new BoolSetting("Highlight Blocks", "Show individual suspicious blocks", true));
    public final IntSliderSetting maxBlocksToRender = register(new IntSliderSetting("Max Blocks", "Max blocks to highlight", 200, 50, 1000));
    public final ColorSetting deepslateBlockColor = register(new ColorSetting("Deepslate Color", "", 100, 100, 100, 200));
    public final ColorSetting cobbledDeepslateBlockColor = register(new ColorSetting("Cobbled Color", "", 80, 80, 80, 200));
    public final ColorSetting rotatedDeepslateBlockColor = register(new ColorSetting("Rotated Color", "", 120, 0, 120, 200));
    public final ColorSetting endStoneBlockColor = register(new ColorSetting("End Stone Color", "", 255, 255, 200, 200));
    public final BoolSetting playSound = register(new BoolSetting("Sound Alerts", "", true));
    public final BoolSetting chatAlerts = register(new BoolSetting("Chat Alerts", "", true));
    public final BoolSetting trialChamberAlerts = register(new BoolSetting("Trial Alerts", "", false));
    public final IntSliderSetting maxAlerts = register(new IntSliderSetting("Max Alerts", "Max alerts per minute", 5, 1, 20));

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, ChunkAnalysis> chunkDataMap = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, Long> notificationTimes = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final AtomicLong activeScanCount = new AtomicLong(0);
    private final Map<BlockPos, SuspiciousBlock> suspiciousBlocksMap = new ConcurrentHashMap<>();

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;

    public ChunkFinder() {
        super("ChunkFinder", "Finds suspicious chunks and colors tunnels", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (mc.world == null) return;
        clearAll();
        shouldScan = true;

        if (useMultiThreading.getValue()) {
            scannerPool = Executors.newFixedThreadPool(threadCount.getValue(), r -> {
                Thread t = new Thread(r, "ChunkFinder-Worker");
                t.setDaemon(true);
                return t;
            });
        }
    }

    @Override
    protected void onDisable() {
        shouldScan = false;
        if (scannerPool != null) {
            scannerPool.shutdownNow();
            scannerPool = null;
        }
        clearAll();
    }

    private void clearAll() {
        flaggedChunks.clear();
        chunkDataMap.clear();
        scannedChunks.clear();
        notificationTimes.clear();
        recentAlerts.clear();
        suspiciousBlocksMap.clear();
        activeScanCount.set(0);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        int radius = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos pos = new ChunkPos(pCx + x, pCz + z);
                if (!scannedChunks.contains(pos) && activeScanCount.get() < maxConcurrentScans.getValue()) {
                    WorldChunk chunk = (WorldChunk) mc.world.getChunkManager().getWorldChunk(pos.x, pos.z);
                    if (chunk != null) {
                        scheduleChunkScan(chunk);
                    }
                }
            }
        }
    }

    private void scheduleChunkScan(WorldChunk chunk) {
        if (!shouldScan) return;

        if (useMultiThreading.getValue() && scannerPool != null) {
            scannerPool.submit(() -> analyzeChunk(chunk));
        } else {
            analyzeChunk(chunk);
        }
    }

    private void analyzeChunk(WorldChunk chunk) {
        if (!shouldScan || chunk == null) return;
        ChunkPos pos = chunk.getPos();
        if (scannedChunks.contains(pos)) return;

        activeScanCount.incrementAndGet();
        try {
            scannedChunks.add(pos);
            ChunkAnalysis analysis = new ChunkAnalysis();
            ChunkSection[] sections = chunk.getSectionArray();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                if (!shouldScan) return;
                ChunkSection section = sections[sectionIndex];
                if (section == null || section.isEmpty()) continue;

                int sectionY = chunk.getBottomY() + sectionIndex * 16;
                if (sectionY > 128) continue;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            int worldY = sectionY + y;
                            if (worldY > 128) continue;

                            BlockState state = section.getBlockState(x, y, z);
                            BlockPos blockPos = new BlockPos(pos.getStartX() + x, worldY, pos.getStartZ() + z);
                            analyzeBlock(blockPos, state, worldY, analysis);
                        }
                    }
                }
            }

            analysis.tunnelType = findTunnelInChunk(chunk);
            chunkDataMap.put(pos, analysis);
            evaluateChunk(pos, analysis);
        } catch (Exception e) {
        } finally {
            activeScanCount.decrementAndGet();
        }
    }

    private void analyzeBlock(BlockPos pos, BlockState state, int worldY, ChunkAnalysis analysis) {
        SuspiciousBlockType blockType = null;

        if (ignoreTrialChambers.getValue() && isTrialChamberBlock(state)) analysis.trialChamberCount++;
        if (isPlayerActivityBlock(state, worldY)) analysis.playerActivityScore++;

        boolean exposed = ignoreExposed.getValue() && isExposedToAirOrFluid(pos);

        if (detectDeepslate.getValue() && isNormalDeepslate(state) && !exposed && !isInLargeDeepslateLine(pos, worldY)) {
            analysis.deepslateCount++;
            blockType = SuspiciousBlockType.DEEPSLATE;
        }
        if (detectRotatedDeepslate.getValue() && isRotatedDeepslateBlock(state) && !exposed) {
            analysis.rotatedDeepslateCount++;
            blockType = SuspiciousBlockType.ROTATED_DEEPSLATE;
        }
        if (detectCobbledDeepslate.getValue() && isCobbledDeepslate(state) && !exposed) {
            analysis.cobbledDeepslateCount++;
            blockType = SuspiciousBlockType.COBBLED_DEEPSLATE;
        }
        if (detectEndStone.getValue() && isEndStone(state) && mc.world.getRegistryKey() != World.END && !exposed) {
            analysis.endStoneCount++;
            blockType = SuspiciousBlockType.END_STONE;
        }

        if (blockType != null && highlightBlocks.getValue()) {
            suspiciousBlocksMap.put(pos, new SuspiciousBlock(blockType));
        }
    }

    private boolean isExposedToAirOrFluid(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos offset = pos.offset(dir);
            BlockState neighbor = mc.world.getBlockState(offset);
            if (neighbor.isAir()) return true;
            FluidState f = neighbor.getFluidState();
            if (f != null && !f.isEmpty()) return true;
        }
        return false;
    }

    private boolean isInLargeDeepslateLine(BlockPos pos, int worldY) {
        final int threshold = worldY > -8 ? 50 : 20;
        int xCount = 1;
        for (int i = 1; i < threshold; i++) {
            if (!isNormalDeepslate(mc.world.getBlockState(pos.offset(Direction.EAST, i)))) break;
            xCount++;
        }
        for (int i = 1; i < threshold; i++) {
            if (!isNormalDeepslate(mc.world.getBlockState(pos.offset(Direction.WEST, i)))) break;
            xCount++;
        }
        if (xCount >= threshold) return true;

        int zCount = 1;
        for (int i = 1; i < threshold; i++) {
            if (!isNormalDeepslate(mc.world.getBlockState(pos.offset(Direction.SOUTH, i)))) break;
            zCount++;
        }
        for (int i = 1; i < threshold; i++) {
            if (!isNormalDeepslate(mc.world.getBlockState(pos.offset(Direction.NORTH, i)))) break;
            zCount++;
        }
        return zCount >= threshold;
    }

    private void evaluateChunk(ChunkPos pos, ChunkAnalysis analysis) {
        if (ignoreTrialChambers.getValue() && analysis.trialChamberCount >= trialChamberThreshold.getValue()) {
            return;
        }

        boolean susp = (detectDeepslate.getValue() && analysis.deepslateCount >= deepslateThreshold.getValue()) ||
                       (detectCobbledDeepslate.getValue() && analysis.cobbledDeepslateCount >= cobbledDeepslateThreshold.getValue()) ||
                       (detectRotatedDeepslate.getValue() && analysis.rotatedDeepslateCount >= rotatedDeepslateThreshold.getValue()) ||
                       (detectEndStone.getValue() && analysis.endStoneCount >= endStoneThreshold.getValue());

        if (susp && flaggedChunks.add(pos)) {
            notifyChunkFound(pos);
        }
    }

    private TunnelType findTunnelInChunk(WorldChunk chunk) {
        int yStart = tunnelMinY.getValue();
        int yEnd = tunnelMaxY.getValue();
        int minHeight = minTunnelHeight.getValue();
        World world = chunk.getWorld();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int startX = chunk.getPos().getStartX() + x;
                int startZ = chunk.getPos().getStartZ() + z;

                int currentY = yStart;
                int tunnelLength = 0;
                boolean validVerticalShaft = true;

                while (currentY < 320) {
                    BlockPos p = new BlockPos(startX, currentY, startZ);
                    BlockState state = world.getBlockState(p);

                    if (!state.isAir()) {
                        if (tunnelLength >= minHeight && validVerticalShaft) {
                            BlockPos overCover = p.up();
                            if (world.getFluidState(overCover).getFluid() == net.minecraft.fluid.Fluids.WATER || 
                                world.getFluidState(overCover).getFluid() == net.minecraft.fluid.Fluids.FLOWING_WATER) {
                                break; 
                            }
                            return TunnelType.COVERED;
                        }
                        break;
                    }

                    boolean hasWalls = isTunnelPart(world, p);
                    if (!world.isSkyVisible(p) && !hasWalls) {
                        validVerticalShaft = false;
                        break;
                    }

                    tunnelLength++;
                    if (tunnelLength >= minHeight && (currentY >= yEnd || world.isSkyVisible(p))) {
                        return TunnelType.OPEN;
                    }
                    currentY++;
                }
            }
        }
        return TunnelType.NONE;
    }

    private boolean isTunnelPart(World world, BlockPos pos) {
        boolean xWalls = isSolid(world, pos.east()) && isSolid(world, pos.west());
        boolean zWalls = isSolid(world, pos.north()) && isSolid(world, pos.south());
        return xWalls || zWalls;
    }

    private boolean isSolid(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOpaque() && !state.isAir();
    }

    private boolean isNormalDeepslate(BlockState s) {
        return s.isOf(Blocks.DEEPSLATE) && s.contains(Properties.AXIS) && s.get(Properties.AXIS) == Direction.Axis.Y;
    }
    private boolean isCobbledDeepslate(BlockState s) { return s.isOf(Blocks.COBBLED_DEEPSLATE); }
    private boolean isRotatedDeepslateBlock(BlockState s) {
        return s.isOf(Blocks.DEEPSLATE) && s.contains(Properties.AXIS) && s.get(Properties.AXIS) != Direction.Axis.Y;
    }
    private boolean isEndStone(BlockState s) { return s.isOf(Blocks.END_STONE); }
    private boolean isTrialChamberBlock(BlockState s) {
        return s.isOf(Blocks.WAXED_COPPER_BLOCK) || s.isOf(Blocks.WAXED_OXIDIZED_COPPER) || s.isOf(Blocks.TUFF_BRICKS);
    }

    private boolean isPlayerActivityBlock(BlockState state, int worldY) {
        if (state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE) || state.isOf(Blocks.COBBLED_DEEPSLATE)) return true;
        if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL)) return true;
        if (state.isOf(Blocks.LADDER) || state.isOf(Blocks.RAIL) || state.isOf(Blocks.POWERED_RAIL)) return true;
        if (worldY < 40 && (state.isOf(Blocks.OAK_PLANKS) || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.CRAFTING_TABLE))) return true;
        return false;
    }

    private void notifyChunkFound(ChunkPos pos) {
        if (recentAlerts.size() >= maxAlerts.getValue()) return;
        recentAlerts.offer(System.currentTimeMillis());

        String msg = "Suspicious chunk: [" + pos.x + ", " + pos.z + "]";
        if (chatAlerts.getValue()) notifyChat(msg);
        if (playSound.getValue()) {
            mc.execute(() -> {
                if (mc.world != null) {
                    mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
                }
            });
        }
    }

    private void notifyChat(String msg) {
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("§6[GFM] §f" + msg), false);
        });
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        Vec3d camPos = camera.getCameraPos();
        double y = renderY.getValue();
        double h = thickness.getValue();
        RenderFx.ShaderOptions shaderOptions = new RenderFx.ShaderOptions(1.1f, bloom.getValue(), strongGlow.getValue(), scannerFx.getValue(), pulse.getValue(), halo.getValue());
        VertexConsumer vc = RenderUtil.beginBatch();

        for (ChunkPos pos : flaggedChunks) {
            ChunkAnalysis analysis = chunkDataMap.get(pos);
            Color c = chunkColor.getJavaColor(); // Yellow default

            if (analysis != null) {
                if (analysis.tunnelType == TunnelType.OPEN) c = openTunnelColor.getJavaColor();
                else if (analysis.tunnelType == TunnelType.COVERED) c = coveredTunnelColor.getJavaColor();
            }

            double dx = pos.getStartX() - camPos.x;
            double dy = y - camPos.y;
            double dz = pos.getStartZ() - camPos.z;
            Box box = new Box(dx, dy, dz, dx + 16, dy + h, dz + 16);
            if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                RenderFx.renderSimpleBox(vc, posMatrix, box, c);
            } else {
                RenderFx.renderShaderBox(vc, posMatrix, box, c, shaderOptions, ((pos.x * 73428767L) ^ (pos.z * 912931L)) * 0.005);
            }
        }

        if (highlightBlocks.getValue()) {
            int rendered = 0;
            for (Map.Entry<BlockPos, SuspiciousBlock> entry : suspiciousBlocksMap.entrySet()) {
                if (rendered++ > maxBlocksToRender.getValue()) break;
                BlockPos p = entry.getKey();
                Color bc = getBlockColor(entry.getValue().type);
                double bdx = p.getX() - camPos.x;
                double bdy = p.getY() - camPos.y;
                double bdz = p.getZ() - camPos.z;
                Box box = new Box(bdx, bdy, bdz, bdx + 1, bdy + 1, bdz + 1);
                if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                    RenderFx.renderSimpleBox(vc, posMatrix, box, bc);
                } else {
                    RenderFx.renderShaderBox(vc, posMatrix, box, bc, shaderOptions, p.asLong() * 0.017);
                }
            }
        }
        RenderUtil.endBatch();
    }

    private Color getBlockColor(SuspiciousBlockType type) {
        return switch (type) {
            case DEEPSLATE -> new Color(deepslateBlockColor.getArgb(), true);
            case COBBLED_DEEPSLATE -> new Color(cobbledDeepslateBlockColor.getArgb(), true);
            case ROTATED_DEEPSLATE -> new Color(rotatedDeepslateBlockColor.getArgb(), true);
            case END_STONE -> new Color(endStoneBlockColor.getArgb(), true);
        };
    }

    private static class ChunkAnalysis {
        int deepslateCount, cobbledDeepslateCount, rotatedDeepslateCount, endStoneCount, trialChamberCount, playerActivityScore;
        TunnelType tunnelType = TunnelType.NONE;
    }
    private enum TunnelType { NONE, OPEN, COVERED }
    private static class SuspiciousBlock {
        final SuspiciousBlockType type;
        SuspiciousBlock(SuspiciousBlockType type) { this.type = type; }
    }
    private enum SuspiciousBlockType { DEEPSLATE, COBBLED_DEEPSLATE, ROTATED_DEEPSLATE, END_STONE }
}
