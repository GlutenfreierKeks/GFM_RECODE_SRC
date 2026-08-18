package de.glutenfreierkeks.gfm_recode.client;

import de.glutenfreierkeks.gfm_recode.client.modules.ModuleManager;
import de.glutenfreierkeks.gfm_recode.client.gui.web.GuiScreen;
import de.glutenfreierkeks.gfm_recode.client.gui.web.HudRenderer;
import de.glutenfreierkeks.gfm_recode.client.gui.web.ResolutionManager;
import de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer;
import de.glutenfreierkeks.gfm_recode.client.discord.DiscordPresenceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main client entrypoint.
 */
public class Gfm_recodeClient implements ClientModInitializer {
    public static ModuleManager modules;
    public static final String NAME = "GFM";
    public static final String VERSION = "2.0";
    public static final Logger LOG = LoggerFactory.getLogger(NAME);

    public static ClientMode currentMode = null;

    private KeyBinding owoGuiKey;
    private KeyBinding clickGuiKey;

    @Override
    public void onInitializeClient() {
        LOG.info("Registering GFM Client events...");

        // Key bindings
        owoGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gfm.owogui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                KeyBinding.Category.GAMEPLAY
        ));

        clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gfm.clickgui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.GAMEPLAY
        ));

        // Start the local API/static-file server (com.sun.net.httpserver – built into JDK)
        WebUiServer.start();

        // Initialize modules and HUD when Minecraft is ready
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOG.info("Minecraft started, initializing modules...");

            ResolutionManager.getInstance().initialize();
            de.glutenfreierkeks.gfm_recode.client.gui.web.BrowserRenderer.initializeBackendAsync();
            modules = new ModuleManager();
            de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.loadConfig();

            // Initialize persistent HTML HUD renderer.
            HudRenderer.init();
            LOG.info("HUD renderer registered.");
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            DiscordPresenceManager.shutdown();
            HudRenderer.close();
            de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer.stop();
            de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (modules == null) return;

            modules.tick(client);

            boolean owoPressed = owoGuiKey.wasPressed();
            boolean clickPressed = clickGuiKey.wasPressed();

            if (clickPressed) {
                if (client.currentScreen == null ||
                        client.currentScreen instanceof de.glutenfreierkeks.gfm_recode.client.gui.imgui.MenuScreen) {
                    de.glutenfreierkeks.gfm_recode.client.gui.imgui.Menu.getInstance().toggle();
                }
            }

            if (owoPressed) {
                if (client.currentScreen instanceof GuiScreen ||
                        client.currentScreen instanceof de.glutenfreierkeks.gfm_recode.client.gui.screens.ModeSelectionScreen) {
                    client.setScreen(null);
                    client.mouse.lockCursor();
                } else if (client.currentScreen == null) {
                    if (currentMode == null) {
                        client.setScreen(new de.glutenfreierkeks.gfm_recode.client.gui.screens.ModeSelectionScreen());
                    } else {
                        client.setScreen(new GuiScreen());
                    }
                }
            }
        });

        // Poke CEF every frame to ensure smooth rendering
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            var cefApp = de.glutenfreierkeks.gfm_recode.client.gui.web.BrowserRenderer.getInitializedApp();
            if (cefApp != null) {
                cefApp.doMessageLoopWork(0L);
            }
            if (modules != null) {
                for (var module : modules.getAll()) {
                    if (module.isEnabled()) {
                        module.render2D(drawContext, tickCounter);
                    }
                }
            }
        });
    }
}
