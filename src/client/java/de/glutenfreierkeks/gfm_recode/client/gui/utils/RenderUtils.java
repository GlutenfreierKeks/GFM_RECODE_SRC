package de.glutenfreierkeks.gfm_recode.client.gui.utils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class RenderUtils {

    /**
     * Enhanced rounded rect with per-corner control
     */
    public static void fillRoundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color, boolean tl, boolean tr, boolean br, boolean bl) {
        if (w <= 0 || h <= 0) return;
        float radius = Math.min(r, Math.min(w, h) / 2);
        
        // Main rectangles to fill everything but the corners
        ctx.fill((int)(x + (bl ? radius : 0)), (int)y, (int)(x + w - (br ? radius : 0)), (int)(y + h), color);
        if (tl || bl) ctx.fill((int)x, (int)(y + (tl ? radius : 0)), (int)(x + (bl ? radius : 0)), (int)(y + h - (bl ? radius : 0)), color);
        if (tr || br) ctx.fill((int)(x + w - (tr ? radius : 0)), (int)(y + (tr ? radius : 0)), (int)(x + w), (int)(y + h - (br ? radius : 0)), color);

        // Fill corners
        if (tl) fillCircleFast(ctx, (int)(x + radius), (int)(y + radius), (int)radius, color, 180, 270);
        if (tr) fillCircleFast(ctx, (int)(x + w - radius), (int)(y + radius), (int)radius, color, 270, 360);
        if (br) fillCircleFast(ctx, (int)(x + w - radius), (int)(y + h - radius), (int)radius, color, 0, 90);
        if (bl) fillCircleFast(ctx, (int)(x + radius), (int)(y + h - radius), (int)radius, color, 90, 180);
    }

    public static void fillRoundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        fillRoundedRect(ctx, x, y, w, h, r, color, true, true, true, true);
    }

    /**
     * Fast filled circle/arc using horizontal strips
     */
    public static void fillCircleFast(DrawContext ctx, int cx, int cy, int r, int color, int startAngle, int endAngle) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            float dx = (float) Math.sqrt(r * r - dy * dy);
            int y = cy + dy;
            
            // This is a simplified arc-aware fill. For corners, we just check which side we're on.
            int x1 = cx - (int)dx;
            int x2 = cx + (int)dx;
            
            if (startAngle == 180 && endAngle == 270) { // TL
                if (dy <= 0) x2 = cx; else continue;
            } else if (startAngle == 270 && endAngle == 360) { // TR
                if (dy <= 0) x1 = cx; else continue;
            } else if (startAngle == 0 && endAngle == 90) { // BR
                if (dy >= 0) x1 = cx; else continue;
            } else if (startAngle == 90 && endAngle == 180) { // BL
                if (dy >= 0) x2 = cx; else continue;
            }
            
            ctx.fill(x1, y, x2, y + 1, color);
        }
    }

    public static void fillCircleFast(DrawContext ctx, int cx, int cy, int r, int color) {
        fillCircleFast(ctx, cx, cy, r, color, 0, 360);
    }

    public static void drawCircle(DrawContext ctx, float x, float y, float radius, int color) {
        if (radius <= 0) return;
        fillCircleFast(ctx, (int)x, (int)y, (int)radius, color);
    }

    public static void drawDropShadow(DrawContext ctx, int x, int y, int w, int h, int r) {
        // More layered shadow for depth
        ctx.fill(x - 1, y + h, x + w + 1, y + h + 2, 0x22000000);
        ctx.fill(x - 2, y + h + 2, x + w + 2, y + h + 4, 0x11000000);
        ctx.fill(x + w, y - 1, x + w + 2, y + h + 1, 0x22000000);
    }

    public static void drawBloomLine(DrawContext ctx, int x, int y, int w, int c1, int c2) {
        drawGradientH(ctx, x, y, w, 1, c1, c2);
    }

    public static void drawGradientH(DrawContext ctx, float x, float y, float w, float h, int c1, int c2) {
        ctx.fillGradient((int)x, (int)y, (int)(x + w), (int)(y + h), c1, c2);
    }

    public static void drawGradientV(DrawContext ctx, float x, float y, float w, float h, int c1, int c2) {
        // Vertical gradient is native to fillGradient if provided correctly
        // But fillGradient is strictly vertical in some mappings or combined
        ctx.fillGradient((int)x, (int)y, (int)(x + w), (int)(y + h), c1, c2);
    }

    public static int blendColor(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        return (((int)(aA + (bA - aA) * t)) << 24) | (((int)(aR + (bR - aR) * t)) << 16) |
                (((int)(aG + (bG - aG) * t)) << 8) | ((int)(aB + (bB - aB) * t));
    }

    public static int withAlpha(int color, float alpha) {
        int a = MathHelper.clamp((int)(((color >> 24) & 0xFF) * alpha), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static boolean isHovered(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}