package de.glutenfreierkeks.gfm_recode.client.utils.thunderrender;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThunderBlockAnimationUtility {
    private static final Map<BlockRenderData, Long> blocks = new ConcurrentHashMap<>();

    private ThunderBlockAnimationUtility() {}

    public static void onRender(VertexConsumer vc, Matrix4f matrix, Vec3d cameraPos, BlockRenderMode filter) {
        blocks.forEach((animation, time) -> {
            if (System.currentTimeMillis() - time > 300f) {
                blocks.remove(animation);
            } else {
                animation.renderWithTime(System.currentTimeMillis() - time, vc, matrix, cameraPos, filter);
            }
        });
    }

    public static void renderBlock(BlockPos pos, Color lineColor, int lineWidth, Color fillColor, BlockAnimationMode animationMode, BlockRenderMode renderMode) {
        if (renderMode == BlockRenderMode.None) {
            return;
        }
        blocks.put(new BlockRenderData(pos, lineColor, lineWidth, fillColor, animationMode, renderMode), System.currentTimeMillis());
    }

    public static boolean isRendering(BlockPos pos) {
        return blocks.keySet().stream().anyMatch(blockRenderData -> blockRenderData.pos().equals(pos));
    }

        private record BlockRenderData(BlockPos pos, Color lineColor, int lineWidth, Color fillColor,
                                   BlockAnimationMode animationMode, BlockRenderMode renderMode) {
        void renderWithTime(Long time, VertexConsumer vc, Matrix4f matrix, Vec3d cameraPos, BlockRenderMode filter) {
            BlockRenderMode activeMode = filter == BlockRenderMode.All ? renderMode : filter;
            switch (animationMode) {
                case Static -> renderBox(vc, matrix, cameraPos, new Box(pos), activeMode, lineColor, lineWidth, fillColor);
                case Decrease -> {
                    float scale = 1 - time / 300f;
                    Box box = animatedPointBox(scale);
                    renderBox(vc, matrix, cameraPos, box, activeMode, lineColor, lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * (1f - time / 300f))));
                }
                case Fade -> renderBox(vc, matrix, cameraPos, new Box(pos), activeMode, ThunderRender2D.injectAlpha(lineColor, (int) (lineColor.getAlpha() * (1f - time / 300f))), lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * (1f - time / 300f))));
                case Fill -> {
                    float scale = time / 300f;
                    Box box = animatedPointBox(scale);
                    renderBox(vc, matrix, cameraPos, box, activeMode, lineColor, lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * (time / 300f))));
                }
                case Flash -> {
                    float scale = time > 100 ? 1 - (time - 100f) / 400f : time / 100f;
                    Box box = animatedPointBox(scale);
                    renderBox(vc, matrix, cameraPos, box, activeMode, lineColor, lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * scale)));
                }
                case Grow -> {
                    float scale = time / 300f;
                    Box box = new Box(pos.getX(), pos.getY() + scale, pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ() + 1);
                    renderBox(vc, matrix, cameraPos, box, activeMode, lineColor, lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * (time / 300f))));
                }
                case TNT -> {
                    float scale = time < 200 ? 1f : 1 + (time - 200f) / 400f;
                    Box box = animatedPointBox(scale);
                    renderBox(vc, matrix, cameraPos, box, activeMode, lineColor, lineWidth, ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * Math.min(1f, scale))));
                }
                case Pull -> {
                    float scale = time < 200 ? 1.5f - (time / 200f) * 0.5f : 1f;
                    renderBox(vc, matrix, cameraPos, animatedPointBox(scale), activeMode, lineColor, lineWidth, fillColor);
                }
                case Hover -> {
                    float scale = 1f + time / 1500f;
                    Color fadeLine = ThunderRender2D.injectAlpha(lineColor, (int) (lineColor.getAlpha() * (1f - time / 300f)));
                    Color fadeFill = ThunderRender2D.injectAlpha(fillColor, (int) (fillColor.getAlpha() * (1f - time / 300f)));
                    renderBox(vc, matrix, cameraPos, animatedPointBox(scale), activeMode, fadeLine, lineWidth, fadeFill);
                }
            }
        }

        private Box animatedPointBox(float scale) {
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
            return box.shrink(scale, scale, scale).offset(0.5 + scale * 0.5, 0.5 + scale * 0.5, 0.5 + scale * 0.5);
        }

        private static void renderBox(VertexConsumer vc, Matrix4f matrix, Vec3d cameraPos, Box box, BlockRenderMode renderMode, Color lineColor, int lineWidth, Color fillColor) {
            Box relativeBox = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            int thunderWidth = Math.max(3, lineWidth);
            if (renderMode == BlockRenderMode.All || renderMode == BlockRenderMode.Line) {
                ThunderRender3D.drawBoxOutline(vc, matrix, relativeBox.expand(0.025), ThunderRender2D.injectAlpha(lineColor, Math.min(255, lineColor.getAlpha())), thunderWidth);
                ThunderRender3D.drawBoxOutline(vc, matrix, relativeBox.expand(0.075), ThunderRender2D.injectAlpha(lineColor, Math.min(95, lineColor.getAlpha())), thunderWidth + 1);
            }
            if (renderMode == BlockRenderMode.All || renderMode == BlockRenderMode.Fill) {
                ThunderRender3D.drawFilledBox(vc, matrix, relativeBox, fillColor);
                ThunderRender3D.drawFilledBox(vc, matrix, relativeBox.expand(0.035), ThunderRender2D.injectAlpha(fillColor, Math.min(55, fillColor.getAlpha())));
            }
        }
    }

    public enum BlockRenderMode {
        Fill, Line, All, None
    }

    public enum BlockAnimationMode {
        Fade, Hover, Decrease, Static, Flash, Grow, Fill, TNT, Pull
    }
}
