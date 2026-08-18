package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ActivatedSpawnerESP extends Module {

    public final ColorSetting color = register(new ColorSetting("Box Color", "Color of the spawner box", 251, 5, 5, 255));
    public final ColorSetting fillColor = register(new ColorSetting("Fill Color", "Color of the spawner box fill", 251, 5, 5, 40));
    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw lines to spawners", true));

    private volatile List<BlockPos> found = new ArrayList<>();
    private final java.util.Set<BlockPos> notifiedMismatches = new java.util.HashSet<>();
    private int tickCounter = 0;

    public ActivatedSpawnerESP() {
        super("ActivatedSpawnerESP", "Highlights activated spawners in loaded chunks", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        found = new ArrayList<>();
        notifiedMismatches.clear();
    }

    @Override
    protected void onDisable() {
        found = new ArrayList<>();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (tickCounter++ % 20 != 0) return;

        List<BlockPos> list = new ArrayList<>();
        int viewDist = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = -viewDist; cx <= viewDist; cx++) {
            for (int cz = -viewDist; cz <= viewDist; cz++) {
                net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pCx + cx, pCz + cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof TrialSpawnerBlockEntity trial) {
                        try {
                            if (trial.getSpawnerState() != TrialSpawnerState.WAITING_FOR_PLAYERS) {
                                list.add(be.getPos());
                                continue;
                            }
                        } catch (Exception ignored) {}
                    } 
                    
                    // Der User möchte, dass wir JEDES BlockEntity checken (auch Fake-Sachen wie ein Deepslate mit Spawner-NBT)
                    try {
                        net.minecraft.nbt.NbtCompound nbt = be.createNbt(mc.world.getRegistryManager());
                        if (nbt != null) {
                            Object delayObj = nbt.get("Delay");
                            if (delayObj != null) {
                                short delay = 20;
                                try {
                                    delay = Short.parseShort(delayObj.toString().replaceAll("[^0-9-]", ""));
                                } catch (Exception ignored) {}
                                
                                // A naturally generated unseen spawner has delay exactly equal to 20
                                if (delay != 20) {
                                    if (!(mc.world.getRegistryKey() == net.minecraft.world.World.NETHER && delay == 0)) {
                                        list.add(be.getPos());
                                        
                                        net.minecraft.block.Block block = mc.world.getBlockState(be.getPos()).getBlock();
                                        if (block != net.minecraft.block.Blocks.SPAWNER && block != net.minecraft.block.Blocks.TRIAL_SPAWNER) {
                                            if (notifiedMismatches.add(be.getPos())) {
                                                mc.player.sendMessage(net.minecraft.text.Text.literal("§7[§cASD§7] §cFAKE-SPAWNER gefunden! §fBlock: §e" + block.getName().getString() + " §fbei §e" + be.getPos().getX() + ", " + be.getPos().getY() + ", " + be.getPos().getZ()), false);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        found = list;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        List<BlockPos> snapshot = found;
        if (snapshot.isEmpty()) return;

        Vec3d camPos = camera.getCameraPos();
        Color c = new Color(color.getArgb(), true);
        Color fColor = new Color(fillColor.getArgb(), true);
        boolean doTracers = tracers.getValue();

        for (BlockPos pos : snapshot) {
            double dx = pos.getX() - camPos.x;
            double dy = pos.getY() - camPos.y;
            double dz = pos.getZ() - camPos.z;

            // Render 1 box exactly as requested.
            Box box = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);
            RenderUtil.drawBox(posMatrix, box, c, 1.0);
            RenderUtil.drawFilledBox(posMatrix, box, fColor);

            if (doTracers) {
                RenderUtil.drawTracer(posMatrix, new Vec3d(0, -0.1, 0), new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5), c);
            }
        }
    }
}
