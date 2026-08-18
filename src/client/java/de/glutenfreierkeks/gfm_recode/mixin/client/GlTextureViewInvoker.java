package de.glutenfreierkeks.gfm_recode.mixin.client;

import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.GlTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTextureView.class)
public interface GlTextureViewInvoker {
    @Invoker("<init>")
    static GlTextureView create(GlTexture texture, int layer, int level) {
        throw new UnsupportedOperationException();
    }
}
