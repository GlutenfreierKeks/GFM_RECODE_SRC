package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.config.ConfigManager;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix4f;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Hud extends Module {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<WidgetId, BoolSetting> visibleSettings = new EnumMap<>(WidgetId.class);
    private final Map<WidgetId, IntSliderSetting> xSettings = new EnumMap<>(WidgetId.class);
    private final Map<WidgetId, IntSliderSetting> ySettings = new EnumMap<>(WidgetId.class);

    private WidgetId draggingWidget;
    private int dragOffsetX;
    private int dragOffsetY;
    private double lastX;
    private double lastZ;
    private double bps;

    public Hud() {
        super("HUD", "Shows a movable HUD with important client info", Category.RENDER);

        registerWidget(WidgetId.WATERMARK, true, 8, 8);
        registerWidget(WidgetId.ARRAYLIST, true, 8, 24);
        registerWidget(WidgetId.COORDS, true, 8, 150);
        registerWidget(WidgetId.CHUNK, true, 8, 164);
        registerWidget(WidgetId.DIRECTION, true, 8, 178);
        registerWidget(WidgetId.FPS, true, 8, 192);
        registerWidget(WidgetId.SPEED, true, 8, 206);
        registerWidget(WidgetId.PING, true, 8, 220);
        registerWidget(WidgetId.TIME, true, 8, 234);
        registerWidget(WidgetId.DIMENSION, true, 8, 248);
        registerWidget(WidgetId.BIOME, true, 8, 262);
        registerWidget(WidgetId.CONFIG, true, 8, 276);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        double dx = mc.player.getX() - lastX;
        double dz = mc.player.getZ() - lastZ;
        bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
        }
    }

    public void renderOverlay(DrawContext ctx, boolean editing) {
        if (mc.player == null || mc.world == null) return;

        for (WidgetId widget : WidgetId.values()) {
            if (!visibleSettings.get(widget).getValue()) continue;

            WidgetBox box = getWidgetBox(widget);
            drawWidget(ctx, widget, box, editing);
        }

        if (editing) {
            renderEditorPanel(ctx);
        }
    }

    public boolean handleChatClick(Click click) {
        if (!(mc.currentScreen instanceof ChatScreen)) return false;

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int panelX = mc.getWindow().getScaledWidth() - 132;
        int panelY = 12;

        int row = (mouseY - (panelY + 20)) / 12;
        if (mouseX >= panelX && mouseX <= panelX + 120 && mouseY >= panelY + 20 && row >= 0 && row < WidgetId.values().length) {
            WidgetId widget = WidgetId.values()[row];
            visibleSettings.get(widget).toggle();
            return true;
        }

        for (WidgetId widget : WidgetId.values()) {
            if (!visibleSettings.get(widget).getValue()) continue;
            WidgetBox box = getWidgetBox(widget);
            if (box.contains(mouseX, mouseY)) {
                draggingWidget = widget;
                dragOffsetX = mouseX - box.x;
                dragOffsetY = mouseY - box.y;
                return true;
            }
        }

        return false;
    }

    public boolean handleChatDrag(Click click) {
        if (draggingWidget == null) return false;

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        xSettings.get(draggingWidget).setValue(mouseX - dragOffsetX);
        ySettings.get(draggingWidget).setValue(mouseY - dragOffsetY);
        return true;
    }

    public boolean handleChatRelease() {
        if (draggingWidget == null) return false;
        draggingWidget = null;
        return true;
    }

    private void drawWidget(DrawContext ctx, WidgetId widget, WidgetBox box, boolean editing) {
        List<String> lines = getWidgetLines(widget);
        if (lines.isEmpty()) return;

        int y = box.y;
        for (String line : lines) {
            int width = mc.textRenderer.getWidth(line);
            ctx.fill(box.x - 2, y - 1, box.x + width + 2, y + 9, 0x660A0814);
            ctx.drawText(mc.textRenderer, line, box.x, y, 0xFFFFFFFF, true);
            y += 10;
        }

        if (editing) {
            ctx.fill(box.x - 3, box.y - 2, box.x + box.width + 3, box.y - 1, 0xFFB06AFF);
            ctx.fill(box.x - 3, box.y + box.height + 1, box.x + box.width + 3, box.y + box.height + 2, 0xFFB06AFF);
            ctx.fill(box.x - 3, box.y - 2, box.x - 2, box.y + box.height + 2, 0xFFB06AFF);
            ctx.fill(box.x + box.width + 2, box.y - 2, box.x + box.width + 3, box.y + box.height + 2, 0xFFB06AFF);
            ctx.drawText(mc.textRenderer, widget.label, box.x, box.y - 11, 0xFFB8A9D7, false);
        }
    }

    private void renderEditorPanel(DrawContext ctx) {
        int panelX = mc.getWindow().getScaledWidth() - 132;
        int panelY = 12;
        ctx.fill(panelX, panelY, panelX + 120, panelY + 164, 0xCC0A0814);
        ctx.fill(panelX, panelY, panelX + 120, panelY + 16, 0xFF161020);
        ctx.drawText(mc.textRenderer, "HUD Editor", panelX + 6, panelY + 4, 0xFFFFFFFF, false);

        int rowY = panelY + 22;
        for (WidgetId widget : WidgetId.values()) {
            boolean visible = visibleSettings.get(widget).getValue();
            ctx.fill(panelX + 4, rowY - 1, panelX + 116, rowY + 9, visible ? 0x6638C46A : 0x55362230);
            ctx.drawText(mc.textRenderer, widget.label, panelX + 8, rowY, 0xFFFFFFFF, false);
            ctx.drawText(mc.textRenderer, visible ? "ON" : "OFF", panelX + 92, rowY, visible ? 0xFF8FFF9A : 0xFFFF8F9A, false);
            rowY += 12;
        }
    }

    private WidgetBox getWidgetBox(WidgetId widget) {
        List<String> lines = getWidgetLines(widget);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.textRenderer.getWidth(line));
        }
        int height = Math.max(10, lines.size() * 10);
        return new WidgetBox(xSettings.get(widget).getValue(), ySettings.get(widget).getValue(), width, height);
    }

    private List<String> getWidgetLines(WidgetId widget) {
        List<String> lines = new ArrayList<>();
        switch (widget) {
            case WATERMARK -> lines.add("GFM Recode");
            case ARRAYLIST -> lines.addAll(getArraylistLines());
            case COORDS -> lines.add("XYZ: " + format(mc.player.getX()) + " / " + format(mc.player.getY()) + " / " + format(mc.player.getZ()));
            case CHUNK -> lines.add("Chunk: " + (mc.player.getBlockX() >> 4) + " / " + (mc.player.getBlockZ() >> 4));
            case DIRECTION -> lines.add("Facing: " + getFacingName(mc.player.getHorizontalFacing()));
            case FPS -> lines.add("FPS: " + mc.getCurrentFps());
            case SPEED -> lines.add(String.format(Locale.US, "Speed: %.2f b/s", bps));
            case PING -> lines.add("Ping: " + getPing());
            case TIME -> lines.add("Time: " + LocalTime.now().format(TIME_FORMAT));
            case DIMENSION -> lines.add("Dimension: " + mc.world.getRegistryKey().getValue().getPath());
            case BIOME -> lines.add("Biome: " + getBiomeName());
            case CONFIG -> lines.add("Config: " + ConfigManager.getCurrentConfigName());
        }
        return lines;
    }

    private List<String> getArraylistLines() {
        List<Module> enabled = Gfm_recodeClient.modules.getAll().stream()
            .filter(module -> module.isEnabled() && module != this)
            .sorted(Comparator.comparingInt((Module module) -> mc.textRenderer.getWidth(getModuleLine(module))).reversed())
            .toList();

        List<String> lines = new ArrayList<>();
        for (Module module : enabled) {
            lines.add(getModuleLine(module));
        }
        if (lines.isEmpty()) lines.add("No modules enabled");
        return lines;
    }

    private String getModuleLine(Module module) {
        String info = module.getDisplayInfo();
        return info == null || info.isEmpty() ? module.name : module.name + " [" + info + "]";
    }

    private String getPing() {
        if (mc.getNetworkHandler() == null || mc.player == null) return "-";
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? "-" : String.valueOf(entry.getLatency());
    }

    private String getBiomeName() {
        RegistryEntry<Biome> biome = mc.world.getBiome(mc.player.getBlockPos());
        return biome.getKey().map(key -> key.getValue().getPath()).orElse("unknown");
    }

    private String getFacingName(Direction direction) {
        return switch (direction) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            default -> direction.asString();
        };
    }

    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private void registerWidget(WidgetId id, boolean visible, int x, int y) {
        BoolSetting visibility = register(new BoolSetting(id.label + " Visible", "Show " + id.label, visible));
        IntSliderSetting xPos = register(new IntSliderSetting(id.label + " X", "X position for " + id.label, x, 0, 4000));
        IntSliderSetting yPos = register(new IntSliderSetting(id.label + " Y", "Y position for " + id.label, y, 0, 4000));
        visibleSettings.put(id, visibility);
        xSettings.put(id, xPos);
        ySettings.put(id, yPos);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    private enum WidgetId {
        WATERMARK("Watermark"),
        ARRAYLIST("ArrayList"),
        COORDS("Coords"),
        CHUNK("Chunk"),
        DIRECTION("Direction"),
        FPS("FPS"),
        SPEED("Speed"),
        PING("Ping"),
        TIME("Time"),
        DIMENSION("Dimension"),
        BIOME("Biome"),
        CONFIG("Config");

        private final String label;

        WidgetId(String label) {
            this.label = label;
        }
    }

    private record WidgetBox(int x, int y, int width, int height) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x - 3 && mouseX <= x + width + 3 && mouseY >= y - 3 && mouseY <= y + height + 3;
        }
    }

    // Web UI methods for settings
    public java.util.Map<String, Object> getWebSettings() {
        java.util.Map<String, Object> settings = new java.util.HashMap<>();
        for (WidgetId widget : WidgetId.values()) {
            String key = widget.label + " Visible";
            settings.put(key, visibleSettings.get(widget).getValue());
        }
        return settings;
    }

    public void applyWebSettings(java.util.Map<String, Object> settings) {
        for (java.util.Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean bool) {
                for (WidgetId widget : WidgetId.values()) {
                    if (key.equals(widget.label + " Visible")) {
                        visibleSettings.get(widget).setValue(bool);
                        break;
                    }
                }
            }
        }
    }
}
