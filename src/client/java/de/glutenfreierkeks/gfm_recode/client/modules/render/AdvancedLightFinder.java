package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class AdvancedLightFinder extends Module {

    public final IntSliderSetting minLightThreshold = register(new IntSliderSetting("Min Light",      "Minimum block-light level to flag", 1, 1, 15));
    public final IntSliderSetting startY            = register(new IntSliderSetting("Start Y",        "Start scanning from this Y level downwards", 0, -64, 320));
    public final BoolSetting excludeSkylitAreas     = register(new BoolSetting("Exclude Skylit",     "Skip positions with sky-light > 8 to reduce outdoor noise", true));
    public final BoolSetting showEmissionMismatch   = register(new BoolSetting("Emission Mismatch",  "Flag blocks whose actual light exceeds their natural emission", true));
    public final BoolSetting showAirLight           = register(new BoolSetting("Air Light",          "Flag air blocks with non-zero block-light (can be noisy)", false));
    public final BoolSetting showStoneGroup         = register(new BoolSetting("Stone Group",        "Flag stone/deepslate-type blocks that show block-light", true));
    public final BoolSetting showHiddenLight        = register(new BoolSetting("Hidden Light Block", "Flag invisible Minecraft light blocks unconditionally", true));

    public final IntSliderSetting renderDistance = register(new IntSliderSetting("Render Distance", "Max distance from player to render flagged blocks", 64, 16, 256));
    public final BoolSetting showLightLevelText  = register(new BoolSetting("Light Level Text",    "Show observed light level above each flagged block", true));

    public final ColorSetting colorEmission = register(new ColorSetting("Emission Color", "EmissionMismatch highlight",    255, 215, 0,   200));
    public final ColorSetting colorAir      = register(new ColorSetting("Air Color",      "AirLight highlight",             0,  255, 255, 200));
    public final ColorSetting colorStone    = register(new ColorSetting("Stone Color",    "StoneGroupMismatch highlight",  255, 140, 0,   200));
    public final ColorSetting colorHidden   = register(new ColorSetting("Hidden Color",   "HiddenLightBlock highlight",    255,  30, 30,  255));

    public final IntSliderSetting maxConcurrentScans = register(new IntSliderSetting("Max Concurrent", "Chunks scanned in parallel", 3, 1, 8));

    private static final Set<Block> ZERO_EMISSION_BLOCKS = Set.of(
            Blocks.DEEPSLATE,           Blocks.COBBLED_DEEPSLATE,      Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,     Blocks.CHISELED_DEEPSLATE,     Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.STONE,               Blocks.COBBLESTONE,            Blocks.ANDESITE,
            Blocks.DIORITE,             Blocks.GRANITE,                Blocks.TUFF,
            Blocks.CALCITE,             Blocks.SMOOTH_BASALT,          Blocks.BASALT,
            Blocks.NETHERRACK,          Blocks.BLACKSTONE,             Blocks.POLISHED_BLACKSTONE,
            Blocks.OBSIDIAN,            Blocks.CRYING_OBSIDIAN,
            Blocks.GRAVEL,              Blocks.DIRT,                   Blocks.SAND,
            Blocks.SANDSTONE
    );

    private final ConcurrentHashMap<ChunkPos, List<FoundLight>> foundLightMap = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final AtomicLong    activeScanCount = new AtomicLong(0);


    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private final List<RenderEntry> renderList = new ArrayList<>();

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;

    public enum DetectionMethod {
        EMISSION_MISMATCH, AIR_LIGHT, STONE_GROUP_MISMATCH, HIDDEN_LIGHT_BLOCK
    }

    public record FoundLight(BlockPos pos, DetectionMethod method, int lightLevel) {}

    private record RenderEntry(
            double wx, double wy, double wz,
            float  fr, float fg, float fb, float fa,
            float  or_, float og, float ob, float oa
    ) {}

    public AdvancedLightFinder() {
        super("AdvancedLightFinder",
                "Detects illegitimate or anomalous block-light that may indicate hidden bases or light blocks.",
                Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        clearAll();
        shouldScan = true;
        scannerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AdvLightFinder-Worker");
            t.setDaemon(true);
            return t;
        });
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
        foundLightMap.clear();
        scannedChunks.clear();
        activeScanCount.set(0);
        renderList.clear();
        dirty.set(false);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        int radius = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                ChunkPos cp = new ChunkPos(pCx + cx, pCz + cz);
                if (!scannedChunks.contains(cp) && activeScanCount.get() < maxConcurrentScans.getValue()) {
                    WorldChunk chunk = (WorldChunk) mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
                    if (chunk != null) scheduleChunkScan(chunk);
                }
            }
        }

        scannedChunks.removeIf(cp -> {
            if (mc.world == null) return false;
            WorldChunk wc = (WorldChunk) mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
            if (wc == null) {
                foundLightMap.remove(cp);
                dirty.set(true);
                return true;
            }
            return false;
        });
    }

    private void scheduleChunkScan(WorldChunk chunk) {
        if (!shouldScan || scannerPool == null || scannerPool.isShutdown()) return;
        scannerPool.submit(() -> scanChunk(chunk));
    }

    private void scanChunk(WorldChunk chunk) {
        if (!shouldScan || chunk == null) return;
        ChunkPos cp = chunk.getPos();
        if (scannedChunks.contains(cp)) return;

        activeScanCount.incrementAndGet();
        try {
            scannedChunks.add(cp);
            List<FoundLight> results = new ArrayList<>();

            if (mc.world == null) return;
            int minY = mc.world.getBottomY();
            int startYLevel = Math.max(startY.getValue(), minY);

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int y = startYLevel; y >= minY; y--) {
                        if (!shouldScan || mc.world == null) return;

                        BlockPos pos = new BlockPos(cp.getStartX() + lx, y, cp.getStartZ() + lz);

                        if (excludeSkylitAreas.getValue()
                                && mc.world.getLightLevel(LightType.SKY, pos) > 8) continue;

                        BlockState state      = mc.world.getBlockState(pos);
                        int        blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
                        int        minThresh  = minLightThreshold.getValue();

                        if (showHiddenLight.getValue() && state.getBlock() == Blocks.LIGHT) {
                            int lvl = state.contains(Properties.LEVEL_15)
                                    ? state.get(Properties.LEVEL_15) : blockLight;
                            results.add(new FoundLight(pos.toImmutable(), DetectionMethod.HIDDEN_LIGHT_BLOCK, lvl));
                            continue;
                        }

                        if (showAirLight.getValue() && state.isAir() && blockLight >= minThresh) {
                            results.add(new FoundLight(pos.toImmutable(), DetectionMethod.AIR_LIGHT, blockLight));
                            continue;
                        }

                        if (state.isAir()) continue;

                        if (showStoneGroup.getValue()
                                && ZERO_EMISSION_BLOCKS.contains(state.getBlock())
                                && blockLight >= minThresh) {
                            results.add(new FoundLight(pos.toImmutable(), DetectionMethod.STONE_GROUP_MISMATCH, blockLight));
                            continue;
                        }

                        if (showEmissionMismatch.getValue()) {
                            int naturalEmission = state.getLuminance();
                            if (blockLight > naturalEmission && blockLight >= minThresh) {
                                results.add(new FoundLight(pos.toImmutable(), DetectionMethod.EMISSION_MISMATCH, blockLight));
                            }
                        }
                    }
                }
            }

            foundLightMap.put(cp, results);
            dirty.set(true);
        } catch (Exception ignored) {
        } finally {
            activeScanCount.decrementAndGet();
        }
    }

    private void rebuildRenderList(Vec3d playerPos, double maxDistSq) {
        renderList.clear();
        for (List<FoundLight> list : foundLightMap.values()) {
            for (FoundLight fl : list) {
                BlockPos p = fl.pos();
                double distSq = playerPos.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (distSq > maxDistSq) continue;

                float[] fill    = resolveFillFloats(fl.method());
                float[] outline = resolveOutlineFloats(fl.method());

                renderList.add(new RenderEntry(
                        p.getX(), p.getY(), p.getZ(),
                        fill[0], fill[1], fill[2], fill[3],
                        outline[0], outline[1], outline[2], outline[3]
                ));
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.player == null || foundLightMap.isEmpty()) return;

        Vec3d camPos    = camera.getCameraPos();
        Vec3d playerPos = mc.player.getEntityPos();
        double maxDistSq = (double) renderDistance.getValue() * renderDistance.getValue();

        if (dirty.compareAndSet(true, false)) {
            rebuildRenderList(playerPos, maxDistSq);
        }

        if (renderList.isEmpty()) return;

        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());

        for (RenderEntry e : renderList) {
            double dx = e.wx() - camPos.x;
            double dy = e.wy() - camPos.y;
            double dz = e.wz() - camPos.z;

            Box box = new Box(dx + 0.01, dy + 0.01, dz + 0.01,
                    dx + 0.99, dy + 0.99, dz + 0.99);

            RenderUtil.drawFilledBoxBatch(consumer, posMatrix, box,
                    e.fr(), e.fg(), e.fb(), e.fa());

            RenderUtil.drawOutlineBoxBatch(consumer, posMatrix, box, 1.0f,
                    e.or_(), e.og(), e.ob(), e.oa());
        }

        vcp.draw();
    }

    private float[] resolveFillFloats(DetectionMethod method) {
        ColorSetting cs = resolveColor(method);
        int a = Math.min(cs.getA(), 70);
        return new float[]{ cs.getR() / 255f, cs.getG() / 255f, cs.getB() / 255f, a / 255f };
    }

    private float[] resolveOutlineFloats(DetectionMethod method) {
        ColorSetting cs = resolveColor(method);
        return new float[]{ cs.getR() / 255f, cs.getG() / 255f, cs.getB() / 255f, cs.getA() / 255f };
    }

    private ColorSetting resolveColor(DetectionMethod method) {
        return switch (method) {
            case EMISSION_MISMATCH    -> colorEmission;
            case AIR_LIGHT            -> colorAir;
            case STONE_GROUP_MISMATCH -> colorStone;
            case HIDDEN_LIGHT_BLOCK   -> colorHidden;
        };
    }

    @Override
    public String getDisplayInfo() {
        int total = foundLightMap.values().stream().mapToInt(List::size).sum();
        return total == 0 ? null : total + " flags";
    }
}
