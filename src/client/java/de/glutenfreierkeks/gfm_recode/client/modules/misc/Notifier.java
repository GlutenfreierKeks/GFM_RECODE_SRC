package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Notifier extends Module {

    private final BoolSetting toggleSounds;
    private final BoolSetting moduleToggleSounds;
    private final BoolSetting storageFinderNotify;
    private final BoolSetting espPlayerNotify;
    private final BoolSetting spawnerNotify;
    private final BoolSetting waypointNotify;
    private final BoolSetting lowDurabilityNotify;
    private final IntSliderSetting notificationDuration;
    private final EnumSetting<NotificationPosition> notificationPosition;
    private final IntSliderSetting minStorageCount;
    private final IntSliderSetting minDurabilityPercent;

    private final List<Notification> notifications = new ArrayList<>();
    private long lastStorageNotification = 0;
    private long lastEspNotification = 0;
    private long lastSpawnerNotification = 0;
    private long lastDurabilityNotification = 0;

    public enum NotificationPosition {
        TOP_RIGHT("Top Right"),
        TOP_LEFT("Top Left"),
        BOTTOM_RIGHT("Bottom Right"),
        BOTTOM_LEFT("Bottom Left");

        public final String displayName;
        NotificationPosition(String displayName) { this.displayName = displayName; }
    }

    public enum NotifierSound {
        THOCK("Thock"),
        CLICK("Click"),
        POP("Pop"),
        BELL("Bell"),
        CHIME("Chime"),
        SOFT("Soft"),
        CRISP("Crisp"),
        MECHANICAL("Mechanical"),
        CUSTOM_ON("GFM Enable"),
        CUSTOM_OFF("GFM Disable"),
        CUSTOM_WARNING("GFM Warning");

        public final String displayName;
        NotifierSound(String displayName) { this.displayName = displayName; }

        public Identifier getSoundId() {
            return switch (this) {
                case THOCK -> Identifier.of("minecraft", "block.note_block.hat");
                case CLICK -> Identifier.of("minecraft", "block.note_block.click");
                case POP -> Identifier.of("minecraft", "entity.item.pickup");
                case BELL -> Identifier.of("minecraft", "block.note_block.bell");
                case CHIME -> Identifier.of("minecraft", "block.note_block.chime");
                case SOFT -> Identifier.of("minecraft", "block.note_block.xylophone");
                case CRISP -> Identifier.of("minecraft", "block.note_block.pling");
                case MECHANICAL -> Identifier.of("minecraft", "block.piston.contract");
                case CUSTOM_ON -> Identifier.of("gfm_recode", "module_toggle_on");
                case CUSTOM_OFF -> Identifier.of("gfm_recode", "module_toggle_off");
                case CUSTOM_WARNING -> Identifier.of("gfm_recode", "notification");
            };
        }

        public float getPitch() {
            return 1.0f;
        }

        public float getVolume() {
            return 0.7f;
        }
    }

    private final EnumSetting<NotifierSound> toggleOnSound;
    private final EnumSetting<NotifierSound> toggleOffSound;
    private final EnumSetting<NotifierSound> alertSound;

    public Notifier() {
        super("Notifier", "Shows notifications and plays sounds for various events", Category.MISC);

        // Toggle sounds
        moduleToggleSounds = register(new BoolSetting("Module Toggle Sounds", "Play sounds when toggling modules", true));
        toggleOnSound = register(new EnumSetting<>("Toggle On Sound", "Sound when enabling a module", NotifierSound.CUSTOM_ON));
        toggleOffSound = register(new EnumSetting<>("Toggle Off Sound", "Sound when disabling a module", NotifierSound.CUSTOM_OFF));

        // Event notifications
        toggleSounds = register(new BoolSetting("Event Sounds", "Play sounds for events", true));
        alertSound = register(new EnumSetting<>("Alert Sound", "Sound for event notifications", NotifierSound.CUSTOM_WARNING));

        // Specific events
        storageFinderNotify = register(new BoolSetting("Storage Finder", "Notify when storage finder finds storages", true));
        minStorageCount = register(new IntSliderSetting("Min Storage Count", "Minimum storages to trigger notification", 10, 1, 50));

        espPlayerNotify = register(new BoolSetting("ESP Player", "Notify when ESP finds players", true));
        spawnerNotify = register(new BoolSetting("Spawner Found", "Notify when spawners are nearby", true));
        waypointNotify = register(new BoolSetting("Waypoint Reached", "Notify when reaching waypoints", true));

        lowDurabilityNotify = register(new BoolSetting("Low Durability", "Notify when items have low durability", true));
        minDurabilityPercent = register(new IntSliderSetting("Min Durability %", "Durability percentage to trigger warning", 10, 5, 50));

        // Display settings
        notificationDuration = register(new IntSliderSetting("Duration", "How long notifications stay visible (seconds)", 3, 1, 10));
        notificationPosition = register(new EnumSetting<>("Position", "Where to show notifications", NotificationPosition.TOP_RIGHT));
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Update notifications
        long now = System.currentTimeMillis();
        notifications.removeIf(n -> n.shouldRemove(now));

        // Check low durability
        if (lowDurabilityNotify.getValue()) {
            checkDurability(now);
        }
    }

    private void checkDurability(long now) {
        if (now - lastDurabilityNotification < 30000) return; // Max once per 30s

        int minPercent = minDurabilityPercent.getValue();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isDamageable()) {
                int maxDmg = stack.getMaxDamage();
                int curDmg = stack.getDamage();
                int remaining = maxDmg - curDmg;
                int percent = (remaining * 100) / maxDmg;

                if (percent <= minPercent) {
                    showNotification("Low Durability", stack.getName().getString() + " at " + percent + "%", NotificationType.WARNING);
                    if (toggleSounds.getValue()) playSound(alertSound.getValue());
                    lastDurabilityNotification = now;
                    return;
                }
            }
        }
    }

    public void onModuleToggle(Module module, boolean enabled) {
        if (!moduleToggleSounds.getValue()) return;

        NotifierSound sound = enabled ? toggleOnSound.getValue() : toggleOffSound.getValue();
        playSound(sound);

        // Show toggle notification
        String action = enabled ? "Enabled" : "Disabled";
        showNotification("Module " + action, module.name, enabled ? NotificationType.SUCCESS : NotificationType.INFO);
    }

    public void onStorageFinderFound(int count) {
        if (!storageFinderNotify.getValue()) return;
        if (count < minStorageCount.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastStorageNotification < 5000) return; // Max once per 5s

        showNotification("Storage Finder", count + " storages found!", NotificationType.INFO);
        if (toggleSounds.getValue()) playSound(alertSound.getValue());
        lastStorageNotification = now;
    }

    public void onEspPlayerFound(String playerName, double distance) {
        if (!espPlayerNotify.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastEspNotification < 10000) return; // Max once per 10s

        showNotification("Player Detected", playerName + " (" + String.format("%.1f", distance) + "m)", NotificationType.WARNING);
        if (toggleSounds.getValue()) playSound(alertSound.getValue());
        lastEspNotification = now;
    }

    public void onSpawnerFound(int count) {
        if (!spawnerNotify.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastSpawnerNotification < 5000) return; // Max once per 5s

        String msg = count == 1 ? "1 spawner nearby!" : count + " spawners nearby!";
        showNotification("Spawner Alert", msg, NotificationType.ALERT);
        if (toggleSounds.getValue()) playSound(alertSound.getValue());
        lastSpawnerNotification = now;
    }

    public void onWaypointReached(String waypointName) {
        if (!waypointNotify.getValue()) return;

        showNotification("Waypoint Reached", waypointName, NotificationType.SUCCESS);
        if (toggleSounds.getValue()) playSound(NotifierSound.CHIME);
    }

    public void showNotification(String title, String message, NotificationType type) {
        notifications.add(new Notification(title, message, type, System.currentTimeMillis()));

        // Also send to HTML GUI
        sendHtmlNotification(title, message, type.name().toLowerCase(), getNotificationDurationMs());
    }

    private void sendHtmlNotification(String title, String message, String type, int durationMs) {
        try {
            de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer.sendNotification(title, message, type, durationMs);
        } catch (Exception e) {
            // Silently ignore - WebUI might not be ready yet
        }
    }

    private void playSound(NotifierSound sound) {
        if (mc.player == null || mc.getSoundManager() == null) return;

        Identifier id = sound.getSoundId();
        if (id == null) return;

        mc.getSoundManager().play(
            new PositionedSoundInstance(
                id,
                SoundCategory.MASTER,
                sound.getVolume(),
                sound.getPitch(),
                SoundInstance.createRandom(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0, 0, 0,
                true
            )
        );
    }

    public List<Notification> getNotifications() {
        return new ArrayList<>(notifications);
    }

    public NotificationPosition getNotificationPosition() {
        return notificationPosition.getValue();
    }

    public int getNotificationDurationMs() {
        return notificationDuration.getValue() * 1000;
    }

    public enum NotificationType {
        INFO("Info", 0xFF00BCD4),
        SUCCESS("Success", 0xFF4CAF50),
        WARNING("Warning", 0xFFFF9800),
        ALERT("Alert", 0xFFF44336);

        public final String name;
        public final int color;

        NotificationType(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    public static class Notification {
        public final String title;
        public final String message;
        public final NotificationType type;
        public final long createdAt;

        public Notification(String title, String message, NotificationType type, long createdAt) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.createdAt = createdAt;
        }

        public boolean shouldRemove(long now) {
            return now - createdAt > 4000; // Remove after 4 seconds default
        }

        public float getAlpha(long now, int durationMs) {
            long age = now - createdAt;
            if (age < 200) {
                return age / 200f; // Fade in
            } else if (age > durationMs - 300) {
                return Math.max(0, (durationMs - age) / 300f); // Fade out
            }
            return 1.0f;
        }
    }
}
