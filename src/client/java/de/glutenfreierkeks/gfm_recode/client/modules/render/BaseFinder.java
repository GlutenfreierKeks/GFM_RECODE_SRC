package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseFinder extends Module {

    // === Detection Settings ===
    public final IntSliderSetting minBudsPerChunk = register(new IntSliderSetting("Min Buds", "Mindestanzahl an Amethyst Buds pro Chunk", 8, 3, 30));
    public final IntSliderSetting scanRadius = register(new IntSliderSetting("Scan Radius", "Chunks um den Spieler", 8, 4, 12));
    public final IntSliderSetting scanDelay = register(new IntSliderSetting("Scan Delay", "Ticks zwischen Scans", 12, 5, 40));

    // === Render Settings ===
    public final BoolSetting renderChunkBox = register(new BoolSetting("Render Chunk Box", "Zeigt große Chunk-Box", true));
    public final BoolSetting renderCircle = register(new BoolSetting("Render Circle", "Zeigt Circle in Chunk-Mitte", true));
    public final ColorSetting chunkColor = register(new ColorSetting("Chunk Color", "Farbe der Markierung", 0, 255, 255, 180)); // Cyan

    public final DoubleSliderSetting circleRadius = register(new DoubleSliderSetting("Circle Radius", "", 8.0, 4.0, 15.0));
    public final DoubleSliderSetting boxHeight = register(new DoubleSliderSetting("Box Height", "Höhe der Chunk-Box", 180.0, 50.0, 300.0));

    // === Performance ===
    public final BoolSetting useCache = register(new BoolSetting("Use Cache", "Chunk Cache verwenden", true));

    private final List<ChunkPos> foundChunks = new ArrayList<>();
    private final Map<ChunkPos, Integer> chunkCache = new HashMap<>();
    private int tickCounter = 0;

    public BaseFinder() {
        super("BaseFinder", "Findet Chunks mit vielen Amethyst Buds (Full Height)", Category.RENDER);
    }

    @Override
    public void onEnable() {
        foundChunks.clear();
        chunkCache.clear();
    }

    @Override
    public void onDisable() {
        foundChunks.clear();
        chunkCache.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter % scanDelay.getValue() != 0) return;

        foundChunks.clear();
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());
        int radius = scanRadius.getValue();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                int budCount = useCache.getValue()
                        ? chunkCache.computeIfAbsent(chunkPos, this::countAmethystBuds)
                        : countAmethystBuds(chunkPos);

                if (budCount >= minBudsPerChunk.getValue()) {
                    foundChunks.add(chunkPos);
                }
            }
        }

        // Cache aufräumen
        if (tickCounter % 80 == 0 && useCache.getValue()) {
            cleanCache(playerChunk);
        }
    }

    /** Vollständiger Scan über die komplette Höhe */
    private int countAmethystBuds(ChunkPos chunkPos) {
        WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        int count = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Volle Höhe mit kompatiblen Methoden
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getTopYInclusive();

        // Fallback falls getTopYInclusive() auch nicht geht
        if (maxY <= minY) {
            maxY = 320;  // Standard für 1.18+
        }

        for (int y = minY; y < maxY; y += 2) {   // Schritt 2 für Performance
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z);

                    var state = chunk.getBlockState(pos);

                    if (state.isOf(Blocks.SMALL_AMETHYST_BUD) ||
                            state.isOf(Blocks.MEDIUM_AMETHYST_BUD) ||
                            state.isOf(Blocks.LARGE_AMETHYST_BUD) ||
                            state.isOf(Blocks.AMETHYST_CLUSTER)) {

                        count++;
                        if (count >= minBudsPerChunk.getValue() * 2) {
                            return count; // Early Exit
                        }
                    }
                }
            }
        }
        return count;
    }

    private void cleanCache(ChunkPos playerChunk) {
        int maxDist = scanRadius.getValue() + 6;
        chunkCache.keySet().removeIf(pos ->
                Math.max(Math.abs(pos.x - playerChunk.x), Math.abs(pos.z - playerChunk.z)) > maxDist
        );
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (foundChunks.isEmpty()) return;

        var vc = RenderUtil.beginBatch();
        Vec3d camPos = camera.getCameraPos();

        float r = chunkColor.getR() / 255f;
        float g = chunkColor.getG() / 255f;
        float b = chunkColor.getB() / 255f;
        float a = chunkColor.getA() / 255f;

        for (ChunkPos chunkPos : foundChunks) {
            double minX = chunkPos.getStartX();
            double minZ = chunkPos.getStartZ();
            double maxX = chunkPos.getEndX() + 1;
            double maxZ = chunkPos.getEndZ() + 1;

            // Chunk Box
            if (renderChunkBox.getValue()) {
                Box box = new Box(
                        minX - camPos.x,
                        0 - camPos.y,
                        minZ - camPos.z,
                        maxX - camPos.x,
                        boxHeight.getValue() - camPos.y,
                        maxZ - camPos.z
                );
                RenderUtil.batchOutlineBox(vc, posMatrix, box, r, g, b, a, 3.5f);
            }

            // Circle
            if (renderCircle.getValue()) {
                double centerX = (minX + maxX) / 2.0;
                double centerZ = (minZ + maxZ) / 2.0;
                double radius = circleRadius.getValue();

                RenderUtil.batchCircleRing(vc, posMatrix, centerX - camPos.x, 2.0 - camPos.y, centerZ - camPos.z, radius, 1.0f, r, g, b, a * 0.9f);
                RenderUtil.batchCircleVerticals(vc, posMatrix, centerX - camPos.x, 2.0 - camPos.y, centerZ - camPos.z, radius, 165.0f, 1.0f, r, g, b, a * 0.55f);
            }
        }

        RenderUtil.endBatch();
    }
}