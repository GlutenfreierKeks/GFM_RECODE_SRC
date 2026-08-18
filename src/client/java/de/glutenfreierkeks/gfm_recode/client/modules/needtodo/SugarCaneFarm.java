package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class SugarCaneFarm extends BaseFarmModule {

    public SugarCaneFarm() {
        super("SugarCaneFarm", "Automatically harvests sugar cane.", Category.FARM);
    }

    @Override
    protected boolean isTargetBlock(BlockPos pos, BlockState state) {
        if (!state.isOf(Blocks.SUGAR_CANE)) return false;
        
        // Only harvest if it's the second or third block in a stack
        BlockPos below = pos.down();
        return mc.world.getBlockState(below).isOf(Blocks.SUGAR_CANE);
    }

    @Override
    protected List<BlockPos> scanTargets() {
        return List.of();
    }

    @Override
    protected boolean isValidTarget(BlockPos pos) {
        return false;
    }
}
