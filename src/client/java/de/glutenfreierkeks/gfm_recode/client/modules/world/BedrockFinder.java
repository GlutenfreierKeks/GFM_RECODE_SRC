package de.glutenfreierkeks.gfm_recode.client.modules.world;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BedrockGridSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4f;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockFinder extends Module {

    private final BedrockGridSetting grid = register(new BedrockGridSetting("Grid", "The bedrock pattern to search for."));
    private final StringSetting customSeed = register(new StringSetting("Custom Seed", "Custom seed for searching.", ""));
    private final EnumSetting<PresetSeed> preset = register(new EnumSetting<>("Preset", "Preset seeds for popular servers.", PresetSeed.NONE));
    private final EnumSetting<Algorithm> algorithm = register(new EnumSetting<>("Algorithm", "The bedrock generation algorithm.", Algorithm.DEFAULT));
    private final IntSliderSetting searchRadius = register(new IntSliderSetting("Search Radius", "Radius in chunks to search.", 10000, 0, 1000000));
    private final IntSliderSetting threads = register(new IntSliderSetting("Threads", "Amount of threads to use.", 1, 1, Runtime.getRuntime().availableProcessors()));
    private final EnumSetting<SearchMode> mode = register(new EnumSetting<>("Mode", "Search alignment.", SearchMode.ANY_OFFSET));

    private boolean searching = false;
    private final Map<Long, boolean[]> chunkCache = new ConcurrentHashMap<>();

    public BedrockFinder() {
        super("BedrockFinder", "Finds coordinates based on bedrock patterns.", Category.WORLD);
    }

    public enum SearchMode {
        CHUNK_ALIGNED, ANY_OFFSET
    }

    public enum Algorithm {
        DEFAULT, TERRAINFINDER
    }

    public enum PresetSeed {
        NONE(0),
        DONUTSMP(6608149111735331168L),
        SLOWFIZ(83307555L);

        public final long seed;
        PresetSeed(long seed) { this.seed = seed; }
    }

    @Override
    protected void onEnable() {
        if (mc.world == null) return;
        startSearch();
    }

    private void startSearch() {
        if (searching) return;
        searching = true;
        chunkCache.clear();

        long seed = getSelectedSeed();
        int radius = searchRadius.getValue();
        int layer = -60;
        int centerX = (int) mc.player.getX() >> 4;
        int centerZ = (int) mc.player.getZ() >> 4;
        int threadCount = threads.getValue();

        List<int[]> relativePoints = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                if (grid.get(x, z)) relativePoints.add(new int[]{x, z});
            }
        }

        if (relativePoints.isEmpty()) {
            mc.player.sendMessage(Text.literal("§d[BedrockFinder] §cError: No blocks marked in grid!"), false);
            searching = false;
            return;
        }

        new Thread(() -> {
            mc.execute(() -> mc.player.sendMessage(Text.literal("§d[BedrockFinder] §7Searching for pattern (§f" + mode.getValue() + "§7) using " + threadCount + " threads..."), false));
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicBoolean found = new AtomicBoolean(false);
            
            // Loop through radius
            for (int r = 0; r <= radius; r++) {
                if (!searching || found.get()) break;
                
                final int currentR = r;
                executor.execute(() -> {
                    // Check ring at distance r
                    if (currentR == 0) {
                        checkChunk(centerX, centerZ, seed, layer, relativePoints, found);
                    } else {
                        for (int dx = -currentR; dx <= currentR; dx++) {
                            if (!searching || found.get()) return;
                            checkChunk(centerX + dx, centerZ + currentR, seed, layer, relativePoints, found);
                            checkChunk(centerX + dx, centerZ - currentR, seed, layer, relativePoints, found);
                        }
                        for (int dz = -currentR + 1; dz <= currentR - 1; dz++) {
                            if (!searching || found.get()) return;
                            checkChunk(centerX + currentR, centerZ + dz, seed, layer, relativePoints, found);
                            checkChunk(centerX - currentR, centerZ + dz, seed, layer, relativePoints, found);
                        }
                    }
                });
                
                // Optional: throttling or feedback
                if (r % 1000 == 0 && r > 0) {
                    mc.execute(() -> mc.player.sendMessage(Text.literal("§d[BedrockFinder] §7Searching... " + currentR + "/" + radius), false));
                }
            }
            
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (!found.get() && searching) {
                mc.execute(() -> mc.player.sendMessage(Text.literal("§d[BedrockFinder] §cNo match found in radius."), false));
            }
            searching = false;
        }, "BedrockFinder-Search").start();
    }

    private void checkChunk(int cx, int cz, long seed, int layer, List<int[]> relativePoints, AtomicBoolean found) {
        if (mode.getValue() == SearchMode.CHUNK_ALIGNED) {
            if (checkMatchAt(seed, cx, cz, 0, 0, layer, relativePoints)) {
                if (found.compareAndSet(false, true)) {
                    found(cx << 4, cz << 4);
                }
            }
        } else {
            for (int ox = 0; ox < 16; ox++) {
                for (int oz = 0; oz < 16; oz++) {
                    if (found.get()) return;
                    if (checkMatchAt(seed, cx, cz, ox, oz, layer, relativePoints)) {
                        if (found.compareAndSet(false, true)) {
                            found(cx * 16 + ox, cz * 16 + oz);
                        }
                        return;
                    }
                }
            }
        }
    }

    private void found(int x, int z) {
        mc.execute(() -> {
            mc.player.sendMessage(Text.literal("§d[BedrockFinder] §aFound match at: §f" + x + ", " + z), false);
        });
        searching = false;
    }

    private boolean checkMatchAt(long seed, int cx, int cz, int ox, int oz, int layer, List<int[]> points) {
        for (int[] p : points) {
            int wx = cx * 16 + ox + p[0];
            int wz = cz * 16 + oz + p[1];
            if (!isBedrockAt(seed, wx, wz, layer)) return false;
        }
        return true;
    }

    private boolean isBedrockAt(long seed, int wx, int wz, int layer) {
        if (algorithm.getValue() == Algorithm.TERRAINFINDER) {
            int cx = wx >> 4;
            int cz = wz >> 4;
            int lx = wx & 15;
            int lz = wz & 15;
            long key = ((long)cx << 32) | (cz & 0xFFFFFFFFL);

            boolean[] data = chunkCache.get(key);
            if (data == null) {
                data = generateTerrainFinderBedrock(cx, cz, layer);
                if (chunkCache.size() > 200) chunkCache.clear();
                chunkCache.put(key, data);
            }
            return data[lx * 16 + lz];
        }

        int cx = wx >> 4;
        int cz = wz >> 4;
        int lx = wx & 15;
        int lz = wz & 15;
        long key = ((long)cx << 32) | (cz & 0xFFFFFFFFL);

        boolean[] data = chunkCache.get(key);
        if (data == null) {
            data = generateChunkBedrock(seed, cx, cz, layer);
            if (chunkCache.size() > 200) chunkCache.clear();
            chunkCache.put(key, data);
        }
        return data[lx * 16 + lz];
    }

    private boolean[] generateTerrainFinderBedrock(int cx, int cz, int yLayer) {
        long state = (((cx * 0x4F9939F508L + cz * 0x1EF1565BD5L) ^ 0x5DEECE66DL) * 0x9D89DAE4D6C29D9L + 0x1844E300013E5B56L) & 0xFFFFFFFFFFFFL;
        boolean[] data = new boolean[256];
        for (int i = 0; i < 256; i++) {
            // Check for Y level - basically if it's bedrock at the given layer
            // The TerrainFinder logic generates for a specific layer.
            
            int y = 4; // YLayer -60 corresponds to Y=4 relative to start of bedrock
            
            boolean isBedrock = false;
            if (y == 0) {
                isBedrock = true;
            } else if (y >= 1 && y <= 4) {
                // TerrainFinder calculates (state >> 17) % 5
                // If y <= random.nextInt(5), then it's bedrock.
                // This means if nextInt(5) is 4, then Y=1,2,3,4 are all bedrock.
                // If nextInt(5) is 0, then only Y=0 is bedrock.
                
                // wait, the TerrainFinder logic:
                // dst[i] = (byte) (4 <= (state >> 17) % 5 ? 1 : 0);
                // This is for ONE SPECIFIC LAYER (probably Y=4?)
                
                // If the user is searching for 12 bedrock, they are likely looking at Y=4 or Y=0?
                // Actually, most bedrock patterns used for seed finding are at Y=4 (top layer of bottom bedrock) 
                // or Y=127 (top layer of nether bedrock).
                
                if (y <= (int)((state >> 17L) % 5)) {
                    isBedrock = true;
                }
            }
            
            data[i] = isBedrock;
            state = ((state * 0x530F32EB772C5F11L + 0x89712D3873C4CD04L) * 0x9D89DAE4D6C29D9L + 0x1844E300013E5B56L) & 0xFFFFFFFFFFFFL;
        }
        return data;
    }

    private boolean[] generateChunkBedrock(long seed, int cx, int cz, int yLayer) {
        Random chunkRandom = new Random(seed);
        long l1 = chunkRandom.nextLong() | 1L;
        long l2 = chunkRandom.nextLong() | 1L;
        chunkRandom.setSeed((long)cx * l1 + (long)cz * l2 ^ seed);

        boolean[] data = new boolean[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean isBedrock = false;
                for (int y = 4; y >= 0; y--) {
                    if (y <= chunkRandom.nextInt(5)) {
                        if (y == 4) isBedrock = true; // Hardcoded to check Y=4 (world Y -60)
                    }
                }
                data[x * 16 + z] = isBedrock;
            }
        }
        return data;
    }

    public long getSelectedSeed() {
        if (preset.getValue() != PresetSeed.NONE) return preset.getValue().seed;
        try {
            return Long.parseLong(customSeed.getValue());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        // Could render found locations here
    }

    @Override
    public String getDisplayInfo() {
        return preset.getValue() != PresetSeed.NONE ? preset.getValue().name() : null;
    }
}
