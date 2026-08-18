package de.glutenfreierkeks.gfm_recode.client.gui.screens;

import de.glutenfreierkeks.gfm_recode.client.config.ConfigManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {

    private final Screen parent;
    private final List<String> configs = new ArrayList<>();
    private String selectedConfig = ConfigManager.getCurrentConfigName();
    private String nameBuffer = "";
    private boolean editingName = false;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Configs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshConfigs();
        nameBuffer = selectedConfig;
    }

    private void refreshConfigs() {
        configs.clear();
        configs.addAll(ConfigManager.listConfigs());
        if (!configs.contains(selectedConfig)) {
            selectedConfig = ConfigManager.getCurrentConfigName();
        }
        if (!configs.contains(selectedConfig) && !configs.isEmpty()) {
            selectedConfig = configs.getFirst();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        // Hintergrund
        ctx.fill(0, 0, this.width, this.height, 0xDD0A0814);

        int panelX = this.width / 2 - 180;
        int panelY = this.height / 2 - 110;
        int panelW = 360;
        int panelH = 220;

        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE0A0814);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, 0xFF161020);
        ctx.drawText(this.textRenderer, "Configs", panelX + 8, panelY + 7, 0xFFFFFFFF, false);
        ctx.drawText(this.textRenderer, "Current: " + ConfigManager.getCurrentConfigName(), panelX + 220, panelY + 7, 0xFFB8A9D7, false);

        int listX = panelX + 8;
        int listY = panelY + 32;
        int listW = 170;
        int listH = 150;
        ctx.fill(listX, listY, listX + listW, listY + listH, 0xCC120D1D);

        int rowY = listY + 4;
        for (String config : configs) {
            boolean selected = config.equals(selectedConfig);
            ctx.fill(listX + 4, rowY - 2, listX + listW - 4, rowY + 10, selected ? 0xFFB06AFF : 0x552A2038);
            ctx.drawText(this.textRenderer, config, listX + 8, rowY, selected ? 0xFF101010 : 0xFFFFFFFF, false);
            rowY += 14;
            if (rowY > listY + listH - 12) break;
        }

        int editorX = panelX + 190;
        int fieldY = panelY + 36;
        ctx.drawText(this.textRenderer, "Name", editorX, fieldY - 12, 0xFFFFFFFF, false);
        ctx.fill(editorX, fieldY, editorX + 150, fieldY + 16, editingName ? 0xFF2A2038 : 0xCC120D1D);
        String shown = editingName ? nameBuffer + "_" : nameBuffer;
        ctx.drawText(this.textRenderer, shown, editorX + 4, fieldY + 4, 0xFFFFFFFF, false);

        renderButton(ctx, editorX, panelY + 68, 70, 18, "Save", mouseX, mouseY);
        renderButton(ctx, editorX + 80, panelY + 68, 70, 18, "Load", mouseX, mouseY);
        renderButton(ctx, editorX, panelY + 92, 70, 18, "Delete", mouseX, mouseY);
        renderButton(ctx, editorX + 80, panelY + 92, 70, 18, "Refresh", mouseX, mouseY);
        renderButton(ctx, editorX, panelY + 124, 70, 18, "Import", mouseX, mouseY);
        renderButton(ctx, editorX + 80, panelY + 124, 70, 18, "Export", mouseX, mouseY);
        renderButton(ctx, editorX, panelY + 156, 150, 18, "Back", mouseX, mouseY);

        ctx.drawText(this.textRenderer, "Left click list to select.", editorX, panelY + 190, 0xFFB8A9D7, false);
        ctx.drawText(this.textRenderer, "Import/Export use file dialogs.", editorX, panelY + 202, 0xFF8C7AA8, false);
    }

    private void renderButton(DrawContext ctx, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = hovered ? 0xFFB06AFF : 0xCC2A2038;
        int fg = hovered ? 0xFF101010 : 0xFFFFFFFF;
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawText(this.textRenderer, text, x + (w - this.textRenderer.getWidth(text)) / 2, y + 5, fg, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();

        int panelX = this.width / 2 - 180;
        int panelY = this.height / 2 - 110;
        int listX = panelX + 8;
        int listY = panelY + 32;
        int listW = 170;
        int listH = 150;
        int editorX = panelX + 190;
        int fieldY = panelY + 36;

        editingName = mx >= editorX && mx <= editorX + 150 && my >= fieldY && my <= fieldY + 16;
        if (editingName) return true;

        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            int index = (my - (listY + 4)) / 14;
            if (index >= 0 && index < configs.size()) {
                selectedConfig = configs.get(index);
                nameBuffer = selectedConfig;
                return true;
            }
        }

        if (buttonHit(mx, my, editorX, panelY + 68, 70, 18)) {
            ConfigManager.saveConfig(nameBuffer);
            selectedConfig = ConfigManager.getCurrentConfigName();
            refreshConfigs();
            return true;
        }
        if (buttonHit(mx, my, editorX + 80, panelY + 68, 70, 18) && selectedConfig != null) {
            ConfigManager.loadConfig(selectedConfig);
            nameBuffer = selectedConfig;
            return true;
        }
        if (buttonHit(mx, my, editorX, panelY + 92, 70, 18) && selectedConfig != null) {
            ConfigManager.deleteConfig(selectedConfig);
            refreshConfigs();
            nameBuffer = ConfigManager.getCurrentConfigName();
            selectedConfig = ConfigManager.getCurrentConfigName();
            return true;
        }
        if (buttonHit(mx, my, editorX + 80, panelY + 92, 70, 18)) {
            refreshConfigs();
            return true;
        }
        if (buttonHit(mx, my, editorX, panelY + 124, 70, 18)) {
            String imported = ConfigManager.importConfigWithDialog();
            if (imported != null) {
                selectedConfig = imported;
                nameBuffer = imported;
                refreshConfigs();
            }
            return true;
        }
        if (buttonHit(mx, my, editorX + 80, panelY + 124, 70, 18) && selectedConfig != null) {
            ConfigManager.exportConfigWithDialog(selectedConfig);
            return true;
        }
        if (buttonHit(mx, my, editorX, panelY + 156, 150, 18)) {
            close();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editingName) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !nameBuffer.isEmpty()) {
                nameBuffer = nameBuffer.substring(0, nameBuffer.length() - 1);
                return true;
            }
            if (input.isEnter()) {
                editingName = false;
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editingName) {
            nameBuffer += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean buttonHit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
