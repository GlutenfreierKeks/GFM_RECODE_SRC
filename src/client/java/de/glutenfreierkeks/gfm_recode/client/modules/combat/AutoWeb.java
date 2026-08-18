package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Comparator;

public class AutoWeb extends Module {
    private final DoubleSliderSetting range = register(new DoubleSliderSetting("Range", "Target range", 4.0, 1.0, 6.0));
    private final DoubleSliderSetting delayMs = register(new DoubleSliderSetting("Delay", "Delay between webs (ms)", 110.0, 20.0, 500.0));
    private final IntSliderSetting slotDelayTicks = register(new IntSliderSetting("SlotDelay", "Ticks to wait after switching to webs", 1, 0, 6));
    private final BoolSetting onlyPlayers = register(new BoolSetting("OnlyPlayers", "Only web players", true));
    private final BoolSetting silent = register(new BoolSetting("Silent", "Hide the rotation from your screen", true));

    private float visualYaw;
    private float visualPitch;
    private boolean rotating = false;

    private long lastPlace = 0L;
    private BlockPos lastWebPos = null;
    private int restoreSlot = -1;
    private int waitTicks = 0;
    private PlacementData pendingPlacement = null;

    public AutoWeb() {
        super("AutoWeb", "Places cobwebs at enemy feet with silent rotation", Category.PLAYER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            visualYaw = mc.player.getYaw();
            visualPitch = mc.player.getPitch();
        }
        rotating = false;
        restoreSlot = -1;
        waitTicks = 0;
        pendingPlacement = null;
        lastPlace = 0L;
        lastWebPos = null;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && silent.getValue()) {
            mc.player.setYaw(visualYaw);
            mc.player.setPitch(visualPitch);
        }
        restoreHeldSlot();
        rotating = false;
        pendingPlacement = null;
        waitTicks = 0;
        lastWebPos = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            rotating = false;
            return;
        }

        if (silent.getValue()) {
            visualYaw = mc.player.getYaw();
            visualPitch = mc.player.getPitch();
        }

        if (pendingPlacement != null) {
            rotateServer(pendingPlacement.lookTarget());
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }

            if (mc.player.getEyePos().distanceTo(pendingPlacement.lookTarget()) > 4.8) {
                cancelPending();
                return;
            }

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, pendingPlacement.hitResult());
            mc.player.swingHand(Hand.MAIN_HAND);
            lastPlace = System.currentTimeMillis();
            lastWebPos = pendingPlacement.placePos();
            restoreHeldSlot();
            pendingPlacement = null;
            rotating = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlace < delayMs.getValue()) {
            rotating = false;
            return;
        }

        PlayerEntity target = mc.world.getPlayers().stream()
                .filter(player -> player != mc.player && player.isAlive() && mc.player.distanceTo(player) <= range.getValue())
                .filter(player -> !onlyPlayers.getValue() || player instanceof PlayerEntity)
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
        if (target == null) {
            rotating = false;
            return;
        }

        PlacementData data = findWebPlacement(target);
        if (data == null) {
            rotating = false;
            return;
        }

        int webSlot = findInHotbar(Items.COBWEB);
        if (webSlot == -1) {
            rotating = false;
            return;
        }

        if (restoreSlot == -1) {
            restoreSlot = mc.player.getInventory().getSelectedSlot();
        }

        if (mc.player.getInventory().getSelectedSlot() != webSlot) {
            mc.player.getInventory().setSelectedSlot(webSlot);
            pendingPlacement = data;
            waitTicks = slotDelayTicks.getValue();
            rotateServer(data.lookTarget());
            return;
        }

        pendingPlacement = data;
        waitTicks = 0;
        rotateServer(data.lookTarget());
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    public void onMouseMoved(double dx, double dy) {
        if (!isSilentActive()) {
            return;
        }

        double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0;

        visualYaw += (float) (dx * factor * 0.15);
        visualPitch += (float) (dy * factor * 0.15);
        visualPitch = MathHelper.clamp(visualPitch, -90, 90);
    }

    public boolean isSilentActive() {
        return isEnabled() && silent.getValue() && rotating;
    }

    public float getVisualYaw() {
        return visualYaw;
    }

    public float getVisualPitch() {
        return visualPitch;
    }

    private void rotateServer(Vec3d target) {
        float[] rotations = RotationUtil.getRotations(target);
        mc.player.setYaw(rotations[0]);
        mc.player.setPitch(rotations[1]);
        mc.player.setHeadYaw(rotations[0]);
        mc.player.setBodyYaw(rotations[0]);
        rotating = true;
    }

    private PlacementData findWebPlacement(PlayerEntity target) {
        BlockPos feet = target.getBlockPos();
        BlockPos[] candidates = new BlockPos[] {
                feet,
                feet.north(),
                feet.south(),
                feet.east(),
                feet.west()
        };

        PlacementData best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (!canPlaceAt(candidate, target)) {
                continue;
            }
            PlacementData data = getPlacementData(candidate);
            if (data == null) {
                continue;
            }
            double dist = target.getEntityPos().distanceTo(candidate.toCenterPos());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = data;
            }
        }
        return best;
    }

    private boolean canPlaceAt(BlockPos pos, PlayerEntity target) {
        var state = mc.world.getBlockState(pos);
        if (!state.isAir() && state.getBlock() != Blocks.COBWEB) {
            return false;
        }
        if (state.getBlock() == Blocks.COBWEB && pos.equals(lastWebPos)) {
            return false;
        }
        Box placeBox = new Box(pos);
        return target.getBoundingBox().expand(0.08).intersects(placeBox);
    }

    private PlacementData getPlacementData(BlockPos pos) {
        Vec3d eyes = mc.player.getEyePos();
        PlacementData best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            BlockPos support = pos.offset(side);
            if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) {
                continue;
            }

            Direction hitFace = side.getOpposite();
            Vec3d hitVec = faceCenterOnBlock(support, hitFace);
            double dist = eyes.distanceTo(hitVec);
            if (dist < bestDist) {
                bestDist = dist;
                best = new PlacementData(pos, new BlockHitResult(hitVec, hitFace, support, false), hitVec);
            }
        }

        return best;
    }

    private Vec3d faceCenterOnBlock(BlockPos block, Direction face) {
        double y = block.getY() + 0.5;
        return switch (face) {
            case UP -> new Vec3d(block.getX() + 0.5, block.getY() + 0.98, block.getZ() + 0.5);
            case DOWN -> new Vec3d(block.getX() + 0.5, block.getY() + 0.02, block.getZ() + 0.5);
            case NORTH -> new Vec3d(block.getX() + 0.5, y, block.getZ() + 0.02);
            case SOUTH -> new Vec3d(block.getX() + 0.5, y, block.getZ() + 0.98);
            case WEST -> new Vec3d(block.getX() + 0.02, block.getY() + 0.5, block.getZ() + 0.5);
            case EAST -> new Vec3d(block.getX() + 0.98, block.getY() + 0.5, block.getZ() + 0.5);
        };
    }

    private int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void restoreHeldSlot() {
        if (restoreSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        }
        restoreSlot = -1;
    }

    private void cancelPending() {
        pendingPlacement = null;
        restoreHeldSlot();
        rotating = false;
        waitTicks = 0;
    }

    private record PlacementData(BlockPos placePos, BlockHitResult hitResult, Vec3d lookTarget) {
    }
}
