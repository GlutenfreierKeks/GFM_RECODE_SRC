package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.ClientMode;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager;
import de.glutenfreierkeks.gfm_recode.client.config.ConfigManager;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class ClientSettings extends Module {

    private final EnumSetting<ClientMode> mode = new EnumSetting<>("Mode", "Client Mode", ClientMode.CHEAT);
    private final EnumSetting<AntiCheatProfileManager.Profile> antiCheat =
            new EnumSetting<>("AntiCheat", "Compatibility profile for modules", AntiCheatProfileManager.Profile.VANILLA);

    private final BoolSetting saveCurrent = new BoolSetting("Save default", "Overwrite config.json", false);
    private final BoolSetting loadDefault = new BoolSetting("Load default", "Reload config.json", false);

    private final StringSetting targetFile = new StringSetting("FileName", "Export or import target name", "legit_config");
    private final BoolSetting exportBtn = new BoolSetting("Export JSON", "Save the current config to FileName", false);
    private final BoolSetting importBtn = new BoolSetting("Import JSON", "Load FileName", false);

    private AntiCheatProfileManager.Profile lastProfile = AntiCheatProfileManager.Profile.VANILLA;

    public ClientSettings() {
        super("Config", "Client settings, anti-cheat profile and config management", Category.CLIENT);
        register(mode);
        register(antiCheat);
        register(saveCurrent);
        register(loadDefault);
        register(targetFile);
        register(exportBtn);
        register(importBtn);

        if (Gfm_recodeClient.currentMode != null) {
            mode.setValue(Gfm_recodeClient.currentMode);
        }
    }

    @Override
    public void onTick() {
        if (Gfm_recodeClient.currentMode != null && mode.getValue() != Gfm_recodeClient.currentMode) {
            Gfm_recodeClient.currentMode = mode.getValue();
        }

        if (antiCheat.getValue() != lastProfile) {
            lastProfile = antiCheat.getValue();
            AntiCheatProfileManager.applyProfile(lastProfile);
        }

        if (saveCurrent.getValue()) {
            saveCurrent.setValue(false);
            ConfigManager.saveConfig();
        }

        if (loadDefault.getValue()) {
            loadDefault.setValue(false);
            ConfigManager.loadConfig();
        }

        if (exportBtn.getValue()) {
            exportBtn.setValue(false);
            ConfigManager.saveConfig(targetFile.getValue());
        }

        if (importBtn.getValue()) {
            importBtn.setValue(false);
            ConfigManager.loadConfig(targetFile.getValue());
        }
    }

    public AntiCheatProfileManager.Profile getAntiCheatProfile() {
        return antiCheat.getValue();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }
}
