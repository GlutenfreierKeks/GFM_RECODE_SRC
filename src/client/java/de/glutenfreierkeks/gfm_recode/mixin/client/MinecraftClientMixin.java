package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.combat.KnockbackDisplacement;
import de.glutenfreierkeks.gfm_recode.client.modules.combat.MaceSwap;
import de.glutenfreierkeks.gfm_recode.client.modules.needtodo.FastPlacer;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow public HitResult crosshairTarget;
    @Shadow private int itemUseCooldown;
    private HitResult backupTarget;

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"))
    private void onHandleBlockBreakingHead(boolean breaking, CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;

        // FastPlacer: zero out item-use cooldown so blocks place instantly
        FastPlacer fp = Gfm_recodeClient.modules.getModuleByClass(FastPlacer.class);
        if (fp != null && fp.shouldBypassCooldown()) {
            if (itemUseCooldown > fp.getDelay()) {
                itemUseCooldown = fp.getDelay();
            }
        }

        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            backupTarget = crosshairTarget;
            // Use the saved rotation target from Freecam
            HitResult target = freecam.getPlayerInteractionTarget(4.5);
            if (target != null) crosshairTarget = target;
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("TAIL"))
    private void onHandleBlockBreakingTail(boolean breaking, CallbackInfo ci) {
        if (backupTarget != null) {
            crosshairTarget = backupTarget;
            backupTarget = null;
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttackHead(CallbackInfoReturnable<Boolean> cir) {
        if (Gfm_recodeClient.modules == null) return;

        if (de.glutenfreierkeks.gfm_recode.client.modules.combat.HitCrystal.isRunning) {
            cir.setReturnValue(false);
            return;
        }

        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            backupTarget = crosshairTarget;
            HitResult target = freecam.getPlayerInteractionTarget(4.5);
            if (target != null) crosshairTarget = target;
        }

        if (crosshairTarget instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();

            if (target instanceof PlayerEntity playerTarget) {
                de.glutenfreierkeks.gfm_recode.client.modules.combat.WTap wTap =
                        Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.WTap.class);
                if (wTap != null && wTap.isEnabled()) {
                    wTap.onAttack(playerTarget);
                }
            }

            MaceSwap maceSwap = Gfm_recodeClient.modules.getModuleByClass(MaceSwap.class);
            if (maceSwap != null && maceSwap.isEnabled()) {
                if (maceSwap.onAttack(target)) {
                    cir.setReturnValue(false);
                    return;
                }
            }

            KnockbackDisplacement displacement = Gfm_recodeClient.modules.getModuleByClass(KnockbackDisplacement.class);
            if (displacement != null && displacement.isEnabled()) {
                if (displacement.onAttack(target)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

    @Inject(method = "doAttack", at = @At("TAIL"))
    private void onDoAttackTail(CallbackInfoReturnable<Boolean> cir) {

        if (backupTarget != null) {
            crosshairTarget = backupTarget;
            backupTarget = null;
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUseHead(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        de.glutenfreierkeks.gfm_recode.client.modules.combat.CrystalPlace cp = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.CrystalPlace.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cp != null && cp.isEnabled() && mc.player != null && mc.player.getMainHandStack().getItem() == net.minecraft.item.Items.END_CRYSTAL) {
            ci.cancel();
            return;
        }

        if (de.glutenfreierkeks.gfm_recode.client.modules.combat.HitCrystal.isRunning) {
            ci.cancel();
            return;
        }

        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            backupTarget = crosshairTarget;
            HitResult target = freecam.getPlayerInteractionTarget(4.5);
            if (target != null) crosshairTarget = target;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        FastPlacer fp = Gfm_recodeClient.modules.getModuleByClass(FastPlacer.class);
        if (fp != null && fp.shouldBypassCooldown()) {
            if (itemUseCooldown > fp.getDelay()) {
                itemUseCooldown = fp.getDelay();
            }
        }
    }

    @Inject(method = "doItemUse", at = @At("TAIL"))
    private void onDoItemUseTail(CallbackInfo ci) {
        if (backupTarget != null) {
            crosshairTarget = backupTarget;
            backupTarget = null;
        }

        if (Gfm_recodeClient.modules == null) return;
        FastPlacer fp = Gfm_recodeClient.modules.getModuleByClass(FastPlacer.class);
        if (fp != null && fp.shouldBypassCooldown()) {
            if (itemUseCooldown > fp.getDelay()) {
                itemUseCooldown = fp.getDelay();
            }
        }
    }

}
