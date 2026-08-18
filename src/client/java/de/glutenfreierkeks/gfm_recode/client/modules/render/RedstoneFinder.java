package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RedstoneFinder extends Module {

    private static final Set<String> REDSTONE_LIKE_PROPERTIES = Set.of(
            "power", "powered", "lit", "open", "triggered", "enabled", "locked",
            "attached", "inverted", "disarmed", "charges", "bites", "delay", "mode"
    );

    private static final Set<String> REDSTONE_NAME_KEYWORDS = Set.of(
            "redstone", "repeater", "comparator", "observer", "lever", "button",
            "pressure_plate", "tripwire", "tripwire_hook", "daylight_detector",
            "target", "piston", "lamp", "rail", "hopper", "dispenser", "dropper",
            "lectern", "note_block", "sculk_sensor", "calibrated_sculk_sensor",
            "lightning_rod", "door", "trapdoor"
    );

    private static final Set<net.minecraft.block.Block> DIRECT_REDSTONE_BLOCKS = Set.of(
            Blocks.REDSTONE_WIRE,
            Blocks.REDSTONE_TORCH,
            Blocks.REDSTONE_WALL_TORCH,
            Blocks.REDSTONE_BLOCK,
            Blocks.REPEATER,
            Blocks.COMPARATOR,
            Blocks.OBSERVER,
            Blocks.TARGET,
            Blocks.DAYLIGHT_DETECTOR,
            Blocks.LECTERN,
            Blocks.NOTE_BLOCK,
            Blocks.SCULK_SENSOR,
            Blocks.CALIBRATED_SCULK_SENSOR,
            Blocks.LEVER,
            Blocks.STONE_BUTTON,
            Blocks.OAK_BUTTON,
            Blocks.SPRUCE_BUTTON,
            Blocks.BIRCH_BUTTON,
            Blocks.JUNGLE_BUTTON,
            Blocks.ACACIA_BUTTON,
            Blocks.CHERRY_BUTTON,
            Blocks.DARK_OAK_BUTTON,
            Blocks.MANGROVE_BUTTON,
            Blocks.BAMBOO_BUTTON,
            Blocks.CRIMSON_BUTTON,
            Blocks.WARPED_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.OAK_PRESSURE_PLATE,
            Blocks.SPRUCE_PRESSURE_PLATE,
            Blocks.BIRCH_PRESSURE_PLATE,
            Blocks.JUNGLE_PRESSURE_PLATE,
            Blocks.ACACIA_PRESSURE_PLATE,
            Blocks.CHERRY_PRESSURE_PLATE,
            Blocks.DARK_OAK_PRESSURE_PLATE,
            Blocks.MANGROVE_PRESSURE_PLATE,
            Blocks.BAMBOO_PRESSURE_PLATE,
            Blocks.CRIMSON_PRESSURE_PLATE,
            Blocks.WARPED_PRESSURE_PLATE,
            Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.PISTON,
            Blocks.STICKY_PISTON,
            Blocks.REDSTONE_LAMP,
            Blocks.POWERED_RAIL,
            Blocks.DETECTOR_RAIL,
            Blocks.ACTIVATOR_RAIL,
            Blocks.RAIL,
            Blocks.HOPPER,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.LIGHTNING_ROD,
            Blocks.IRON_DOOR,
            Blocks.IRON_TRAPDOOR
    );

    public final IntSliderSetting startY = register(new IntSliderSetting("Start Y", "Scan from this Y level downwards", 320, -64, 320));
    public final IntSliderSetting minPower = register(new IntSliderSetting("Min Power", "Minimum power level to prefer in matches", 1, 0, 15));
    public final IntSliderSetting renderDistance = register(new IntSliderSetting("Render Distance", "Max distance (blocks) to render", 64, 16, 256));
    public final IntSliderSetting maxConcurrent = register(new IntSliderSetting("Max Concurrent", "Chunks scanned in parallel", 2, 1, 8));
    public final BoolSetting chatNotify = register(new BoolSetting("Chat Notify", "Announce active redstone-like blocks once in chat", true));
    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw tracer lines to flagged blocks", false));

    public final ColorSetting colorPowered = register(new ColorSetting("Powered Color", "Active or powered redstone-like block", 255, 30, 30, 255));
    public final ColorSetting colorUnpowered = register(new ColorSetting("Unpowered Color", "Passive redstone-like block", 120, 20, 20, 200));

    private record FoundRedstone(BlockPos pos, int score, int power, boolean active, long lastSeenTick, String info) {}

    private record RenderEntry(double wx, double wy, double wz, boolean active) {}

    private final ConcurrentHashMap<ChunkPos, List<FoundRedstone>> foundMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Long> lastScanTick = new ConcurrentHashMap<>();
    private final Set<ChunkPos> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> notified = ConcurrentHashMap.newKeySet();
    private final AtomicLong activeScanCount = new AtomicLong(0);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final List<RenderEntry> renderList = new ArrayList<>();

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;
    private long worldTick = 0L;

    public RedstoneFinder() {
        super("RedstoneFinder",
                "Scans and remembers every loaded block with redstone-like traits, power behavior and signal behavior.",
                Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        clearAll();
        shouldScan = true;
        scannerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "RedstoneFinder-Worker");
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
        foundMap.clear();
        lastScanTick.clear();
        queuedChunks.clear();
        notified.clear();
        activeScanCount.set(0);
        renderList.clear();
        dirty.set(false);
        worldTick = 0L;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        worldTick++;

        int radius = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;
        List<ChunkPos> candidates = new ArrayList<>();

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                candidates.add(new ChunkPos(pCx + cx, pCz + cz));
            }
        }

        candidates.sort(Comparator.comparingInt(cp -> Math.abs(cp.x - pCx) + Math.abs(cp.z - pCz)));
        for (ChunkPos cp : candidates) {
            if (activeScanCount.get() >= maxConcurrent.getValue()) break;

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
            if (chunk == null) continue;

            long lastTick = lastScanTick.getOrDefault(cp, Long.MIN_VALUE);
            if (!queuedChunks.contains(cp) && worldTick - lastTick >= 120L) {
                scheduleChunkScan(chunk);
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

            int minY = mc.world.getBottomY();
            int topY = mc.world.getBottomY() + mc.world.getHeight() - 1;
            int minPowerVal = minPower.getValue();
            int playerY = mc.player != null ? mc.player.getBlockY() : Math.min(startY.getValue(), topY);
            int centerY = Math.max(minY, Math.min(topY, playerY));
            int configuredTopY = Math.min(startY.getValue(), topY);
            int primaryTopY = Math.max(centerY, configuredTopY);
            List<Integer> yOrder = buildPriorityYOrder(minY, topY, centerY, primaryTopY);

            Map<BlockPos, FoundRedstone> merged = new HashMap<>();
            for (FoundRedstone old : foundMap.getOrDefault(cp, List.of())) {
                merged.put(old.pos(), old);
            }

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int y : yOrder) {
                        if (!shouldScan || mc.world == null) return;

                        BlockPos pos = new BlockPos(cp.getStartX() + lx, y, cp.getStartZ() + lz);
                        BlockState state = mc.world.getBlockState(pos);
                        FoundRedstone hit = classifyRedstoneLikeBlock(pos, state, minPowerVal);
                        if (hit == null) continue;

                        FoundRedstone previous = merged.get(hit.pos());
                        if (previous != null && previous.score() > hit.score()) {
                            hit = new FoundRedstone(hit.pos(), previous.score(), Math.max(previous.power(), hit.power()),
                                    hit.active() || previous.active(), worldTick, previous.info() + " || " + hit.info());
                        }
                        merged.put(hit.pos(), hit);

                        if (chatNotify.getValue() && hit.active()) {
                            BlockPos immPos = pos.toImmutable();
                            if (notified.add(immPos)) {
                                FoundRedstone finalHit = hit;
                                mc.execute(() -> {
                                    if (mc.player != null) {
                                        mc.player.sendMessage(Text.literal(
                                                "§7[§cRedstoneFinder§7] §cTreffer! §f" + finalHit.info() +
                                                        " §f@ §e" + immPos.getX() + ", " + immPos.getY() + ", " + immPos.getZ()
                                        ), false);
                                    }
                                });
                            }
                        }
                    }
                }
            }

            List<FoundRedstone> results = merged.values().stream()
                    .sorted(Comparator.comparingInt(FoundRedstone::score).reversed())
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

    private List<Integer> buildPriorityYOrder(int minY, int maxY, int centerY, int fallbackTopY) {
        List<Integer> order = new ArrayList<>(maxY - minY + 1);
        boolean[] used = new boolean[maxY - minY + 1];

        addY(order, used, minY, maxY, centerY);
        for (int offset = 1; offset <= maxY - minY; offset++) {
            addY(order, used, minY, maxY, centerY - offset);
            addY(order, used, minY, maxY, centerY + offset);
        }

        for (int y = fallbackTopY; y >= minY; y--) {
            addY(order, used, minY, maxY, y);
        }
        for (int y = maxY; y > fallbackTopY; y--) {
            addY(order, used, minY, maxY, y);
        }
        return order;
    }

    private void addY(List<Integer> order, boolean[] used, int minY, int maxY, int y) {
        if (y < minY || y > maxY) return;
        int index = y - minY;
        if (used[index]) return;
        used[index] = true;
        order.add(y);
    }

    private FoundRedstone classifyRedstoneLikeBlock(BlockPos pos, BlockState state, int minPowerVal) {
        if (mc.world == null) return null;

        int score = 0;
        boolean active = false;
        int strongestPower = 0;
        List<String> infoParts = new ArrayList<>();

        String blockName = Registries.BLOCK.getId(state.getBlock()).toString().toLowerCase(Locale.ROOT);
        if (DIRECT_REDSTONE_BLOCKS.contains(state.getBlock())) {
            score += 8;
            infoParts.add("direct_block");
        }
        for (String keyword : REDSTONE_NAME_KEYWORDS) {
            if (blockName.contains(keyword)) {
                score += 2;
                infoParts.add("name:" + keyword);
                break;
            }
        }

        if (state.isOf(Blocks.REDSTONE_WIRE)) {
            score += 10;
            infoParts.add("wire");
        }
        if (state.isOf(Blocks.REPEATER) || state.isOf(Blocks.COMPARATOR)) {
            score += 6;
            infoParts.add("logic_block");
        }
        if (state.isOf(Blocks.OBSERVER)) {
            score += 6;
            infoParts.add("observer");
        }
        if (state.isOf(Blocks.REDSTONE_TORCH) || state.isOf(Blocks.REDSTONE_WALL_TORCH)) {
            score += 7;
            active = true;
            strongestPower = Math.max(strongestPower, 15);
            infoParts.add("torch_like");
        }

        if (state.contains(Properties.POWER)) {
            int power = state.get(Properties.POWER);
            strongestPower = Math.max(strongestPower, power);
            score += 5;
            if (power > 0) active = true;
            infoParts.add("POWER=" + power);
        }

        Set<String> presentProperties = new LinkedHashSet<>();
        for (Property<?> property : state.getProperties()) {
            String name = property.getName().toLowerCase(Locale.ROOT);
            if (!REDSTONE_LIKE_PROPERTIES.contains(name)) continue;

            presentProperties.add(name);
            score += 2;
            try {
                Comparable<?> comparable = state.get(property);
                if (comparable instanceof Boolean bool && bool) {
                    active = true;
                    score += 2;
                } else if (comparable instanceof Number number) {
                    strongestPower = Math.max(strongestPower, number.intValue());
                    if (number.intValue() > 0) {
                        active = true;
                        score += 1;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (!presentProperties.isEmpty()) {
            infoParts.add("props=" + String.join(",", presentProperties));
        }

        if (state.contains(Properties.POWERED)) {
            score += 3;
            if (state.get(Properties.POWERED)) {
                active = true;
                strongestPower = Math.max(strongestPower, 15);
            }
            infoParts.add("powered=" + state.get(Properties.POWERED));
        }

        if (state.contains(Properties.LIT)) {
            score += 2;
            if (state.get(Properties.LIT)) active = true;
            infoParts.add("lit=" + state.get(Properties.LIT));
        }

        if (state.contains(Properties.OPEN)) {
            score += 2;
            infoParts.add("open=" + state.get(Properties.OPEN));
        }

        if (state.contains(Properties.TRIGGERED)) {
            score += 3;
            if (state.get(Properties.TRIGGERED)) active = true;
            infoParts.add("triggered=" + state.get(Properties.TRIGGERED));
        }

        if (state.contains(Properties.INVERTED)) {
            score += 2;
            infoParts.add("inverted=" + state.get(Properties.INVERTED));
        }

        try {
            if (state.emitsRedstonePower()) {
                score += 3;
                infoParts.add("emits");
                for (Direction direction : Direction.values()) {
                    int weak = mc.world.getEmittedRedstonePower(pos, direction);
                    if (weak > 0) {
                        strongestPower = Math.max(strongestPower, weak);
                        active = true;
                        score += 2;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        int poweredNeighbors = 0;
        int redstoneNeighbors = 0;
        for (Direction direction : Direction.values()) {
            try {
                BlockPos neighborPos = pos.offset(direction);
                BlockState neighbor = mc.world.getBlockState(neighborPos);
                String neighborId = Registries.BLOCK.getId(neighbor.getBlock()).toString().toLowerCase(Locale.ROOT);
                if (DIRECT_REDSTONE_BLOCKS.contains(neighbor.getBlock()) || neighborId.contains("redstone") || neighbor.contains(Properties.POWER)) {
                    redstoneNeighbors++;
                }
                if (mc.world.getReceivedRedstonePower(neighborPos) > 0 || mc.world.isReceivingRedstonePower(neighborPos)) {
                    poweredNeighbors++;
                }
            } catch (Exception ignored) {
            }
        }
        if (redstoneNeighbors > 0) {
            score += Math.min(4, redstoneNeighbors);
            infoParts.add("neighbors=" + redstoneNeighbors);
        }
        if (poweredNeighbors > 0) {
            strongestPower = Math.max(strongestPower, poweredNeighbors);
            active = true;
            score += Math.min(5, poweredNeighbors);
            infoParts.add("powered_neighbors=" + poweredNeighbors);
        }

        try {
            int receivedPower = mc.world.getReceivedRedstonePower(pos);
            if (receivedPower > 0 || mc.world.isReceivingRedstonePower(pos)) {
                strongestPower = Math.max(strongestPower, receivedPower);
                active = true;
                score += 4;
                infoParts.add("receives=" + receivedPower);
            }
        } catch (Exception ignored) {
        }

        try {
            if (state.hasComparatorOutput()) {
                int comparatorOutput = 0;
                for (Direction direction : Direction.values()) {
                    comparatorOutput = Math.max(comparatorOutput, state.getComparatorOutput(mc.world, pos, direction));
                }
                score += 2;
                infoParts.add("comparator=" + comparatorOutput);
                if (comparatorOutput > 0) {
                    strongestPower = Math.max(strongestPower, comparatorOutput);
                    active = true;
                    score += 2;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (state.contains(Properties.DELAY)) {
                score += 3;
                infoParts.add("delay=" + state.get(Properties.DELAY));
            }
        } catch (Exception ignored) {
        }

        try {
            if (state.contains(Properties.FACING) || state.contains(Properties.HORIZONTAL_FACING)) {
                if (state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.REPEATER) || state.isOf(Blocks.COMPARATOR)
                        || state.isOf(Blocks.PISTON) || state.isOf(Blocks.STICKY_PISTON)) {
                    score += 2;
                    infoParts.add("facing_controlled");
                }
            }
        } catch (Exception ignored) {
        }

        if (score <= 0) return null;
        if (state.isOf(Blocks.REDSTONE_WIRE) && strongestPower < minPowerVal) return null;
        if (strongestPower < minPowerVal && score < 2 && !active && !DIRECT_REDSTONE_BLOCKS.contains(state.getBlock())) return null;
        if (infoParts.isEmpty()) infoParts.add("redstone-like");

        return new FoundRedstone(pos.toImmutable(), score, strongestPower, active, worldTick, String.join(" | ", infoParts));
    }

    private void rebuildRenderList(Vec3d playerPos, double maxDistSq) {
        renderList.clear();
        for (List<FoundRedstone> list : foundMap.values()) {
            for (FoundRedstone fr : list) {
                BlockPos p = fr.pos();
                double distSq = playerPos.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (distSq > maxDistSq) continue;
                renderList.add(new RenderEntry(p.getX(), p.getY(), p.getZ(), fr.active()));
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.player == null || foundMap.isEmpty()) return;

        Vec3d camPos = camera.getCameraPos();
        Vec3d playerPos = mc.player.getEntityPos();
        double maxDistSq = (double) renderDistance.getValue() * renderDistance.getValue();

        if (dirty.compareAndSet(true, false)) {
            rebuildRenderList(playerPos, maxDistSq);
        }
        if (renderList.isEmpty()) return;

        boolean doTracers = tracers.getValue();
        for (RenderEntry e : renderList) {
            double dx = e.wx() - camPos.x;
            double dy = e.wy() - camPos.y;
            double dz = e.wz() - camPos.z;

            Box box = new Box(dx + 0.05, dy, dz + 0.05, dx + 0.95, dy + 0.25, dz + 0.95);
            Color baseColor = e.active()
                    ? new Color(colorPowered.getArgb(), true)
                    : new Color(colorUnpowered.getArgb(), true);
            Color fillColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.max(12, Math.min(255, (int) (baseColor.getAlpha() * 0.35f))));

            RenderUtil.drawFilledBox(posMatrix, box, fillColor);
            RenderUtil.drawBox(posMatrix, box, baseColor, 1.0);

            if (doTracers) {
                RenderUtil.drawTracer(posMatrix, new Vec3d(0, -0.1, 0), new Vec3d(dx + 0.5, dy + 0.15, dz + 0.5), baseColor);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        int total = foundMap.values().stream().mapToInt(List::size).sum();
        return total == 0 ? null : total + " redstone";
    }
}
