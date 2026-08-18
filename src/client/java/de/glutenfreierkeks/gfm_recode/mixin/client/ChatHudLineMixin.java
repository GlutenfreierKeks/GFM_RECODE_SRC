package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.utils.NameProtectUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatHudLine.class)
public abstract class ChatHudLineMixin {

    @Shadow public abstract @Nullable MessageIndicator indicator();
    @Shadow public abstract Text content();

    @Inject(method = "breakLines", at = @At("HEAD"), cancellable = true)
    private void gfm$replaceOwnNameInChat(TextRenderer textRenderer, int width, CallbackInfoReturnable<List<OrderedText>> cir) {
        if (!NameProtectUtil.isEnabled()) return;

        int adjustedWidth = width;
        MessageIndicator indicator = indicator();
        if (indicator != null && indicator.icon() != null) {
            adjustedWidth -= indicator.icon().width + 4 + 2;
        }

        cir.setReturnValue(ChatMessages.breakRenderedChatMessageLines(NameProtectUtil.replaceOwnName(content()), adjustedWidth, textRenderer));
    }
}
