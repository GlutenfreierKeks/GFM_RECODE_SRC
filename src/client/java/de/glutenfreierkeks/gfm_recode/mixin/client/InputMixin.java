package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Input.class)
public class InputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            ci.cancel();

            // Zero out all movement fields in Input using reflection
            // This works regardless of Yarn name mapping
            try {
                Class<?> clazz = this.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        field.setAccessible(true);
                        if (field.getType() == float.class) {
                            field.setFloat(this, 0.0f);
                        } else if (field.getType() == boolean.class) {
                            field.setBoolean(this, false);
                        } else if (field.getType().isRecord()) {
                            // In 1.21+, Input has a playerInput record. 
                            // If we find a record field, we might need to "zero" it but 
                            // cancelling tick() should already prevent it from having any impact 
                            // if we also zero the primitive fields the game actually uses.
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }
    }
}
