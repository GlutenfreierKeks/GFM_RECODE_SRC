package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SeedPlacer extends BaseFarmModule {

    public enum SeedType {
        WHEAT(Items.WHEAT_SEEDS), CARROT(Items.CARROT), POTATO(Items.POTATO),
        BEETROOT(Items.BEETROOT_SEEDS), MELON(Items.MELON_SEEDS),
        PUMPKIN(Items.PUMPKIN_SEEDS), TORCHFLOWER(Items.TORCHFLOWER_SEEDS),
        PITCHER(Items.PITCHER_POD);
        private final Item item;
        SeedType(Item i) { this.item = i; }
        public Item getItem() { return item; }
    }

    private final EnumSetting<SeedType> seedType = register(new EnumSetting<>("SeedType", "Type of seeds to plant", SeedType.WHEAT));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show plantable areas", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed", "ESP red", 0, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen", "ESP green", 200, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue", "ESP blue", 80, 0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha", "ESP alpha", 200, 0, 255));

    private int placedCount = 0;

    public SeedPlacer() {
        super("SeedPlacer", "Automatically places seeds on Farmland.", Category.FARM);
    }

    @Override
    public void onEnable() {
        resetBase();
        placedCount = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && placedCount > 0)
            mc.player.sendMessage(net.minecraft.text.Text.literal("§a[SeedPlacer] Placed " + placedCount + " seeds!"), false);
        placedCount = 0;
    }

    @Override
    public void onTick() {
        if (!baseTick()) return;

        int seedSlot = findSeedSlot();
        if (seedSlot < 0) { restoreSlot(); return; }

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
            if (currentTarget == null) { restoreSlot(); return; }
        }

        if (!inReach(currentTarget)) { currentTarget = null; return; }

        switchToSlot(seedSlot);

        Vec3d lookAt = new Vec3d(currentTarget.getX() + 0.5, currentTarget.getY() + 1.0, currentTarget.getZ() + 0.5);
        if (!rotateToPoint(lookAt)) return;

        double jit = rotJitter.getValue();
        Vec3d hitVec = new Vec3d(
                currentTarget.getX() + 0.5 + (RNG.nextDouble() - 0.5) * jit,
                currentTarget.getY() + 1.0,
                currentTarget.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * jit
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.UP, currentTarget, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        restoreSlot();
        placedCount++;
        currentTarget = null;
        applyRandomDelay();
    }

    @Override
    protected List<BlockPos> scanTargets() {
        int r = range.getValue();
        BlockPos player = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(player, r, 3, r)) {
            if (isValidTarget(pos)) found.add(pos.toImmutable());
        }
        return found;
    }

    @Override
    protected boolean isValidTarget(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).getBlock() == Blocks.FARMLAND && mc.world.isAir(pos.up());
    }

    private int findSeedSlot() {
        Item t = seedType.getValue().getItem();
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == t) return i;
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
        String s = seedType.getValue().name().toLowerCase();
        return (findSeedSlot() >= 0 ? "§a✓" : "§c✗") + " §f" + s + " | §e" + placedCount + " §fplaced";
    }
}
