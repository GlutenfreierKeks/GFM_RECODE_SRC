package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.ChestSearch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        ChestSearch chestSearch = (ChestSearch) Gfm_recodeClient.modules.getModuleByClass(ChestSearch.class);
        if (chestSearch == null || !chestSearch.isEnabled()) return;

        String query = chestSearch.query.getValue().toLowerCase();
        if (query.isEmpty()) return;

        if (slot.hasStack()) {
            String name = slot.getStack().getName().getString().toLowerCase();
            if (!name.contains(query)) {
                // Darken slot if not matching
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x88000000);
            } else {
                // Highlight slot if matching
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x5500FF00);
            }
        } else {
            // Darken empty slots too
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x88000000);
        }
    }
}
