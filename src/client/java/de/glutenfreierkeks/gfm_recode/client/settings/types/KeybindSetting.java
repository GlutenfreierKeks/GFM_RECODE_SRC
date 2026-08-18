package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * A setting that lets the player bind a keyboard key to a module action.
 * Value is the GLFW key code. Use {@link #getKeyName()} for display.
 */
public class KeybindSetting extends Setting<Integer> {
    public static final int MOUSE_BASE = 1000;

    /** Whether the GUI is currently waiting for a key press to assign. */
    private boolean listening = false;
    private String cachedName = null;

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    /** Default: no key bound (GLFW_KEY_UNKNOWN = -1). */
    public KeybindSetting(String name, String description) {
        this(name, description, GLFW.GLFW_KEY_UNKNOWN);
    }

    public boolean isListening() { return listening; }
    public void    startListening() { listening = true; }
    public void    stopListening()  { listening = false; }

    @Override
    public void setValue(Integer v) {
        if (this.value == null || !this.value.equals(v)) {
            super.setValue(v);
            cachedName = null; // Invalidate cache
        }
    }

    /**
     * Call this from the GUI's keyPressed handler while listening.
     * Assigns the key and stops listening.
     */
    public void setKey(int key) {
        setValue(key);
        stopListening();
    }

    /** Returns true if this key is currently pressed. */
    public boolean isPressed(long windowHandle) {
        if (value == null || value <= 0) return false;
        if (value >= MOUSE_BASE) {
            return GLFW.glfwGetMouseButton(windowHandle, value - MOUSE_BASE) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(windowHandle, value) == GLFW.GLFW_PRESS;
    }

    /** Human-readable key name. */
    public String getKeyName() {
        if (value == null || value == GLFW.GLFW_KEY_UNKNOWN) return "None";

        if (cachedName != null) return cachedName;

        if (!RenderSystem.isOnRenderThread()) {
            return getFallbackName();
        }

        try {
            String name = GLFW.glfwGetKeyName(value, 0);
            if (name != null && !name.isBlank()) {
                cachedName = name.toUpperCase();
                return cachedName;
            }
        } catch (Exception ignored) {}

        cachedName = getFallbackName();
        return cachedName;
    }

    private String getFallbackName() {
        if (value >= MOUSE_BASE) {
            return "MOUSE" + (value - MOUSE_BASE + 1);
        }
        // Fallback for special keys
        return switch (value) {
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_LEFT_SHIFT   -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT  -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL-> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT     -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT    -> "RALT";
            case GLFW.GLFW_KEY_CAPS_LOCK    -> "CAPS";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_ENTER        -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE    -> "BKSP";
            case GLFW.GLFW_KEY_DELETE       -> "DEL";
            case GLFW.GLFW_KEY_ESCAPE       -> "ESC";
            case GLFW.GLFW_KEY_SPACE        -> "SPACE";
            case GLFW.GLFW_KEY_UP           -> "UP";
            case GLFW.GLFW_KEY_DOWN         -> "DOWN";
            case GLFW.GLFW_KEY_LEFT         -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT        -> "RIGHT";
            case GLFW.GLFW_KEY_INSERT       -> "INS";
            case GLFW.GLFW_KEY_HOME         -> "HOME";
            case GLFW.GLFW_KEY_END          -> "END";
            case GLFW.GLFW_KEY_PAGE_UP      -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN    -> "PGDN";
            default -> "KEY_" + value;
        };
    }

    @Override
    public SettingType getType() { return SettingType.KEYBIND; }
}
