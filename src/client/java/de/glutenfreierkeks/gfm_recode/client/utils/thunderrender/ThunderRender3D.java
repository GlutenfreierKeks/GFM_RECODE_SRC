package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

public final class ThunderRender3D {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static float prevCircleStep;
    private static float circleStep;
    private static final long initTime = System.currentTimeMillis();

    private ThunderRender3D() {}

    public static void drawFilledBox(VertexConsumer vc, Matrix4f matrix, Box box, Color color) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        RenderUtil.batchFilledBox(vc, matrix, box, r, g, b, a);
    }

    public static void drawBoxOutline(VertexConsumer vc, Matrix4f matrix, Box box, Color color, float lineWidth) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        RenderUtil.batchOutlineBox(vc, matrix, box, r, g, b, a, lineWidth);
    }

    public static void drawFilledFadeBox(VertexConsumer vc, Matrix4f matrix, Box box, Color bottom, Color top) {
        RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.minX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.minZ, bottom, top, top, bottom);
        RenderUtil.batchQuad(vc, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.minY, (float) box.maxZ, bottom, top, top, bottom);
        RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.maxX, (float) box.minY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, bottom, bottom, top, top);
        RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.minZ, bottom, bottom, top, top);
        RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, top, top, top, top);
    }

    public static void drawFilledSide(VertexConsumer vc, Matrix4f matrix, Box box, Color color, Direction direction) {
        switch (direction) {
            case DOWN -> RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.minY, (float) box.maxZ, color, color, color, color);
            case UP -> RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, color, color, color, color);
            case NORTH -> RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.minX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.minZ, color, color, color, color);
            case SOUTH -> RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.maxX, (float) box.minY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, color, color, color, color);
            case WEST -> RenderUtil.batchQuad(vc, matrix, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.minZ, color, color, color, color);
            case EAST -> RenderUtil.batchQuad(vc, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.maxX, (float) box.minY, (float) box.maxZ, color, color, color, color);
        }
    }

    public static void renderCrosses(VertexConsumer vc, Matrix4f matrix, Box box, Color color, float lineWidth) {
        drawLine(vc, matrix, new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.minX, box.minY, box.maxZ), color, lineWidth);
        drawLine(vc, matrix, new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ), color, lineWidth);
    }

    public static void drawCircle3D(VertexConsumer vc, Matrix4f matrix, Entity entity, Camera camera, float tickDelta, float radius, Color color, int points, boolean hudColor, int colorOffset) {
        Vec3d center = interpolate(entity, camera, tickDelta, 0);
        for (int i = 0; i < points; i++) {
            Color segmentColor = hudColor ? ThunderRender2D.astolfo(i * colorOffset, color.getAlpha()) : color;
            drawRingSegment(vc, matrix, center.x, center.y, center.z, radius, 0.025, 0.035, i, points, segmentColor);
        }
    }

    public static void drawOldTargetEsp(VertexConsumer vc, Matrix4f matrix, Entity target, Camera camera, float tickDelta, Color baseColor) {
        double cs = prevCircleStep + (circleStep - prevCircleStep) * tickDelta;
        double sinAnim = absSinAnimation(cs);
        Vec3d base = interpolate(target, camera, tickDelta, 0);
        double y = base.y + sinAnim * target.getHeight();
        double radius = Math.max(target.getWidth() * 0.95f, 0.42f);
        int points = 72;
        for (int i = 0; i < points; i++) {
            double wave = 0.65 + 0.35 * Math.sin(i * 0.25 + circleStep);
            Color color = ThunderRender2D.injectAlpha(baseColor, (int) (160 + wave * 65));
            drawRingSegment(vc, matrix, base.x, y, base.z, radius, 0.06, 0.075, i, points, color);
            drawRingSegment(vc, matrix, base.x, y, base.z, radius + 0.09, 0.08, 0.09, i, points, ThunderRender2D.injectAlpha(baseColor, 65));
        }
    }

    public static void drawTargetEsp(VertexConsumer vc, Matrix4f matrix, Entity target, Camera camera, float tickDelta, Color baseColor) {
        Vec3d base = interpolate(target, camera, tickDelta, 0);
        double radius = Math.max(target.getWidth() * 0.78f, 0.36f);
        for (int lane = 0; lane < 3; lane++) {
            double height = target.getHeight();
            for (int i = 0; i < 360; i += 2) {
                double phase = ((System.currentTimeMillis() - initTime) / 8.0 + lane * 120) % 360;
                double a0 = Math.toRadians((i + phase) % 360);
                double a1 = Math.toRadians((i + 2 + phase) % 360);
                double tail = 1.0 - (i / 360.0);
                int alpha = (int) (60 + tail * 175);
                Color color = ThunderRender2D.injectAlpha(baseColor, alpha);
                Color glow = ThunderRender2D.injectAlpha(baseColor, 46);
                float x0 = (float) (base.x + Math.cos(a0) * radius);
                float z0 = (float) (base.z + Math.sin(a0) * radius);
                float x1 = (float) (base.x + Math.cos(a1) * radius);
                float z1 = (float) (base.z + Math.sin(a1) * radius);
                float y0 = (float) (base.y + height);
                float y1 = y0 + 0.16f;
                RenderUtil.batchQuad(vc, matrix, x0, y0 - 0.04f, z0, x1, y0, z1, x1, y1 + 0.08f, z1, x0, y1 + 0.04f, z0, glow, glow, glow, glow);
                RenderUtil.batchQuad(vc, matrix, x0, y0, z0, x1, y0 + 0.04f, z1, x1, y1, z1, x0, y1 - 0.04f, z0, color, color, color, color);
                height -= 0.008f;
            }
        }
    }

    public static void renderGhosts(VertexConsumer vc, Matrix4f matrix, Camera camera, float tickDelta, int espLength, int factor, float shaking, float amplitude, Entity target, Color baseColor) {
        Vec3d body = interpolate(target, camera, tickDelta, target.getHeight() * 0.58);
        float age = (float) ThunderRender2D.interpolate(target.age - 1, target.age, tickDelta);
        Color core = ThunderRender2D.injectAlpha(baseColor, 72);
        drawGhostBody(vc, matrix, body, Math.max(target.getWidth() * 1.45f, 0.72f), target.getHeight() * 0.42f, core);

        Vec3d orbitBase = interpolate(target, camera, tickDelta, target.getHeight() * 0.5);
        for (int lane = 0; lane < 3; lane++) {
            Vec3d previous = null;
            int trailPoints = Math.max(8, espLength * 2);
            for (int i = 0; i <= trailPoints; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + age) * factor + (lane * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(age * 2.5f + i * (lane + 1)) * amplitude) / Math.max(1.6f, shaking);
                float offset = (float) i / Math.max(1, trailPoints);
                Color color = ThunderRender2D.applyOpacity(baseColor, 0.22f + offset * 0.42f);
                Vec3d point = orbitBase.add(Math.cos(radians) * target.getWidth() * 0.78, sinQuad * 0.55, Math.sin(radians) * target.getWidth() * 0.78);
                if (previous != null) {
                    drawLine(vc, matrix, previous, point, color, 7.0f + offset * 6.0f);
                }
                previous = point;
            }
        }
    }

    public static void drawSphere(VertexConsumer vc, Matrix4f matrix, Vec3d center, float radius, int slices, int stacks, Color color) {
        for (int i = 1; i < stacks; i++) {
            float rho = (float) i * (float) Math.PI / stacks;
            Vec3d previous = null;
            for (int j = 0; j <= slices; j++) {
                float theta = (float) j * ((float) Math.PI * 2f) / slices;
                Vec3d current = center.add(Math.cos(theta) * Math.sin(rho) * radius, Math.sin(theta) * Math.sin(rho) * radius, Math.cos(rho) * radius);
                if (previous != null) {
                    drawLine(vc, matrix, previous, current, color, 0.8f);
                }
                previous = current;
            }
        }
    }

    public static void drawCylinder(VertexConsumer vc, Matrix4f matrix, Vec3d center, float radius, float height, int slices, int stacks, Color color) {
        for (int level = 0; level <= stacks; level++) {
            double y = center.y + height * level / Math.max(1, stacks);
            Vec3d previous = null;
            for (int i = 0; i <= slices; i++) {
                double angle = i * Math.PI * 2.0 / slices;
                Vec3d current = new Vec3d(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
                if (previous != null) {
                    drawLine(vc, matrix, previous, current, color, 0.8f);
                }
                previous = current;
            }
        }
    }

    public static void updateTargetESP() {
        prevCircleStep = circleStep;
        circleStep += 0.15f;
    }

    public static double absSinAnimation(double input) {
        return Math.abs(1 + Math.sin(input)) / 2;
    }

    public static Vec3d interpolate(Entity entity, Camera camera, float tickDelta, double yOffset) {
        double x = ThunderRender2D.interpolate(entity.lastX, entity.getX(), tickDelta) - camera.getCameraPos().x;
        double y = ThunderRender2D.interpolate(entity.lastY, entity.getY(), tickDelta) - camera.getCameraPos().y + yOffset;
        double z = ThunderRender2D.interpolate(entity.lastZ, entity.getZ(), tickDelta) - camera.getCameraPos().z;
        return new Vec3d(x, y, z);
    }

    private static void drawRingSegment(VertexConsumer vc, Matrix4f matrix, double cx, double y, double cz, double radius, double halfWidth, double halfHeight, int segment, int points, Color color) {
        double a0 = segment * Math.PI * 2.0 / points;
        double a1 = (segment + 1) * Math.PI * 2.0 / points;
        double inner = Math.max(0.01, radius - halfWidth);
        double outer = radius + halfWidth;
        float y0 = (float) (y - halfHeight);
        float y1 = (float) (y + halfHeight);
        float ix0 = (float) (cx + Math.cos(a0) * inner);
        float iz0 = (float) (cz + Math.sin(a0) * inner);
        float ix1 = (float) (cx + Math.cos(a1) * inner);
        float iz1 = (float) (cz + Math.sin(a1) * inner);
        float ox0 = (float) (cx + Math.cos(a0) * outer);
        float oz0 = (float) (cz + Math.sin(a0) * outer);
        float ox1 = (float) (cx + Math.cos(a1) * outer);
        float oz1 = (float) (cz + Math.sin(a1) * outer);
        RenderUtil.batchQuad(vc, matrix,
                ix0, y1, iz0, ox0, y1, oz0, ox1, y1, oz1, ix1, y1, iz1,
                color, color, color, color);
        RenderUtil.batchQuad(vc, matrix,
                ix1, y0, iz1, ox1, y0, oz1, ox0, y0, oz0, ix0, y0, iz0,
                color, color, color, color);
        RenderUtil.batchQuad(vc, matrix,
                ox0, y0, oz0, ox1, y0, oz1, ox1, y1, oz1, ox0, y1, oz0,
                color, color, color, color);
        RenderUtil.batchQuad(vc, matrix,
                ix1, y0, iz1, ix0, y0, iz0, ix0, y1, iz0, ix1, y1, iz1,
                color, color, color, color);
    }

    private static void drawGhostBody(VertexConsumer vc, Matrix4f matrix, Vec3d center, double radius, double height, Color color) {
        int stacks = 14;
        for (int stack = 0; stack <= stacks; stack++) {
            double progress = stack / (double) stacks;
            double wave = Math.sin(progress * Math.PI);
            double y = center.y - height * 0.55 + progress * height;
            double radiusScale = Math.max(0.18, wave);
            int alpha = (int) (color.getAlpha() * (0.35 + wave * 0.95));
            drawFilledDisc(vc, matrix, center.x, y, center.z, radius * radiusScale, ThunderRender2D.injectAlpha(color, alpha), 48);
        }

        drawFilledVerticalDiscX(vc, matrix, center, radius * 0.95, height * 0.72, ThunderRender2D.injectAlpha(color, (int) (color.getAlpha() * 0.85)), 48);
        drawFilledVerticalDiscZ(vc, matrix, center, radius * 0.95, height * 0.72, ThunderRender2D.injectAlpha(color, (int) (color.getAlpha() * 0.85)), 48);

        for (int lobe = 0; lobe < 6; lobe++) {
            double angle = lobe * Math.PI * 2.0 / 6.0;
            Vec3d lobeCenter = center.add(Math.cos(angle) * radius * 0.42, -height * 0.58, Math.sin(angle) * radius * 0.42);
            drawFilledDisc(vc, matrix, lobeCenter.x, lobeCenter.y, lobeCenter.z, radius * 0.32, ThunderRender2D.injectAlpha(color, color.getAlpha()), 32);
        }
    }

    private static void drawFilledDisc(VertexConsumer vc, Matrix4f matrix, double cx, double y, double cz, double radius, Color color, int points) {
        Color edge = ThunderRender2D.injectAlpha(color, (int) (color.getAlpha() * 0.45f));
        for (int i = 0; i < points; i++) {
            double a0 = i * Math.PI * 2.0 / points;
            double a1 = (i + 1) * Math.PI * 2.0 / points;
            RenderUtil.batchQuad(vc, matrix,
                    (float) cx, (float) y, (float) cz,
                    (float) (cx + Math.cos(a0) * radius), (float) y, (float) (cz + Math.sin(a0) * radius),
                    (float) (cx + Math.cos(a1) * radius), (float) y, (float) (cz + Math.sin(a1) * radius),
                    (float) cx, (float) y, (float) cz,
                    color, edge, edge, color);
        }
    }

    private static void drawFilledVerticalDiscX(VertexConsumer vc, Matrix4f matrix, Vec3d center, double radius, double height, Color color, int points) {
        Color edge = ThunderRender2D.injectAlpha(color, (int) (color.getAlpha() * 0.45f));
        for (int i = 0; i < points; i++) {
            double a0 = i * Math.PI * 2.0 / points;
            double a1 = (i + 1) * Math.PI * 2.0 / points;
            RenderUtil.batchQuad(vc, matrix,
                    (float) center.x, (float) center.y, (float) center.z,
                    (float) (center.x + Math.cos(a0) * radius), (float) (center.y + Math.sin(a0) * height), (float) center.z,
                    (float) (center.x + Math.cos(a1) * radius), (float) (center.y + Math.sin(a1) * height), (float) center.z,
                    (float) center.x, (float) center.y, (float) center.z,
                    color, edge, edge, color);
        }
    }

    private static void drawFilledVerticalDiscZ(VertexConsumer vc, Matrix4f matrix, Vec3d center, double radius, double height, Color color, int points) {
        Color edge = ThunderRender2D.injectAlpha(color, (int) (color.getAlpha() * 0.45f));
        for (int i = 0; i < points; i++) {
            double a0 = i * Math.PI * 2.0 / points;
            double a1 = (i + 1) * Math.PI * 2.0 / points;
            RenderUtil.batchQuad(vc, matrix,
                    (float) center.x, (float) center.y, (float) center.z,
                    (float) center.x, (float) (center.y + Math.sin(a0) * height), (float) (center.z + Math.cos(a0) * radius),
                    (float) center.x, (float) (center.y + Math.sin(a1) * height), (float) (center.z + Math.cos(a1) * radius),
                    (float) center.x, (float) center.y, (float) center.z,
                    color, edge, edge, color);
        }
    }

    private static void drawLine(VertexConsumer vc, Matrix4f matrix, Vec3d start, Vec3d end, Color color, float lineWidth) {
        Vec3d direction = end.subtract(start);
        Vec3d normal = new Vec3d(-direction.z, 0, direction.x);
        if (normal.lengthSquared() < 0.0001) {
            normal = new Vec3d(lineWidth / 200.0, 0, 0);
        } else {
            normal = normal.normalize().multiply(lineWidth / 160.0);
        }
        RenderUtil.batchQuad(vc, matrix,
                (float) (start.x - normal.x), (float) start.y, (float) (start.z - normal.z),
                (float) (start.x + normal.x), (float) start.y, (float) (start.z + normal.z),
                (float) (end.x + normal.x), (float) end.y, (float) (end.z + normal.z),
                (float) (end.x - normal.x), (float) end.y, (float) (end.z - normal.z),
                color, color, color, color);
    }
}
