package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class StorageFinder extends Module {

    public final EnumSetting<RenderFx.VisualMode> visualMode = register(new EnumSetting<>("Visual Mode", "Simple or shader visuals", RenderFx.VisualMode.SHADER));
    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw tracer lines", true));
    public final BoolSetting bloom = register(new BoolSetting("Bloom", "Soft inner bloom", true));
    public final BoolSetting strongGlow = register(new BoolSetting("Strong Glow", "Extra wide glow layers", true));
    public final BoolSetting scannerFx = register(new BoolSetting("Scanner", "Animated scan plane", true));
    public final BoolSetting pulse = register(new BoolSetting("Pulse", "Animated pulse shell", true));
    public final BoolSetting halo = register(new BoolSetting("Halo", "Extra halo shells", true));

    public final BoolSetting showChest = register(new BoolSetting("Chest", "Highlight Chests", true));
    public final BoolSetting showTrappedChest = register(new BoolSetting("TrappedChest", "Highlight Trapped Chests", true));
    public final BoolSetting showBarrel = register(new BoolSetting("Barrel", "Highlight Barrels", true));
    public final BoolSetting showShulker = register(new BoolSetting("ShulkerBox", "Highlight Shulker Boxes", true));
    public final BoolSetting showHopper = register(new BoolSetting("Hopper", "Highlight Hoppers", true));
    public final BoolSetting showDropper = register(new BoolSetting("Dropper", "Highlight Droppers", true));
    public final BoolSetting showDispenser = register(new BoolSetting("Dispenser", "Highlight Dispensers", true));
    public final BoolSetting showFurnace = register(new BoolSetting("Furnace", "Highlight Furnaces", true));
    public final BoolSetting showBlastFurnace = register(new BoolSetting("BlastFurnace", "Highlight Blast Furnaces", true));
    public final BoolSetting showSmoker = register(new BoolSetting("Smoker", "Highlight Smokers", true));

    public final ColorSetting colorChest = register(new ColorSetting("Chest Color", "", 255, 200, 0, 255));
    public final ColorSetting colorTrappedChest = register(new ColorSetting("TrappedChest Color", "", 255, 80, 20, 255));
    public final ColorSetting colorBarrel = register(new ColorSetting("Barrel Color", "", 160, 100, 40, 255));
    public final ColorSetting colorShulker = register(new ColorSetting("Shulker Color", "", 170, 50, 220, 255));
    public final ColorSetting colorHopper = register(new ColorSetting("Hopper Color", "", 70, 130, 200, 255));
    public final ColorSetting colorDropper = register(new ColorSetting("Dropper Color", "", 30, 180, 160, 255));
    public final ColorSetting colorDispenser = register(new ColorSetting("Dispenser Color", "", 100, 220, 60, 255));
    public final ColorSetting colorFurnace = register(new ColorSetting("Furnace Color", "", 255, 140, 30, 255));
    public final ColorSetting colorBlastFurnace = register(new ColorSetting("BlastFurnace Color", "", 40, 210, 230, 255));
    public final ColorSetting colorSmoker = register(new ColorSetting("Smoker Color", "", 255, 100, 180, 255));

    private enum StorageType {
        CHEST, TRAPPED_CHEST, BARREL, SHULKER, HOPPER, DROPPER, DISPENSER, FURNACE, BLAST_FURNACE, SMOKER
    }

    private static final class StorageEntry {
        final BlockPos pos;
        final StorageType type;

        StorageEntry(BlockPos pos, StorageType type) {
            this.pos = pos;
            this.type = type;
        }
    }

    private volatile List<StorageEntry> found = new ArrayList<>();
    private int tickCounter = 0;

    public StorageFinder() {
        super("StorageFinder", "Highlights storage blocks", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        found = new ArrayList<>();
    }

    @Override
    protected void onDisable() {
        found = new ArrayList<>();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null || tickCounter++ % 20 != 0) {
            return;
        }

        List<StorageEntry> list = new ArrayList<>();
        int viewDist = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = -viewDist; cx <= viewDist; cx++) {
            for (int cz = -viewDist; cz <= viewDist; cz++) {
                net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pCx + cx, pCz + cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    StorageType type = classify(be);
                    if (type != null && isTypeEnabled(type)) {
                        list.add(new StorageEntry(be.getPos(), type));
                    }
                }
            }
        }
        found = list;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        List<StorageEntry> snapshot = found;
        if (snapshot.isEmpty()) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        RenderFx.ShaderOptions shaderOptions = new RenderFx.ShaderOptions(1.0f, bloom.getValue(), strongGlow.getValue(), scannerFx.getValue(), pulse.getValue(), halo.getValue());
        VertexConsumer vc = RenderUtil.beginBatch();

        for (StorageEntry entry : snapshot) {
            BlockPos pos = entry.pos;
            Color color = getTypeColor(entry.type);
            double dx = pos.getX() - camPos.x;
            double dy = pos.getY() - camPos.y;
            double dz = pos.getZ() - camPos.z;
            Box box = new Box(dx, dy, dz, dx + 1.0, dy + 1.0, dz + 1.0);

            if (visualMode.getValue() == RenderFx.VisualMode.SIMPLE) {
                RenderFx.renderSimpleBox(vc, posMatrix, box, color);
            } else {
                RenderFx.renderShaderBox(vc, posMatrix, box, color, shaderOptions, pos.asLong() * 0.011);
            }
        }

        RenderUtil.endBatch();

        if (tracers.getValue()) {
            for (StorageEntry entry : snapshot) {
                BlockPos pos = entry.pos;
                Color color = getTypeColor(entry.type);
                double dx = pos.getX() - camPos.x;
                double dy = pos.getY() - camPos.y;
                double dz = pos.getZ() - camPos.z;
                Vec3d start = Vec3d.fromPolar(mc.gameRenderer.getCamera().getPitch(), mc.gameRenderer.getCamera().getYaw()).multiply(0.5);
                RenderUtil.drawTracer(posMatrix, start, new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5), color);
            }
        }
    }

    private StorageType classify(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return StorageType.CHEST;
        if (be instanceof TrappedChestBlockEntity) return StorageType.TRAPPED_CHEST;
        if (be instanceof BarrelBlockEntity) return StorageType.BARREL;
        if (be instanceof ShulkerBoxBlockEntity) return StorageType.SHULKER;
        if (be instanceof HopperBlockEntity) return StorageType.HOPPER;
        if (be instanceof DropperBlockEntity) return StorageType.DROPPER;
        if (be instanceof DispenserBlockEntity) return StorageType.DISPENSER;
        if (be instanceof FurnaceBlockEntity) return StorageType.FURNACE;
        if (be instanceof BlastFurnaceBlockEntity) return StorageType.BLAST_FURNACE;
        if (be instanceof SmokerBlockEntity) return StorageType.SMOKER;
        return null;
    }

    private boolean isTypeEnabled(StorageType type) {
        return switch (type) {
            case CHEST -> showChest.getValue();
            case TRAPPED_CHEST -> showTrappedChest.getValue();
            case BARREL -> showBarrel.getValue();
            case SHULKER -> showShulker.getValue();
            case HOPPER -> showHopper.getValue();
            case DROPPER -> showDropper.getValue();
            case DISPENSER -> showDispenser.getValue();
            case FURNACE -> showFurnace.getValue();
            case BLAST_FURNACE -> showBlastFurnace.getValue();
            case SMOKER -> showSmoker.getValue();
        };
    }

    private Color getTypeColor(StorageType type) {
        return new Color(switch (type) {
            case CHEST -> colorChest.getArgb();
            case TRAPPED_CHEST -> colorTrappedChest.getArgb();
            case BARREL -> colorBarrel.getArgb();
            case SHULKER -> colorShulker.getArgb();
            case HOPPER -> colorHopper.getArgb();
            case DROPPER -> colorDropper.getArgb();
            case DISPENSER -> colorDispenser.getArgb();
            case FURNACE -> colorFurnace.getArgb();
            case BLAST_FURNACE -> colorBlastFurnace.getArgb();
            case SMOKER -> colorSmoker.getArgb();
        }, true);
    }

    @Override
    public String getDisplayInfo() {
        int size = found.size();
        return size == 0 ? null : size + " storage";
    }
}
