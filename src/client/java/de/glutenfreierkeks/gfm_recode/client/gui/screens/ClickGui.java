// ClickGui.java
package de.glutenfreierkeks.gfm_recode.client.gui.screens;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.gui.utils.RenderUtils;
import de.glutenfreierkeks.gfm_recode.client.gui.utils.Animation;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.modules.Module.Category;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.*;

public class ClickGui extends Screen {

    // ── Farben (Atomskycode Design) ───────────────────────────────────────────
    static final int ACCENT_START   = 0xFFDE7CD6;
    static final int ACCENT_END     = 0xFFA95FB0;
    static final int BG_GLASS       = 0xE617161C; // rgba(23, 22, 28, 0.9)
    static final int BG_HEADER      = 0xFF110A18;
    static final int BG_BODY        = 0xE6070011; // rgba(7, 0, 17, 0.9)
    static final int BG_MODULE      = 0x4D000000; // rgba(0, 0, 0, 0.3)
    static final int TEXT_MAIN      = 0xFFFFFFFF;
    static final int TEXT_SEC       = 0xFF9C9C9C;
    static final int TEXT_DIM       = 0xFF6A6A6A;
    static final int INDICATOR_ON   = 0xFF6BFF6B;
    static final int INDICATOR_OFF  = 0xFFFF4B4B;

    // Legacy compatibility (mapped to new theme)
    static final int ACCENT         = ACCENT_START;
    static final int ACCENT_LIGHT   = ACCENT_START;
    static final int TEXT_ON        = TEXT_MAIN;

    private static final Identifier LOGO = Identifier.of("gfm_recode", "assets/gfm_recode/icon.png");

    private static final StyleSpriteSource SMOOTH_FONT = new StyleSpriteSource.Font(Identifier.of("minecraft", "uniform"));
    private static Text s(String text) { return Text.literal(text).styled(style -> style.withFont(SMOOTH_FONT)); }

    private static final List<Panel> panels = new ArrayList<>();
    private static boolean initialized = false;
    private static Module activeModule = null;
    private static Panel  activePanel  = null;
    private static Setting<?> draggingSetting = null;
    private static Setting<?> focusedSetting  = null;
    private static BedrockGridSetting draggingBedrock = null;
    private static boolean draggingBedrockState = false;
    private static float settingsScroll = 0;
    private static float uiScale = 1.0f;

    private static final Map<Setting<?>, Animation> boolAnims = new HashMap<>();
    private static final Map<Module, Animation>     moduleAnims = new HashMap<>();
    private static final Map<Setting<?>, Float>     inventoryVisualRows = new HashMap<>();
    private static final Map<Setting<?>, String>    itemSearches = new HashMap<>();
    private static final Map<Setting<?>, String>    stringBuffers = new HashMap<>();
    private static final Map<Setting<?>, float[]>   colorVisuals = new HashMap<>();
    private static final Map<Setting<?>, Map<Integer, Animation>> inventoryPopAnims = new HashMap<>();
    private static final Map<Setting<?>, Map<Integer, Animation>> bedrockPopAnims = new HashMap<>();

    public ClickGui() { super(Text.literal("ClickGUI")); }

    @Override
    protected void init() {
        if (!initialized) {
            int x = 10;
            for (Category cat : Category.values()) { panels.add(new Panel(cat, x, 10)); x += 170; }
            initialized = true;
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Global background dim
        ctx.fill(0, 0, this.width, this.height, 0x99000000);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(uiScale, uiScale);
        int smx = (int) (mx / uiScale);
        int smy = (int) (my / uiScale);
        
        int sw = (int)(this.width / uiScale);
        int sh = (int)(this.height / uiScale);

        renderGlobalElements(ctx, smx, smy, sw, sh);
        renderConfigButton(ctx, smx, smy, delta, sw, sh);

        for (Panel p : panels) p.render(ctx, smx, smy, delta);
        if (activeModule != null && activePanel != null) renderSettingsPanel(ctx, activeModule, activePanel, smx, smy);
        
        ctx.getMatrices().popMatrix();
    }

    private void renderGlobalElements(DrawContext ctx, int mx, int my, int sw, int sh) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        
        // Logo: atomskycode design
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(37.0f, 13.0f);
        String logoText = "atomskycode";
        RenderUtils.drawGradientH(ctx, 0, 0, (float)tr.getWidth(logoText), 20, withAlpha(ACCENT_START, 0.7f), withAlpha(ACCENT_END, 0.7f));
        ctx.drawText(tr, logoText, 0, 0, 0xFFFFFFFF, false);
        ctx.getMatrices().popMatrix();

        // Subtext: design by atomskycode @atomclient
        ctx.drawText(tr, "design by atomskycode @atomclient", 26, 35, 0x33FFFFFF, false);
        
        // THEMES placeholder (Bottom Right)
        int tx = sw - 150, ty = sh - 100;
        RenderUtils.fillRoundedRect(ctx, (float)tx, (float)ty, 140f, 80f, 15f, BG_BODY);
        RenderUtils.fillRoundedRect(ctx, (float)tx, (float)ty, 140f, 25f, 15f, BG_HEADER, true, true, false, false);
        ctx.drawText(tr, "Themes", tx + 10, ty + 7, TEXT_MAIN, false);
        
        RenderUtils.fillRoundedRect(ctx, tx + 10, ty + 35, 12, 12, 4, ACCENT_START);
        ctx.drawText(tr, "Pink (Active)", tx + 28, ty + 37, TEXT_SEC, false);
        RenderUtils.fillRoundedRect(ctx, tx + 10, ty + 55, 12, 12, 4, 0xFFFFFFFF);
        ctx.drawText(tr, "White", tx + 28, ty + 57, TEXT_SEC, false);
        
        // Watermark/Footer at bottom center
        ctx.drawText(tr, "GFM CLIENT - " + Gfm_recodeClient.VERSION, (sw - tr.getWidth("GFM CLIENT - " + Gfm_recodeClient.VERSION)) / 2, sh - 20, 0x44FFFFFF, false);
    }

    private static int withAlpha(int color, float alpha) { return RenderUtils.withAlpha(color, alpha); }

    private int configBtnX, configBtnY;
    private static final int BTN_W = 60, BTN_H = 18;

    private void renderConfigButton(DrawContext ctx, int mx, int my, float delta, int screenW, int screenH) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        configBtnX = screenW - BTN_W - 10;
        configBtnY = 10;
        boolean hovered = mx >= configBtnX && mx < configBtnX + BTN_W && my >= configBtnY && my < configBtnY + BTN_H;
        int border = hovered ? ACCENT_START : ACCENT_END;
        RenderUtils.fillRoundedRect(ctx, (float) configBtnX, (float) configBtnY, (float) BTN_W, (float) BTN_H, 6f, hovered ? withAlpha(BG_HEADER, 0.8f) : BG_HEADER);
        ctx.fill(configBtnX, configBtnY, configBtnX + BTN_W, configBtnY + 1, border);
        
        String txt = "Configs";
        int tx = configBtnX + (BTN_W - tr.getWidth(txt)) / 2;
        int ty = configBtnY + (BTN_H - 8) / 2;
        ctx.drawText(tr, txt, tx, ty, TEXT_MAIN, false);
    }

    private void renderSettingsPanel(DrawContext ctx, Module mod, Panel p, int mx, int my) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int sw = 190, sx = p.x + Panel.W + 8; if (sx + sw > this.width / uiScale) sx = p.x - sw - 8;
        int sy = p.y; List<Setting<?>> settings = mod.getSettings();
        
        int contentH = 0; 
        for (Setting<?> s : settings) contentH += getSettingHeight(s) + 2;
        
        int maxH = Math.min(this.height - p.y - 20, 600);
        int totalH = Math.min(contentH + Panel.HEADER_H + 4, maxH);
        
        settingsScroll = Math.max(0, Math.min(settingsScroll, Math.max(0, contentH - (totalH - Panel.HEADER_H))));

        RenderUtils.drawDropShadow(ctx, sx, sy, sw, totalH, 8);
        RenderUtils.fillRoundedRect(ctx, sx, sy, sw, totalH, 15, BG_GLASS);
        RenderUtils.fillRoundedRect(ctx, sx, sy, sw, Panel.HEADER_H, 15, BG_HEADER, true, true, false, false);
        
        ctx.drawText(tr, mod.name, sx + (sw - tr.getWidth(mod.name)) / 2, sy + 6, ACCENT_START, false);
        
        ctx.enableScissor(sx, sy + Panel.HEADER_H + 1, sx + sw, sy + totalH - 1);
        int cy = sy + Panel.HEADER_H + 2 - (int)settingsScroll; 
        for (Setting<?> set : settings) {
            cy = renderSettingWidget(ctx, tr, set, sx, cy, sw, mx, my);
        }
        ctx.disableScissor();
    }

    private int getSettingHeight(Setting<?> s) {
        if (s instanceof ColorSetting) return 115;
        if (s instanceof ItemSetting) return 150;
        if (s instanceof InventorySlotSetting i) {
            float target = i.getLayout().rows;
            float current = inventoryVisualRows.getOrDefault(s, target);
            current = RenderUtils.lerp(current, target, 0.2f);
            inventoryVisualRows.put(s, current);
            int base = 28; // Header for Layout
            if (!i.getSuggestions().isEmpty()) base += 10;
            return (int)(base + current * 20);
        }
        if (s instanceof BedrockGridSetting) return 185;
        return Panel.SET_H;
    }

    private int renderSettingWidget(DrawContext ctx, TextRenderer tr, Setting<?> set, int sx, int cy, int sw, int mx, int my) {
        int h = getSettingHeight(set); 
        RenderUtils.fillRoundedRect(ctx, sx + 6, cy, sw - 12, h, 8, BG_MODULE);
        ctx.drawText(tr, set.name, sx + 12, cy + 5, TEXT_MAIN, false);
        if      (set instanceof BoolSetting         s) renderBool(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof IntSliderSetting     s) renderIntSlider(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof DoubleSliderSetting  s) renderDoubleSlider(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof EnumSetting<?>       s) renderEnum(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof ColorSetting         s) renderColorPicker(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof ItemSetting          s) renderItemPicker(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof InventorySlotSetting s) renderInventorySlot(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof BedrockGridSetting   s) renderBedrockGrid(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof KeybindSetting       s) renderKeybind(ctx, tr, s, sx, cy, sw, mx, my);
        else if (set instanceof StringSetting        s) renderString(ctx, tr, s, sx, cy, sw, mx, my);
        return cy + h + 2;
    }

    private void renderBool(DrawContext ctx, TextRenderer tr, BoolSetting s, int sx, int cy, int sw, int mx, int my) {
        int tx = sx + sw - 28, ty = cy + 4, tw = 16, th = 16;
        RenderUtils.fillRoundedRect(ctx, tx, ty, tw, th, 4, 0x80000000);
        String icon = s.getValue() ? "✓" : "✕";
        int color = s.getValue() ? INDICATOR_ON : INDICATOR_OFF;
        ctx.drawText(tr, icon, tx + (tw - tr.getWidth(icon)) / 2 + 1, ty + 4, color, false);
    }

    private void renderIntSlider(DrawContext ctx, TextRenderer tr, IntSliderSetting s, int sx, int cy, int sw, int mx, int my) {
        String val = String.valueOf(s.getValue()); ctx.drawText(tr, val, sx + sw - tr.getWidth(val) - 12, cy + 5, TEXT_SEC, false);
        int bx = sx + 12, bw = sw - 24, by = cy + 18, sh = 6;
        RenderUtils.fillRoundedRect(ctx, bx, by, bw, sh, 3, 0x44000000);
        RenderUtils.drawGradientH(ctx, bx, by, (float)(bw * s.getPercent()), sh, ACCENT_START, ACCENT_END);
        RenderUtils.drawCircle(ctx, bx + (float)(bw * s.getPercent()), by + sh/2f, 4f, TEXT_MAIN);
    }

    private void renderDoubleSlider(DrawContext ctx, TextRenderer tr, DoubleSliderSetting s, int sx, int cy, int sw, int mx, int my) {
        String val = s.getFormatted(); ctx.drawText(tr, val, sx + sw - tr.getWidth(val) - 12, cy + 5, TEXT_SEC, false);
        int bx = sx + 12, bw = sw - 24, by = cy + 18, sh = 6;
        RenderUtils.fillRoundedRect(ctx, bx, by, bw, sh, 3, 0x44000000);
        RenderUtils.drawGradientH(ctx, bx, by, (float)(bw * s.getPercent()), sh, ACCENT_START, ACCENT_END);
        RenderUtils.drawCircle(ctx, bx + (float)(bw * s.getPercent()), by + sh/2f, 4f, TEXT_MAIN);
    }

    private void renderEnum(DrawContext ctx, TextRenderer tr, EnumSetting<?> s, int sx, int cy, int sw, int mx, int my) {
        String cV = s.getValue().name();
        int vw = 80, vx = sx + sw - vw - 12, vh = 14;
        RenderUtils.fillRoundedRect(ctx, vx, cy + 4, vw, vh, 6, 0x4D000000);
        ctx.drawText(tr, cV, vx + (vw - tr.getWidth(cV)) / 2, cy + 7, TEXT_SEC, false);
    }

    private void renderColorPicker(DrawContext ctx, TextRenderer tr, ColorSetting s, int sx, int cy, int sw, int mx, int my) {
        int bx=sx+8, by=cy+16, bVal=70;
        float[] vis = colorVisuals.computeIfAbsent(s, k -> new float[]{s.getHue(), s.getSat(), s.getVal(), s.getA() / 255f});
        vis[0] = RenderUtils.lerp(vis[0], s.getHue(), 0.2f); vis[1] = RenderUtils.lerp(vis[1], s.getSat(), 0.2f); vis[2] = RenderUtils.lerp(vis[2], s.getVal(), 0.2f); vis[3] = RenderUtils.lerp(vis[3], s.getA() / 255f, 0.2f);
        RenderUtils.fillRoundedRect(ctx, bx, by, bVal, bVal, 4, java.awt.Color.HSBtoRGB(vis[0], 1, 1));
        RenderUtils.drawGradientH(ctx, bx, by, bVal, bVal, 0xFFFFFFFF, 0x00FFFFFF); RenderUtils.drawGradientV(ctx, bx, by, bVal, bVal, 0x00000000, 0xFF000000);
        RenderUtils.drawCircle(ctx, bx + (vis[1] * bVal), by + ((1 - vis[2]) * bVal), 4f, 0xFFFFFFFF);

        // Hue slider
        int hx = bx + bVal + 5, hw = 10;
        for (int i = 0; i < bVal; i++) {
            ctx.fill(hx, by + i, hx + hw, by + i + 1, java.awt.Color.HSBtoRGB(i / (float)bVal, 1, 1));
        }
        ctx.fill(hx, by + (int)(vis[0] * bVal) - 1, hx + hw, by + (int)(vis[0] * bVal) + 1, 0xFFFFFFFF);

        // Alpha slider
        int ax = hx + hw + 5, aw = 10;
        // Simple alpha gradient
        int baseColor = java.awt.Color.HSBtoRGB(vis[0], vis[1], vis[2]) & 0x00FFFFFF;
        RenderUtils.drawGradientV(ctx, ax, by, aw, bVal, 0xFF000000 | baseColor, 0x00000000 | baseColor);
        ctx.fill(ax, by + (int)((1 - vis[3]) * bVal) - 1, ax + aw, by + (int)((1 - vis[3]) * bVal) + 1, 0xFFFFFFFF);
    }

    private void renderItemPicker(DrawContext ctx, TextRenderer tr, ItemSetting s, int sx, int cy, int sw, int mx, int my) {
        int SL=24, CO=7, gx=sx+6, gy=cy+16+14; String sr = itemSearches.getOrDefault(s, "");
        RenderUtils.fillRoundedRect(ctx, sx+6, cy+14, sw-12, 12, 3, 0xFF0A0814);
        ctx.drawText(tr, s(sr.isEmpty() ? "Search..." : sr), sx+10, cy+16, TEXT_DIM, false);
        List<Item> aL = new ArrayList<>(Registries.ITEM.stream().toList()), sel = s.getValue();
        List<Item> d = new ArrayList<>(); for(Item it:aL){if(sr.isEmpty()||it.getName().getString().toLowerCase().contains(sr.toLowerCase()))d.add(it);if(d.size()>=CO*5)break;}
        for(int i=0;i<d.size();i++){
            int ix=gx+(i%CO)*(SL+1),iy=gy+(i/CO)*(SL+1); Item it=d.get(i); boolean hv=RenderUtils.isHovered(mx,my,ix,iy,SL,SL),isS=sel.contains(it);
            RenderUtils.fillRoundedRect(ctx,ix,iy,SL,SL,4, isS ? 0xCC1E1630 : 0xFF0E0C18);
            if(hv)ctx.fill(ix,iy,ix+SL,iy+SL,0x22FFFFFF);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(ix + 4f, iy + 4f);
            ctx.drawItem(new ItemStack(it), 0, 0);
            ctx.getMatrices().popMatrix();
        }
    }

    private void renderKeybind(DrawContext ctx, TextRenderer tr, KeybindSetting s, int sx, int cy, int sw, int mx, int my) {
        String v = s.getKeyName(); int vw = Math.max(24, tr.getWidth(s(v)) + 12), vx = sx + sw - vw - 8;
        RenderUtils.fillRoundedRect(ctx, vx, cy+3, vw, 11, 4, 0xFF1E1630); ctx.drawText(tr, s(v), vx + (vw - tr.getWidth(s(v))) / 2, cy + 5, TEXT_MAIN, false);
    }

    private void renderString(DrawContext ctx, TextRenderer tr, StringSetting s, int sx, int cy, int sw, int mx, int my) {
        boolean editing = focusedSetting == s;
        String value = editing ? stringBuffers.getOrDefault(s, s.getValue()) : s.getValue();
        String shown = editing ? value + "_" : value;
        ctx.drawText(tr, s(shown), sx + sw - tr.getWidth(s(shown)) - 8, cy + 4, editing ? ACCENT_START : TEXT_DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            uiScale += verticalAmount * 0.1f;
            uiScale = Math.max(0.5f, Math.min(uiScale, 2.5f));
            return true;
        }
        
        double smx = mx / uiScale;
        double smy = my / uiScale;
        
        if (activeModule != null && activePanel != null) {
            int sw = 190, sx = activePanel.x + Panel.W + 8; if (sx + sw > this.width / uiScale) sx = activePanel.x - sw - 8;
            if (RenderUtils.isHovered((int)smx, (int)smy, sx, activePanel.y, sw, 600)) {
                settingsScroll -= verticalAmount * 20;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override public boolean mouseClicked(Click c, boolean d) {
        int imx=(int)(c.x() / uiScale), imy=(int)(c.y() / uiScale); focusedSetting=null;
        if (activeModule != null) {
            for (Setting<?> s : activeModule.getSettings()) {
                if (s instanceof KeybindSetting k && k.isListening()) {
                    k.setKey(KeybindSetting.MOUSE_BASE + c.button());
                    return true;
                }
            }
        }
        
        if (RenderUtils.isHovered(imx, imy, configBtnX, configBtnY, BTN_W, BTN_H)) {
            this.client.setScreen(new ConfigScreen(this));
            return true;
        }
        
        if (activeModule != null && activePanel != null) {
            int sw = 190, sx = activePanel.x + Panel.W + 8; if (sx + sw > this.width / uiScale) sx = activePanel.x - sw - 8;
            if (RenderUtils.isHovered(imx, imy, sx, activePanel.y, sw, 600)) { handleSettingsClick(activeModule, sx, activePanel.y, sw, imx, imy, c.button()); return true; }
        }
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).mouseClicked(imx, imy, c.button())) { Panel p = panels.remove(i); panels.add(p); return true; }
        if (activeModule != null) activeModule = null;
        return super.mouseClicked(c, d);
    }

    private void handleSettingsClick(Module mod, int sx, int sy, int sw, int mx, int my, int button) {
        int contentH = 0;
        for (Setting<?> s : mod.getSettings()) contentH += getSettingHeight(s) + 2;
        int maxH = Math.min(this.height - sy - 20, 400);
        int totalH = Math.min(contentH + Panel.HEADER_H + 4, maxH);

        int cy = sy + Panel.HEADER_H + 2 - (int)settingsScroll;
        for (Setting<?> s : mod.getSettings()) {
            int h = getSettingHeight(s);
            // Only handle click if the setting is within the visible (scissored) area
            if (cy + h > sy + Panel.HEADER_H && cy < sy + totalH) {
                if (RenderUtils.isHovered(mx, my, sx, cy, sw, h)) {
                    if      (s instanceof BoolSetting b)    b.setValue(!b.getValue());
                    else if (s instanceof EnumSetting<?> e) e.cycle();
                    else if (s instanceof KeybindSetting k) k.startListening();
                    else if (s instanceof ItemSetting is)   handleItemPickerClick(is, sx, cy, mx, my);
                    else if (s instanceof InventorySlotSetting iss) handleInventorySlotClick(iss, sx, cy, sw, mx, my);
                    else if (s instanceof BedrockGridSetting bgs)   handleBedrockGridClick(bgs, sx, cy, sw, mx, my);
                    else if (s instanceof StringSetting ss) { focusedSetting = ss; stringBuffers.putIfAbsent(ss, ss.getValue()); }
                    else if (s instanceof IntSliderSetting || s instanceof DoubleSliderSetting || s instanceof ColorSetting) { draggingSetting = s; updateSlider(s, sx, cy, sw, mx, my); }
                    return;
                }
            }
            cy += h + 2;
        }
    }

    private void handleItemPickerClick(ItemSetting s, int sx, int cy, int mx, int my) {
        if(RenderUtils.isHovered(mx,my,sx+6,cy+14,178,12)) { focusedSetting = s; return; }
        int SL=24, CO=7, gx=sx+6, gy=cy+16+14; String sr = itemSearches.getOrDefault(s, "");
        List<Item> aL=new ArrayList<>(Registries.ITEM.stream().toList()), sL=s.getValue();
        List<Item> d = new ArrayList<>(); for(Item it:aL){if(sr.isEmpty()||it.getName().getString().toLowerCase().contains(sr.toLowerCase()))d.add(it);if(d.size()>=CO*5)break;}
        for (int i=0;i<d.size();i++){if(RenderUtils.isHovered(mx,my,gx+(i%CO)*(SL+1), gy+(i/CO)*(SL+1), SL, SL)) { s.toggle(dispAt(d, i)); return; }}
    }

    private void renderInventorySlot(DrawContext ctx, TextRenderer tr, InventorySlotSetting s, int sx, int cy, int sw, int mx, int my) {
        float visRows = inventoryVisualRows.getOrDefault(s, (float)s.getLayout().rows);
        int rows = s.getLayout().rows, cols = s.getLayout().columns;
        int size = 18, spacing = 2;
        int startX = sx + (sw - (cols * (size + spacing) - spacing)) / 2;
        int startY = cy + 28;

        String lName = "Layout: " + s.getLayout().name();
        boolean lHov = RenderUtils.isHovered(mx, my, sx + 8, cy + 14, tr.getWidth(s(lName)), 9);
        ctx.drawText(tr, s(lName), sx + 8, cy + 14, lHov ? ACCENT_START : TEXT_DIM, false);

        Map<Integer, InventorySlotSetting.Suggestion> sug = s.getSuggestions();
        if (!sug.isEmpty()) {
            ctx.drawText(tr, s("Hints:"), sx + 8, cy + 26, TEXT_DIM, false);
            startY += 10;
        }

        ctx.enableScissor(sx, cy, sx + sw, cy + getSettingHeight(s));

        InventorySlotSetting.Suggestion hoveredSuggestion = null;
        Map<Integer, Animation> sAnims = inventoryPopAnims.computeIfAbsent(s, k -> new HashMap<>());

        for (int r = 0; r < Math.ceil(visRows + 1); r++) {
            for (int c = 0; c < cols; c++) {
                int slot = r * cols + c;
                int ix = startX + c * (size + spacing);
                int iy = startY + r * (size + spacing);

                float rowAlpha = Math.max(0, Math.min(1, visRows - r));
                if (rowAlpha <= 0) continue;

                boolean selected = s.isSelected(slot);
                boolean hovered = RenderUtils.isHovered(mx, my, ix, iy, size, size);

                Animation pop = sAnims.computeIfAbsent(slot, k -> new Animation(150));
                pop.setForward(selected);
                float p = pop.getEaseInOutF();

                int baseColor = selected ? ACCENT_START : 0xFF1E1630;

                if (p > 0.01f) {
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(ix + size/2f, iy + size/2f);
                    ctx.getMatrices().scale(1 + p * 0.1f, 1 + p * 0.1f);
                    ctx.getMatrices().translate(-size/2f, -size/2f);

                    RenderUtils.fillRoundedRect(ctx, 0, 0, size, size, 3, RenderUtils.withAlpha(baseColor, rowAlpha));
                    if (hovered && rowAlpha > 0.5f) ctx.fill(0, 0, size, size, RenderUtils.withAlpha(0x22FFFFFF, rowAlpha));

                    if (sug.containsKey(slot)) {
                        InventorySlotSetting.Suggestion suggestion = sug.get(slot);
                        ctx.drawItem(suggestion.stack, 1, 1);
                        if (hovered && rowAlpha > 0.8f) hoveredSuggestion = suggestion;
                    }

                    if (selected) {
                        ctx.fill(1, 1, size - 1, size - 1, RenderUtils.withAlpha(0xFF1E1630, rowAlpha));
                        ctx.fill(2, 2, size - 2, size - 2, RenderUtils.withAlpha(ACCENT_START, rowAlpha));
                    }
                    ctx.getMatrices().popMatrix();
                } else {
                    RenderUtils.fillRoundedRect(ctx, ix, iy, size, size, 3, RenderUtils.withAlpha(baseColor, rowAlpha));
                    if (hovered && rowAlpha > 0.5f) ctx.fill(ix, iy, ix + size, iy + size, RenderUtils.withAlpha(0x22FFFFFF, rowAlpha));

                    if (sug.containsKey(slot)) {
                        InventorySlotSetting.Suggestion suggestion = sug.get(slot);
                        ctx.drawItem(suggestion.stack, ix + 1, iy + 1);
                        if (hovered && rowAlpha > 0.8f) hoveredSuggestion = suggestion;
                    }
                }
            }
        }
        ctx.disableScissor();

        if (hoveredSuggestion != null) {
            int tw = tr.getWidth(s(hoveredSuggestion.name)) + 8;
            RenderUtils.fillRoundedRect(ctx, mx + 8, my - 16, tw, 12, 4, 0xEE0A0814);
            ctx.drawText(tr, s(hoveredSuggestion.name), mx + 12, my - 14, TEXT_MAIN, false);
        }
    }

    private void handleInventorySlotClick(InventorySlotSetting s, int sx, int cy, int sw, int mx, int my) {
        int rows = s.getLayout().rows, cols = s.getLayout().columns;
        int size = 18, spacing = 2;
        int startX = sx + (sw - (cols * (size + spacing) - spacing)) / 2;
        int startY = cy + 28;

        String lName = "Layout: " + s.getLayout().name();
        if (RenderUtils.isHovered(mx, my, sx + 8, cy + 14, MinecraftClient.getInstance().textRenderer.getWidth(lName), 9)) {
            s.cycleLayout();
            return;
        }

        if (!s.getSuggestions().isEmpty()) startY += 10;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slot = r * cols + c;
                int ix = startX + c * (size + spacing);
                int iy = startY + r * (size + spacing);
                if (RenderUtils.isHovered(mx, my, ix, iy, size, size)) {
                    s.click(slot);
                    return;
                }
            }
        }
    }

    private void renderBedrockGrid(DrawContext ctx, TextRenderer tr, BedrockGridSetting s, int sx, int cy, int sw, int mx, int my) {
        int size = 7, spacing = 1;
        int gridW = 24 * (size + spacing) - spacing;
        int startX = sx + (sw - gridW) / 2;
        int startY = cy + 18;

        boolean hovClear = RenderUtils.isHovered(mx, my, sx + sw - 40, cy + 4, 35, 9);
        ctx.drawText(tr, s("Clear"), sx + sw - 40, cy + 4, hovClear ? ACCENT_START : TEXT_DIM, false);

        boolean hovPaste = RenderUtils.isHovered(mx, my, sx + sw - 80, cy + 4, 35, 9);
        ctx.drawText(tr, s("Paste"), sx + sw - 80, cy + 4, hovPaste ? ACCENT_START : TEXT_DIM, false);

        Map<Integer, Animation> sAnims = bedrockPopAnims.computeIfAbsent(s, k -> new HashMap<>());

        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                int slot = x * 24 + z;
                int ix = startX + x * (size + spacing);
                int iy = startY + z * (size + spacing);

                boolean selected = s.get(x, z);
                boolean hovered = RenderUtils.isHovered(mx, my, ix, iy, size, size);

                Animation pop = sAnims.computeIfAbsent(slot, k -> new Animation(150));
                pop.setForward(selected);
                float p = pop.getEaseInOutF();

                int baseColor = selected ? ACCENT_START : 0xFF1E1630;

                if (p > 0.01f) {
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(ix + size/2f, iy + size/2f);
                    ctx.getMatrices().scale(0.8f + p * 0.2f, 0.8f + p * 0.2f);
                    ctx.getMatrices().translate(-size/2f, -size/2f);
                    RenderUtils.fillRoundedRect(ctx, 0, 0, size, size, 1.5f, baseColor);
                    if (hovered) ctx.fill(0, 0, size, size, 0x22FFFFFF);
                    ctx.getMatrices().popMatrix();
                } else {
                    ctx.fill(ix, iy, ix + size, iy + size, baseColor);
                    if (hovered) ctx.fill(ix, iy, ix + size, iy + size, 0x22FFFFFF);
                }
            }
        }
    }

    private void handleBedrockGridClick(BedrockGridSetting s, int sx, int cy, int sw, int mx, int my) {
        if (RenderUtils.isHovered(mx, my, sx + sw - 40, cy + 4, 35, 9)) {
            s.clear();
            return;
        }
        if (RenderUtils.isHovered(mx, my, sx + sw - 80, cy + 4, 35, 9)) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                // Support formats like "1,2;3,4" or "1 2, 3 4" or just a list of numbers
                String[] parts = clipboard.split("[;,\\n]");
                s.clear();
                for (String part : parts) {
                    String[] coords = part.trim().split("[\\s]+");
                    if (coords.length >= 2) {
                        try {
                            int x = Integer.parseInt(coords[0]);
                            int z = Integer.parseInt(coords[1]);
                            s.set(x, z, true);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            return;
        }
        int size = 7, spacing = 1;
        int gridW = 24 * (size + spacing) - spacing;
        int startX = sx + (sw - gridW) / 2;
        int startY = cy + 18;

        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                int ix = startX + x * (size + spacing);
                int iy = startY + z * (size + spacing);
                if (RenderUtils.isHovered(mx, my, ix, iy, size, size)) {
                    draggingBedrock = s;
                    draggingBedrockState = !s.get(x, z);
                    s.set(x, z, draggingBedrockState);
                    return;
                }
            }
        }
    }
    private Item dispAt(List<Item> d, int i) { return d.get(i); }

    private void updateSlider(Setting<?> s, int sx, int cy, int sw, int mx, int my) {
        int bx = sx + 8, bw = sw - 16; float p = Math.max(0, Math.min(1, (float)(mx - bx) / bw));
        if      (s instanceof IntSliderSetting is)    is.setValue((int)(is.getMin() + p * (is.getMax() - is.getMin())));
        else if (s instanceof DoubleSliderSetting ds) ds.setValue(ds.getMin() + p * (ds.getMax() - ds.getMin()));
        else if (s instanceof ColorSetting cs)        updateColor(cs, sx, cy, mx, my);
    }

    private void updateColor(ColorSetting cs, int sx, int cy, int mx, int my) {
        int bx=sx+8, by=cy+16, bS=70;
        if(RenderUtils.isHovered(mx,my,bx,by,bS,bS)){cs.setSat((float)(mx-bx)/bS); cs.setVal(1-(float)(my-by)/bS);}
        else if(RenderUtils.isHovered(mx,my,bx+bS+5,by,10,bS)){cs.setHue((float)(my-by)/bS);}
        else if(RenderUtils.isHovered(mx,my,bx+bS+20,by,10,bS)){cs.setAlpha((int)((1-(float)(my-by)/bS)*255));}
    }

    private boolean isHoveredCategory(int mx, int my) { for (Panel p : panels) if (RenderUtils.isHovered(mx, my, p.x, p.y, Panel.W, Panel.HEADER_H)) return true; return false; }
    @Override public boolean mouseReleased(Click c) { for (Panel p : panels) p.mouseReleased(); draggingSetting = null; draggingBedrock = null; return true; }
    @Override public boolean mouseDragged(Click c, double ox, double oy) {
        int smx = (int)(c.x() / uiScale);
        int smy = (int)(c.y() / uiScale);
        if (draggingSetting != null && activePanel != null) {
            int sw = 190, sx = activePanel.x + Panel.W + 8; if (sx + sw > this.width / uiScale) sx = activePanel.x - sw - 8;
            updateSlider(draggingSetting, sx, activePanel.y + findSettingYOffset(draggingSetting), sw, smx, smy); return true;
        }
        if (draggingBedrock != null && activePanel != null) {
            int sw = 190, sx = activePanel.x + Panel.W + 8; if (sx + sw > this.width / uiScale) sx = activePanel.x - sw - 8;
            int cy = activePanel.y + findSettingYOffset(draggingBedrock);
            int size = 7, spacing = 1;
            int gridW = 24 * (size + spacing) - spacing;
            int startX = sx + (sw - gridW) / 2;
            int startY = cy + 18;
            int mx = smx, my = smy;
            for (int x = 0; x < 24; x++) {
                for (int z = 0; z < 24; z++) {
                    int ix = startX + x * (size + spacing);
                    int iy = startY + z * (size + spacing);
                    if (RenderUtils.isHovered(mx, my, ix, iy, size, size)) {
                        draggingBedrock.set(x, z, draggingBedrockState);
                    }
                }
            }
            return true;
        }
        for (Panel p : panels) if (p.mouseDragged(smx, smy)) return true; return super.mouseDragged(c, ox, oy);
    }

    private int findSettingYOffset(Setting<?> s) {
        if (activeModule == null) return 0; int y = Panel.HEADER_H + 2 - (int)settingsScroll;
        for (Setting<?> set : activeModule.getSettings()) { if (set == s) return y; y += getSettingHeight(set) + 2; }
        return 0;
    }

    @Override public boolean keyPressed(KeyInput e) {
        if (focusedSetting instanceof ItemSetting s) { if (e.key() == 259) { String sr = itemSearches.getOrDefault(s, ""); if (!sr.isEmpty()) itemSearches.put(s, sr.substring(0, sr.length() - 1)); } return true; }
        if (focusedSetting instanceof StringSetting s) {
            String cur = stringBuffers.getOrDefault(s, s.getValue());
            if (e.key() == 259 && !cur.isEmpty()) { stringBuffers.put(s, cur.substring(0, cur.length() - 1)); return true; }
            if (e.key() == 257 || e.key() == 335) { s.setValue(stringBuffers.getOrDefault(s, s.getValue())); focusedSetting = null; return true; }
            if (e.isEscape()) { focusedSetting = null; return true; }

            // Paste support (Ctrl + V)
            if (e.key() == GLFW.GLFW_KEY_V && (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL))) {
                String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
                stringBuffers.put(s, cur + clipboard);
                return true;
            }
            return true;
        }
        if (activeModule != null) for (Setting<?> s : activeModule.getSettings()) if (s instanceof KeybindSetting k && k.isListening()) { k.setKey(e.key()); return true; }
        if (e.isEscape()) { this.client.setScreen(null); return true; } return super.keyPressed(e);
    }

    @Override public boolean charTyped(CharInput i) {
        if (focusedSetting instanceof ItemSetting s) { String sr = itemSearches.getOrDefault(s, ""); itemSearches.put(s, sr + i.asString()); return true; }
        if (focusedSetting instanceof StringSetting s) { String cur = stringBuffers.getOrDefault(s, s.getValue()); stringBuffers.put(s, cur + i.asString()); return true; }
        return super.charTyped(i);
    }
    @Override public boolean shouldPause() { return false; }

    static class Panel {
        public final Category category; public int x, y; public static final int W = 158, HEADER_H = 22, MOD_H = 22, SET_H = 20;
        private boolean dragging; private int dragOffX, dragOffY; private boolean collapsed = false;
        private final List<float[]> bubblesTemplate = new ArrayList<>();

        Panel(Category cat, int x, int y) {
            this.category = cat; this.x = x; this.y = y;
            for(int i=0; i<6; i++) bubblesTemplate.add(new float[]{(float)Math.random()*W, (float)Math.random()*MOD_H, 0.5f+(float)Math.random()*0.5f, (float)Math.random()*10, 1.0f+(float)Math.random()*1.0f});
        }

        void render(DrawContext ctx, int mx, int my, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer; List<Module> mods = Gfm_recodeClient.modules.getByCategory(category);
            int totalH = HEADER_H + (collapsed ? 0 : mods.size() * MOD_H + 10);
            
            RenderUtils.drawDropShadow(ctx, x, y, W, totalH, 15);
            RenderUtils.fillRoundedRect(ctx, x, y, W, totalH, 15, BG_BODY);
            RenderUtils.fillRoundedRect(ctx, x, y, W, HEADER_H, 15, BG_HEADER, true, true, false, false);
            
            // Header: Category Name + Icon
            String icon = "•";
            if (category == Category.RENDER) icon = "👁";
            else if (category == Category.PLAYER) icon = "👤";
            else if (category == Category.WORLD) icon = "🌍";
            else if (category == Category.FARM) icon = "🚜";
            else if (category == Category.MISC) icon = "⚙";
            else if (category == Category.CLIENT) icon = "⚙";
            ctx.drawText(tr, icon, x + 10, y + 6, ACCENT_START, false);
            ctx.drawText(tr, category.displayName, x + 30, y + 7, TEXT_SEC, false);
            
            if (collapsed) return;
            int cy = y + HEADER_H + 5;
            for (Module m : mods) {
                boolean hovered = RenderUtils.isHovered(mx, my, x + 6, cy, W - 12, MOD_H - 2);
                RenderUtils.fillRoundedRect(ctx, x + 6, cy, W - 12, MOD_H - 2, 8, BG_MODULE);
                
                int textColor = m.isEnabled() ? ACCENT_START : TEXT_DIM;
                ctx.drawText(tr, m.name, x + (W - tr.getWidth(m.name)) / 2, cy + 5, textColor, m.isEnabled());
                
                // Indicator Dot
                RenderUtils.drawCircle(ctx, x + W - 15, cy + 9, 2.5f, m.isEnabled() ? INDICATOR_ON : INDICATOR_OFF);
                
                cy += MOD_H;
            }
        }
        boolean mouseClicked(int imx, int imy, int button) {
            if (RenderUtils.isHovered(imx, imy, x, y, W, HEADER_H)) { if (button == 1) collapsed = !collapsed; else { dragging = true; dragOffX = x - imx; dragOffY = y - imy; } return true; }
            if (collapsed) return false; int cy = y + HEADER_H;
            for (Module m : Gfm_recodeClient.modules.getByCategory(category)) { 
                boolean disabledByMode = Gfm_recodeClient.currentMode == de.glutenfreierkeks.gfm_recode.client.ClientMode.MACRO && !m.macroAllowed;
                if (disabledByMode) { cy += MOD_H; continue; }
                if (RenderUtils.isHovered(imx, imy, x, cy, W, MOD_H)) { if (button == 0) m.toggle(); else { activeModule = m; activePanel = this; settingsScroll = 0; } return true; } cy += MOD_H; 
            }
            return false;
        }
        boolean mouseReleased() { dragging = false; return true; }
        boolean mouseDragged(int smx, int smy) { if (dragging) { x = smx + dragOffX; y = smy + dragOffY; return true; } return false; }
    }
}
