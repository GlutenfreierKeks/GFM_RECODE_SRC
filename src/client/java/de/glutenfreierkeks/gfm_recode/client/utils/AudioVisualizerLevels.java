package de.glutenfreierkeks.gfm_recode.client.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lightweight visualizer band data (no audio capture / PowerShell). */
public final class AudioVisualizerLevels {
    private static final int BANDS = 20;

    private AudioVisualizerLevels() {}

    public static void apply(Map<String, Object> data) {
        boolean playing = Boolean.TRUE.equals(data.get("available"))
                && !"Paused".equalsIgnoreCase(String.valueOf(data.getOrDefault("status", "")));

        String title = String.valueOf(data.getOrDefault("title", ""));
        float[] levels = computeBands(playing, title);
        List<Double> list = new ArrayList<>(BANDS);
        for (float level : levels) {
            list.add((double) level);
        }
        data.put("levels", list);
    }

    private static float[] computeBands(boolean playing, String title) {
        float[] out = new float[BANDS];
        if (!playing) {
            for (int i = 0; i < BANDS; i++) {
                out[i] = 0.06f;
            }
            return out;
        }

        long t = System.currentTimeMillis();
        int seed = title.hashCode();

        for (int i = 0; i < BANDS; i++) {
            double phase = t * 0.009 + i * 0.62 + (seed % 97) * 0.008;
            double wave = Math.abs(Math.sin(phase)) * 0.55 + Math.abs(Math.sin(phase * 2.1 + i)) * 0.35;
            double value = 0.12 + wave * 0.55;
            out[i] = (float) Math.min(1.0, Math.max(0.08, value));
        }
        return out;
    }
}
