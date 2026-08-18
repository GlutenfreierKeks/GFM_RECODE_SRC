package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.gui.imgui.ImGuiManager;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onWindowInit(CallbackInfo ci) {
        Window window = (Window) (Object) this;
        ImGuiManager.getInstance().onGlfwInit(window.getHandle());
    }
}
