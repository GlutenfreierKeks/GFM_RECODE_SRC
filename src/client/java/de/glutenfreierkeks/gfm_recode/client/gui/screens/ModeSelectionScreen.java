package de.glutenfreierkeks.gfm_recode.client.gui.screens;

import de.glutenfreierkeks.gfm_recode.client.ClientMode;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.gui.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModeSelectionScreen extends Screen {

    private static final int ACCENT = 0xFFB06AFF;
    private static final int BG_PANEL = 0xF00A0814;
    private static final int TEXT_ON = 0xFFEEEEFF;
    private static final int TEXT_DIM = 0xFF554866;
    private static final int BG_HEADER = 0xF8120E1E;

    private static final StyleSpriteSource SMOOTH_FONT = new StyleSpriteSource.Font(Identifier.of("minecraft", "uniform"));
    private static Text s(String text) { return Text.literal(text).styled(style -> style.withFont(SMOOTH_FONT)); }

    public ModeSelectionScreen() {
        super(Text.literal("Mode Selection"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        int panelW = 400;
        int panelH = 180;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        RenderUtils.drawDropShadow(ctx, px, py, panelW, panelH, 8);
        RenderUtils.fillRoundedRect(ctx, px, py, panelW, panelH, 8, BG_PANEL);

        // Header
        RenderUtils.fillRoundedRect(ctx, px, py, panelW, 25, 8, BG_HEADER);
        ctx.fill(px, py + 10, px + panelW, py + 25, BG_HEADER);
        RenderUtils.drawBloomLine(ctx, px + 10, py + 24, panelW - 20, ACCENT, 0xFFD4AAFF);

        ctx.drawText(this.textRenderer, s("What mode do you want to choose?"), px + (panelW - this.textRenderer.getWidth(s("What mode do you want to choose?"))) / 2, py + 8, TEXT_ON, false);

        int btnW = 150;
        int btnH = 40;
        
        // Cheat Btn
        int cheatX = px + 30;
        int cheatY = py + 60;
        boolean cheatHov = RenderUtils.isHovered(mouseX, mouseY, cheatX, cheatY, btnW, btnH);
        RenderUtils.fillRoundedRect(ctx, cheatX, cheatY, btnW, btnH, 6, cheatHov ? 0xCC1E1630 : 0xFF0E0C18);
        if (cheatHov) ctx.fill(cheatX, cheatY, cheatX + btnW, cheatY + btnH, 0x22FFFFFF);
        ctx.drawText(this.textRenderer, s("Cheat"), cheatX + (btnW - this.textRenderer.getWidth(s("Cheat"))) / 2, cheatY + 16, TEXT_ON, false);

        // Macro Btn
        int macroX = px + panelW - btnW - 30;
        int macroY = py + 60;
        boolean macroHov = RenderUtils.isHovered(mouseX, mouseY, macroX, macroY, btnW, btnH);
        RenderUtils.fillRoundedRect(ctx, macroX, macroY, btnW, btnH, 6, macroHov ? 0xCC1E1630 : 0xFF0E0C18);
        if (macroHov) ctx.fill(macroX, macroY, macroX + btnW, macroY + btnH, 0x22FFFFFF);
        ctx.drawText(this.textRenderer, s("Macro"), macroX + (btnW - this.textRenderer.getWidth(s("Macro"))) / 2, macroY + 16, TEXT_ON, false);

        // Description
        int descY = py + 120;
        ctx.drawText(this.textRenderer, s("Macro mode disables modules such as:"), px + (panelW - this.textRenderer.getWidth(s("Macro mode disables modules such as:"))) / 2, descY, TEXT_DIM, false);
        ctx.drawText(this.textRenderer, s("StorageESP, TriggerBot, AimAssist and others."), px + (panelW - this.textRenderer.getWidth(s("StorageESP, TriggerBot, AimAssist and others."))) / 2, descY + 15, TEXT_DIM, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click c, boolean d) {
        double mouseX = c.x();
        double mouseY = c.y();
        int button = c.button();
        
        if (button == 0) {
            int panelW = 400;
            int panelH = 180;
            int px = (this.width - panelW) / 2;
            int py = (this.height - panelH) / 2;
            int btnW = 150;
            int btnH = 40;

            int cheatX = px + 30;
            int cheatY = py + 60;
            if (RenderUtils.isHovered((int)mouseX, (int)mouseY, cheatX, cheatY, btnW, btnH)) {
                selectMode(ClientMode.CHEAT);
                return true;
            }

            int macroX = px + panelW - btnW - 30;
            int macroY = py + 60;
            if (RenderUtils.isHovered((int)mouseX, (int)mouseY, macroX, macroY, btnW, btnH)) {
                selectMode(ClientMode.MACRO);
                return true;
            }
        }
        return super.mouseClicked(c, d);
    }

    private void selectMode(ClientMode mode) {
        Gfm_recodeClient.currentMode = mode;
        Gfm_recodeClient.LOG.info("Selected mode: " + mode);
        MinecraftClient.getInstance().setScreen(new de.glutenfreierkeks.gfm_recode.client.gui.web.HtmlGuiScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(null);
            return true;
        }
        return super.keyPressed(e);
    }
}
