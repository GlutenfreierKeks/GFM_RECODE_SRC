package de.glutenfreierkeks.gfm_recode.client.gui.web;

/**
 * Compatibility wrapper for older initialization sites.
 */
public final class HudBrowserRenderer {
    private HudBrowserRenderer() {
    }

    public static void init() {
        HudRenderer.init();
    }
}
