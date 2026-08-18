package de.glutenfreierkeks.gfm_recode.client.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Background poll only — never blocks the render thread.
 */
public final class NowPlayingPoller {
    private static final long POLL_INTERVAL_MS = 5000L;

    private static volatile boolean running;
    private static volatile Map<String, Object> cached = defaultState();
    private static volatile String cachedJson = "{\"available\":false,\"reason\":\"Off\",\"connected\":true}";

    private NowPlayingPoller() {}

    public static void start() {
        if (running) {
            return;
        }
        running = true;
        Thread t = new Thread(NowPlayingPoller::loop, "GFM-NowPlaying");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        running = false;
    }

    public static Map<String, Object> getCached() {
        return new LinkedHashMap<>(cached);
    }

    public static Map<String, Object> getCurrent() {
        return getCached();
    }

    public static String getCachedJson() {
        return cachedJson;
    }

    private static void loop() {
        while (running) {
            try {
                Map<String, Object> data = WindowTitleNowPlaying.fetchBlocking();
                data.put("connected", true);
                if (Boolean.TRUE.equals(data.get("available"))) {
                    data.put("status", normalizeStatus(String.valueOf(data.getOrDefault("status", "Playing"))));
                    if (!data.containsKey("albumArt")) {
                        data.put("albumArt", "");
                    }
                }
                cached = data;
                cachedJson = buildJson(data);
            } catch (Exception ignored) {
                Map<String, Object> err = defaultState();
                err.put("reason", "Poll error");
                cached = err;
                cachedJson = buildJson(err);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static Map<String, Object> defaultState() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("connected", true);
        data.put("reason", "Off");
        return data;
    }

    private static String normalizeStatus(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("pause")) {
            return "Paused";
        }
        if (lower.contains("play")) {
            return "Playing";
        }
        return raw.isBlank() ? "Playing" : raw;
    }

    private static String buildJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (val instanceof Iterable<?> list) {
                sb.append("[");
                boolean innerFirst = true;
                for (Object item : list) {
                    if (!innerFirst) {
                        sb.append(",");
                    }
                    sb.append(item);
                    innerFirst = false;
                }
                sb.append("]");
            } else {
                sb.append(val);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
