package de.glutenfreierkeks.gfm_recode.client.gui.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BrowserEspOverlayState {
    private static volatile List<Map<String, Object>> entries = List.of();

    private BrowserEspOverlayState() {}

    public static void setEntries(List<Map<String, Object>> newEntries) {
        entries = List.copyOf(newEntries);
    }

    public static List<Map<String, Object>> getEntries() {
        return new ArrayList<>(entries);
    }

    public static void clear() {
        entries = List.of();
    }
}
