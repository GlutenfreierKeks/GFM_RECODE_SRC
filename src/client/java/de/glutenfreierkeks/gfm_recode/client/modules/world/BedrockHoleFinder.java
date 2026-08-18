package de.glutenfreierkeks.gfm_recode.client.modules.world;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BedrockHoleFinder extends Module {

    private final EnumSetting<Shape> shape = register(new EnumSetting<>("Shape", "The shape to look for", Shape.TWO_BY_ONE_X));
    private final IntSliderSetting radius = register(new IntSliderSetting("Radius", "Scan radius", 16, 1, 64));
    private final BoolSetting checkTwoHigh = register(new BoolSetting("Check 2 High", "Ensure walls are bedrock at head level too", true));
    private final ColorSetting renderColor = register(new ColorSetting("Color", "Color of the found hole", 255, 0, 0, 150));

    private final Set<Set<BlockPos>> foundHoles = new HashSet<>();

    public enum Shape {
        TWO_BY_ONE_X("2x1 (X)"),
        ONE_BY_TWO_Z("1x2 (Z)"),
        TWO_BY_TWO("2x2"),
        L_SHAPE("L-Shape"),
        TWO_BY_ONE_BY_ONE("2x1x1 (Vertical)");

        public final String name;
        Shape(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public BedrockHoleFinder() {
        super("BedrockHoleFinder", "Finds specific shapes surrounded by bedrock", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        foundHoles.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.getValue();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    // Optimization: if base block is bedrock, it's not a hole
                    if (mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) continue;
                    
                    Set<BlockPos> hole = checkShape(pos, shape.getValue());
                    if (hole != null) {
                        foundHoles.add(hole);
                    }
                }
            }
        }
    }

    private Set<BlockPos> checkShape(BlockPos pos, Shape shape) {
        List<BlockPos> baseBlocks = new ArrayList<>();
        baseBlocks.add(pos);

        switch (shape) {
            case TWO_BY_ONE_X -> baseBlocks.add(pos.east());
            case ONE_BY_TWO_Z -> baseBlocks.add(pos.south());
            case TWO_BY_TWO -> {
                baseBlocks.add(pos.east());
                baseBlocks.add(pos.south());
                baseBlocks.add(pos.south().east());
            }
            case L_SHAPE -> {
                baseBlocks.add(pos.east());
                baseBlocks.add(pos.south());
            }
            case TWO_BY_ONE_BY_ONE -> baseBlocks.add(pos.up());
        }

        Set<BlockPos> holeVolume = new HashSet<>();
        if (shape == Shape.TWO_BY_ONE_BY_ONE) {
            holeVolume.addAll(baseBlocks);
        } else {
            int height = checkTwoHigh.getValue() ? 2 : 1;
            for (int i = 0; i < height; i++) {
                for (BlockPos b : baseBlocks) {
                    holeVolume.add(b.up(i));
                }
            }
        }

        for (BlockPos p : holeVolume) {
            if (!mc.world.getBlockState(p).isAir()) {
                return null;
            }
        }

        for (BlockPos p : holeVolume) {
            BlockPos[] sides = {p.north(), p.south(), p.east(), p.west()};
            for (BlockPos side : sides) {
                if (holeVolume.contains(side)) {
                    continue;
                }
                if (!mc.world.getBlockState(side).isOf(Blocks.BEDROCK)) {
                    return null;
                }
            }
        }

        for (BlockPos p : holeVolume) {
            if (holeVolume.contains(p.down())) {
                continue;
            }
            if (!mc.world.getBlockState(p.down()).isOf(Blocks.BEDROCK)) {
                return null;
            }
        }

        return holeVolume;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (foundHoles.isEmpty()) return;

        Color color = renderColor.getJavaColor();
        for (Set<BlockPos> hole : foundHoles) {
            for (BlockPos pos : hole) {
                Box box = new Box(pos).offset(-camera.getCameraPos().x, -camera.getCameraPos().y, -camera.getCameraPos().z);
                RenderUtil.drawFilledBox(posMatrix, box, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                RenderUtil.drawBox(posMatrix, box, color, 1.0f);
            }
        }
    }
}
