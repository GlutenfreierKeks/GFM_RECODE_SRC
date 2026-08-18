package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.gui.imgui.MenuScreen;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // Tracks whether we forced third-person perspective for Freelook,
    // so we can restore it exactly when the key is released.
    private boolean freelookForcedPerspective = false;
    private Perspective freelookSavedPerspective = Perspective.FIRST_PERSON;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.currentScreen instanceof MenuScreen) return;

        // ── Freelook perspective management ──────────────────────────────────
        if (Gfm_recodeClient.modules != null) {
            Freelook freelook = Gfm_recodeClient.modules.getModuleByClass(Freelook.class);
            if (freelook != null && freelook.isEnabled()) {
                if (freelook.isActive() && !freelookForcedPerspective) {
                    // Key just pressed — save current perspective and switch to third-person
                    freelookSavedPerspective = mc.options.getPerspective();
                    mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    freelookForcedPerspective = true;
                } else if (!freelook.isActive() && freelookForcedPerspective) {
                    // Key just released — restore original perspective
                    mc.options.setPerspective(freelookSavedPerspective);
                    freelookForcedPerspective = false;
                }
            } else if (freelookForcedPerspective) {
                // Module was disabled mid-hold — restore perspective
                mc.options.setPerspective(freelookSavedPerspective);
                freelookForcedPerspective = false;
            }
        }

        // ── ImGui ─────────────────────────────────────────────────────────────
        de.glutenfreierkeks.gfm_recode.client.gui.imgui.ImGuiManager.getInstance().tryRenderThreadInit();
        de.glutenfreierkeks.gfm_recode.client.gui.imgui.ImGuiManager.getInstance().onFrameRender();
    }
}