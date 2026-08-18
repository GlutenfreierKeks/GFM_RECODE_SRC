package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.utils.NameProtectUtil;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to replace player name in entity nametag rendering (F5 third-person view).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyVariable(
        method = "renderLabelIfPresent",
        at = @At("HEAD"),
        ordinal = 0
    )
    private Text gfm$replaceEntityNameTag(Text text, LivingEntity entity, double x, double y, double z, int maxDistance) {
        if (text != null && NameProtectUtil.isEnabled()) {
            return NameProtectUtil.replaceOwnName(text);
        }
        return text;
    }
}
