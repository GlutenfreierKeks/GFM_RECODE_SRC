package de.glutenfreierkeks.gfm_recode.client.utils;

import de.glutenfreierkeks.gfm_recode.client.modules.ModuleManager;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.SoundVolume;
import de.glutenfreierkeks.gfm_recode.client.config.ConfigManager;
import de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SoundUtil {
    private static final String DEFAULT_SOUND = "default";

    public static void playToggleOn() {
        playSound("sounds/toggle_on.wav", ClientSettingsState.toggleOnSound);
    }

    public static void playToggleOff() {
        playSound("sounds/toggle_off.wav", ClientSettingsState.toggleOffSound);
    }

    public static void playNotification() {
        playSound("sounds/warning.wav", ClientSettingsState.notificationSound);
    }

    private static void playSound(String defaultPath, String selectedSound) {
        if (!ClientSettingsState.playNotificationSounds) {
            return;
        }
        try (InputStream inputStream = openSoundStream(defaultPath, selectedSound)) {
            if (inputStream == null) {
                System.err.println("Sound file not found: " + selectedSound);
                return;
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(inputStream));
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            
            // Apply volume from ClientSettingsState
            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double volume = ClientSettingsState.soundVolume;
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float range = max - min;
            float gain = (float) (min + (range * volume));
            volumeControl.setValue(gain);
            
            clip.start();
        } catch (Exception e) {
            System.err.println("Error playing sound: " + selectedSound);
            e.printStackTrace();
        }
    }

    private static InputStream openSoundStream(String defaultPath, String selectedSound) throws Exception {
        if (selectedSound != null && !selectedSound.isBlank() && !DEFAULT_SOUND.equals(selectedSound)) {
            File custom = ConfigManager.getSoundFile(selectedSound);
            if (custom.exists()) {
                return new FileInputStream(custom);
            }
        }
        return SoundUtil.class.getClassLoader().getResourceAsStream("assets/gfm_recode/" + defaultPath);
    }
}
