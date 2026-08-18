package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.ServerBlockerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CactusTower extends Module {

    private static final Random RANDOM = new Random();

    // ── Settings ─────────────────────────────────────────────────────────────
    public final IntSliderSetting interactDelay = register(new IntSliderSetting("Interact Delay", "Delay between actions in ticks", 2, 0, 10));
    public final IntSliderSetting delayRandomness = register(new IntSliderSetting("Delay Random", "Random jitter for delay", 1, 0, 5));
    public final BoolSetting silent = register(new BoolSetting("Silent", "Enable silent rotation", true));

    // ── Internal state ────────────────────────────────────────────────────────
    private int tickCooldown      = 0;
    private int currentStep       = 0;
    private boolean actionStarted = false;
    private final List<MacroAction> actions = new ArrayList<>();
    private BlockPos basePos;
    
    // Silent rotation state
    private float visualYaw;
    private float visualPitch;

    public CactusTower() {
        super("CactusTower", "Creates a cactus farm tower with automated building sequence", Category.FARM);
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }


        currentStep   = 0;
        tickCooldown  = 0;
        actionStarted = false;
        basePos       = mc.player.getBlockPos();
        actions.clear();
        
        visualYaw = mc.player.getYaw();
        visualPitch = mc.player.getPitch();

        // ==================== Sequence Definition ====================
        
        addLevel(BlockType.BUILDING, 0); 
        
        for (int i = 0; i < 6; i++) {
            int h = i * 4;
            addLevel(BlockType.SAND, h + 1);
            addLevel(BlockType.CACTUS, h + 2);
            addLevel(BlockType.CACTUS, h + 3);
            addLevel(BlockType.BUILDING, h + 4); 
            addFenceRing(h + 2); 
        }
    }

    private void addLevel(BlockType type, int heightOffset) {
        BlockPos centerPos = basePos.add(0, heightOffset, 0);
        actions.add(new TowerJumpAction(centerPos));
        
        // Outer blocks
        actions.add(new PlaceBlockAction(type, new BlockPos(0, heightOffset, -2)));
        actions.add(new PlaceBlockAction(type, new BlockPos(2, heightOffset, -2)));
        actions.add(new PlaceBlockAction(type, new BlockPos(2, heightOffset, 0)));
        actions.add(new PlaceBlockAction(type, new BlockPos(2, heightOffset, 2)));
        actions.add(new PlaceBlockAction(type, new BlockPos(0, heightOffset, 2)));
        actions.add(new PlaceBlockAction(type, new BlockPos(-2, heightOffset, 2)));
        actions.add(new PlaceBlockAction(type, new BlockPos(-2, heightOffset, 0)));
        actions.add(new PlaceBlockAction(type, new BlockPos(-2, heightOffset, -2)));
    }

    private void addFenceRing(int height) {
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(1,  height, -2)));
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(-1, height, -2)));
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(1,  height,  0)));
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(-1, height,  0)));
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(-1, height,  2)));
        actions.add(new PlaceBlockAction(BlockType.FENCE, new BlockPos(1,  height,  2)));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (currentStep >= actions.size()) {
            setEnabled(false);
            return;
        }

        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        MacroAction action = actions.get(currentStep);

        if (!actionStarted) {
            action.onStart();
            actionStarted = true;
        }

        if (action.execute()) {
            currentStep++;
            actionStarted = false;
            
            int base   = interactDelay.getValue();
            int jitter = delayRandomness.getValue();
            tickCooldown = base + (jitter > 0 ? RANDOM.nextInt(jitter * 2 + 1) - jitter : 0);
            if (tickCooldown < 0) tickCooldown = 0;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setYaw(visualYaw);
            mc.player.setPitch(visualPitch);
            mc.player.setJumping(false);
        }
    }

    public void onMouseMoved(double dx, double dy) {
        if (isEnabled() && silent.getValue()) {
            double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double factor = sensitivity * sensitivity * sensitivity * 8.0;
            
            visualYaw += (float) (dx * factor * 0.15);
            visualPitch += (float) (dy * factor * 0.15);
            visualPitch = MathHelper.clamp(visualPitch, -90, 90);
        }
    }

    public boolean isSilentActive() { return isEnabled() && silent.getValue(); }
    public float getVisualYaw() { return visualYaw; }
    public float getVisualPitch() { return visualPitch; }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public String getDisplayInfo() {
        return currentStep + "/" + actions.size();
    }

    // ── Inventory Logic ──────────────────────────────────────────────────────

    private enum BlockType {
        SAND, CACTUS, FENCE, BUILDING
    }

    private int findOrSwapSlot(BlockType type) {
        if (mc.player == null || mc.interactionManager == null) return -1;

        Item targetItem = switch (type) {
            case SAND -> Items.SAND;
            case CACTUS -> Items.CACTUS;
            case FENCE -> findFenceItem();
            case BUILDING -> findBuildingItem();
        };

        if (targetItem == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem) return i;
        }

        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem) {
                int hotbarSlot = 0; 
                for (int j = 0; j < 9; j++) {
                    if (mc.player.getInventory().getStack(j).isEmpty()) {
                        hotbarSlot = j;
                        break;
                    }
                }
                mc.interactionManager.clickSlot(0, i, hotbarSlot, SlotActionType.SWAP, mc.player);
                return hotbarSlot;
            }
        }

        return -1;
    }

    private Item findFenceItem() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof FenceBlock) return stack.getItem();
        }
        return null;
    }

    private Item findBuildingItem() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem bi) {
                Block b = bi.getBlock();
                if (b != Blocks.SAND && b != Blocks.CACTUS && !(b instanceof FenceBlock) && b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN)) {
                    return stack.getItem();
                }
            }
        }
        return null;
    }
    
    private void switchSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return;
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private abstract class MacroAction {
        abstract boolean execute(); // returns true when done
        void onStart() {}
    }

    private class TowerJumpAction extends MacroAction {
        private final BlockPos target;
        private double startY;
        private int ticksInAir = 0;
        private int attempts = 0;
        private int jumpCooldown = 0;

        TowerJumpAction(BlockPos target) {
            this.target = target;
        }

        @Override
        void onStart() {
            this.startY = mc.player.getY();
            this.ticksInAir = 0;
            this.attempts = 0;
            this.jumpCooldown = 0;
            mc.player.setJumping(false);
        }

        @Override
        boolean execute() {
            if (mc.player == null) return true;

            // Success condition: landed on a higher level
            if (mc.player.getY() >= startY + 0.9 && mc.player.isOnGround()) {
                mc.player.setJumping(false);
                return true;
            }

            if (jumpCooldown > 0) jumpCooldown--;

            if (mc.player.isOnGround()) {
                if (jumpCooldown == 0) {
                    float[] rotations = RotationUtil.getRotations(new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5));
                    mc.player.setYaw(rotations[0]);
                    mc.player.setPitch(rotations[1]);
                    mc.player.setHeadYaw(rotations[0]);
                    mc.player.setBodyYaw(rotations[0]);
                    
                    // LEGIT JUMP: Use jumping field so the vanilla physics loop handles it.
                    // This ensures correct packet synchronization and prevents Simulation flags.
                    mc.player.setJumping(true);
                    ticksInAir = 0;
                    attempts++;
                    jumpCooldown = 15; // Increased cooldown for safety
                }
            } else {
                mc.player.setJumping(false); // Only jump for one tick
                ticksInAir++;
                
                // Try to place when high enough
                if (mc.player.getY() >= target.getY() + 1.01 && ticksInAir > 6) {
                    placeTarget(target);
                }
            }

            return attempts > 20;
        }

        private void placeTarget(BlockPos pos) {
            if (mc.world.getBlockState(pos).isAir()) {
                int slot = findOrSwapSlot(BlockType.BUILDING);
                if (slot == -1) return;

                switchSlot(slot);

                BlockPos neighbor = pos.down();
                if (mc.world.getBlockState(neighbor).isAir()) neighbor = pos;

                Vec3d hitVec = new Vec3d(pos.getX() + 0.5 + (RANDOM.nextDouble()-0.5)*0.05, pos.getY(), pos.getZ() + 0.5 + (RANDOM.nextDouble()-0.5)*0.05);
                BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, neighbor, false);

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private class WaitAction extends MacroAction {
        private final int ticks;
        private int remaining;
        WaitAction(int ticks) { this.ticks = ticks; }
        @Override void onStart() { remaining = ticks; }
        @Override boolean execute() {
            if (remaining > 0) { remaining--; return false; }
            return true;
        }
    }

    private class PlaceBlockAction extends MacroAction {
        private final BlockType type;
        private final BlockPos relativePos;

        private int phase = 0;

        PlaceBlockAction(BlockType type, BlockPos relativePos) {
            this.type = type;
            this.relativePos = relativePos;
        }

        @Override
        void onStart() { phase = 0; }

        @Override
        boolean execute() {
            if (mc.player == null || mc.world == null) return true;

            BlockPos target = basePos.add(relativePos);
            if (!mc.world.getBlockState(target).isAir()) return true;

            int slot = findOrSwapSlot(type);
            if (slot == -1) return true;

            switch (phase) {
                case 0: { 
                    float[] rotations = RotationUtil.getRotations(new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5));
                    mc.player.setYaw(rotations[0]);
                    mc.player.setPitch(rotations[1]);
                    mc.player.setHeadYaw(rotations[0]);
                    mc.player.setBodyYaw(rotations[0]);
                    
                    switchSlot(slot);
                    phase = 1;
                    return false;
                }

                case 1: { 
                    BlockPos neighbor = target.down();
                    if (mc.world.getBlockState(neighbor).isAir()) neighbor = target;

                    Vec3d hitVec = new Vec3d(
                            target.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.15,
                            target.getY()       + (RANDOM.nextDouble() - 0.5) * 0.05,
                            target.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.15
                    );
                    BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, neighbor, false);

                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    return true;
                }
            }
            return true;
        }
    }
}