package de.glutenfreierkeks.gfm_recode.client.gui.web;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks framebuffer/native resolution and GUI scale changes.
 */
public final class ResolutionManager {

    public interface Listener {
        void onResolutionChanged(ResolutionSnapshot previous, ResolutionSnapshot current);
    }

    public record ResolutionSnapshot(
            int framebufferWidth,
            int framebufferHeight,
            int scaledWidth,
            int scaledHeight,
            int scaleFactor
    ) {
        public int toFramebufferX(double scaledX) {
            return clamp((int) Math.round(scaledX * framebufferWidth / (double) Math.max(1, scaledWidth)), 0, Math.max(0, framebufferWidth - 1));
        }

        public int toFramebufferY(double scaledY) {
            return clamp((int) Math.round(scaledY * framebufferHeight / (double) Math.max(1, scaledHeight)), 0, Math.max(0, framebufferHeight - 1));
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static final ResolutionManager INSTANCE = new ResolutionManager();

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile ResolutionSnapshot currentSnapshot;
    private volatile boolean initialized;

    private ResolutionManager() {
    }

    public static ResolutionManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        refresh(true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> refresh(false));
    }

    public ResolutionSnapshot getCurrent() {
        ResolutionSnapshot snapshot = currentSnapshot;
        if (snapshot == null) {
            refresh(true);
            snapshot = currentSnapshot;
        }
        return Objects.requireNonNull(snapshot, "resolution snapshot");
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        ResolutionSnapshot current = getCurrent();
        listener.onResolutionChanged(current, current);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void refresh(boolean force) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        ResolutionSnapshot next = capture(client.getWindow());
        ResolutionSnapshot previous = currentSnapshot;
        if (force || previous == null || !previous.equals(next)) {
            currentSnapshot = next;
            for (Listener listener : listeners) {
                listener.onResolutionChanged(previous == null ? next : previous, next);
            }
        }
    }

    private static ResolutionSnapshot capture(Window window) {
        return new ResolutionSnapshot(
                Math.max(1, window.getFramebufferWidth()),
                Math.max(1, window.getFramebufferHeight()),
                Math.max(1, window.getScaledWidth()),
                Math.max(1, window.getScaledHeight()),
                Math.max(1, window.getScaleFactor())
        );
    }
}
