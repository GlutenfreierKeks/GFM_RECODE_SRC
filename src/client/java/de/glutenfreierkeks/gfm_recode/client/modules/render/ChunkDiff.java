package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;

public class ChunkDiff extends Module {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("ChunkDiff");

    // ── Settings ──────────────────────────────────────────────────────────────
    public final StringSetting       seedInput  = register(new StringSetting      ("World Seed",  "World seed as number",         "0"           ));
    public final IntSliderSetting    scanRadius = register(new IntSliderSetting   ("Scan Radius", "Chunk radius around player",    2, 1, 6      ));
    public final BoolSetting         fillBoxes  = register(new BoolSetting        ("Fill Boxes",  "Fill boxes with color",         true          ));
    public final DoubleSliderSetting lineWidth  = register(new DoubleSliderSetting("Line Width",  "",                              1.0, 0.1, 3.0 ));
    public final IntSliderSetting    maxDiffs   = register(new IntSliderSetting   ("Max Diffs",   "Max rendered per frame",        2000, 50, 10000));

    private static final Color COL = new Color(0, 255, 0, 210);

    // ── State ─────────────────────────────────────────────────────────────────
    private final ConcurrentHashMap<BlockPos, Boolean> diffs   = new ConcurrentHashMap<>();
    private final Set<ChunkPos>                        scanned = ConcurrentHashMap.newKeySet();
    private ExecutorService executor;
    private volatile boolean active         = false;
    private volatile long    lastSeed       = Long.MIN_VALUE;
    private volatile boolean generatorReady = false;

    private volatile NoiseChunkGenerator generator;
    private volatile NoiseConfig         noiseConfig;

    public ChunkDiff() {
        super("ChunkDiff", "Compares chunks against seeded Vanilla terrain", Category.RENDER);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onEnable() {
        diffs.clear();
        scanned.clear();
        active = true;
        generatorReady = false;

        executor = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                r -> { Thread t = new Thread(r, "ChunkDiff-Worker"); t.setDaemon(true); return t; }
        );

        buildGenerator();
    }

    @Override
    protected void onDisable() {
        active = false;
        if (executor != null) { executor.shutdownNow(); executor = null; }
        diffs.clear();
        scanned.clear();
        generator   = null;
        noiseConfig = null;
        generatorReady = false;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null || !active) return;

        long parsed = parseSeed();
        if (parsed != lastSeed) {
            lastSeed = parsed;
            diffs.clear();
            scanned.clear();
            buildGenerator();
        }

        if (!generatorReady) return;

        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;
        int r   = scanRadius.getValue();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos cp = new ChunkPos(pCx + dx, pCz + dz);
                if (scanned.contains(cp)) continue;

                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
                if (chunk == null) continue;

                scanned.add(cp);
                executor.submit(() -> analyzeChunk(chunk));
            }
        }

        if (mc.player.age % 20 == 0) {
            diffs.keySet().removeIf(pos -> {
                int ddx = Math.abs((pos.getX() >> 4) - pCx);
                int ddz = Math.abs((pos.getZ() >> 4) - pCz);
                return ddx > r + 1 || ddz > r + 1;
            });
            scanned.removeIf(cp -> Math.abs(cp.x - pCx) > r + 1 || Math.abs(cp.z - pCz) > r + 1);
        }
    }

    // ── Analysis ──────────────────────────────────────────────────────────────
    private void analyzeChunk(WorldChunk real) {
        if (!active || generator == null || noiseConfig == null || mc.world == null) return;

        ChunkPos cp = real.getPos();
        try {
            ProtoChunk proto = createProtoChunk(cp);
            if (proto == null) {
                LOG.warn("[ChunkDiff] createProtoChunk returned null for {}", cp);
                return;
            }

            generator.populateNoise(
                    Blender.getNoBlending(),
                    noiseConfig,
                    (StructureAccessor) null,
                    proto
            ).join();

            // Debug: count how many non-air blocks the proto got
            int protoBlocks = 0;
            int minY = real.getBottomY();
            int maxY = real.getBottomY() + real.getHeight();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockPos pos = new BlockPos(cp.getStartX() + x, y, cp.getStartZ() + z);
                        if (!proto.getBlockState(pos).isAir()) protoBlocks++;
                    }
                }
            }
            LOG.info("[ChunkDiff] chunk {} -> proto has {} non-air blocks in Y=[{},{}]", cp, protoBlocks, minY, maxY);

            // Now compare: flag any position where the block type differs
            int diffCount = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        if (!active) return;

                        BlockPos   pos      = new BlockPos(cp.getStartX() + x, y, cp.getStartZ() + z);
                        BlockState expected = proto.getBlockState(pos);
                        BlockState actual   = real.getBlockState(pos);

                        if (expected.getBlock() != actual.getBlock()) {
                            diffs.put(pos, true);
                            diffCount++;
                        }
                    }
                }
            }
            LOG.info("[ChunkDiff] chunk {} -> {} diffs found", cp, diffCount);

        } catch (Exception e) {
            LOG.error("[ChunkDiff] error analyzing chunk {}: {}", cp, e.toString());
        }
    }

    // ── ProtoChunk via Reflection ─────────────────────────────────────────────
    private static Constructor<?> PROTO_CHUNK_CTOR;

    private ProtoChunk createProtoChunk(ChunkPos cp) {
        if (mc.world == null) return null;
        try {
            if (PROTO_CHUNK_CTOR == null) {
                for (Constructor<?> c : ProtoChunk.class.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    // Modern ProtoChunk usually: ChunkPos, UpgradeData, HeightLimitView, Registry<Biome>, BlendingData
                    if (p.length >= 5 && p[0] == ChunkPos.class && p[1] == UpgradeData.class) {
                        c.setAccessible(true);
                        PROTO_CHUNK_CTOR = c;
                        break;
                    }
                }
            }

            if (PROTO_CHUNK_CTOR != null) {
                var biomeRegistry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                return (ProtoChunk) PROTO_CHUNK_CTOR.newInstance(
                    cp, UpgradeData.NO_UPGRADE_DATA, mc.world, biomeRegistry, null
                );
            }
        } catch (Exception e) {
            LOG.error("[ChunkDiff] ProtoChunk creation failed: {}", e.toString());
        }
        return null;
    }

    // ── Generator Setup ───────────────────────────────────────────────────────
    private void buildGenerator() {
        generator      = null;
        noiseConfig    = null;
        generatorReady = false;

        if (mc.world == null) return;

        try {
            long seed = parseSeed();
            
            // 1. Start with the world registries
            DynamicRegistryManager reg = mc.world.getRegistryManager();
            
            // 2. DEEP SCAN with Logging
            try {
                for (java.lang.reflect.Field f : mc.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(mc);
                    if (val instanceof DynamicRegistryManager drm) {
                        LOG.info("[ChunkDiff] Registry Scanner found DRM in field '{}' with {} registries", f.getName(), drm.streamAllRegistries().count());
                        if (drm.streamAllRegistries().count() > reg.streamAllRegistries().count()) {
                            reg = drm;
                        }
                    } else if (val != null && (val.getClass().getName().contains("CombinedDynamicRegistries") || val.getClass().getSimpleName().contains("CombinedDynamicRegistries"))) {
                        for (java.lang.reflect.Method m : val.getClass().getDeclaredMethods()) {
                            if (m.getParameterCount() == 0 && (m.getReturnType() == DynamicRegistryManager.class || m.getReturnType().getName().contains("DynamicRegistryManager"))) {
                                m.setAccessible(true);
                                DynamicRegistryManager combinedDrm = (DynamicRegistryManager) m.invoke(val);
                                LOG.info("[ChunkDiff] Registry Scanner found DRM in Combined with {} registries", combinedDrm.streamAllRegistries().count());
                                if (combinedDrm.streamAllRegistries().count() > reg.streamAllRegistries().count()) {
                                    reg = combinedDrm;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            RegistryEntry.Reference<ChunkGeneratorSettings> settings = null;
            
            // 3. Hacky Check for active generator
            try {
                Object cm = mc.world.getChunkManager();
                for (java.lang.reflect.Method m : cm.getClass().getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getName().contains("ChunkGenerator")) {
                        m.setAccessible(true);
                        Object worldGen = m.invoke(cm);
                        if (worldGen instanceof NoiseChunkGenerator noiseGen) {
                            settings = (RegistryEntry.Reference<ChunkGeneratorSettings>) noiseGen.getSettings();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 4. Resolve settings
            if (settings == null) {
                Registry<ChunkGeneratorSettings> genSettingsReg = reg.getOptional(RegistryKeys.CHUNK_GENERATOR_SETTINGS).orElse(null);
                if (genSettingsReg == null) {
                    for (var rEntry : reg.streamAllRegistries().toList()) {
                        String id = rEntry.key().getValue().toString();
                        if (id.contains("settings") || id.contains("noise")) {
                            LOG.info("[ChunkDiff] Found potential registry: {}", id);
                        }
                        if (id.endsWith("chunk_generator_settings") || id.endsWith("noise_settings")) {
                            genSettingsReg = (Registry<ChunkGeneratorSettings>) rEntry.value();
                            break;
                        }
                    }
                }
                
                if (genSettingsReg == null) {
                    LOG.error("[ChunkDiff] All registries scanned. No settings found. Available registries in current DRM:");
                    reg.streamAllRegistries().forEach(e -> LOG.error("  - {}", e.key().getValue()));
                    throw new NoSuchElementException("Missing chunk generator settings registry (even in internal scan)");
                }

                final Registry<ChunkGeneratorSettings> finalReg = genSettingsReg;
                settings = finalReg.getEntry(ChunkGeneratorSettings.OVERWORLD.getValue())
                        .or(() -> finalReg.getEntry(Identifier.ofVanilla("overworld")))
                        .orElseThrow();
            }
            
            var biomeSource = new FixedBiomeSource(mc.world.getBiome(mc.player.getBlockPos()));
            generator = new NoiseChunkGenerator(biomeSource, settings);

            // 5. Noise Parameters
            Registry<?> noiseParamsReg = reg.getOptional(RegistryKeys.NOISE_PARAMETERS).orElse(null);
            if (noiseParamsReg == null) {
                for (var rEntry : reg.streamAllRegistries().toList()) {
                    if (rEntry.key().getValue().getPath().contains("noise") && !rEntry.key().getValue().getPath().contains("settings")) {
                        noiseParamsReg = (Registry<?>) rEntry.value();
                        break;
                    }
                }
            }
            if (noiseParamsReg == null) throw new IllegalStateException("Missing noise parameters");

            noiseConfig = NoiseConfig.create(settings.value(), (net.minecraft.registry.RegistryEntryLookup) noiseParamsReg, seed);

            LOG.info("[ChunkDiff] Generator built successfully with seed {}", seed);
            generatorReady = true;
        } catch (Exception e) {
            LOG.error("[ChunkDiff] buildGenerator failed: {}", e.toString());
            generatorReady = false;
        }
    }

    private long parseSeed() {
        String s = seedInput.getValue().trim();
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return (long) s.hashCode(); }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (diffs.isEmpty()) return;

        Vec3d cam   = camera.getCameraPos();
        int   cap   = maxDiffs.getValue();
        int   count = 0;

        for (BlockPos bp : diffs.keySet()) {
            if (count++ >= cap) break;

            double dx = bp.getX() - cam.x;
            double dy = bp.getY() - cam.y;
            double dz = bp.getZ() - cam.z;
            Box box = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);

            if (fillBoxes.getValue())
                RenderUtil.drawFilledBox(posMatrix, box, new Color(0, 255, 0, 45));
            RenderUtil.drawBox(posMatrix, box, COL, lineWidth.getValue());
        }
    }
}