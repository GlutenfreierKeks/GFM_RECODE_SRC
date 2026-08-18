package de.glutenfreierkeks.gfm_recode.client.gui.imgui;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public class Menu {
    private static final Menu INSTANCE = new Menu();

    private boolean opened = false;
    private Module.Category selected = Module.Category.RENDER;
    private int scrollOffset = 0;

    private Menu() {
    }

    public static Menu getInstance() {
        return INSTANCE;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public void toggle() {
        if (opened) {
            MinecraftClient.getInstance().setScreen(null);
            closeMenu();
        } else {
            opened = true;
            MinecraftClient.getInstance().setScreen(new MenuScreen());
        }
    }

    public void closeMenu() {
        opened = false;
        TetraCustomImGui.clearMaps();
    }

    public void draw(float width, float height, float scale) {
        if (!opened || Gfm_recodeClient.modules == null) return;

        float fontScale = scale;
        TetraCustomImGui.applyTetraTheme(scale);

        int flags = ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoMove
                | (ImGui.getIO().getKeyShift() ? ImGuiWindowFlags.NoScrollWithMouse : 0);

        ImGui.begin(Gfm_recodeClient.NAME, new ImBoolean(true), flags);
        ImGui.setWindowFontScale(fontScale);

        float x = 900f * scale;
        float y = 600f * scale;
        ImGui.setWindowSize(x, y);
        ImGui.setWindowPos(width / 2f - x / 2f, height / 2f - y / 2f);

        float totalWidth = ImGui.getContentRegionAvailX();
        float totalHeight = ImGui.getContentRegionAvailY();
        float leftWidth = totalWidth * 0.28f;

        ImGui.beginChild("##left_panel", leftWidth, totalHeight, false);
        ImGui.setWindowFontScale(fontScale * 2.5f);
        String title = Gfm_recodeClient.NAME;
        float center = ImGui.getContentRegionAvailX() / 2f - ImGui.calcTextSizeX(title) / 2f;
        ImGui.setCursorPosX(center);
        ImGui.setCursorPosY(ImGui.calcTextSizeY("I") / 4f);
        ImGui.text(title);
        ImGui.setWindowFontScale(fontScale);
        ImGui.dummy(10f * scale, 10f * scale);

        ImGui.setWindowFontScale(fontScale * 1.5f);
        for (Module.Category category : Module.Category.values()) {
            if (TetraCustomImGui.navButton(category.displayName, selected == category,
                    ImGui.getContentRegionAvailX(), 40f * scale, scale)) {
                selected = category;
            }
        }
        ImGui.setWindowFontScale(fontScale);

        ImGui.setCursorPosY(totalHeight - ImGui.calcTextSizeY("I"));
        ImGui.text("gfm recode");
        ImGui.endChild();

        ImGui.sameLine();

        float dividerX = ImGui.getCursorScreenPosX();
        float topY = ImGui.getCursorScreenPosY();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addLine(dividerX, topY, dividerX, topY + totalHeight,
                ImGui.getColorU32(0.35f, 0.38f, 0.45f, 1.0f), 1.0f);

        ImGui.sameLine();

        ImGui.beginChild("##right_panel", 0, totalHeight, false);
        ImGui.setWindowFontScale(fontScale * 2f);
        ImGui.dummy(5f * scale, 5f * scale);
        ImGui.indent(20f * scale);
        ImGui.text(selected.displayName);
        ImGui.unindent(20f * scale);
        ImGui.setWindowFontScale(fontScale);
        ImGui.dummy(5f * scale, 5f * scale);

        for (Module module : Gfm_recodeClient.modules.getByCategory(selected)) {
            ImGui.setWindowFontScale(fontScale * 1.5f);
            ImGui.text(module.name);
            ImGui.setWindowFontScale(fontScale);
            ImGui.separator();

            boolean enabled = TetraCustomImGui.toggleSwitch("Enabled##module_" + module.name, module.isEnabled(), scale);
            if (enabled != module.isEnabled()) {
                module.toggle();
            }
            ImGui.dummy(0f, 2f * scale);

            for (Setting<?> setting : module.getSettings()) {
                renderSetting(module, setting, scale);
                ImGui.separator();
            }

            ImGui.dummy(3f * scale, 3f * scale);
        }

        ImGui.endChild();
        ImGui.end();
    }

    public void drawMinecraft(DrawContext context, int width, int height, int mouseX, int mouseY) {
        if (!opened || Gfm_recodeClient.modules == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer text = client.textRenderer;

        int windowWidth = Math.min(900, width - 40);
        int windowHeight = Math.min(600, height - 40);
        int x = (width - windowWidth) / 2;
        int y = (height - windowHeight) / 2;
        int leftWidth = Math.round(windowWidth * 0.28f);
        int rightX = x + leftWidth + 1;
        int rightWidth = windowWidth - leftWidth - 1;

        fillRoundish(context, x, y, windowWidth, windowHeight, 0xF5091A1F);
        context.fill(x, y, x + windowWidth, y + windowHeight, 0xCC172026);
        context.fill(x + leftWidth, y + 12, x + leftWidth + 1, y + windowHeight - 12, 0xCC59616F);

        drawCentered(context, text, Gfm_recodeClient.NAME, x + leftWidth / 2, y + 22, 0xFFF2F5FA);

        int categoryY = y + 58;
        for (Module.Category category : Module.Category.values()) {
            boolean active = category == selected;
            boolean hovered = inside(mouseX, mouseY, x + 1, categoryY, leftWidth - 2, 28);
            int bg = active ? 0x1FFFFFFF : hovered ? 0x12FFFFFF : 0x00000000;
            if (bg != 0) context.fill(x + 1, categoryY, x + leftWidth - 1, categoryY + 28, bg);
            if (active || hovered) {
                context.fill(x + 1, categoryY, x + (active ? 5 : 3), categoryY + 28, active ? 0xFF73B8FF : 0xFF474F61);
            }
            context.drawTextWithShadow(text, category.displayName, x + 14 + (active ? 3 : hovered ? 1 : 0), categoryY + 10, 0xFFF2F5FA);
            categoryY += 32;
        }

        context.drawTextWithShadow(text, "gfm recode", x + 12, y + windowHeight - 18, 0xAAFFFFFF);

        context.drawTextWithShadow(text, selected.displayName, rightX + 22, y + 22, 0xFFF2F5FA);

        int clipTop = y + 54;
        int clipBottom = y + windowHeight - 12;
        context.enableScissor(rightX + 8, clipTop, x + windowWidth - 10, clipBottom);

        int cy = clipTop + 4 - scrollOffset;
        List<Module> modules = Gfm_recodeClient.modules.getByCategory(selected);
        for (Module module : modules) {
            if (cy > clipBottom) break;
            if (cy + 86 >= clipTop) {
                context.drawTextWithShadow(text, module.name, rightX + 22, cy + 2, 0xFFF2F5FA);
                cy += 15;
                context.fill(rightX + 18, cy, x + windowWidth - 24, cy + 1, 0xFF404650);
                cy += 8;

                drawToggleRow(context, text, "Enabled", module.isEnabled(), rightX + 22, cy, rightWidth - 46);
                cy += 28;

                for (Setting<?> setting : module.getSettings()) {
                    if (cy > clipBottom) break;
                    drawSettingRow(context, text, setting, rightX + 22, cy, rightWidth - 46);
                    cy += 28;
                }
                cy += 8;
            } else {
                cy += 51 + module.getSettings().size() * 28;
            }
        }

        context.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int width, int height) {
        if (!opened || Gfm_recodeClient.modules == null || button != 0) return false;

        int windowWidth = Math.min(900, width - 40);
        int windowHeight = Math.min(600, height - 40);
        int x = (width - windowWidth) / 2;
        int y = (height - windowHeight) / 2;
        int leftWidth = Math.round(windowWidth * 0.28f);
        int rightX = x + leftWidth + 1;
        int rightWidth = windowWidth - leftWidth - 1;

        int categoryY = y + 58;
        for (Module.Category category : Module.Category.values()) {
            if (inside(mouseX, mouseY, x + 1, categoryY, leftWidth - 2, 28)) {
                selected = category;
                scrollOffset = 0;
                return true;
            }
            categoryY += 32;
        }

        int clipTop = y + 54;
        int clipBottom = y + windowHeight - 12;
        int cy = clipTop + 4 - scrollOffset;
        for (Module module : Gfm_recodeClient.modules.getByCategory(selected)) {
            cy += 23;
            if (inside(mouseX, mouseY, rightX + 22, cy, rightWidth - 46, 22)) {
                module.toggle();
                return true;
            }
            cy += 28;

            for (Setting<?> setting : module.getSettings()) {
                if (inside(mouseX, mouseY, rightX + 22, cy, rightWidth - 46, 22) && cy >= clipTop && cy <= clipBottom) {
                    clickSetting(setting, mouseX, rightX + 22, rightWidth - 46);
                    return true;
                }
                cy += 28;
            }
            cy += 8;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int width, int height) {
        if (!opened) return false;
        int windowWidth = Math.min(900, width - 40);
        int windowHeight = Math.min(600, height - 40);
        int x = (width - windowWidth) / 2;
        int y = (height - windowHeight) / 2;
        int leftWidth = Math.round(windowWidth * 0.28f);

        if (!inside(mouseX, mouseY, x + leftWidth, y, windowWidth - leftWidth, windowHeight)) return false;
        scrollOffset = Math.max(0, scrollOffset - (int) Math.round(verticalAmount * 24));
        return true;
    }

    private void renderSetting(Module module, Setting<?> setting, float scale) {
        String id = "##" + module.name + "_" + setting.name;
        switch (setting.getType()) {
            case BOOL -> {
                BoolSetting bool = (BoolSetting) setting;
                boolean value = TetraCustomImGui.toggleSwitch(setting.name + id, bool.getValue(), scale);
                if (value != bool.getValue()) bool.setValue(value);
            }
            case SLIDER_INT -> {
                IntSliderSetting slider = (IntSliderSetting) setting;
                int value = TetraCustomImGui.intSlider(setting.name + id, slider.getValue(), slider.getMin(), slider.getMax(), scale);
                if (value != slider.getValue()) slider.setValue(value);
            }
            case SLIDER_DOUBLE -> {
                DoubleSliderSetting slider = (DoubleSliderSetting) setting;
                float value = TetraCustomImGui.floatSlider(setting.name + id, slider.getValue().floatValue(),
                        (float) slider.getMin(), (float) slider.getMax(), scale);
                if (Double.compare(value, slider.getValue()) != 0) slider.setValue((double) value);
            }
            case ENUM -> renderEnum((EnumSetting<?>) setting, id, scale);
            case STRING -> {
                StringSetting string = (StringSetting) setting;
                String value = TetraCustomImGui.textBox(setting.name + id, string.getValue(), scale);
                if (!value.equals(string.getValue())) string.setValue(value);
            }
            case COLOR -> {
                ColorSetting color = (ColorSetting) setting;
                float[] value = {
                        color.getR() / 255f,
                        color.getG() / 255f,
                        color.getB() / 255f,
                        color.getA() / 255f
                };
                TetraCustomImGui.colorPicker(setting.name + id, value, scale);
                int r = Math.clamp(Math.round(value[0] * 255f), 0, 255);
                int g = Math.clamp(Math.round(value[1] * 255f), 0, 255);
                int b = Math.clamp(Math.round(value[2] * 255f), 0, 255);
                int a = Math.clamp(Math.round(value[3] * 255f), 0, 255);
                if (r != color.getR() || g != color.getG() || b != color.getB() || a != color.getA()) {
                    color.setRGB(r, g, b, a);
                }
            }
            case KEYBIND -> renderKeybind((KeybindSetting) setting, id, scale);
            default -> ImGui.textDisabled(setting.name + ": unsupported");
        }
    }

    private <E extends Enum<E>> void renderEnum(EnumSetting<E> setting, String id, float scale) {
        E value = TetraCustomImGui.enumDropdown(setting.name + id, setting.getValue(), setting.getValues(), scale);
        if (value != setting.getValue()) {
            setting.setValue(value);
        }
    }

    private void renderKeybind(KeybindSetting setting, String id, float scale) {
        String label = setting.isListening()
                ? setting.name + ": Press a key" + id
                : setting.name + ": " + setting.getKeyName() + id;
        if (TetraCustomImGui.button(label, scale)) {
            setting.startListening();
        }
    }

    private void drawSettingRow(DrawContext context, TextRenderer text, Setting<?> setting, int x, int y, int width) {
        switch (setting.getType()) {
            case BOOL -> drawToggleRow(context, text, setting.name, ((BoolSetting) setting).getValue(), x, y, width);
            case SLIDER_INT -> {
                IntSliderSetting slider = (IntSliderSetting) setting;
                drawSliderRow(context, text, setting.name, slider.getValue(), slider.getMin(), slider.getMax(), x, y, width);
            }
            case SLIDER_DOUBLE -> {
                DoubleSliderSetting slider = (DoubleSliderSetting) setting;
                drawSliderRow(context, text, setting.name, slider.getValue().floatValue(), (float) slider.getMin(), (float) slider.getMax(), x, y, width);
            }
            case ENUM -> drawFieldRow(context, text, setting.name, ((EnumSetting<?>) setting).getValue().name(), x, y, width);
            case STRING -> drawFieldRow(context, text, setting.name, ((StringSetting) setting).getValue(), x, y, width);
            case COLOR -> {
                ColorSetting color = (ColorSetting) setting;
                drawFieldRow(context, text, setting.name, String.format("#%02X%02X%02X", color.getR(), color.getG(), color.getB()), x, y, width);
                context.fill(x + 168, y + 5, x + 184, y + 21, color.getArgb());
            }
            case KEYBIND -> {
                KeybindSetting key = (KeybindSetting) setting;
                drawFieldRow(context, text, setting.name, key.isListening() ? "Press a key" : key.getKeyName(), x, y, width);
            }
            default -> drawFieldRow(context, text, setting.name, "unsupported", x, y, width);
        }
    }

    private void drawToggleRow(DrawContext context, TextRenderer text, String label, boolean value, int x, int y, int width) {
        context.drawTextWithShadow(text, label, x, y + 7, 0xFFF2F5FA);
        int switchW = 42;
        int switchH = 22;
        int switchX = x + width - switchW;
        int bg = value ? 0xFF73B8FF : 0x22FFFFFF;
        context.fill(switchX, y, switchX + switchW, y + switchH, bg);
        context.fill(switchX + (value ? 22 : 3), y + 3, switchX + (value ? 39 : 20), y + 19, 0xFFF2F5FA);
    }

    private void drawFieldRow(DrawContext context, TextRenderer text, String label, String value, int x, int y, int width) {
        context.drawTextWithShadow(text, label, x, y + 7, 0xFFF2F5FA);
        int fieldX = x + 165;
        int fieldW = Math.max(80, width - 190);
        context.fill(fieldX, y, fieldX + fieldW, y + 22, 0x14FFFFFF);
        context.fill(fieldX, y, fieldX + fieldW, y + 1, 0x22FFFFFF);
        context.drawTextWithShadow(text, trimToWidth(text, value, fieldW - 12), fieldX + 8, y + 7, 0xFFF2F5FA);
    }

    private void drawSliderRow(DrawContext context, TextRenderer text, String label, float value, float min, float max, int x, int y, int width) {
        context.drawTextWithShadow(text, label, x, y + 7, 0xFFF2F5FA);
        int sliderX = x + 195;
        int sliderW = Math.max(80, width - 245);
        int sliderY = y + 10;
        float t = Math.clamp((value - min) / (max - min), 0f, 1f);
        context.drawTextWithShadow(text, formatNumber(min), sliderX - 35, y + 7, 0x88FFFFFF);
        context.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 4, 0x18FFFFFF);
        context.fill(sliderX, sliderY, sliderX + Math.round(sliderW * t), sliderY + 4, 0xFF73B8FF);
        int knobX = sliderX + Math.round(sliderW * t);
        context.fill(knobX - 4, sliderY - 4, knobX + 4, sliderY + 8, 0xFFF2F5FA);
        context.drawTextWithShadow(text, formatNumber(value), sliderX + sliderW + 8, y + 7, 0xFFF2F5FA);
    }

    private void clickSetting(Setting<?> setting, double mouseX, int x, int width) {
        switch (setting.getType()) {
            case BOOL -> {
                BoolSetting bool = (BoolSetting) setting;
                bool.setValue(!bool.getValue());
            }
            case SLIDER_INT -> {
                IntSliderSetting slider = (IntSliderSetting) setting;
                int sliderX = x + 195;
                int sliderW = Math.max(80, width - 245);
                float t = Math.clamp((float) ((mouseX - sliderX) / sliderW), 0f, 1f);
                slider.setValue(slider.getMin() + Math.round(t * (slider.getMax() - slider.getMin())));
            }
            case SLIDER_DOUBLE -> {
                DoubleSliderSetting slider = (DoubleSliderSetting) setting;
                int sliderX = x + 195;
                int sliderW = Math.max(80, width - 245);
                float t = Math.clamp((float) ((mouseX - sliderX) / sliderW), 0f, 1f);
                slider.setValue(slider.getMin() + t * (slider.getMax() - slider.getMin()));
            }
            case ENUM -> cycleEnum((EnumSetting<?>) setting);
            case KEYBIND -> ((KeybindSetting) setting).startListening();
            default -> {
            }
        }
    }

    private <E extends Enum<E>> void cycleEnum(EnumSetting<E> setting) {
        E[] values = setting.getValues();
        int next = (setting.getValue().ordinal() + 1) % values.length;
        setting.setValue(values[next]);
    }

    private static void fillRoundish(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 6, y, x + width - 6, y + height, color);
        context.fill(x, y + 6, x + width, y + height - 6, color);
    }

    private static void drawCentered(DrawContext context, TextRenderer text, String value, int cx, int y, int color) {
        context.drawTextWithShadow(text, value, cx - text.getWidth(value) / 2, y, color);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static String trimToWidth(TextRenderer text, String value, int width) {
        if (text.getWidth(value) <= width) return value;
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && text.getWidth(value.substring(0, end) + ellipsis) > width) end--;
        return value.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static String formatNumber(float value) {
        return Math.abs(value - Math.round(value)) < 0.001f ? String.valueOf(Math.round(value)) : String.format("%.2f", value);
    }
}
