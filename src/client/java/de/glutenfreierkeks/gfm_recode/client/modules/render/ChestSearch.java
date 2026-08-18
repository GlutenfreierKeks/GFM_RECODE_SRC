package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ChestSearch extends Module {

    public final StringSetting query = register(new StringSetting("Query", "Find items in chests", ""));
    public final ColorSetting color = register(new ColorSetting("Color", "Chest highlight color", 255, 255, 0, 100));
    public final ColorSetting matchColor = register(new ColorSetting("Match Color", "Found item color", 0, 255, 0, 255));
    public final IntSliderSetting radius = register(new IntSliderSetting("Radius", "Search range in blocks", 32, 5, 128));

    private final List<BlockPos> foundChests = new ArrayList<>();
    private int tickCounter = 0;

    public ChestSearch() {
        super("ChestSearch", "Highlights chests and items", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (tickCounter++ % 20 != 0) return;

        List<BlockPos> found = new ArrayList<>();
        int renderDistance = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pCx + x, pCz + z);
                if (chunk != null) {
                    for (net.minecraft.block.entity.BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof net.minecraft.block.entity.ChestBlockEntity ||
                                be instanceof net.minecraft.block.entity.TrappedChestBlockEntity ||
                                be instanceof net.minecraft.block.entity.BarrelBlockEntity ||
                                be instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity) {
                            found.add(be.getPos());
                        }
                    }
                }
            }
        }

        synchronized (foundChests) {
            foundChests.clear();
            foundChests.addAll(found);
            System.out.println("Found chests: " + foundChests.size());
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null) return;

        Color base = new Color(color.getArgb(), true);
        Vec3d camPos = camera.getCameraPos();

        synchronized (foundChests) {
            for (BlockPos pos : foundChests) {
                double dx = pos.getX() - camPos.x;
                double dy = pos.getY() - camPos.y;
                double dz = pos.getZ() - camPos.z;
                Box box = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);

                RenderUtil.drawBox(posMatrix, box, base, 1.0);
                RenderUtil.drawFilledBox(posMatrix, box, new Color(base.getRed(), base.getGreen(), base.getBlue(), 30));
            }
        }
    }
}
