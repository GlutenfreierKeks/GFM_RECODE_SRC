package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.gui.web.HudRenderer;
import de.glutenfreierkeks.gfm_recode.client.modules.render.HudModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    private HudModule gfm$getHudModule() {
        if (Gfm_recodeClient.modules == null) {
            return null;
        }
        return Gfm_recodeClient.modules.getModuleByClass(HudModule.class);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void gfm$renderHudEditor(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled()) {
            HudRenderer.forwardMouseMove(mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void gfm$forwardMouseClick(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled()) {
            HudRenderer.forwardMousePress(click);
        }
    }


    @Inject(method = "mouseScrolled", at = @At("HEAD"))
    private void gfm$forwardMouseScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled()) {
            HudRenderer.forwardMouseScroll(mouseX, mouseY, verticalAmount, horizontalAmount);
        }
    }
}
