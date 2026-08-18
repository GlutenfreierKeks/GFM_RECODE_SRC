package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;

        // Freecam takes highest priority
        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            setRotation(freecam.getCameraYaw(), freecam.getCameraPitch());
            setPos(freecam.getCameraPos());
            return;
        }

        // Freelook: rotate camera freely while player body stays locked
        Freelook freelook = Gfm_recodeClient.modules.getModuleByClass(Freelook.class);
        if (freelook != null && freelook.isActive()) {
            setRotation(freelook.getCameraYaw(), freelook.getCameraPitch());
            setPos(freelook.getCameraPos());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.combat.KnockbackDisplacement displacement = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.KnockbackDisplacement.class);
        if (displacement != null && displacement.isEnabled() && displacement.isSilentActive()) {
            setRotation(displacement.getOriginalYaw(), displacement.getOriginalPitch());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.movement.WindHop windHop = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.movement.WindHop.class);
        if (windHop != null && windHop.isEnabled() && windHop.isSilentActive()) {
            setRotation(windHop.getOriginalYaw(), windHop.getOriginalPitch());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.combat.KillAura killAura = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isSilentActive()) {
            setRotation(killAura.getVisualYaw(), killAura.getVisualPitch());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.combat.AutoWeb autoWeb = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.AutoWeb.class);
        if (autoWeb != null && autoWeb.isEnabled() && autoWeb.isSilentActive()) {
            setRotation(autoWeb.getVisualYaw(), autoWeb.getVisualPitch());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.movement.Clutch clutch = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.movement.Clutch.class);
        if (clutch != null && clutch.isEnabled() && clutch.isSilentActive()) {
            setRotation(clutch.getVisualYaw(), clutch.getVisualPitch());
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.movement.CactusTower cactusTower = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.movement.CactusTower.class);
        if (cactusTower != null && cactusTower.isEnabled() && cactusTower.isSilentActive()) {
            setRotation(cactusTower.getVisualYaw(), cactusTower.getVisualPitch());
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        if (Gfm_recodeClient.modules == null) return;

        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            cir.setReturnValue(desiredCameraDistance);
            return;
        }

        // Also bypass wall clipping for Freelook so camera can go through walls
        Freelook freelook = Gfm_recodeClient.modules.getModuleByClass(Freelook.class);
        if (freelook != null && freelook.isActive()) {
            cir.setReturnValue(desiredCameraDistance);
        }
    }
}