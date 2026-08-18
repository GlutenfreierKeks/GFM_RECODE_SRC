package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SpawnerFinder extends Module {

    public final BoolSetting showNormal = register(new BoolSetting("Normal Spawner", "Highlight normal mob_spawner blocks", true));
    public final BoolSetting showFake = register(new BoolSetting("Fake Spawner", "Highlight spawner traits on non-spawner blocks", true));
    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw tracer lines to found spawners", false));
    public final BoolSetting chatNotify = register(new BoolSetting("Chat Notify", "Send a chat message when a new spawner is found", true));

    public final ColorSetting colorNormal = register(new ColorSetting("Normal Color", "Color for normal spawners", 255, 140, 0, 255));
    public final ColorSetting colorFake = register(new ColorSetting("Fake Color", "Color for fake spawners", 255, 20, 20, 255));

    private record SpawnerEntry(BlockPos pos, boolean fake, boolean lightAnomaly, int score, long lastSeenTick, String info) {}

    private final ConcurrentHashMap<ChunkPos, List<SpawnerEntry>> foundMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Long> lastScanTick = new ConcurrentHashMap<>();
    private final Set<ChunkPos> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> notified = ConcurrentHashMap.newKeySet();
    private final AtomicLong activeScanCount = new AtomicLong(0);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile List<SpawnerEntry> renderEntries = new ArrayList<>();

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;
    private long worldTick = 0L;

    public SpawnerFinder() {
        super("SpawnerFinder",
                "Scans and remembers loaded chunks for real spawners, fake spawner NBT/signatures and light anomalies without lag spikes.",
                Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        foundMap.clear();
        lastScanTick.clear();
        queuedChunks.clear();
        notified.clear();
        renderEntries = new ArrayList<>();
        dirty.set(false);
        worldTick = 0L;
        shouldScan = true;
        scannerPool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "SpawnerFinder-Worker");
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
        foundMap.clear();
        lastScanTick.clear();
        queuedChunks.clear();
        renderEntries = new ArrayList<>();
        dirty.set(false);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        worldTick++;

        int viewDist = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = -viewDist; cx <= viewDist; cx++) {
            for (int cz = -viewDist; cz <= viewDist; cz++) {
                ChunkPos cp = new ChunkPos(pCx + cx, pCz + cz);
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
                if (chunk == null) continue;

                long lastTick = lastScanTick.getOrDefault(cp, Long.MIN_VALUE);
                if (!queuedChunks.contains(cp) && activeScanCount.get() < 1 && worldTick - lastTick >= 240L) {
                    scheduleChunkScan(chunk);
                }
            }
        }

        foundMap.keySet().removeIf(cp -> {
            if (mc.world == null) return false;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
            if (chunk == null) {
                foundMap.remove(cp);
                lastScanTick.remove(cp);
                dirty.set(true);
                return true;
            }
            return false;
        });

        if (dirty.compareAndSet(true, false)) {
            List<SpawnerEntry> all = new ArrayList<>();
            for (List<SpawnerEntry> entries : foundMap.values()) {
                all.addAll(entries);
            }
            all.sort(Comparator.comparingInt(SpawnerEntry::score).reversed());
            renderEntries = all;
        }
    }

    private void scheduleChunkScan(WorldChunk chunk) {
        if (!shouldScan || scannerPool == null || scannerPool.isShutdown()) return;
        ChunkPos cp = chunk.getPos();
        if (!queuedChunks.add(cp)) return;
        scannerPool.submit(() -> scanChunk(chunk));
    }

    private void scanChunk(WorldChunk chunk) {
        if (!shouldScan || chunk == null) return;
        ChunkPos cp = chunk.getPos();
        activeScanCount.incrementAndGet();
        try {
            if (mc.world == null) return;

            Map<BlockPos, SpawnerEntry> merged = new HashMap<>();
            for (SpawnerEntry old : foundMap.getOrDefault(cp, List.of())) {
                merged.put(old.pos(), old);
            }

            inspectDirectSpawnerBlocks(chunk, merged);

            for (BlockEntity be : chunk.getBlockEntities().values()) {
                inspectBlockEntity(be, merged);
            }

            if (merged.isEmpty()) {
                int minY = mc.world.getBottomY();
                int maxY = mc.world.getBottomY() + mc.world.getHeight() - 1;
                int startX = cp.getStartX();
                int startZ = cp.getStartZ();

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = maxY; y >= minY; y--) {
                            if (!shouldScan || mc.world == null) return;

                            BlockPos pos = new BlockPos(startX + lx, y, startZ + lz);
                            inspectLightAnomaly(pos, merged);
                        }
                    }
                }
            }

            List<SpawnerEntry> results = merged.values().stream()
                    .sorted(Comparator.comparingInt(SpawnerEntry::score).reversed())
                    .toList();
            foundMap.put(cp, results);
            lastScanTick.put(cp, worldTick);
            dirty.set(true);
        } catch (Exception ignored) {
        } finally {
            queuedChunks.remove(cp);
            activeScanCount.decrementAndGet();
        }
    }

    private void inspectDirectSpawnerBlocks(WorldChunk chunk, Map<BlockPos, SpawnerEntry> merged) {
        if (mc.world == null) return;
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getBottomY() + mc.world.getHeight() - 1;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(startX + lx, y, startZ + lz);
                    BlockState state = mc.world.getBlockState(pos);
                    if (!state.isOf(Blocks.SPAWNER)) continue;

                    boolean lightAnomaly = hasLightAnomaly(pos, state);
                    SpawnerEntry entry = new SpawnerEntry(pos.toImmutable(), false, lightAnomaly, lightAnomaly ? 8 : 7, worldTick,
                            lightAnomaly ? "spawner_block | Licht-Anomalie" : "spawner_block");
                    merged.put(pos.toImmutable(), mergeSpawnerEntry(merged.get(pos), entry));
                    notifyIfNeeded(state, merged.get(pos.toImmutable()));
                }
            }
        }
    }

    private void inspectBlockEntity(BlockEntity be, Map<BlockPos, SpawnerEntry> merged) {
        if (mc.world == null || be == null) return;

        BlockPos pos = be.getPos().toImmutable();
        BlockState state = mc.world.getBlockState(pos);
        boolean isSpawnerBlock = state.isOf(Blocks.SPAWNER);
        boolean hasMobSpawnerBe = be instanceof MobSpawnerBlockEntity;
        NbtCompound nbt = safeCreateNbt(be);
        boolean hasSpawnerNbt = isSpawnerLikeNbt(nbt);
        boolean particleSignature = hasSpawnerParticleSignature(nbt);
        boolean lightAnomaly = hasLightAnomaly(pos, state);

        int score = 0;
        List<String> infoParts = new ArrayList<>();
        if (isSpawnerBlock) {
            score += 6;
            infoParts.add("spawner_block");
        }
        if (hasMobSpawnerBe) {
            score += 8;
            infoParts.add("MobSpawnerBlockEntity");
        }
        if (hasSpawnerNbt) {
            score += 6;
            infoParts.add("Spawner-NBT");
        }
        if (particleSignature) {
            score += 5;
            infoParts.add("Spawner-Partikel-Signatur");
        }
        if (lightAnomaly) {
            score += 2;
            infoParts.add("Licht-Anomalie");
        }
        if (score <= 0) return;

        boolean fake = !isSpawnerBlock && (hasMobSpawnerBe || hasSpawnerNbt || particleSignature);
        if (fake && !showFake.getValue()) return;
        if (!fake && !showNormal.getValue()) return;

        String nbtInfo = buildInfoFromNbt(nbt);
        if (!nbtInfo.isEmpty()) infoParts.add(nbtInfo);

        SpawnerEntry entry = new SpawnerEntry(pos, fake, lightAnomaly, score, worldTick, String.join(" | ", infoParts));
        merged.put(pos, mergeSpawnerEntry(merged.get(pos), entry));
        notifyIfNeeded(state, merged.get(pos));
    }

    private void inspectLightAnomaly(BlockPos pos, Map<BlockPos, SpawnerEntry> merged) {
        if (mc.world == null) return;
        BlockState state = mc.world.getBlockState(pos);
        if (!hasLightAnomaly(pos, state)) return;

        String blockName = state.getBlock().getName().getString().toLowerCase();
        if (!blockName.contains("spawner")) return;

        SpawnerEntry entry = new SpawnerEntry(pos.toImmutable(), !state.isOf(Blocks.SPAWNER), true, 2, worldTick, "Licht-Anomalie | name:" + blockName);
        merged.put(pos.toImmutable(), mergeSpawnerEntry(merged.get(pos), entry));
        notifyIfNeeded(state, merged.get(pos.toImmutable()));
    }

    private SpawnerEntry mergeSpawnerEntry(SpawnerEntry oldEntry, SpawnerEntry newEntry) {
        if (oldEntry == null) return newEntry;
        return new SpawnerEntry(
                newEntry.pos(),
                newEntry.fake() || oldEntry.fake(),
                newEntry.lightAnomaly() || oldEntry.lightAnomaly(),
                Math.max(oldEntry.score(), newEntry.score()),
                worldTick,
                oldEntry.info() + " || " + newEntry.info()
        );
    }

    private void notifyIfNeeded(BlockState state, SpawnerEntry entry) {
        if (mc.player == null || !chatNotify.getValue() || entry == null || !notified.add(entry.pos())) return;

        String blockName = state.getBlock().getName().getString();
        if (entry.fake()) {
            mc.player.sendMessage(Text.literal(
                    "§7[§cSpawnerFinder§7] §cFAKE-Spawner! §fBlock: §e" + blockName +
                            " §f@ §e" + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ() +
                            " §8| §7" + entry.info()
            ), false);
        } else {
            mc.player.sendMessage(Text.literal(
                    "§7[§aSpawnerFinder§7] §aSpawner gefunden §f@ §e" +
                            entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ() +
                            " §8| §7" + entry.info()
            ), false);
        }
    }

    private NbtCompound safeCreateNbt(BlockEntity be) {
        if (mc.world == null) return null;
        try {
            return be.createNbt(mc.world.getRegistryManager());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isSpawnerLikeNbt(NbtCompound nbt) {
        if (nbt == null) return false;
        return nbt.contains("SpawnData")
                || nbt.contains("SpawnPotentials")
                || nbt.contains("SpawnCount")
                || nbt.contains("SpawnRange")
                || nbt.contains("Delay")
                || nbt.contains("MinSpawnDelay")
                || nbt.contains("MaxSpawnDelay")
                || nbt.contains("MaxNearbyEntities")
                || nbt.contains("RequiredPlayerRange");
    }

    private boolean hasSpawnerParticleSignature(NbtCompound nbt) {
        if (nbt == null) return false;
        return (nbt.contains("Delay") || nbt.contains("SpawnData"))
                && (nbt.contains("SpawnCount") || nbt.contains("MinSpawnDelay") || nbt.contains("MaxSpawnDelay"));
    }

    private boolean hasLightAnomaly(BlockPos pos, BlockState state) {
        if (mc.world == null) return false;
        try {
            int expected = state.getLuminance();
            int actual = mc.world.getLightLevel(LightType.BLOCK, pos);
            return actual > expected && actual > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String buildInfoFromNbt(NbtCompound nbt) {
        if (nbt == null) return "";
        try {
            StringBuilder sb = new StringBuilder();

            if (nbt.contains("SpawnData")) {
                NbtCompound spawnData = nbt.getCompound("SpawnData").orElse(null);
                if (spawnData != null && spawnData.contains("entity")) {
                    NbtCompound entity = spawnData.getCompound("entity").orElse(null);
                    if (entity != null && entity.contains("id")) {
                        String id = entity.getString("id").orElse("");
                        sb.append("Mob: ").append(id.replace("minecraft:", "")).append("  ");
                    }
                }
            }
            if (nbt.contains("SpawnCount")) sb.append("Count:").append(nbt.getShort("SpawnCount").orElse((short) 0)).append("  ");
            if (nbt.contains("SpawnRange")) sb.append("Range:").append(nbt.getShort("SpawnRange").orElse((short) 0)).append("  ");
            if (nbt.contains("MinSpawnDelay") && nbt.contains("MaxSpawnDelay")) {
                sb.append("Delay:").append(nbt.getShort("MinSpawnDelay").orElse((short) 0)).append("-")
                        .append(nbt.getShort("MaxSpawnDelay").orElse((short) 0)).append("t  ");
            } else if (nbt.contains("Delay")) {
                sb.append("Delay:").append(nbt.getShort("Delay").orElse((short) 0)).append("t  ");
            }
            if (nbt.contains("RequiredPlayerRange")) {
                sb.append("PlayerRange:").append(nbt.getShort("RequiredPlayerRange").orElse((short) 0));
            }

            return sb.toString().trim();
        } catch (Exception e) {
            return "NBT unlesbar";
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        List<SpawnerEntry> snapshot = renderEntries;
        if (snapshot.isEmpty()) return;

        Vec3d camPos = camera.getCameraPos();
        boolean doTracers = tracers.getValue();

        for (SpawnerEntry e : snapshot) {
            Color base = e.fake()
                    ? new Color(colorFake.getArgb(), true)
                    : new Color(colorNormal.getArgb(), true);
            Color c = e.lightAnomaly()
                    ? new Color(Math.min(255, base.getRed()), Math.min(255, base.getGreen() + 40), Math.min(255, base.getBlue() + 40), base.getAlpha())
                    : base;
            Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), 35);

            double dx = e.pos().getX() - camPos.x;
            double dy = e.pos().getY() - camPos.y;
            double dz = e.pos().getZ() - camPos.z;

            Box box = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);
            RenderUtil.drawBox(posMatrix, box, c, 1.0);
            RenderUtil.drawFilledBox(posMatrix, box, fill);

            if (doTracers) {
                RenderUtil.drawTracer(posMatrix, new Vec3d(0, -0.1, 0), new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5), c);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return renderEntries.isEmpty() ? null : renderEntries.size() + " spawner";
    }
}
