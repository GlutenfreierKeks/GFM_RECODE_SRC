package de.glutenfreierkeks.gfm_recode.client.gui.imgui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;

public class TetraCustomImGui {
    private static final Map<Integer, Float> buttonHoverAnim = new HashMap<>();
    private static final Map<Integer, Float> buttonClickAnim = new HashMap<>();
    private static final Map<Integer, Float> colorHoverAnim = new HashMap<>();
    private static final Map<Integer, Float> colorOpenAnim = new HashMap<>();
    private static final Map<Integer, Float> colorBtnAnim = new HashMap<>();
    private static final Map<Integer, Float> dropdownHoverAnim = new HashMap<>();
    private static final Map<Integer, Float> dropdownOpenAnim = new HashMap<>();
    private static final Map<Integer, Float> dropdownBtnAnim = new HashMap<>();
    private static final Map<Integer, ImString> textBuffers = new HashMap<>();
    private static final Map<Integer, Float> textHoverAnim = new HashMap<>();
    private static final Map<Integer, Float> textFocusAnim = new HashMap<>();
    private static final Map<Integer, Float> clearBtnAnim = new HashMap<>();
    private static final Map<Integer, Float> floatSliderAnimations = new HashMap<>();
    private static final Map<Integer, Float> floatHoverAnimations = new HashMap<>();
    private static final Map<Integer, Float> floatBadgeAnimations = new HashMap<>();
    private static final Map<Integer, Float> intSliderAnimations = new HashMap<>();
    private static final Map<Integer, Float> intHoverAnimations = new HashMap<>();
    private static final Map<Integer, Float> intBadgeAnimations = new HashMap<>();
    private static final Map<Integer, Float> toggleAnimations = new HashMap<>();
    private static final Map<Integer, Float> navHoverAnimations = new HashMap<>();
    private static final Map<Integer, Float> navSelectAnimations = new HashMap<>();

    private TetraCustomImGui() {
    }

    public static void clearMaps() {
        buttonHoverAnim.clear();
        buttonClickAnim.clear();
        colorHoverAnim.clear();
        colorOpenAnim.clear();
        colorBtnAnim.clear();
        dropdownHoverAnim.clear();
        dropdownOpenAnim.clear();
        dropdownBtnAnim.clear();
        textBuffers.clear();
        textHoverAnim.clear();
        textFocusAnim.clear();
        clearBtnAnim.clear();
        floatSliderAnimations.clear();
        floatHoverAnimations.clear();
        floatBadgeAnimations.clear();
        intSliderAnimations.clear();
        intHoverAnimations.clear();
        intBadgeAnimations.clear();
        toggleAnimations.clear();
        navHoverAnimations.clear();
        navSelectAnimations.clear();
    }

    public static boolean button(String label, float scale) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float width = ImGui.getContentRegionAvailX() - 10f * scale;
        float height = 24f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);

        ImGui.invisibleButton(label, width, height);
        boolean hovered = ImGui.isItemHovered();
        boolean clicked = ImGui.isItemClicked();

        float hoverAnim = approach(buttonHoverAnim, id, hovered ? 1f : 0f, 0.15f);
        float clickAnim = approach(buttonClickAnim, id, clicked ? 1f : 0f, 0.25f);

        int bgColor = ImGui.getColorU32(1f, 1f, 1f, 0.05f + 0.05f * hoverAnim);
        if (clicked || clickAnim > 0.25f) {
            bgColor = ImGui.getColorU32(ImGuiCol.CheckMark);
        }

        drawList.addRectFilled(pos.x, pos.y, pos.x + width, pos.y + height, bgColor, 8f * scale);
        drawList.addRect(pos.x, pos.y, pos.x + width, pos.y + height,
                ImGui.getColorU32(1f, 1f, 1f, 0.08f + 0.15f * hoverAnim), 8f * scale);

        ImVec2 textSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(pos.x + (width - textSize.x) * 0.5f,
                pos.y + (height - textSize.y) * 0.5f,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);
        return clicked;
    }

    public static boolean navButton(String label, boolean selected, float width, float height, float scale) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);

        boolean hovered = ImGui.isMouseHoveringRect(pos.x, pos.y, pos.x + width, pos.y + height);
        boolean clicked = hovered && ImGui.isMouseClicked(0);
        float hoverAnim = approach(navHoverAnimations, id, hovered ? 1f : 0f, 0.50f);
        float selectAnim = approach(navSelectAnimations, id, selected ? 1f : 0f, 0.50f);

        float bgAlpha = 0.03f * hoverAnim + 0.06f * selectAnim;
        if (bgAlpha > 0.001f) {
            drawList.addRectFilled(pos.x, pos.y, pos.x + width, pos.y + height,
                    ImGui.getColorU32(1f, 1f, 1f, bgAlpha), 0f);
        }

        float barWidth = 2f * hoverAnim * scale + 3f * selectAnim * scale;
        float barAlpha = Math.max(hoverAnim * 0.7f, selectAnim);
        if (barWidth > 0.01f) {
            ImVec4 bar = ImGui.colorConvertU32ToFloat4(selected
                    ? ImGui.getColorU32(ImGuiCol.CheckMark)
                    : ImGui.getColorU32(ImGuiCol.HeaderHovered));
            drawList.addRectFilled(pos.x, pos.y, pos.x + barWidth, pos.y + height,
                    ImGui.getColorU32(bar.x, bar.y, bar.z, barAlpha), 0f);
        }

        ImVec2 textSize = ImGui.calcTextSize(label);
        float textOffset = (2f * hoverAnim + 4f * selectAnim) * scale;
        drawList.addText(pos.x + 12f * scale + textOffset, pos.y + (height - textSize.y) * 0.5f,
                ImGui.getColorU32(ImGuiCol.Text), label);

        ImGui.invisibleButton("##" + label + "_nav", width, height);
        return clicked;
    }

    public static boolean toggleSwitch(String label, boolean value, float scale) {
        float width = ImGui.getContentRegionAvailX() - 10f * scale;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float switchWidth = 42f * scale;
        float switchHeight = 22f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        float switchX = pos.x + width - switchWidth;
        float switchY = pos.y;

        boolean hovered = ImGui.isMouseHoveringRect(switchX, switchY, switchX + switchWidth, switchY + switchHeight);
        boolean clicked = hovered && ImGui.isMouseClicked(0);
        if (clicked) value = !value;

        float anim = approach(toggleAnimations, id, value ? 1f : 0f, 0.15f);
        int bgColor = value
                ? ImGui.getColorU32(ImGuiCol.CheckMark)
                : ImGui.getColorU32(1f, 1f, 1f, hovered ? 0.10f : 0.05f);

        String drawnLabel = visibleLabel(label);
        ImVec2 textSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(pos.x, pos.y + (switchHeight - textSize.y) * 0.5f,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);
        drawList.addRectFilled(switchX, switchY, switchX + switchWidth, switchY + switchHeight, bgColor, switchHeight / 2f);

        float padding = 3f * scale;
        float knobRadius = switchHeight / 2f - padding;
        float knobX = switchX + padding + knobRadius + (switchWidth - padding * 2f - knobRadius * 2f) * anim;
        drawList.addCircleFilled(knobX, switchY + switchHeight / 2f, knobRadius, ImGui.getColorU32(ImGuiCol.Text));

        ImGui.invisibleButton(label, width, switchHeight);
        return value;
    }

    public static void colorPicker(String label, float[] color, float scale) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 24f * scale;
        float spacing = 165f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);
        float x = pos.x;
        float y = pos.y;
        float fieldX = x + spacing;
        float fieldWidth = 395f * scale;

        ImGui.setCursorScreenPos(new ImVec2(fieldX, y));
        ImGui.invisibleButton("##color_" + id, fieldWidth, height);
        boolean hovered = ImGui.isItemHovered();
        boolean clicked = ImGui.isItemClicked();
        if (clicked && !ImGui.isPopupOpen("##popup_" + id)) ImGui.openPopup("##popup_" + id);

        float hoverAnim = approach(colorHoverAnim, id, hovered ? 1f : 0f, 0.15f);
        boolean open = ImGui.isPopupOpen("##popup_" + id);
        float openAnim = approach(colorOpenAnim, id, open ? 1f : 0f, 0.2f);

        ImVec2 labelSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(x, y + (height - labelSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);
        drawList.addRectFilled(fieldX, y, fieldX + fieldWidth, y + height,
                open ? ImGui.getColorU32(1f, 1f, 1f, 0f) : ImGui.getColorU32(1f, 1f, 1f, 0.05f + 0.05f * hoverAnim),
                height / 2f);
        drawList.addRect(fieldX, y, fieldX + fieldWidth, y + height,
                ImGui.getColorU32(1f, 1f, 1f, 0.08f + 0.15f * openAnim), height / 2f);

        float previewSize = height - 8f * scale;
        float previewX = fieldX + 6f * scale;
        float previewY = y + (height - previewSize) * 0.5f;
        drawList.addRectFilled(previewX, previewY, previewX + previewSize, previewY + previewSize,
                ImGui.getColorU32(color[0], color[1], color[2], color.length > 3 ? color[3] : 1f), previewSize / 3f);
        drawList.addRect(previewX, previewY, previewX + previewSize, previewY + previewSize,
                ImGui.getColorU32(1f, 1f, 1f, 0.15f), previewSize / 3f);

        String valueText = String.format("#%02X%02X%02X", (int) (color[0] * 255f), (int) (color[1] * 255f), (int) (color[2] * 255f));
        ImVec2 valueSize = ImGui.calcTextSize(valueText);
        drawList.addText(previewX + previewSize + 8f * scale, y + (height - valueSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(ImGuiCol.Text), valueText);

        float btnX = fieldX + fieldWidth + scale;
        String pickerText = "+";
        ImVec2 pickerSize = ImGui.calcTextSize(pickerText);
        ImGui.setCursorScreenPos(new ImVec2(btnX, y + 1.5f * scale));
        ImGui.invisibleButton("##picker_" + id, height, height);
        boolean pickerHovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked() && !ImGui.isPopupOpen("##popup_" + id)) ImGui.openPopup("##popup_" + id);
        float pickerAnim = approach(colorBtnAnim, id, pickerHovered ? 1f : 0f, 0.2f);
        drawList.addText(btnX + (height - pickerSize.x) * 0.5f, y + (height - pickerSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(1f, 1f, 1f, 0.20f + 0.60f * pickerAnim), pickerText);

        ImGui.setNextWindowPos(fieldX, y + height + 2.5f * scale);
        if (ImGui.beginPopup("##popup_" + id)) {
            ImGui.setWindowFontScale(scale / 8f);
            ImGui.pushItemWidth(220f * scale);
            ImGui.colorPicker4("##picker_internal_" + id, color,
                    ImGuiColorEditFlags.PickerHueWheel
                            | ImGuiColorEditFlags.NoSidePreview
                            | ImGuiColorEditFlags.NoSmallPreview
                            | ImGuiColorEditFlags.AlphaBar
                            | ImGuiColorEditFlags.AlphaPreviewHalf);
            ImGui.popItemWidth();
            ImGui.endPopup();
        }
    }

    public static <T extends Enum<T>> T enumDropdown(String label, T value, T[] options, float scale) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 24f * scale;
        float spacing = 165f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);
        float fieldX = pos.x + spacing;
        float fieldWidth = 395f * scale;

        ImGui.setCursorScreenPos(new ImVec2(fieldX, pos.y));
        ImGui.invisibleButton("##dropdown_" + id, fieldWidth, height);
        boolean hovered = ImGui.isItemHovered();
        boolean clicked = ImGui.isItemClicked();
        if (clicked && !ImGui.isPopupOpen("##popup_" + id)) ImGui.openPopup("##popup_" + id);

        float hoverAnim = approach(dropdownHoverAnim, id, hovered ? 1f : 0f, 0.15f);
        boolean open = ImGui.isPopupOpen("##popup_" + id);
        float openAnim = approach(dropdownOpenAnim, id, open ? 1f : 0f, 0.2f);

        ImVec2 labelSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(pos.x, pos.y + (height - labelSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);
        drawList.addRectFilled(fieldX, pos.y, fieldX + fieldWidth, pos.y + height,
                open ? ImGui.getColorU32(1f, 1f, 1f, 0f) : ImGui.getColorU32(1f, 1f, 1f, 0.05f + 0.05f * hoverAnim),
                height / 2f);
        drawList.addRect(fieldX, pos.y, fieldX + fieldWidth, pos.y + height,
                ImGui.getColorU32(1f, 1f, 1f, 0.08f + 0.15f * openAnim), height / 2f);
        drawList.addText(fieldX + 8f * scale, pos.y + 4.5f * scale, ImGui.getColorU32(ImGuiCol.Text), value.name());

        String arrowText = open ? "^" : "v";
        ImVec2 arrowSize = ImGui.calcTextSize(arrowText);
        float btnX = fieldX + fieldWidth + scale;
        ImGui.setCursorScreenPos(new ImVec2(btnX, pos.y + 1.5f * scale));
        ImGui.invisibleButton("##arrow_" + id, height, height);
        boolean arrowHovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked() && !ImGui.isPopupOpen("##popup_" + id)) ImGui.openPopup("##popup_" + id);
        float arrowAnim = approach(dropdownBtnAnim, id, arrowHovered ? 1f : 0f, 0.2f);
        drawList.addText(btnX + (height - arrowSize.x) * 0.5f, pos.y + (height - arrowSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(1f, 1f, 1f, 0.20f + 0.60f * arrowAnim), arrowText);

        ImGui.setNextWindowPos(fieldX, pos.y);
        if (ImGui.beginPopup("##popup_" + id)) {
            ImGui.setWindowFontScale(scale / 8f);
            for (T option : options) {
                boolean selected = option == value;
                if (selected) ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.CheckMark));
                if (ImGui.selectable(option.name(), false)) value = option;
                if (selected) ImGui.popStyleColor();
            }
            ImGui.endPopup();
        }
        return value;
    }

    public static String textBox(String label, String value, float scale) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 24f * scale;
        float spacing = 165f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);
        float fieldX = pos.x + spacing;
        float fieldWidth = 395f * scale;

        ImString buffer = textBuffers.computeIfAbsent(id, ignored -> new ImString(value, 512));
        boolean hovered = ImGui.isMouseHoveringRect(fieldX, pos.y, fieldX + fieldWidth, pos.y + height);
        float hoverAnim = approach(textHoverAnim, id, hovered ? 1f : 0f, 0.15f);

        ImVec2 labelSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(pos.x, pos.y + (height - labelSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);
        drawList.addRectFilled(fieldX, pos.y, fieldX + fieldWidth, pos.y + height,
                ImGui.getColorU32(1f, 1f, 1f, 0.06f + 0.04f * hoverAnim), height / 2f);

        ImGui.setCursorScreenPos(new ImVec2(fieldX + 2f * scale, pos.y));
        ImGui.pushItemWidth(fieldWidth - 2f * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 5f * scale, 0.5f * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0f, 0f, 0f, 0f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + scale * 3.5f);
        boolean changed = ImGui.inputText("##" + label, buffer);
        float activeAnim = approach(textFocusAnim, id, ImGui.isItemActive() ? 1f : 0f, 0.18f);
        ImGui.popStyleColor(3);
        ImGui.popStyleVar(3);
        ImGui.popItemWidth();

        drawList.addRect(fieldX, pos.y, fieldX + fieldWidth, pos.y + height,
                ImGui.getColorU32(1f, 1f, 1f, 0.08f + 0.15f * activeAnim), height / 2f);

        String clearText = "x";
        ImVec2 clearSize = ImGui.calcTextSize(clearText);
        float btnX = fieldX + fieldWidth + scale;
        ImGui.setCursorScreenPos(new ImVec2(btnX, pos.y));
        ImGui.invisibleButton("##clear_" + id, height, height);
        boolean clearHovered = ImGui.isItemHovered();
        float clearAnim = approach(clearBtnAnim, id, clearHovered ? 1f : 0f, 0.2f);
        drawList.addText(btnX + (height - clearSize.x) * 0.5f, pos.y + (height - clearSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(1f, 1f, 1f, 0.20f + 0.60f * clearAnim), clearText);
        if (ImGui.isItemClicked()) {
            buffer.set("");
            changed = true;
        }
        return changed ? buffer.get() : value;
    }

    public static float floatSlider(String label, float value, float min, float max, float scale) {
        float width = ImGui.getContentRegionAvailX() - 10f * scale;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 22f * scale;
        float spacing = 120f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);
        float sliderX = pos.x + spacing + 75f * scale;
        float sliderWidth = width - (sliderX - pos.x) - 50f * scale;
        float sliderY = pos.y + height * 0.35f;
        float sliderHeight = height * 0.3f;

        float t = Math.clamp((value - min) / (max - min), 0f, 1f);
        ImGui.invisibleButton(label, width, height);
        boolean active = ImGui.isItemActive();
        boolean hovered = ImGui.isItemHovered();
        if (active) {
            t = Math.clamp((ImGui.getIO().getMousePos().x - sliderX) / sliderWidth, 0f, 1f);
            value = min + t * (max - min);
        }
        if (hovered && ImGui.getIO().getKeyShift()) {
            value = Math.clamp(value - ImGui.getIO().getMouseWheel() * (max - min) / 25f, min, max);
            t = Math.clamp((value - min) / (max - min), 0f, 1f);
        }

        drawSlider(drawList, label, drawnLabel, value, min, max, scale, pos, sliderX, sliderWidth, sliderY, sliderHeight,
                t, active, hovered, floatSliderAnimations, floatHoverAnimations, floatBadgeAnimations, id, true);
        return Math.round(value * 100f) / 100f;
    }

    public static int intSlider(String label, int value, int min, int max, float scale) {
        float width = ImGui.getContentRegionAvailX() - 10f * scale;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 22f * scale;
        float spacing = 120f * scale;
        ImVec2 pos = ImGui.getCursorScreenPos();
        int id = ImGui.getID(label);
        String drawnLabel = visibleLabel(label);
        float sliderX = pos.x + spacing + 75f * scale;
        float sliderWidth = width - (sliderX - pos.x) - 50f * scale;
        float sliderY = pos.y + height * 0.35f;
        float sliderHeight = height * 0.3f;

        float t = Math.clamp((value - min) / (float) (max - min), 0f, 1f);
        ImGui.invisibleButton(label, width, height);
        boolean active = ImGui.isItemActive();
        boolean hovered = ImGui.isItemHovered();
        if (active) {
            t = Math.clamp((ImGui.getIO().getMousePos().x - sliderX) / sliderWidth, 0f, 1f);
            value = min + Math.round(t * (max - min));
        }
        if (hovered && ImGui.getIO().getKeyShift()) {
            value = Math.clamp(value - Math.round(ImGui.getIO().getMouseWheel() * (max - min) / 25f), min, max);
            t = Math.clamp((value - min) / (float) (max - min), 0f, 1f);
        }

        drawSlider(drawList, label, drawnLabel, value, min, max, scale, pos, sliderX, sliderWidth, sliderY, sliderHeight,
                t, active, hovered, intSliderAnimations, intHoverAnimations, intBadgeAnimations, id, false);
        return value;
    }

    public static void applyTetraTheme(float scale) {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(14f * scale);
        style.setChildRounding(10f * scale);
        style.setFrameRounding(10f * scale);
        style.setPopupRounding(10f * scale);
        style.setScrollbarRounding(12f * scale);
        style.setGrabRounding(12f * scale);
        style.setTabRounding(10f * scale);
        style.setWindowBorderSize(1f);
        style.setChildBorderSize(1f);
        style.setPopupBorderSize(1f);
        style.setFrameBorderSize(0f);
        style.setTabBorderSize(0f);
        style.setWindowPadding(12f * scale, 12f * scale);
        style.setFramePadding(10f * scale, 6f * scale);
        style.setCellPadding(8f * scale, 6f * scale);
        style.setItemSpacing(10f * scale, 8f * scale);
        style.setItemInnerSpacing(6f * scale, 6f * scale);
        style.setIndentSpacing(20f * scale);
        style.setScrollbarSize(10f * scale);
        style.setWindowTitleAlign(0.5f * scale, 0.5f * scale);

        ImVec4[] colors = style.getColors();
        colors[ImGuiCol.Text] = new ImVec4(0.95f, 0.96f, 0.98f, 1.00f);
        colors[ImGuiCol.TextDisabled] = new ImVec4(0.50f, 0.50f, 0.55f, 1.00f);
        colors[ImGuiCol.WindowBg] = new ImVec4(0.09f, 0.10f, 0.12f, 0.96f);
        colors[ImGuiCol.ChildBg] = new ImVec4(0.11f, 0.12f, 0.14f, 0.90f);
        colors[ImGuiCol.PopupBg] = new ImVec4(0.08f, 0.08f, 0.10f, 0.98f);
        colors[ImGuiCol.Border] = new ImVec4(0.20f, 0.22f, 0.27f, 0.80f);
        colors[ImGuiCol.BorderShadow] = new ImVec4(0f, 0f, 0f, 0f);
        colors[ImGuiCol.FrameBg] = new ImVec4(0.16f, 0.17f, 0.20f, 1.00f);
        colors[ImGuiCol.FrameBgHovered] = new ImVec4(0.22f, 0.24f, 0.28f, 1.00f);
        colors[ImGuiCol.FrameBgActive] = new ImVec4(0.28f, 0.31f, 0.36f, 1.00f);
        colors[ImGuiCol.TitleBg] = new ImVec4(0.10f, 0.11f, 0.13f, 1.00f);
        colors[ImGuiCol.TitleBgActive] = new ImVec4(0.14f, 0.15f, 0.18f, 1.00f);
        colors[ImGuiCol.TitleBgCollapsed] = new ImVec4(0.08f, 0.08f, 0.09f, 1.00f);
        colors[ImGuiCol.MenuBarBg] = new ImVec4(0.12f, 0.13f, 0.15f, 1.00f);
        colors[ImGuiCol.ScrollbarBg] = new ImVec4(0.10f, 0.11f, 0.13f, 1.00f);
        colors[ImGuiCol.ScrollbarGrab] = new ImVec4(0.24f, 0.26f, 0.30f, 1.00f);
        colors[ImGuiCol.ScrollbarGrabHovered] = new ImVec4(0.30f, 0.33f, 0.38f, 1.00f);
        colors[ImGuiCol.ScrollbarGrabActive] = new ImVec4(0.35f, 0.38f, 0.44f, 1.00f);
        colors[ImGuiCol.CheckMark] = new ImVec4(0.45f, 0.72f, 1.00f, 1.00f);
        colors[ImGuiCol.SliderGrab] = new ImVec4(0.40f, 0.68f, 1.00f, 1.00f);
        colors[ImGuiCol.SliderGrabActive] = new ImVec4(0.55f, 0.78f, 1.00f, 1.00f);
        colors[ImGuiCol.Button] = new ImVec4(0.18f, 0.20f, 0.24f, 1.00f);
        colors[ImGuiCol.ButtonHovered] = new ImVec4(0.25f, 0.28f, 0.34f, 1.00f);
        colors[ImGuiCol.ButtonActive] = new ImVec4(0.30f, 0.34f, 0.40f, 1.00f);
        colors[ImGuiCol.Header] = new ImVec4(0.20f, 0.22f, 0.27f, 1.00f);
        colors[ImGuiCol.HeaderHovered] = new ImVec4(0.28f, 0.31f, 0.38f, 1.00f);
        colors[ImGuiCol.HeaderActive] = new ImVec4(0.32f, 0.36f, 0.43f, 1.00f);
        colors[ImGuiCol.Separator] = new ImVec4(0.25f, 0.27f, 0.32f, 1.00f);
        colors[ImGuiCol.SeparatorHovered] = new ImVec4(0.35f, 0.40f, 0.48f, 1.00f);
        colors[ImGuiCol.SeparatorActive] = new ImVec4(0.40f, 0.45f, 0.55f, 1.00f);
        colors[ImGuiCol.ResizeGrip] = new ImVec4(0.28f, 0.31f, 0.38f, 0.20f);
        colors[ImGuiCol.ResizeGripHovered] = new ImVec4(0.40f, 0.45f, 0.55f, 0.70f);
        colors[ImGuiCol.ResizeGripActive] = new ImVec4(0.50f, 0.56f, 0.68f, 0.95f);
        colors[ImGuiCol.Tab] = new ImVec4(0.14f, 0.15f, 0.18f, 1.00f);
        colors[ImGuiCol.TabHovered] = new ImVec4(0.30f, 0.34f, 0.42f, 1.00f);
        colors[ImGuiCol.TabActive] = new ImVec4(0.22f, 0.25f, 0.31f, 1.00f);
        colors[ImGuiCol.TabUnfocused] = new ImVec4(0.10f, 0.11f, 0.13f, 1.00f);
        colors[ImGuiCol.TabUnfocusedActive] = new ImVec4(0.16f, 0.18f, 0.22f, 1.00f);
        colors[ImGuiCol.TextSelectedBg] = new ImVec4(0.26f, 0.59f, 0.98f, 0.35f);
        colors[ImGuiCol.NavHighlight] = new ImVec4(0.45f, 0.72f, 1.00f, 1.00f);
        colors[ImGuiCol.ModalWindowDimBg] = new ImVec4(0.20f, 0.20f, 0.20f, 0.35f);
    }

    private static void drawSlider(ImDrawList drawList, String label, String drawnLabel, float value, float min, float max,
                                   float scale, ImVec2 pos, float sliderX, float sliderWidth, float sliderY,
                                   float sliderHeight, float target, boolean active, boolean hovered,
                                   Map<Integer, Float> followMap, Map<Integer, Float> hoverMap,
                                   Map<Integer, Float> badgeMap, int id, boolean decimals) {
        float height = 22f * scale;
        float hoverAnim = approach(hoverMap, id, hovered ? 1f : 0f, 0.15f);
        float followAnim = approach(followMap, id, target, 0.25f);
        float badgeAnim = approach(badgeMap, id, (hovered || active) ? 1f : 0f, 0.25f);

        ImVec2 labelSize = ImGui.calcTextSize(drawnLabel);
        drawList.addText(pos.x, pos.y + (height - labelSize.y) * 0.5f + 1.5f * scale,
                ImGui.getColorU32(ImGuiCol.Text), drawnLabel);

        String minText = decimals ? String.format("%.2f", min) : String.valueOf((int) min);
        String maxText = decimals ? String.format("%.2f", max) : String.valueOf((int) max);
        int dimText = ImGui.getColorU32(1f, 1f, 1f, 0.35f);
        drawList.addText(sliderX - ImGui.calcTextSizeX(minText) - 10f * scale, pos.y + 2.5f * scale, dimText, minText);
        drawList.addText(sliderX + sliderWidth + 10f * scale, pos.y + 2.5f * scale, dimText, maxText);

        drawList.addRectFilled(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight,
                ImGui.getColorU32(1f, 1f, 1f, 0.05f + 0.05f * hoverAnim), sliderHeight / 2f);
        drawList.addRectFilled(sliderX, sliderY, sliderX + sliderWidth * followAnim, sliderY + sliderHeight,
                ImGui.getColorU32(ImGuiCol.CheckMark), sliderHeight / 2f);

        float knobRadius = height * 0.35f;
        float knobX = sliderX + sliderWidth * followAnim;
        float knobY = pos.y + height / 2f;
        drawList.addCircleFilled(knobX, knobY, knobRadius, ImGui.getColorU32(ImGuiCol.Text));

        String valueText = decimals ? String.format("%.2f", value) : String.format("%.0f", value);
        ImVec2 valueSize = ImGui.calcTextSize(valueText);
        float badgeScale = 0.7f + 0.3f * badgeAnim;
        float badgeWidth = (valueSize.x + 10f * scale) * badgeScale;
        float badgeHeight = (valueSize.y + 6f * scale) * badgeScale;
        float badgeX = knobX - badgeWidth / 2f;
        float badgeY = knobY - knobRadius - 25f * scale - 10f * badgeAnim;
        drawList.addRectFilled(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight,
                ImGui.getColorU32(1f, 1f, 1f, 0.12f * badgeAnim), badgeHeight / 2f);
        drawList.addText(badgeX + (badgeWidth - valueSize.x) / 2f, badgeY + (badgeHeight - valueSize.y) / 2f,
                ImGui.getColorU32(1f, 1f, 1f, 0.9f * badgeAnim), valueText);
    }

    private static float approach(Map<Integer, Float> map, int id, float target, float speed) {
        float value = map.getOrDefault(id, target);
        value += (target - value) * speed;
        map.put(id, value);
        return value;
    }

    private static String visibleLabel(String label) {
        int marker = label.indexOf("##");
        return marker >= 0 ? label.substring(0, marker) : label;
    }
}
