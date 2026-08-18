package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import de.glutenfreierkeks.gfm_recode.client.gui.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public final class ThunderHudRenderer {
    private ThunderHudRenderer() {}

    public static void drawThunderPanel(DrawContext context, int x, int y, int width, int height, Color color, float animation) {
        int bg = argb(185, 9, 9, 13);
        int inner = argb(105, color.getRed(), color.getGreen(), color.getBlue());
        int line = argb(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
        int glow = argb(42, color.getRed(), color.getGreen(), color.getBlue());

        RenderUtils.drawDropShadow(context, x, y, width, height, 5);
        RenderUtils.fillRoundedRect(context, x, y, width, height, 5, bg);
        RenderUtils.fillRoundedRect(context, x + 1, y + 1, width - 2, height - 2, 4, argb(95, 18, 18, 24));

        int sweep = Math.max(10, (int) (width * 0.34f));
        int sweepX = x + (int) ((width + sweep) * animation) - sweep;
        context.fill(x + 3, y + 3, x + width - 3, y + 4, glow);
        context.fill(Math.max(x + 3, sweepX), y + 3, Math.min(x + width - 3, sweepX + sweep), y + 5, line);
        context.fill(x + 4, y + height - 6, x + width - 4, y + height - 5, inner);
    }

    public static void drawThunderText(DrawContext context, String title, String value, int x, int y, Color color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int text = argb(245, 244, 246, 255);
        int muted = argb(180, 176, 184, 202);
        context.drawText(mc.textRenderer, title, x, y, text, false);
        context.drawText(mc.textRenderer, value, x, y + 11, muted, false);
        context.fill(x, y + 23, x + Math.max(18, mc.textRenderer.getWidth(title)), y + 24, argb(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue()));
    }

    public static void drawPulseDots(DrawContext context, int x, int y, int count, Color color, float animation) {
        for (int i = 0; i < count; i++) {
            float wave = (float) ((Math.sin(animation * Math.PI * 2.0 + i * 0.7) + 1.0) * 0.5);
            int radius = 2 + (int) (wave * 2);
            RenderUtils.fillCircleFast(context, x + i * 10, y, radius, argb((int) (75 + wave * 130), color.getRed(), color.getGreen(), color.getBlue()));
        }
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
