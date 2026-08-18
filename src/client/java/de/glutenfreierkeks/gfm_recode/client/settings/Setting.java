package de.glutenfreierkeks.gfm_recode.client.settings;

/**
 * Base class for all settings.
 * Every setting has a name, description, and a generic value.
 */
public abstract class Setting<T> {

    public final String name;
    public final String description;

    protected T value;
    protected final T defaultValue;

    public Setting(String name, String description, T defaultValue) {
        this.name         = name;
        this.description  = description;
        this.defaultValue = defaultValue;
        this.value        = defaultValue;
    }

    public T getValue()        { return value; }
    public void setValue(T v)  { this.value = v; }
    public T getDefaultValue() { return defaultValue; }

    public void reset() { this.value = defaultValue; }

    /** Used by GUI to know what kind of widget to render. */
    public abstract SettingType getType();

    public enum SettingType {
        BOOL,
        SLIDER_INT,
        SLIDER_DOUBLE,
        ENUM,
        STRING,
        COLOR,
        ITEM,
        INVENTORY,
        KEYBIND,
        BEDROCK_GRID
    }
}
