package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AutoTips extends Module {

    private final IntSliderSetting minutes = register(
            new IntSliderSetting("Minutes", "Minutes between tips", 10, 1, 60)
    );

    private long lastTipTime = 0;
    private final Random random = new Random();

    // Dein Gradient Prefix
    private final String PREFIX = "ɢʟᴜᴛᴇɴ ꜰᴀʀᴍ ᴍᴀᴄʀᴏѕ";

    private final List<String> tips = Arrays.asList(
            "Use /report bug <Explanation> to report a bug!",
            "Press the right shift key to toggle features.",
            "Right-click on modules to open their settings.",
            "Press F3+P to allow tabbing out of the game.",
            "Found a scammer? Use /report scammer <IGN> <Proof>.",
            "Join our Discord for support: discord.gg/kr2u3ygamt",
            "Enable HUD for the real Hacker view.",
            "This is one of many messages that comes every 10 minutes.",
            "GET RICH FAST! Just keep farming.",
            "Use .help to view all available commands.",
            "Customize keybinds in Settings > Keybind.",
            "Always check my new post on TikTok User: GlutenFarmMacro",
            "Press Alt+F4 for a surprise (Just kidding, don't).",
            "You help me very much if you use that mod!",
            "Don't know what a module does? Feel free to ask in the DC.",
            "GLUTEN FARM MACRO ON TOP >>>>>>>>>>",
            "What mod is that? Search up GlutenFarmMacros on Modrinth.",
            "Skicce is noob",
            "Need FPS boost? Toggle Performance Mode in Settings.",
            "Activate Spawner Sell and watch your profits skyrocket!",
            "Reminder: Messages rotate every 10 minutes to keep you updated.",
            "Want to see who is using the MOD on this server? Enable HUD",
            "Press F11 to toggle fullscreen mode instantly.",
            "Support the mod by sharing it with friends!",
            "Confused about modules? Ask the community in Discord.",
            "GLUTEN FARM MACRO >>> THE FUTURE OF FARMING",
            "Looking for updates? Search GlutenFarmMacros on Modrinth.",
            "Skicce still noob confirmed.",
            "Pro tip: Combine SpawnerSell + SpawnerBooster for maximum gains.",
            "Your feedback makes this mod stronger—keep reporting bugs!",
            "6-7 Player are using that mod. Wait... 6-6-6-6-7? ",
            "Quick reminder to learn for school gng!"
    );

    public AutoTips() {
        super("AutoTips", "Sends automatic tips in chat", Category.MISC);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        long intervalMs = minutes.getValue() * 60 * 1000L;

        if (System.currentTimeMillis() - lastTipTime > intervalMs) {
            sendRandomTip();
            lastTipTime = System.currentTimeMillis();
        }
    }

    private void sendRandomTip() {
        if (tips.isEmpty()) return;

        String randomTip = tips.get(random.nextInt(tips.size()));

        // 1. Sound abspielen (Pling mit hoher Tonlage für Aufmerksamkeit)
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }

        // 2. Nachricht formatieren
        // Eine graue Trennlinie (Durchgestrichene Leereichen)
        String separator = "§7§m                                                      ";

        // Leere Zeile für Abstand
        mc.inGameHud.getChatHud().addMessage(Text.literal(""));

        // Obere Linie
        mc.inGameHud.getChatHud().addMessage(Text.literal(separator));

        // Der eigentliche Tipp: Prefix + Pfeil + Label + Nachricht
        // §f macht den Text weiß (besser lesbar als grau)
        mc.inGameHud.getChatHud().addMessage(Text.literal("§x§4§6§0§0§E§A§lɢ§x§5§1§0§A§E§6§lʟ§x§5§C§1§5§E§2§lᴜ§x§6§7§1§F§D§D§lᴛ§x§7§2§2§A§D§9§lᴇ§x§7§D§3§4§D§5§lɴ §x§9§3§4§9§C§D§lꜰ§x§9§E§5§4§C§9§lᴀ§x§A§9§5§E§C§4§lʀ§x§B§4§6§8§C§0§lᴍ §x§C§A§7§D§B§8§lᴍ§x§D§5§8§8§B§4§lᴀ§x§E§0§9§2§A§F§lᴄ§x§E§B§9§D§A§B§lʀ§x§F§6§A§7§A§7§lᴏ" + " §8» §6§lTIPP §8┃ §f" + randomTip));

        // Untere Linie
        mc.inGameHud.getChatHud().addMessage(Text.literal(separator));

        // Leere Zeile danach
        mc.inGameHud.getChatHud().addMessage(Text.literal(""));
    }
}
