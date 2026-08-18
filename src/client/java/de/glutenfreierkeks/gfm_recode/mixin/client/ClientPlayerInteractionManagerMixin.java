package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.ToolSaver;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Gfm_recodeClient.modules == null) return;
        ToolSaver toolSaver = Gfm_recodeClient.modules.getModuleByClass(ToolSaver.class);
        if (toolSaver != null && toolSaver.isEnabled() && toolSaver.shouldSave()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Gfm_recodeClient.modules == null) return;
        ToolSaver toolSaver = Gfm_recodeClient.modules.getModuleByClass(ToolSaver.class);
        if (toolSaver != null && toolSaver.isEnabled() && toolSaver.shouldSave()) {
            cir.setReturnValue(false);
        }
    }
}
