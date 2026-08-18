package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.modules.needtodo.AntiBaseLeaker;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderManager.class)
public class BlockRenderManagerMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void gfm$swapModelForAntiBaseLeaker(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        BlockStateModel disguised = AntiBaseLeaker.getDisguise(state, (BlockRenderManager) (Object) this);
        if (disguised != null) {
            cir.setReturnValue(disguised);
        }
    }
}
