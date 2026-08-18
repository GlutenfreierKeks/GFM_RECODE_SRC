package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Marks chunks that contain mobs (Skeleton, Zombie, Creeper, Spider, etc.)
 * in render distance, signaling possible player activity.
 * Displays chunks with a scanning pulse animation.
 */
public class PlayerActivity extends Module {

    private final ColorSetting boxColor = register(new ColorSetting("Color", "Highlight color", 255, 0, 0, 100));
    private final Set<ChunkPos> activeChunks = new HashSet<>();
    private final Set<ChunkPos> notifiedChunks = new HashSet<>();
    private final Map<ChunkPos, Long> chunkDiscoveryTime = new HashMap<>();
    private long lastClearTime = System.currentTimeMillis();

    public PlayerActivity() {
        super("PlayerActivity", "Detects possible player activity by mobs", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        // Clear detected chunks every 5 seconds to re-scan
        if (System.currentTimeMillis() - lastClearTime > 5000) {
            activeChunks.clear();
            chunkDiscoveryTime.clear();
            lastClearTime = System.currentTimeMillis();
        }

        for (Entity entity : mc.world.getEntities()) {
            if (isHostile(entity)) {
                ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                
                if (activeChunks.add(chunkPos)) {
                    chunkDiscoveryTime.put(chunkPos, System.currentTimeMillis());
                    if (notifiedChunks.add(chunkPos)) {
                        mc.player.sendMessage(Text.literal("§d[PlayerActivity] §fFound activity at §b" + chunkPos.x + " " + chunkPos.z), false);
                    }
                }
            }
        }
    }

    private boolean isHostile(Entity entity) {
        return entity instanceof SkeletonEntity || 
               entity instanceof ZombieEntity || 
               entity instanceof CreeperEntity || 
               entity instanceof SpiderEntity ||
               entity instanceof EndermanEntity ||
               entity instanceof WitchEntity;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (activeChunks.isEmpty()) return;

        double camX = camera.getCameraPos().x;
        double camY = camera.getCameraPos().y;
        double camZ = camera.getCameraPos().z;
        long currentTime = System.currentTimeMillis();

        for (ChunkPos chunk : activeChunks) {
            Box box = new Box(
                chunk.getStartX(), mc.world.getBottomY(), chunk.getStartZ(),
                chunk.getEndX() + 1, mc.world.getBottomY() + 384, chunk.getEndZ() + 1
            ).offset(-camX, -camY, -camZ);

            // Calculate animation progress (pulsing effect)
            long discoveryTime = chunkDiscoveryTime.getOrDefault(chunk, currentTime);
            long timeSinceDiscovery = currentTime - discoveryTime;
            float animationProgress = (timeSinceDiscovery % 1000) / 1000.0f; // 1 second pulse cycle

            // Pulsing alpha effect
            int baseAlpha = boxColor.getJavaColor().getAlpha();
            int pulseAlpha = (int) (baseAlpha * (0.5f + 0.5f * (float) Math.sin(animationProgress * Math.PI * 2)));

            // Draw pulsing filled box
            Color pulseColor = new Color(
                boxColor.getJavaColor().getRed(),
                boxColor.getJavaColor().getGreen(),
                boxColor.getJavaColor().getBlue(),
                Math.max(30, pulseAlpha)
            );
            RenderUtil.drawFilledBox(posMatrix, box, pulseColor);
            RenderUtil.drawBox(posMatrix, box, boxColor.getJavaColor(), 1.0);

            // Draw scanning line animation
            double scanHeight = box.minY + (box.maxY - box.minY) * animationProgress;
            Box scanLineBox = new Box(
                box.minX, scanHeight, box.minZ,
                box.maxX, scanHeight + 1, box.maxZ
            );
            Color scanColor = new Color(
                boxColor.getJavaColor().getRed(),
                Math.max(0, Math.min(255, boxColor.getJavaColor().getGreen() + 100)),
                boxColor.getJavaColor().getBlue(),
                200
            );
            RenderUtil.drawFilledBox(posMatrix, scanLineBox, scanColor);
        }
    }

    @Override
    public void onEnable() {
        activeChunks.clear();
        notifiedChunks.clear();
        chunkDiscoveryTime.clear();
        super.onEnable();
    }
}
