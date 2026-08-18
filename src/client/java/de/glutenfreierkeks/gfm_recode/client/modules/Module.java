package de.glutenfreierkeks.gfm_recode.client.modules;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all modules.
 * Extend this and add settings in the constructor.
 */
public abstract class Module {

    // ── Identity ─────────────────────────────────────────────────
    public final String   name;
    public final String   description;
    public final Category category;

    // ── State ─────────────────────────────────────────────────────
    private boolean enabled = false;
    private KeybindSetting keybind;
    private KeybindType keybindType = KeybindType.TOGGLE;
    private boolean keyHeldDown = false;
    
    public boolean macroAllowed = true;

    public enum KeybindType {
        TOGGLE, HOLD
    }

    // ── Settings ──────────────────────────────────────────────────
    protected final List<Setting<?>> settings = new ArrayList<>();

    // ── Minecraft shortcut ────────────────────────────────────────
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public Module(String name, String description, Category category) {
        this.name        = name;
        this.description = description;
        this.category    = category;
        this.keybind     = register(new KeybindSetting("Keybind", "The key to toggle this module.", -1));
    }

    // ── Enable / Disable ──────────────────────────────────────────
    public void toggle() {
        boolean willEnable = !enabled;
        if (willEnable) {
            String reason = AntiCheatProfileManager.getBlockReason(this, AntiCheatProfileManager.getCurrentProfile());
            if (reason != null) {
                de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer.sendNotification("Module blocked", name + ": " + reason, "warning", 2600);
                return;
            }
        }
        if (enabled) {
            disable();
            de.glutenfreierkeks.gfm_recode.client.utils.SoundUtil.playToggleOff();
        } else {
            enable();
            de.glutenfreierkeks.gfm_recode.client.utils.SoundUtil.playToggleOn();
        }
        
        // Notify Notifier module
        if (Gfm_recodeClient.modules != null) {
            de.glutenfreierkeks.gfm_recode.client.modules.misc.Notifier notifier = 
                (de.glutenfreierkeks.gfm_recode.client.modules.misc.Notifier) 
                Gfm_recodeClient.modules.getByName("Notifier");
            if (notifier != null && notifier.isEnabled() && !this.name.equals("Notifier")) {
                notifier.onModuleToggle(this, willEnable);
            }
        }
    }

    public void enable() {
        String reason = AntiCheatProfileManager.getBlockReason(this, AntiCheatProfileManager.getCurrentProfile());
        if (reason != null) {
            de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer.sendNotification("Module blocked", name + ": " + reason, "warning", 2600);
            return;
        }
        enabled = true;
        onEnable();
    }

    public void disable() {
        enabled = false;
        onDisable();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            if (enabled) enable(); else disable();
        }
    }

    public boolean isEnabled() { return enabled; }

    public KeybindSetting getKeybindSetting() { return keybind; }
    public KeybindType getKeybindType() { return keybindType; }
    public void setKeybindType(KeybindType type) { this.keybindType = type; }
    public void cycleKeybindType() {
        this.keybindType = this.keybindType == KeybindType.TOGGLE ? KeybindType.HOLD : KeybindType.TOGGLE;
    }
    
    public void setKeyHeldDown(boolean held) { this.keyHeldDown = held; }
    public boolean isKeyHeldDown() { return keyHeldDown; }

    // ── Lifecycle hooks (override in subclass) ────────────────────
    protected void onEnable()  {}
    protected void onDisable() {}

    public abstract void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta);

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {}

    /** Called every client tick while the module is enabled. */
    public void onTick() {}

    /** Information to be displayed in the ArrayList. */
    public String getDisplayInfo() { return null; }

    // ── Settings helpers ──────────────────────────────────────────
    protected <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() { return settings; }

    // ── Category ──────────────────────────────────────────────────
    public enum Category {
        RENDER      ("Render"),
        WORLD       ("World"),
        PLAYER      ("Player"),
        FARM        ("Farm"),
        MISC        ("Misc"),
        CLIENT      ("Config");

        public final String displayName;
        Category(String displayName) { this.displayName = displayName; }
    }
}
