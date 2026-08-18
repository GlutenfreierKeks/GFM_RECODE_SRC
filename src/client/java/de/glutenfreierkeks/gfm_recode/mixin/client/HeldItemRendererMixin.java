package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.ViewModel;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Shadow @Final private ItemModelManager itemModelManager;

    @ModifyVariable(
            method = "renderFirstPersonItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private float gfm$remapSwingSpeed(float swingProgress) {
        ViewModel module = getViewModel();
        if (module == null || !module.isEnabled()) {
            return swingProgress;
        }

        float speed = Math.max(0.05F, module.getSwingSpeed());
        float exponent = 1.0F / speed;
        return (float) Math.pow(Math.max(0.0F, Math.min(1.0F, swingProgress)), exponent);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void gfm$applyViewModel(
            AbstractClientPlayerEntity player,
            float tickProgress,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue orderedRenderCommandQueue,
            int light,
            CallbackInfo ci
    ) {
        ViewModel module = getViewModel();
        if (module == null || !module.isEnabled()) {
            return;
        }

        boolean mainHand = hand == Hand.MAIN_HAND;
        Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
        float scale = module.getScale(mainHand);

        matrices.translate(
                side * module.getOffsetX(mainHand),
                module.getOffsetY(mainHand),
                module.getOffsetZ(mainHand)
        );

        if (module.isItemOutlineEnabled()) {
            matrices.translate(side * 0.025F, 0.01F, -0.05F);
            matrices.scale(1.08F, 1.08F, 1.08F);
        }

        matrices.scale(scale, scale, scale);
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("HEAD"))
    private void gfm$renderOutlinePass(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext renderMode,
            MatrixStack matrices,
            OrderedRenderCommandQueue orderedRenderCommandQueue,
            int light,
            CallbackInfo ci
    ) {
        ViewModel module = getViewModel();
        if (module == null || !module.isEnabled() || stack.isEmpty()) {
            return;
        }

        if (renderMode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && renderMode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }

        if (module.isItemOutlineEnabled() || module.isHandGlowEnabled()) {
            ItemRenderState outlineState = new ItemRenderState();
            this.itemModelManager.clearAndUpdate(outlineState, stack, renderMode, entity.getEntityWorld(), entity, entity.getId() + renderMode.ordinal());

            matrices.push();
            if (module.isHandGlowEnabled()) {
                // Hand glow logic (render with a glowing scale / shift)
                matrices.scale(1.02F, 1.02F, 1.02F);
            }
            if (module.isItemOutlineEnabled()) {
                // Outline pass - scale slightly larger
                matrices.scale(1.05F, 1.05F, 1.05F);
                // Render with white outline overlay or full bright (white light / OverlayTexture overlay)
                outlineState.render(matrices, orderedRenderCommandQueue, 15728880, OverlayTexture.packUv(0, 10), 0xFFFFFF);
            } else {
                outlineState.render(matrices, orderedRenderCommandQueue, light, OverlayTexture.DEFAULT_UV, 0);
            }
            matrices.pop();
        }
    }

    private ViewModel getViewModel() {
        if (Gfm_recodeClient.modules == null) {
            return null;
        }
        return Gfm_recodeClient.modules.getModuleByClass(ViewModel.class);
    }
}
