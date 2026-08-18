package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.client.render.Camera;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

public class ChestCounter extends Module {

    private int lastCount = -1;

    public ChestCounter() {
        super("ChestCounter", "Announces the number of chests in render distance", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        // Only scan once per second (20 ticks) to ensure zero lag
        if (mc.player.age % 20 != 0) return;

        int currentCount = 0;
        
        // Iterate through all loaded chunks in render distance
        int renderDistance = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pCx + x, pCz + z);
                if (chunk != null) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (isChest(be)) {
                            currentCount++;
                        }
                    }
                }
            }
        }

        if (currentCount != lastCount) {
            mc.player.sendMessage(Text.literal("§7[§6GFM§7] §fChests in range: §e" + currentCount), false);
            lastCount = currentCount;
        }
    }

    private boolean isChest(BlockEntity be) {
        return be instanceof ChestBlockEntity || 
               be instanceof TrappedChestBlockEntity || 
               be instanceof BarrelBlockEntity || 
               be instanceof ShulkerBoxBlockEntity;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    protected void onDisable() {
        lastCount = -1;
    }
}
