package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freelook;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;

        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            freecam.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
            return;
        }

        // Freelook: redirect mouse delta to camera rotation, cancel vanilla player rotation
        Freelook freelook = Gfm_recodeClient.modules.getModuleByClass(Freelook.class);
        if (freelook != null && freelook.isActive()) {
            freelook.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.combat.KillAura killAura = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isSilentActive()) {
            killAura.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.combat.AutoWeb autoWeb = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.combat.AutoWeb.class);
        if (autoWeb != null && autoWeb.isEnabled() && autoWeb.isSilentActive()) {
            autoWeb.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.movement.Clutch clutch = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.movement.Clutch.class);
        if (clutch != null && clutch.isEnabled() && clutch.isSilentActive()) {
            clutch.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
            return;
        }

        de.glutenfreierkeks.gfm_recode.client.modules.movement.CactusTower cactusTower = Gfm_recodeClient.modules.getModuleByClass(de.glutenfreierkeks.gfm_recode.client.modules.movement.CactusTower.class);
        if (cactusTower != null && cactusTower.isEnabled() && cactusTower.isSilentActive()) {
            cactusTower.onMouseMoved(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            freecam.onMouseScroll(vertical);
        }
    }
}