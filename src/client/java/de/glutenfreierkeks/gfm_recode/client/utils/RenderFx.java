package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.awt.Color;

public final class RenderFx {
    private RenderFx() {}

    public enum VisualMode {
        SIMPLE,
        SHADER
    }

    public static final class ShaderOptions {
        public final float lineWidth;
        public final boolean bloom;
        public final boolean strongGlow;
        public final boolean scanner;
        public final boolean pulse;
        public final boolean halo;

        public ShaderOptions(float lineWidth, boolean bloom, boolean strongGlow, boolean scanner, boolean pulse, boolean halo) {
            this.lineWidth = lineWidth;
            this.bloom = bloom;
            this.strongGlow = strongGlow;
            this.scanner = scanner;
            this.pulse = pulse;
            this.halo = halo;
        }
    }

    public static void renderSimpleBox(VertexConsumer vc, Matrix4f matrix, Box box, Color color) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        RenderUtil.batchFilledBox(vc, matrix, box, r, g, b, Math.max(0.12f, a * 0.24f));
    }

    public static void renderShaderBox(VertexConsumer vc, Matrix4f matrix, Box box, Color color, ShaderOptions options, double seed) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        float width = Math.max(0.85f, options.lineWidth);
        float bloomWave = 0.75f + pulse(seed + 0.23, 1.85) * 0.45f;
        float glowWave = 0.70f + pulse(seed + 0.61, 2.35) * 0.65f;
        float shellWave = 0.65f + pulse(seed + 1.14, 1.35) * 0.55f;

        RenderUtil.batchFilledBox(vc, matrix, box, r, g, b, a * 0.10f);
        RenderUtil.batchFilledBox(vc, matrix, contract(box, 0.02f), r, g, b, a * 0.08f);
        RenderUtil.batchOutlineBox(vc, matrix, box, r, g, b, a * (0.90f + glowWave * 0.15f), width);

        if (options.bloom) {
            for (int i = 1; i <= 8; i++) {
                float expand = 0.018f * i;
                float falloff = 1f - (i - 1) / 8f;
                float alpha = a * bloomWave * (0.11f * easeOut(falloff));
                if (alpha <= 0.003f) {
                    break;
                }
                RenderUtil.batchFilledBox(vc, matrix, expand(box, expand), r, g, b, alpha);
            }

            for (int i = 1; i <= 5; i++) {
                float expand = 0.028f * i;
                float alpha = a * bloomWave * Math.max(0f, 0.055f - (i - 1) * 0.009f);
                if (alpha <= 0.003f) {
                    break;
                }
                RenderUtil.batchOutlineBox(vc, matrix, expand(box, expand), r, g, b, alpha, width * 0.95f);
            }
        }

        if (options.strongGlow) {
            int layers = options.bloom ? 20 : 14;
            for (int i = 1; i <= layers; i++) {
                float expand = (0.016f + glowWave * 0.004f) * i;
                float falloff = 1f - (i - 1) / (float) layers;
                float alpha = a * glowWave * (0.24f * easeOut(falloff));
                if (alpha <= 0.003f) {
                    break;
                }
                Box glowBox = expand(box, expand);
                RenderUtil.batchFilledBox(vc, matrix, glowBox, r, g, b, alpha * 0.55f);
                RenderUtil.batchOutlineBox(vc, matrix, glowBox, r, g, b, alpha * 0.55f, width * (0.7f + shellWave * 0.12f));
            }
        }

        if (options.pulse) {
            float pulse = pulse(seed, 2.7);
            float expandA = 0.03f + pulse * 0.11f;
            float expandB = 0.09f + pulse(seed + 0.9, 1.8f) * 0.17f;
            RenderUtil.batchOutlineBox(vc, matrix, expand(box, expandA), r, g, b, a * (0.18f + pulse * 0.22f), width);
            RenderUtil.batchOutlineBox(vc, matrix, expand(box, expandB), r, g, b, a * 0.10f, width * 0.9f);
        }

        if (options.halo) {
            for (int i = 1; i <= 4; i++) {
                float drift = pulse(seed + i * 0.37, 1.15 + i * 0.22f) * 0.06f;
                float expand = 0.10f * i + drift;
                float alpha = a * shellWave * (0.065f / i);
                Box haloBox = expand(box, expand);
                RenderUtil.batchFilledBox(vc, matrix, haloBox, r, g, b, alpha * 0.45f);
            }
        }

        if (options.scanner) {
            float progress = pingPong(seed, 0.55);
            float fade = 0.18f + 0.82f * (float) Math.sin(progress * Math.PI);
            RenderUtil.batchScanPlane(vc, matrix, box, progress, r, g, b, a * fade);
            RenderUtil.batchScanPlane(vc, matrix, box, clamp01(progress * 0.72f + 0.14f), r, g, b, a * fade * 0.30f);

            double height = box.maxY - box.minY;
            double scanY = box.minY + height * progress;
            for (int i = 1; i <= 14; i++) {
                float expand = 0.018f * i;
                float slabAlpha = a * fade * glowWave * Math.max(0f, 0.28f - (i - 1) * 0.018f);
                if (slabAlpha <= 0.003f) {
                    break;
                }
                Box slab = new Box(
                        box.minX - expand, scanY - 0.045f - expand * 0.3f, box.minZ - expand,
                        box.maxX + expand, scanY + 0.045f + expand * 0.3f, box.maxZ + expand
                );
                RenderUtil.batchFilledBox(vc, matrix, slab, r, g, b, slabAlpha);
            }
        }
    }

    public static void renderSimpleEntity(VertexConsumer vc, Matrix4f matrix, Box box, Color color) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double bodyWidth = Math.max((box.maxX - box.minX) * 0.52, 0.22);
        double height = box.maxY - box.minY;
        double legHeight = height * 0.42;
        double torsoBottom = box.minY + legHeight;
        double torsoTop = box.minY + height * 0.78;
        double headBottom = torsoTop;
        double headTop = box.maxY;
        double armWidth = bodyWidth * 0.22;
        double legWidth = bodyWidth * 0.24;
        double torsoWidth = bodyWidth * 0.62;
        double headSize = bodyWidth * 0.46;

        Box[] parts = new Box[] {
                centeredBox(cx, torsoBottom, cz, torsoWidth, torsoTop - torsoBottom, torsoWidth * 0.55),
                centeredBox(cx, headBottom, cz, headSize, headTop - headBottom, headSize),
                centeredBox(cx - legWidth * 0.95, box.minY, cz, legWidth, legHeight, legWidth),
                centeredBox(cx + legWidth * 0.95, box.minY, cz, legWidth, legHeight, legWidth),
                centeredBox(cx - (torsoWidth + armWidth * 1.35), torsoBottom, cz, armWidth, torsoTop - torsoBottom, armWidth),
                centeredBox(cx + (torsoWidth + armWidth * 1.35), torsoBottom, cz, armWidth, torsoTop - torsoBottom, armWidth)
        };

        for (Box part : parts) {
            RenderUtil.batchFilledBox(vc, matrix, part, r, g, b, Math.max(0.14f, a * 0.28f));
        }
    }

    public static void renderShaderEntity(VertexConsumer vc, Matrix4f matrix, double cx, double baseY, double cz, float height, double radius, Color color, ShaderOptions options, double seed) {
        renderShaderEntityModel(vc, matrix, cx, baseY, cz, height, radius, color, options, seed);
    }

    public static void renderShaderEntityModel(VertexConsumer vc, Matrix4f matrix, double cx, double baseY, double cz, float height, double radius, Color color, ShaderOptions options, double seed) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        float bloomWave = 0.78f + pulse(seed + 0.17, 1.95) * 0.42f;
        float glowWave = 0.72f + pulse(seed + 0.47, 2.65) * 0.62f;
        float orbitWave = 0.70f + pulse(seed + 1.07, 1.15) * 0.50f;
        double bodyWidth = Math.max(radius * 0.95, 0.22);
        double torsoWidth = bodyWidth * 0.62;
        double armWidth = bodyWidth * 0.22;
        double legWidth = bodyWidth * 0.24;
        double headSize = bodyWidth * 0.46;
        double legHeight = height * 0.42;
        double torsoBottom = baseY + legHeight;
        double torsoTop = baseY + height * 0.78;
        double armTop = baseY + height * 0.76;
        double headBottom = torsoTop;
        double headTop = Math.min(baseY + height, headBottom + height * 0.22);

        Box torso = centeredBox(cx, torsoBottom, cz, torsoWidth, torsoTop - torsoBottom, torsoWidth * 0.55);
        Box head = centeredBox(cx, headBottom, cz, headSize, headTop - headBottom, headSize);
        Box leftLeg = centeredBox(cx - legWidth * 0.95, baseY, cz, legWidth, legHeight, legWidth);
        Box rightLeg = centeredBox(cx + legWidth * 0.95, baseY, cz, legWidth, legHeight, legWidth);
        Box leftArm = centeredBox(cx - (torsoWidth + armWidth * 1.35), torsoBottom, cz, armWidth, armTop - torsoBottom, armWidth);
        Box rightArm = centeredBox(cx + (torsoWidth + armWidth * 1.35), torsoBottom, cz, armWidth, armTop - torsoBottom, armWidth);
        Box[] parts = new Box[] { torso, head, leftLeg, rightLeg, leftArm, rightArm };

        for (Box part : parts) {
            RenderUtil.batchFilledBox(vc, matrix, part, r, g, b, a * 0.22f);
            RenderUtil.batchOutlineBox(vc, matrix, part, r, g, b, a * (0.78f + glowWave * 0.16f), Math.max(options.lineWidth * 0.8f, 0.75f));
        }

        if (options.bloom) {
            for (int i = 1; i <= 8; i++) {
                float falloff = 1f - (i - 1) / 8f;
                float alpha = a * bloomWave * (0.10f * easeOut(falloff));
                if (alpha <= 0.003f) {
                    break;
                }
                float expand = 0.018f * i;
                for (Box part : parts) {
                    RenderUtil.batchFilledBox(vc, matrix, expand(part, expand), r, g, b, alpha * 0.72f);
                }
            }
        }

        if (options.strongGlow) {
            int layers = options.bloom ? 20 : 14;
            for (int i = 1; i <= layers; i++) {
                float falloff = 1f - (i - 1) / (float) layers;
                float alpha = a * glowWave * (0.22f * easeOut(falloff));
                if (alpha <= 0.003f) {
                    break;
                }
                float expand = (0.015f + glowWave * 0.003f) * i;
                for (Box part : parts) {
                    Box glow = expand(part, expand);
                    RenderUtil.batchFilledBox(vc, matrix, glow, r, g, b, alpha * 0.68f);
                }
            }
        }

        if (options.pulse) {
            float pulse = pulse(seed, 3.0);
            float expandPrimary = 0.028f + pulse * 0.085f;
            float expandSecondary = 0.085f + pulse(seed + 0.7, 1.6f) * 0.14f;
            for (Box part : parts) {
                RenderUtil.batchOutlineBox(vc, matrix, expand(part, expandPrimary), r, g, b, a * (0.18f + pulse * 0.18f), Math.max(options.lineWidth * 0.7f, 0.65f));
                RenderUtil.batchFilledBox(vc, matrix, expand(part, expandSecondary), r, g, b, a * 0.06f);
            }
        }

        if (options.halo) {
            for (int i = 1; i <= 4; i++) {
                float alpha = a * orbitWave * (0.07f / i);
                float expand = 0.10f * i + pulse(seed + i, 1.1 + i * 0.2) * 0.05f;
                for (Box part : parts) {
                    RenderUtil.batchFilledBox(vc, matrix, expand(part, expand), r, g, b, alpha * 0.35f);
                }
            }
        }

        if (options.scanner) {
            float progress = 1f - pingPong(seed, 0.65);
            float fade = 0.18f + 0.82f * (float) Math.sin(progress * Math.PI);
            double scanY = baseY + height * progress;
            Box modelBounds = combine(parts);
            Box scanBox = new Box(modelBounds.minX, scanY - 0.045f, modelBounds.minZ, modelBounds.maxX, scanY + 0.045f, modelBounds.maxZ);
            RenderUtil.batchFilledBox(vc, matrix, scanBox, r, g, b, a * fade * 0.65f);
        }
    }

    private static Box expand(Box box, double amount) {
        return new Box(
                box.minX - amount, box.minY - amount, box.minZ - amount,
                box.maxX + amount, box.maxY + amount, box.maxZ + amount
        );
    }

    private static Box contract(Box box, double amount) {
        return new Box(
                box.minX + amount, box.minY + amount, box.minZ + amount,
                box.maxX - amount, box.maxY - amount, box.maxZ - amount
        );
    }

    private static Box centeredBox(double cx, double minY, double cz, double halfWidthX, double height, double halfWidthZ) {
        return new Box(
                cx - halfWidthX, minY, cz - halfWidthZ,
                cx + halfWidthX, minY + height, cz + halfWidthZ
        );
    }

    private static Box combine(Box[] boxes) {
        Box current = boxes[0];
        for (int i = 1; i < boxes.length; i++) {
            current = current.union(boxes[i]);
        }
        return current;
    }

    private static float easeOut(float value) {
        float clamped = clamp01(value);
        return 1f - (1f - clamped) * (1f - clamped);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float pingPong(double seed, double speed) {
        double t = System.currentTimeMillis() / 1000.0 * speed + seed;
        double fract = t - Math.floor(t);
        return (float) (fract < 0.5 ? fract * 2.0 : 2.0 - fract * 2.0);
    }

    private static float pulse(double seed, double speed) {
        return (float) ((Math.sin(System.currentTimeMillis() / 1000.0 * speed + seed * 3.1) + 1.0) * 0.5);
    }
}
