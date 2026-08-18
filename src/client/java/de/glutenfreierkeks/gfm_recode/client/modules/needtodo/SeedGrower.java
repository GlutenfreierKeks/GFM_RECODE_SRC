package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SeedGrower extends BaseFarmModule {

    private final BoolSetting growWheat      = register(new BoolSetting("GrowWheat",      "Grow wheat crops", true));
    private final BoolSetting growCarrots    = register(new BoolSetting("GrowCarrots",    "Grow carrot crops", true));
    private final BoolSetting growPotatoes   = register(new BoolSetting("GrowPotatoes",   "Grow potato crops", true));
    private final BoolSetting growBeetroot   = register(new BoolSetting("GrowBeetroot",   "Grow beetroot crops", true));
    private final BoolSetting growNetherWart = register(new BoolSetting("GrowNetherWart", "Grow nether wart crops", true));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show ESP for crops", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed", "ESP red", 100, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen", "ESP green", 255, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue", "ESP blue", 100, 0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha", "ESP alpha", 200, 0, 255));

    private int grownCount = 0;

    public SeedGrower() {
        super("SeedGrower", "Automatically grows crops using bonemeal.", Category.FARM);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetBase();
        grownCount = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && grownCount > 0)
            mc.player.sendMessage(Text.literal("§a[SeedGrower] Grew " + grownCount + " crops!"), false);
        grownCount = 0;
    }

    @Override
    public void onTick() {
        if (!baseTick()) return;

        int bmSlot = findBoneMealSlot();
        if (bmSlot < 0) {
            restoreSlot();
            return;
        }

        if (currentTarget != null && !isValidTarget(currentTarget)) {
            currentTarget = null;
            rotatingTicks = 0;
        }

        if (currentTarget == null) {
            List<BlockPos> targets = getTargets();
            for (BlockPos p : targets) {
                if (inReach(p)) {
                    currentTarget = p;
                    rotatingTicks = 0;
                    break;
                }
            }
            if (currentTarget == null) {
                restoreSlot();
                return;
            }
        }

        if (!inReach(currentTarget)) {
            currentTarget = null;
            return;
        }

        switchToSlot(bmSlot);

        if (!rotateToTarget()) return;

        double jit = rotJitter.getValue();
        Vec3d hitVec = new Vec3d(
                currentTarget.getX() + 0.5 + (RNG.nextDouble() - 0.5) * jit,
                currentTarget.getY() + 0.5,
                currentTarget.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * jit
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.UP, currentTarget, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        restoreSlot();
        grownCount++;
        currentTarget = null;
        applyRandomDelay();
    }

    @Override
    protected List<BlockPos> scanTargets() {
        int r = range.getValue();
        BlockPos player = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(player, r, 3, r)) {
            BlockState st = mc.world.getBlockState(pos);
            if (!shouldGrowCrop(st.getBlock())) continue;
            if (!needsGrowing(st.getBlock(), st)) continue;
            found.add(pos.toImmutable());
        }
        return found;
    }

    @Override
    protected boolean isValidTarget(BlockPos pos) {
        BlockState st = mc.world.getBlockState(pos);
        return shouldGrowCrop(st.getBlock()) && needsGrowing(st.getBlock(), st);
    }

    private boolean shouldGrowCrop(Block b) {
        if (growWheat.getValue()      && b == Blocks.WHEAT)       return true;
        if (growCarrots.getValue()    && b == Blocks.CARROTS)     return true;
        if (growPotatoes.getValue()   && b == Blocks.POTATOES)    return true;
        if (growBeetroot.getValue()   && b == Blocks.BEETROOTS)   return true;
        if (growNetherWart.getValue() && b == Blocks.NETHER_WART) return true;
        return false;
    }

    private boolean needsGrowing(Block b, BlockState st) {
        try {
            if (b == Blocks.NETHER_WART) return st.get(net.minecraft.block.NetherWartBlock.AGE) < 3;
            if (b instanceof CropBlock cb) return st.get(CropBlock.AGE) < cb.getMaxAge();
        } catch (Exception ignored) {}
        return false;
    }

    private int findBoneMealSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL) return i;
        return -1;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    protected boolean isTargetBlock(BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public String getDisplayInfo() {
        boolean bm = findBoneMealSlot() >= 0;
        int t = 0;
        if (growWheat.getValue()) t++; if (growCarrots.getValue()) t++;
        if (growPotatoes.getValue()) t++; if (growBeetroot.getValue()) t++;
        if (growNetherWart.getValue()) t++;
        return (bm ? "§a✓" : "§c✗ no bm") + " §f| §e" + grownCount + " §fgrown | " + t + " types";
    }
}
