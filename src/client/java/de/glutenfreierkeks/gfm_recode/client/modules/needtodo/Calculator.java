package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Calculator extends Module {

    private final BoolSetting publicChat = register(new BoolSetting("PublicChat", "Send answers to public chat", true));
    private final IntSliderSetting delaySeconds = register(new IntSliderSetting("DelaySec", "Delay before answering (seconds)", 3, 0, 10));
    private final BoolSetting debugMode = register(new BoolSetting("DebugMode", "Show debug messages in chat", false));

    // Erkennt: 1+2 | 3,5 * 4.2 | 10 / 2 | 5 x 5
    private final Pattern pattern = Pattern.compile("(\\d+[,.]?\\d*)\\s*([+\\-*/x])\\s*(\\d+[,.]?\\d*)");

    public Calculator() {
        super("Calculator",
                "Automatically solves math equations in chat.",
                Category.MISC);
    }

    @Override
    public void onEnable() {
        debug("Calculator enabled and listening to chat");
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    /**
     * WICHTIG: Diese Methode wird von deinem Chat Event System aufgerufen
     * Sie muss public sein und den Namen onChatMessage haben
     */
    public void onChatMessage(Text message) {
        if (mc.player == null) return;

        String rawMessage = message.getString();
        debug("Received chat message: " + rawMessage);

        // Clean message (remove color codes)
        String cleanMessage = rawMessage.replaceAll("§[0-9a-fk-or]", "");

        // Eigene Nachrichten ignorieren
        String playerName = mc.player.getName().getString();
        if (cleanMessage.contains(playerName + ":") ||
                cleanMessage.contains("<" + playerName + ">") ||
                cleanMessage.contains("[Calculator]")) {
            debug("Ignoring own message");
            return;
        }

        // Bereits berechnete Gleichungen ignorieren (enthält =)
        if (cleanMessage.contains("=")) {
            debug("Ignoring message with = sign");
            return;
        }

        // Suche nach Rechenaufgabe
        Matcher matcher = pattern.matcher(cleanMessage);
        if (matcher.find()) {
            debug("Math equation found: " + matcher.group());
            calculateAndReply(matcher);
        } else {
            debug("No math equation found");
        }
    }

    private void calculateAndReply(Matcher matcher) {
        new Thread(() -> {
            try {
                // Delay before responding
                if (delaySeconds.getValue() > 0) {
                    Thread.sleep(delaySeconds.getValue() * 1000L);
                }

                // Calculate result
                String result = calculate(matcher);
                if (result == null) {
                    debug("Calculation failed");
                    return;
                }

                // Send reply on main thread
                mc.execute(() -> sendReply(result));

            } catch (InterruptedException e) {
                debug("Thread interrupted: " + e.getMessage());
            }
        }).start();
    }

    private String calculate(Matcher matcher) {
        try {
            // Parse numbers (replace comma with dot for parseDouble)
            String num1Str = matcher.group(1).replace(",", ".");
            String operator = matcher.group(2);
            String num2Str = matcher.group(3).replace(",", ".");

            double num1 = Double.parseDouble(num1Str);
            double num2 = Double.parseDouble(num2Str);

            debug("Calculating: " + num1 + " " + operator + " " + num2);

            // Calculate based on operator
            double result;
            switch (operator) {
                case "+" -> result = num1 + num2;
                case "-" -> result = num1 - num2;
                case "*", "x" -> result = num1 * num2;
                case "/" -> {
                    if (num2 == 0) {
                        debug("Division by zero detected");
                        return null;
                    }
                    result = num1 / num2;
                }
                default -> {
                    debug("Unknown operator: " + operator);
                    return null;
                }
            }

            // Format numbers (remove .0 for whole numbers)
            String formattedNum1 = formatNumber(num1);
            String formattedNum2 = formatNumber(num2);
            String formattedResult = formatNumber(result);

            return formattedNum1 + " " + operator + " " + formattedNum2 + " = " + formattedResult;

        } catch (NumberFormatException e) {
            debug("Number parsing error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            debug("Calculation error: " + e.getMessage());
            return null;
        }
    }

    private String formatNumber(double number) {
        // If whole number, format without decimals
        if (number % 1 == 0) {
            return String.format("%.0f", number);
        }
        // Otherwise, keep decimals but remove trailing zeros
        String formatted = String.valueOf(number);
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }

    private void sendReply(String calculation) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        debug("Sending reply: " + calculation);

        if (publicChat.getValue()) {
            // Send to public chat
            mc.player.networkHandler.sendChatMessage(calculation);
        } else {
            // Send as local message
            mc.player.sendMessage(
                    Text.literal(Formatting.AQUA + "[Calculator] " + Formatting.WHITE + calculation),
                    false
            );
        }
    }

    private void debug(String message) {
        if (debugMode.getValue() && mc.player != null) {
            mc.player.sendMessage(
                    Text.literal(Formatting.GRAY + "[Calc-Debug] " + message),
                    false
            );
        }
    }

    @Override
    public String getDisplayInfo() {
        return publicChat.getValue() ? "Public" : "Private";
    }
}
