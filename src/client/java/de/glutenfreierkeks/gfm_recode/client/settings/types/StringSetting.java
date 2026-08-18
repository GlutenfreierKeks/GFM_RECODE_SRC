package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;

public class StringSetting extends Setting<String> {

    public StringSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    public SettingType getType() { return SettingType.STRING; }
}
