package de.glutenfreierkeks.gfm_recode.client.gui;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.Notifier;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class NotifierRenderer {

    private static final int NOTIFICATION_WIDTH = 200;
    private static final int NOTIFICATION_HEIGHT = 50;
    private static final int PADDING = 10;
    private static final int GAP = 8;

    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            render(drawContext);
        });
    }

    private static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.options.hudHidden) return;

        // Get notifier module
        Notifier notifier = Gfm_recodeClient.modules != null
            ? (Notifier) Gfm_recodeClient.modules.getByName("Notifier")
            : null;

        if (notifier == null || !notifier.isEnabled()) return;

        List<Notifier.Notification> notifications = notifier.getNotifications();
        if (notifications.isEmpty()) return;

        Notifier.NotificationPosition position = notifier.getNotificationPosition();
        int durationMs = notifier.getNotificationDurationMs();
        long now = System.currentTimeMillis();

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int index = 0;
        for (Notifier.Notification notif : notifications) {
            float alpha = notif.getAlpha(now, durationMs);
            if (alpha <= 0) continue;

            int x = getX(position, screenWidth, index);
            int y = getY(position, screenHeight, index);

            renderNotification(context, notif, x, y, alpha);
            index++;
        }
    }

    private static int getX(Notifier.NotificationPosition position, int screenWidth, int index) {
        return switch (position) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - NOTIFICATION_WIDTH - PADDING;
            case TOP_LEFT, BOTTOM_LEFT -> PADDING;
        };
    }

    private static int getY(Notifier.NotificationPosition position, int screenHeight, int index) {
        int offset = index * (NOTIFICATION_HEIGHT + GAP);
        return switch (position) {
            case TOP_RIGHT, TOP_LEFT -> PADDING + offset;
            case BOTTOM_RIGHT, BOTTOM_LEFT -> screenHeight - NOTIFICATION_HEIGHT - PADDING - offset;
        };
    }

    private static void renderNotification(DrawContext context, Notifier.Notification notif, int x, int y, float alpha) {
        int bgColor = 0xCC1A1A2E;
        int borderColor = notif.type.color;
        int textColor = 0xFFFFFFFF;
        int subtextColor = 0xFFAAAAAA;

        // Apply alpha
        bgColor = applyAlpha(bgColor, alpha);
        borderColor = applyAlpha(borderColor, alpha);
        textColor = applyAlpha(textColor, alpha);
        subtextColor = applyAlpha(subtextColor, alpha);

        // Background
        context.fill(x, y, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, bgColor);

        // Border (left accent)
        context.fill(x, y, x + 3, y + NOTIFICATION_HEIGHT, borderColor);

        // Top border
        context.fill(x, y, x + NOTIFICATION_WIDTH, y + 1, borderColor);

        // Title
        context.drawTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            notif.title,
            x + 10,
            y + 8,
            textColor
        );

        // Message
        context.drawTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            notif.message,
            x + 10,
            y + 26,
            subtextColor
        );
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        int rgb = color & 0xFFFFFF;
        return (a << 24) | rgb;
    }
}
