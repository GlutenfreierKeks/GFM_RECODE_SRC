package de.glutenfreierkeks.gfm_recode.mixin.client;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.render.StatsHider;
import de.glutenfreierkeks.gfm_recode.client.modules.render.HudModule;
import de.glutenfreierkeks.gfm_recode.client.utils.NameProtectUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.SidebarEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow @Final private static Comparator<ScoreboardEntry> SCOREBOARD_ENTRY_COMPARATOR;
    @Shadow @Final private MinecraftClient client;
    @Shadow public abstract TextRenderer getTextRenderer();

    private HudModule gfm$getHudModule() {
        if (Gfm_recodeClient.modules == null) {
            return null;
        }
        return Gfm_recodeClient.modules.getModuleByClass(HudModule.class);
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void gfm$hideVanillaHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled() && hud.showHotbar.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void gfm$hideVanillaStatusBars(DrawContext context, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled() && hud.showHotbar.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void gfm$hideHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled() && hud.showHotbar.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void gfm$hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void gfm$renderModifiedSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        HudModule hud = gfm$getHudModule();
        if (hud != null && hud.isEnabled() && hud.showScoreboard.getValue()) {
            ci.cancel();
            return;
        }

        StatsHider statsHider = Gfm_recodeClient.modules == null ? null : Gfm_recodeClient.modules.getModuleByClass(StatsHider.class);
        boolean statsEnabled = statsHider != null && statsHider.isEnabled();
        boolean nameProtect = NameProtectUtil.isEnabled();

        if (!statsEnabled && !nameProtect) return;

        if (statsEnabled && statsHider.getMode() == StatsHider.Mode.SCOREBOARD_OFF) {
            ci.cancel();
            return;
        }

        ci.cancel();

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        SidebarEntry[] sidebarEntries = scoreboard.getScoreboardEntries(objective)
            .stream()
            .filter(score -> !score.hidden())
            .sorted(SCOREBOARD_ENTRY_COMPARATOR)
            .limit(15L)
            .map(scoreboardEntry -> {
                Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                Text originalName = Team.decorateName(team, scoreboardEntry.name());
                Text scoreText = scoreboardEntry.formatted(numberFormat);
                int scoreWidth = this.getTextRenderer().getWidth(scoreText);
                return new SidebarEntry(originalName, scoreText, scoreWidth);
            })
            .toArray(SidebarEntry[]::new);

        for (int i = 0; i < sidebarEntries.length; i++) {
            SidebarEntry entry = sidebarEntries[i];
            Text lineText = statsEnabled ? statsHider.getRenderedLine(i, entry.name()) : entry.name();
            lineText = NameProtectUtil.replaceOwnName(lineText);
            sidebarEntries[i] = new SidebarEntry(lineText, entry.score(), entry.scoreWidth());
        }

        Text title = statsEnabled ? statsHider.getRenderedTitle(objective.getDisplayName()) : objective.getDisplayName();
        title = NameProtectUtil.replaceOwnName(title);

        int titleWidth = this.getTextRenderer().getWidth(title);
        int maxWidth = titleWidth;
        int joinerWidth = this.getTextRenderer().getWidth(": ");

        for (SidebarEntry sidebarEntry : sidebarEntries) {
            maxWidth = Math.max(maxWidth, this.getTextRenderer().getWidth(sidebarEntry.name()) + (sidebarEntry.scoreWidth() > 0 ? joinerWidth + sidebarEntry.scoreWidth() : 0));
        }

        int lines = sidebarEntries.length;
        int totalHeight = lines * 9;
        int bottom = context.getScaledWindowHeight() / 2 + totalHeight / 3;
        int left = context.getScaledWindowWidth() - maxWidth - 3;
        int right = context.getScaledWindowWidth() - 1;
        int headerColor = this.client.options.getTextBackgroundColor(0.4F);
        int bodyColor = this.client.options.getTextBackgroundColor(0.3F);
        int top = bottom - lines * 9;

        context.fill(left - 2, top - 10, right, top - 1, headerColor);
        context.fill(left - 2, top - 1, right, bottom, bodyColor);
        context.drawText(this.getTextRenderer(), title, left + maxWidth / 2 - titleWidth / 2, top - 9, Colors.WHITE, false);

        for (int i = 0; i < lines; i++) {
            SidebarEntry entry = sidebarEntries[i];
            int y = bottom - (lines - i) * 9;
            context.drawText(this.getTextRenderer(), entry.name(), left, y, Colors.WHITE, false);
            context.drawText(this.getTextRenderer(), entry.score(), right - entry.scoreWidth(), y, Colors.WHITE, false);
        }
    }
}
