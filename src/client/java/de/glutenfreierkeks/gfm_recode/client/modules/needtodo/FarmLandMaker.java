package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
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

public class FarmLandMaker extends BaseFarmModule {

    public enum Mode { NORMAL, CHESS }

    private final EnumSetting<Mode>    mode = register(new EnumSetting<>("Mode", "Placement mode", Mode.NORMAL));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show ESP for target blocks", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed",    "ESP red component", 255, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen",  "ESP green component", 180, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue",   "ESP blue component", 0,   0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha",  "ESP alpha component", 200, 0, 255));

    private int convertedCount = 0;

    public FarmLandMaker() { super("FarmLandMaker", "Converts dirt to farmland with a hoe", Category.FARM); }

    @Override
    public void onEnable() {
        super.onEnable();
        resetBase();
        convertedCount = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && convertedCount > 0)
            mc.player.sendMessage(Text.literal("§a[FarmLandMaker] Converted " + convertedCount + " blocks!"), false);
        convertedCount = 0;
    }

    @Override
    public void onTick() {
        if (!baseTick()) return;

        int hoeSlot = findHoeSlot();
        if (hoeSlot < 0) { restoreSlot(); return; }

        if (currentTarget != null && !isValidTarget(currentTarget)) {
            currentTarget = null; rotatingTicks = 0;
        }

        if (currentTarget == null) {
            List<BlockPos> targets = getTargets();
            for (BlockPos p : targets) {
                if (inReach(p)) { currentTarget = p; rotatingTicks = 0; break; }
            }
            if (currentTarget == null) { restoreSlot(); return; }
        }

        if (!inReach(currentTarget)) { currentTarget = null; return; }

        switchToSlot(hoeSlot);

        Vec3d lookAt = new Vec3d(currentTarget.getX()+0.5, currentTarget.getY()+1.0, currentTarget.getZ()+0.5);
        if (!rotateToPoint(lookAt)) return;

        double jit = rotJitter.getValue();
        Vec3d hitVec = new Vec3d(
                currentTarget.getX() + 0.5 + (RNG.nextDouble()-0.5)*jit,
                currentTarget.getY() + 1.0,
                currentTarget.getZ() + 0.5 + (RNG.nextDouble()-0.5)*jit
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.UP, currentTarget, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        restoreSlot();
        convertedCount++;
        currentTarget = null;
        applyRandomDelay();
    }

    @Override
    protected List<BlockPos> scanTargets() {
        int r = range.getValue();
        BlockPos player = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(player, r, 2, r)) {
            if (isValidTarget(pos)) found.add(pos.toImmutable());
        }
        return found;
    }

    @Override
    protected boolean isValidTarget(BlockPos pos) {
        var b = mc.world.getBlockState(pos).getBlock();
        if (b != Blocks.DIRT && b != Blocks.GRASS_BLOCK) return false;
        if (mode.getValue() == Mode.CHESS) return ((pos.getX() + pos.getZ()) % 2 == 0);
        return true;
    }

    private int findHoeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.WOODEN_HOE  || s.getItem() == Items.STONE_HOE
                    || s.getItem() == Items.IRON_HOE    || s.getItem() == Items.GOLDEN_HOE
                    || s.getItem() == Items.DIAMOND_HOE || s.getItem() == Items.NETHERITE_HOE)
                return i;
        }
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
        String m = mode.getValue() == Mode.CHESS ? "§bchess" : "§7normal";
        return m + " §f| " + (findHoeSlot()>=0?"§ahoe":"§cno hoe") + " §f| §e" + convertedCount + " §fconv";
    }
}
