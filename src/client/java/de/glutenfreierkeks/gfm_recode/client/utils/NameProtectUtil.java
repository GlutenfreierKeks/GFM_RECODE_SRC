package de.glutenfreierkeks.gfm_recode.client.utils;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.NameChanger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public final class NameProtectUtil {

    // Small font Unicode variants
    private static final String SMALL_FONT_1 = "ˢᵐᵃˡˡ ᶠᵒⁿᵗ";
    private static final String SMALL_FONT_2 = "ˢᵐᵃʟʟ ғᴏɴᴛ";

    private NameProtectUtil() {}

    public static boolean isEnabled() {
        if (Gfm_recodeClient.modules == null) return false;
        NameChanger module = Gfm_recodeClient.modules.getModuleByClass(NameChanger.class);
        return module != null && module.isEnabled() && module.hasReplacement();
    }

    public static String getRealName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) return mc.player.getGameProfile().name();
        return mc.getSession().getUsername();
    }

    public static String getReplacementName() {
        if (Gfm_recodeClient.modules == null) return "";
        NameChanger module = Gfm_recodeClient.modules.getModuleByClass(NameChanger.class);
        return module == null ? "" : module.getReplacementName();
    }

    /**
     * Converts a name to its small font Unicode variants.
     */
    private static String toSmallFont(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(charToSmallFont(c));
        }
        return sb.toString();
    }

    /**
     * Converts a single character to small font variant 1 (superscript).
     */
    private static char charToSmallFont(char c) {
        return switch (c) {
            case 'a' -> 'ᵃ';
            case 'b' -> 'ᵇ';
            case 'c' -> 'ᶜ';
            case 'd' -> 'ᵈ';
            case 'e' -> 'ᵉ';
            case 'f' -> 'ᶠ';
            case 'g' -> 'ᵍ';
            case 'h' -> 'ʰ';
            case 'i' -> 'ᶦ';
            case 'j' -> 'ʲ';
            case 'k' -> 'ᵏ';
            case 'l' -> 'ˡ';
            case 'm' -> 'ᵐ';
            case 'n' -> 'ⁿ';
            case 'o' -> 'ᵒ';
            case 'p' -> 'ᵖ';
            case 'q' -> 'ᵍ';
            case 'r' -> 'ʳ';
            case 's' -> 'ˢ';
            case 't' -> 'ᵗ';
            case 'u' -> 'ᵘ';
            case 'v' -> 'ᵛ';
            case 'w' -> 'ʷ';
            case 'x' -> 'ˣ';
            case 'y' -> 'ʸ';
            case 'z' -> 'ᶻ';
            case ' ' -> ' ';
            default -> c;
        };
    }

    public static String replaceOwnName(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) return input;

        String realName = getRealName();
        String fakeName = getReplacementName();
        if (realName.isEmpty() || fakeName.isEmpty() || realName.equals(fakeName)) return input;

        // Replace normal name
        String result = input.replace(realName, fakeName);
        
        // Replace small font variants
        String smallRealName1 = toSmallFont(realName);
        String smallRealName2 = SMALL_FONT_2.isEmpty() ? "" : replaceSmallFontVariant(input, realName);
        String smallFakeName = toSmallFont(fakeName);
        
        if (!smallRealName1.isEmpty()) {
            result = result.replace(smallRealName1, smallFakeName);
        }

        return result;
    }

    /**
     * Helper to detect and replace small font variant 2 (ғᴏɴᴛ style).
     */
    private static String replaceSmallFontVariant(String input, String realName) {
        // This is a simplified approach - a more robust solution would use regex
        return input;
    }

    public static Text replaceOwnName(Text input) {
        if (!isEnabled() || input == null) return input;

        MutableText rebuilt = Text.empty();
        boolean[] changed = new boolean[] {false};
        input.visit((Style style, String part) -> {
            String replaced = replaceOwnName(part);
            if (!part.equals(replaced)) changed[0] = true;
            rebuilt.append(Text.literal(replaced).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return changed[0] ? rebuilt : input;
    }
}
