package de.glutenfreierkeks.gfm_recode.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderHook(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix3, GpuBufferSlice bufferSlice, Vector4f vector4f, boolean z, CallbackInfo ci) {
        if (Gfm_recodeClient.modules == null) return;
        for (Module module : Gfm_recodeClient.modules.getAll()) {
            if (module.isEnabled()) {
                module.render3D(positionMatrix, projectionMatrix, camera, tickCounter.getFixedDeltaTicks());
            }
        }
    }
}
