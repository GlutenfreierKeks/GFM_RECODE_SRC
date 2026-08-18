package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.thunderrender.ThunderHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Comparator;

public class TESTHUD extends Module {
    private final EnumSetting<Layout> layout = register(new EnumSetting<>("Layout", "Thunder HUD layout", Layout.COMPACT));
    private final ColorSetting color = register(new ColorSetting("Color", "Thunder HUD color", 80, 185, 255, 230));
    private final BoolSetting watermark = register(new BoolSetting("Watermark", "Render watermark panel", true));
    private final BoolSetting arrayList = register(new BoolSetting("ArrayList", "Render enabled modules", true));
    private final BoolSetting stats = register(new BoolSetting("Stats", "Render stats panel", true));
    private final BoolSetting pulseDots = register(new BoolSetting("Pulse Dots", "Render Thunder pulse dots", true));
    private final IntSliderSetting x = register(new IntSliderSetting("X", "Panel X", 8, 0, 500));
    private final IntSliderSetting y = register(new IntSliderSetting("Y", "Panel Y", 8, 0, 500));

    public enum Layout { COMPACT, WIDE, MINIMAL }

    public TESTHUD() {
        super("TESTHUD", "ThunderHack styled test HUD", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (mc.player == null) {
            return;
        }

        Color c = new Color(color.getArgb(), true);
        float animation = (System.currentTimeMillis() % 1800L) / 1800f;
        int startX = x.getValue();
        int startY = y.getValue();
        int width = layout.getValue() == Layout.WIDE ? 190 : layout.getValue() == Layout.MINIMAL ? 92 : 136;

        if (watermark.getValue()) {
            ThunderHudRenderer.drawThunderPanel(context, startX, startY, width, 38, c, animation);
            ThunderHudRenderer.drawThunderText(context, "GFM THUNDER", "fps " + mc.getCurrentFps() + " | " + mc.player.getName().getString(), startX + 8, startY + 8, c);
            if (pulseDots.getValue()) {
                ThunderHudRenderer.drawPulseDots(context, startX + width - 42, startY + 19, 3, c, animation);
            }
        }

        if (stats.getValue()) {
            int statY = startY + 46;
            ThunderHudRenderer.drawThunderPanel(context, startX, statY, width, 48, c, 1f - animation);
            String coords = (int) mc.player.getX() + " " + (int) mc.player.getY() + " " + (int) mc.player.getZ();
            ThunderHudRenderer.drawThunderText(context, "POSITION", coords, startX + 8, statY + 8, c);
        }

        if (arrayList.getValue() && Gfm_recodeClient.modules != null) {
            int right = context.getScaledWindowWidth() - startX;
            int row = startY;
            var enabled = Gfm_recodeClient.modules.getAll().stream()
                    .filter(Module::isEnabled)
                    .sorted(Comparator.comparingInt(module -> -mc.textRenderer.getWidth(module.name)))
                    .toList();
            for (Module module : enabled) {
                String text = module.getDisplayInfo() == null ? module.name : module.name + " " + module.getDisplayInfo();
                int rowWidth = mc.textRenderer.getWidth(text) + 16;
                int rowX = right - rowWidth;
                ThunderHudRenderer.drawThunderPanel(context, rowX, row, rowWidth, 18, c, (animation + row * 0.01f) % 1f);
                context.drawText(mc.textRenderer, text, rowX + 7, row + 5, ThunderHudRenderer.argb(240, 244, 246, 255), false);
                row += 20;
                if (row > context.getScaledWindowHeight() - 24) {
                    break;
                }
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
