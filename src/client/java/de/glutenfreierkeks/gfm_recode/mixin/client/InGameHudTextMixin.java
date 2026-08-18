package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.utils.NameProtectUtil;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.gui.hud.InGameHud;

/**
 * Mixin to replace player names in title, subtitle, and action bar.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudTextMixin {

    @ModifyArg(
        method = "setTitle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;setTitleTicks()V"),
        index = 0
    )
    private Text gfm$replaceTitle(Text text) {
        if (text != null && NameProtectUtil.isEnabled()) {
            return NameProtectUtil.replaceOwnName(text);
        }
        return text;
    }

    @ModifyArg(
        method = "setSubtitle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;setTitleTicks()V"),
        index = 0
    )
    private Text gfm$replaceSubtitle(Text text) {
        if (text != null && NameProtectUtil.isEnabled()) {
            return NameProtectUtil.replaceOwnName(text);
        }
        return text;
    }

    @ModifyArg(
        method = "setActionBar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;literal(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"),
        index = 0
    )
    private String gfm$replaceActionBar(String text) {
        if (text != null && NameProtectUtil.isEnabled()) {
            return NameProtectUtil.replaceOwnName(text);
        }
        return text;
    }
}
