package de.glutenfreierkeks.gfm_recode.mixin.client;

import net.minecraft.client.texture.AbstractTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Accessor("glTexture")
    void setGlTexture(GpuTexture glTexture);

    @Accessor("glTextureView")
    void setGlTextureView(GpuTextureView glTextureView);

    @Accessor("sampler")
    void setSampler(GpuSampler sampler);
}
