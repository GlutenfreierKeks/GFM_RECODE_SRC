package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.gui.imgui.ImGuiManager;
import de.glutenfreierkeks.gfm_recode.client.gui.imgui.MenuScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void gfm$renderImGuiMenu(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!((Object) this instanceof MenuScreen)) return;

        ImGuiManager.getInstance().tryRenderThreadInit();
        ImGuiManager.getInstance().onFrameRender();
    }
}
