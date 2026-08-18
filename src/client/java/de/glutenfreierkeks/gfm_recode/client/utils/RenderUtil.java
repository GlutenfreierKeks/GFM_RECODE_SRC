package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * RenderUtil – all geometry goes into ONE vcp.draw() call per render3D frame
 * via the batch variants. The standalone helpers (drawBox, drawFilledBox, …)
 * still exist for callers that need a single isolated draw, but the batch
 * helpers write directly into a consumer that the caller flushes once.
 *
 * Performance rules
 * -----------------
 *  • Every vcp.getBuffer() + vcp.draw() pair is expensive (pipeline flush).
 *  • ESP / StorageFinder call beginBatch() once, push all geometry, then endBatch().
 *  • Circle geometry uses CIRCLE_SEGMENTS = 32 (down from 48) – visually identical
 *    at entity scale but ~33 % fewer vertices.
 */
public final class RenderUtil {
    private RenderUtil() {}

    private static final int CIRCLE_SEGMENTS = 32;

    // ══════════════════════════════════════════════════════════════════════════
    // Batch API  (use these inside render3D loops for best performance)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Opens a batch; returns the VertexConsumer to pass to all batch* methods.
     * Call endBatch() exactly once when all geometry for this frame is written.
     */
    public static VertexConsumer beginBatch() {
        var mc  = MinecraftClient.getInstance();
        return mc.getBufferBuilders().getEntityVertexConsumers()
                .getBuffer(RenderLayers.textBackgroundSeeThrough());
    }

    /** Flushes the batch. Call once after all batch* calls for the frame. */
    public static void endBatch() {
        MinecraftClient.getInstance()
                .getBufferBuilders()
                .getEntityVertexConsumers()
                .draw();
    }

    // ── Box ───────────────────────────────────────────────────────────────────

    /**
     * Non-filled outline box written into an open batch consumer.
     * Renders all 6 faces as very thin slabs so there are NO gaps regardless
     * of camera angle (unlike the 12-edge quad approach).
     */
    public static void batchOutlineBox(VertexConsumer vc, Matrix4f m, Box box,
                                       float r, float g, float b, float a,
                                       float thickness) {
        float t  = Math.max(thickness / 100f, 0.005f);
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        int light = 15728880;

        // Bottom ring (4 edges at y1)
        quad(vc, m, x1,y1,z1,  x2,y1,z1,  x2,y1+t,z1, x1,y1+t,z1,  r,g,b,a,light); // N
        quad(vc, m, x1,y1,z2,  x2,y1,z2,  x2,y1+t,z2, x1,y1+t,z2,  r,g,b,a,light); // S
        quad(vc, m, x1,y1,z1,  x1,y1,z2,  x1,y1+t,z2, x1,y1+t,z1,  r,g,b,a,light); // W
        quad(vc, m, x2,y1,z1,  x2,y1,z2,  x2,y1+t,z2, x2,y1+t,z1,  r,g,b,a,light); // E
        // Top ring (4 edges at y2)
        quad(vc, m, x1,y2-t,z1, x2,y2-t,z1, x2,y2,z1,  x1,y2,z1,   r,g,b,a,light);
        quad(vc, m, x1,y2-t,z2, x2,y2-t,z2, x2,y2,z2,  x1,y2,z2,   r,g,b,a,light);
        quad(vc, m, x1,y2-t,z1, x1,y2-t,z2, x1,y2,z2,  x1,y2,z1,   r,g,b,a,light);
        quad(vc, m, x2,y2-t,z1, x2,y2-t,z2, x2,y2,z2,  x2,y2,z1,   r,g,b,a,light);
        // Vertical pillars (4 corners)
        quad(vc, m, x1,   y1,z1, x1+t,y1,z1, x1+t,y2,z1, x1,  y2,z1, r,g,b,a,light); // NW
        quad(vc, m, x2-t, y1,z1, x2,  y1,z1, x2,  y2,z1, x2-t,y2,z1, r,g,b,a,light); // NE
        quad(vc, m, x1,   y1,z2, x1+t,y1,z2, x1+t,y2,z2, x1,  y2,z2, r,g,b,a,light); // SW
        quad(vc, m, x2-t, y1,z2, x2,  y1,z2, x2,  y2,z2, x2-t,y2,z2, r,g,b,a,light); // SE
    }

    /** Solid filled box written into an open batch consumer. */
    public static void batchFilledBox(VertexConsumer vc, Matrix4f m, Box box,
                                      float r, float g, float b, float a) {
        float x1=(float)box.minX, y1=(float)box.minY, z1=(float)box.minZ;
        float x2=(float)box.maxX, y2=(float)box.maxY, z2=(float)box.maxZ;
        int light = 15728880;
        quad(vc,m, x1,y1,z1, x1,y1,z2, x2,y1,z2, x2,y1,z1, r,g,b,a,light); // bottom
        quad(vc,m, x1,y2,z1, x2,y2,z1, x2,y2,z2, x1,y2,z2, r,g,b,a,light); // top
        quad(vc,m, x1,y1,z1, x2,y1,z1, x2,y2,z1, x1,y2,z1, r,g,b,a,light); // north
        quad(vc,m, x1,y1,z2, x1,y2,z2, x2,y2,z2, x2,y1,z2, r,g,b,a,light); // south
        quad(vc,m, x1,y1,z1, x1,y2,z1, x1,y2,z2, x1,y1,z2, r,g,b,a,light); // west
        quad(vc,m, x2,y1,z1, x2,y1,z2, x2,y2,z2, x2,y2,z1, r,g,b,a,light); // east
    }

    /**
     * Horizontal circle ring (torus slab in XZ-plane) written into an open batch consumer.
     * The ring is FLAT (constant Y), so glow layers expand the radius horizontally only –
     * no vertical spread.
     *
     * @param cx  camera-relative X centre
     * @param cy  camera-relative Y of the ring
     * @param cz  camera-relative Z centre
     */
    public static void batchCircleRing(VertexConsumer vc, Matrix4f m,
                                       double cx, double cy, double cz,
                                       double radius, float tubeHalfWidth,
                                       float r, float g, float b, float a) {
        int light = 15728880;
        float hw  = Math.max(tubeHalfWidth, 0.006f);
        float yLo = (float)(cy - hw * 0.35f);
        float yHi = (float)(cy + hw * 0.35f);
        double inner = radius - hw;
        double outer = radius + hw;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a0 = 2.0 * Math.PI * i       / CIRCLE_SEGMENTS;
            double a1 = 2.0 * Math.PI * (i + 1) / CIRCLE_SEGMENTS;
            float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
            float ox0=(float)(cx+outer*c0), oz0=(float)(cz+outer*s0);
            float ox1=(float)(cx+outer*c1), oz1=(float)(cz+outer*s1);
            float ix0=(float)(cx+inner*c0), iz0=(float)(cz+inner*s0);
            float ix1=(float)(cx+inner*c1), iz1=(float)(cz+inner*s1);
            // top face
            quad(vc,m, ix0,yHi,iz0, ox0,yHi,oz0, ox1,yHi,oz1, ix1,yHi,iz1, r,g,b,a,light);
            // bottom face
            quad(vc,m, ix0,yLo,iz0, ix1,yLo,iz1, ox1,yLo,oz1, ox0,yLo,oz0, r,g,b,a,light);
        }
    }

    /**
     * 8 vertical struts connecting bottom ring to top ring.
     * Written into an open batch consumer.
     */
    public static void batchCircleVerticals(VertexConsumer vc, Matrix4f m,
                                            double cx, double baseY, double cz,
                                            double radius, float height,
                                            float hw,
                                            float r, float g, float b, float a) {
        int light = 15728880;
        hw = Math.max(hw, 0.004f);
        for (int i = 0; i < 8; i++) {
            double angle = 2.0 * Math.PI * i / 8;
            float px     = (float)(cx + radius * Math.cos(angle));
            float pz     = (float)(cz + radius * Math.sin(angle));
            float perpX  = (float)(-Math.sin(angle)) * hw;
            float perpZ  = (float)( Math.cos(angle)) * hw;
            float y0     = (float) baseY;
            float y1     = (float)(baseY + height);
            quad(vc, m,
                    px-perpX, y0, pz-perpZ,
                    px+perpX, y0, pz+perpZ,
                    px+perpX, y1, pz+perpZ,
                    px-perpX, y1, pz-perpZ,
                    r, g, b, a, light);
        }
    }

    /** Horizontal scan slab inside a box. scanProgress: 0=bottom, 1=top. */
    public static void batchScanPlane(VertexConsumer vc, Matrix4f m,
                                      Box box, float scanProgress,
                                      float r, float g, float b, float a) {
        double h    = box.maxY - box.minY;
        double y    = box.minY + h * scanProgress;
        float  slH  = 0.04f;
        Box slab = new Box(box.minX, y - slH, box.minZ, box.maxX, y + slH, box.maxZ);
        batchFilledBox(vc, m, slab, r, g, b, a);
    }

    public static void batchQuad(VertexConsumer vc, Matrix4f m,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 Color c1, Color c2, Color c3, Color c4) {
        int light = 15728880;
        vc.vertex(m, x1, y1, z1).color(c1.getRed() / 255f, c1.getGreen() / 255f, c1.getBlue() / 255f, c1.getAlpha() / 255f).light(light);
        vc.vertex(m, x2, y2, z2).color(c2.getRed() / 255f, c2.getGreen() / 255f, c2.getBlue() / 255f, c2.getAlpha() / 255f).light(light);
        vc.vertex(m, x3, y3, z3).color(c3.getRed() / 255f, c3.getGreen() / 255f, c3.getBlue() / 255f, c3.getAlpha() / 255f).light(light);
        vc.vertex(m, x4, y4, z4).color(c4.getRed() / 255f, c4.getGreen() / 255f, c4.getBlue() / 255f, c4.getAlpha() / 255f).light(light);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Legacy batch aliases — keep old call-sites in other modules compiling
    // ══════════════════════════════════════════════════════════════════════════

    /** @deprecated Use {@link #batchFilledBox} instead. */
    @Deprecated
    public static void drawFilledBoxBatch(VertexConsumer vc, Matrix4f m, Box box,
                                          float r, float g, float b, float a) {
        batchFilledBox(vc, m, box, r, g, b, a);
    }

    /** @deprecated Use {@link #batchOutlineBox} instead. */
    @Deprecated
    public static void drawOutlineBoxBatch(VertexConsumer vc, Matrix4f m, Box box,
                                           float lineWidth, float r, float g, float b, float a) {
        batchOutlineBox(vc, m, box, r, g, b, a, lineWidth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Legacy single-draw helpers (unchanged API, used by callers outside loops)
    // ══════════════════════════════════════════════════════════════════════════

    public static void drawBox(Matrix4f matrix, BlockPos pos, Color color,
                               double lineWidth, boolean fill, boolean outline) {
        Box box = new Box(pos);
        if (fill)    drawFilledBox(matrix, box, new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        if (outline) drawBox(matrix, box, color, lineWidth);
    }

    public static void drawBox(Matrix4f matrix, Box box, Color color, double lineWidth) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());
        float r=color.getRed()/255f, g=color.getGreen()/255f,
                b=color.getBlue()/255f, a=color.getAlpha()/255f;
        batchOutlineBox(vc, matrix, box, r, g, b, a, (float) lineWidth);
        vcp.draw();
    }

    public static void drawFilledBox(Matrix4f matrix, Box box, Color color) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());
        float r=color.getRed()/255f, g=color.getGreen()/255f,
                b=color.getBlue()/255f, a=color.getAlpha()/255f;
        batchFilledBox(vc, matrix, box, r, g, b, a);
        vcp.draw();
    }

    public static void drawCircleRing(Matrix4f matrix,
                                      double cx, double cy, double cz,
                                      double radius, float width, Color color) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());
        float r=color.getRed()/255f, g=color.getGreen()/255f,
                b=color.getBlue()/255f, a=color.getAlpha()/255f;
        batchCircleRing(vc, matrix, cx, cy, cz, radius, width/120f, r, g, b, a);
        vcp.draw();
    }

    public static void drawCircleVerticals(Matrix4f matrix,
                                           double cx, double baseY, double cz,
                                           double radius, float height,
                                           float width, Color color) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());
        float r=color.getRed()/255f, g=color.getGreen()/255f,
                b=color.getBlue()/255f, a=color.getAlpha()/255f;
        batchCircleVerticals(vc, matrix, cx, baseY, cz, radius, height, width/150f, r, g, b, a);
        vcp.draw();
    }

    public static void drawTracer(Matrix4f matrix, Vec3d start, Vec3d end, Color color) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(Layers.TRACERS);
        float r=color.getRed()/255f, g=color.getGreen()/255f,
                b=color.getBlue()/255f, a=color.getAlpha()/255f;
        vc.vertex(matrix,(float)start.x,(float)start.y,(float)start.z)
                .color(r,g,b,a).normal(0f,0f,1f).lineWidth(1f);
        vc.vertex(matrix,(float)end.x,(float)end.y,(float)end.z)
                .color(r,g,b,a).normal(0f,0f,1f).lineWidth(1f);
        vcp.draw();
    }

    public static void drawScanPlane(Matrix4f matrix, Box box, float scanProgress,
                                     float r, float g, float b, float a) {
        var mc  = MinecraftClient.getInstance();
        var vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayers.textBackgroundSeeThrough());
        batchScanPlane(vc, matrix, box, scanProgress, r, g, b, a);
        vcp.draw();
    }

    // ── Internal quad helper ──────────────────────────────────────────────────

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x1,float y1,float z1,
                             float x2,float y2,float z2,
                             float x3,float y3,float z3,
                             float x4,float y4,float z4,
                             float r,float g,float b,float a,int light) {
        vc.vertex(m,x1,y1,z1).color(r,g,b,a).light(light);
        vc.vertex(m,x2,y2,z2).color(r,g,b,a).light(light);
        vc.vertex(m,x3,y3,z3).color(r,g,b,a).light(light);
        vc.vertex(m,x4,y4,z4).color(r,g,b,a).light(light);
    }
}
