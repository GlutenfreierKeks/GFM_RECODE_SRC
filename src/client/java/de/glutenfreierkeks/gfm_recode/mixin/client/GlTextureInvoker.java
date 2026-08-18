package de.glutenfreierkeks.gfm_recode.mixin.client;

import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTexture.class)
public interface GlTextureInvoker {
    @Invoker("<init>")
    static GlTexture create(int usage, String name, TextureFormat format, int width, int height, int layers, int levels, int glId) {
        throw new UnsupportedOperationException();
    }
}
