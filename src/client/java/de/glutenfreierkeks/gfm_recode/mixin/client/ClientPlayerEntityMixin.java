package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.combat.InteractionLock;
import de.glutenfreierkeks.gfm_recode.client.modules.combat.SpinBot;
import de.glutenfreierkeks.gfm_recode.client.modules.render.Freecam;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    private float originalYaw;
    private float originalPitch;

    @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
    private void onIsUsingItem(CallbackInfoReturnable<Boolean> cir) {
        if (Gfm_recodeClient.modules == null) return;
        Freecam freecam = Gfm_recodeClient.modules.getModuleByClass(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            // Prevents player from slowing down while using items in freecam
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovementHead(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        SpinBot spinBot = Gfm_recodeClient.modules.getModuleByClass(SpinBot.class);
        if (spinBot != null && spinBot.isEnabled()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

            float spinYaw = spinBot.getSpinYaw();
            float viewYaw = player.getYaw();

            // Calculate rotation difference in radians
            float diff = (viewYaw - spinYaw) * (float) (Math.PI / 180.0);
            float cos = (float) Math.cos(diff);
            float sin = (float) Math.sin(diff);

            Object input = player.input;
            try {
                // Use reflection to bypass mapping issues
                java.lang.reflect.Field fwdField = null;
                java.lang.reflect.Field sideField = null;

                // Try names first (Yarn)
                try { fwdField = input.getClass().getDeclaredField("movementForward"); } catch (Exception ignored) {}
                try { sideField = input.getClass().getDeclaredField("movementSideways"); } catch (Exception ignored) {}

                // If names fail, find by type (there are only 2 floats in Input)
                if (fwdField == null || sideField == null) {
                    for (java.lang.reflect.Field f : input.getClass().getDeclaredFields()) {
                        if (f.getType() == float.class) {
                            if (sideField == null) sideField = f;
                            else fwdField = f;
                        }
                    }
                }

                if (fwdField != null && sideField != null) {
                    fwdField.setAccessible(true);
                    sideField.setAccessible(true);

                    float forward = fwdField.getFloat(input);
                    float sideways = sideField.getFloat(input);

                    float newForward = forward * cos + sideways * sin;
                    float newSideways = sideways * cos - forward * sin;

                    fwdField.setFloat(input, newForward);
                    sideField.setFloat(input, newSideways);

                    // Also update pressing flags if possible
                    for (java.lang.reflect.Field f : input.getClass().getDeclaredFields()) {
                        if (f.getType() == boolean.class) {
                            f.setAccessible(true);
                            String name = f.getName().toLowerCase();
                            if (name.contains("forward")) f.setBoolean(input, newForward > 0);
                            else if (name.contains("back")) f.setBoolean(input, newForward < 0);
                            else if (name.contains("left")) f.setBoolean(input, newSideways > 0);
                            else if (name.contains("right")) f.setBoolean(input, newSideways < 0);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        InteractionLock.onMovementPacketSent();

        if (Gfm_recodeClient.modules == null) return;
        SpinBot spinBot = Gfm_recodeClient.modules.getModuleByClass(SpinBot.class);

        if (spinBot != null && spinBot.isEnabled()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            originalYaw = player.getYaw();
            originalPitch = player.getPitch();

            // Set the spinning rotation for the packets
            player.setYaw(spinBot.getSpinYaw());
            player.setPitch(spinBot.getSpinPitch());
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        SpinBot spinBot = Gfm_recodeClient.modules.getModuleByClass(SpinBot.class);

        if (spinBot != null && spinBot.isEnabled()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            // Restore the stable view yaw for the player
            player.setYaw(originalYaw);
            player.setPitch(originalPitch);
        }
    }
}
