package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class AutoBerry extends BaseFarmModule {

    private final BoolSetting harvestEmpty = register(new BoolSetting("HarvestEmpty", "Harvest empty bushes", false));
    private final IntSliderSetting minAge   = register(new IntSliderSetting("MinAge", "Minimum age to harvest", 2, 1, 3));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show ESP for bushes", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed", "ESP red", 255, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen", "ESP green", 100, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue", "ESP blue", 0, 0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha", "ESP alpha", 200, 0, 255));

    private int harvestCount = 0;

    public AutoBerry() {
        super("AutoBerry", "Automatically harvests sweet berry bushes.", Category.FARM);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetBase();
        harvestCount = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && harvestCount > 0)
            mc.player.sendMessage(net.minecraft.text.Text.literal("§a[AutoBerry] Harvested " + harvestCount + " bushes!"), false);
        harvestCount = 0;
    }

    @Override
    public void onTick() {
        if (!baseTick()) return;

        if (currentTarget != null && !isValidTarget(currentTarget)) {
            currentTarget = null; rotatingTicks = 0;
        }

        if (currentTarget == null) {
            List<BlockPos> targets = getTargets();
            for (BlockPos p : targets) {
                if (inReach(p)) { currentTarget = p; rotatingTicks = 0; break; }
            }
            if (currentTarget == null) return;
        }

        if (!inReach(currentTarget)) { currentTarget = null; return; }

        if (!rotateToTarget()) return;

        // Jitter im Hit-Vektor
        double jit = rotJitter.getValue();
        Vec3d hitVec = new Vec3d(
                currentTarget.getX() + 0.5 + (RNG.nextDouble()-0.5)*jit*0.4,
                currentTarget.getY() + 0.5 + (RNG.nextDouble()-0.5)*jit*0.2,
                currentTarget.getZ() + 0.5 + (RNG.nextDouble()-0.5)*jit*0.4
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.DOWN, currentTarget, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        harvestCount++;
        currentTarget = null;
        applyRandomDelay();
    }

    @Override
    protected List<BlockPos> scanTargets() {
        int r = range.getValue();
        BlockPos player = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(player, r, r, r)) {
            BlockState st = mc.world.getBlockState(pos);
            if (!(st.getBlock() instanceof SweetBerryBushBlock)) continue;
            int age = st.get(SweetBerryBushBlock.AGE);
            if (harvestEmpty.getValue() || age >= minAge.getValue()) found.add(pos.toImmutable());
        }
        return found;
    }

    @Override
    protected boolean isValidTarget(BlockPos pos) {
        BlockState st = mc.world.getBlockState(pos);
        if (!(st.getBlock() instanceof SweetBerryBushBlock)) return false;
        int age = st.get(SweetBerryBushBlock.AGE);
        return harvestEmpty.getValue() || age >= minAge.getValue();
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
        return "§e" + harvestCount + " §fharvested | min age: " + minAge.getValue();
    }
}
