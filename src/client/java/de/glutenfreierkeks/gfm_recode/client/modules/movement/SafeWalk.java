package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public class SafeWalk extends Module {

    public final DoubleSliderSetting distance = register(new DoubleSliderSetting("Distance", "Distance to edge before sneaking", 0.18, 0.05, 0.8));
    public final IntSliderSetting depth = register(new IntSliderSetting("Depth", "Minimum air blocks below edge", 3, 1, 50));

    private boolean forcedSneak = false;

    public SafeWalk() {
        super("SafeWalk", "Automatically sneaks only at real edges", Category.MISC);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null) {
            return;
        }

        boolean shouldForceSneak = shouldSneakAtEdge();
        if (shouldForceSneak) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
            return;
        }

        if (forcedSneak) {
            mc.options.sneakKey.setPressed(false);
            forcedSneak = false;
        }
    }

    @Override
    public void onDisable() {
        if (forcedSneak && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        forcedSneak = false;
    }

    private boolean shouldSneakAtEdge() {
        if (mc.player.getAbilities().flying || mc.player.isClimbing() || mc.player.isTouchingWater() || mc.player.hasVehicle()) {
            return false;
        }

        if (!mc.player.isOnGround()) {
            return false;
        }

        double threshold = distance.getValue();
        Box box = mc.player.getBoundingBox();
        double minX = box.minX + threshold;
        double maxX = box.maxX - threshold;
        double minZ = box.minZ + threshold;
        double maxZ = box.maxZ - threshold;

        if (maxX <= minX || maxZ <= minZ) {
            return false;
        }

        Box supportBox = new Box(minX, box.minY - 0.07, minZ, maxX, box.minY - 0.001, maxZ);
        if (!mc.world.isSpaceEmpty(mc.player, supportBox)) {
            return false;
        }

        return hasDeepFallBelow(supportBox, depth.getValue());
    }

    private boolean hasDeepFallBelow(Box box, int minDepth) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 1; i <= minDepth; i++) {
            int y = (int) Math.floor(box.minY) - i;
            boolean foundSupport = false;

            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX - 1.0E-4); x++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ - 1.0E-4); z++) {
                    mutable.set(x, y, z);
                    if (!mc.world.getBlockState(mutable).isAir()) {
                        foundSupport = true;
                        break;
                    }
                }
                if (foundSupport) {
                    break;
                }
            }

            if (foundSupport) {
                return false;
            }
        }
        return true;
    }
}
