package org.phantam.fozminespoofcore.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for colorizing Minecraft messages.
 * Supports legacy color codes (&), standard hex (#RRGGBB), and Spigot hex (&x&r&g&b...).
 */
public final class ColorUtils {

    // Matches standard hex format: #RRGGBB or &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#?([a-fA-F0-9]{6})");

    // Matches Spigot legacy hex: &x&r&g&b&r&g&b (exactly 6 hex pairs after &x)
    private static final Pattern SPIGOT_HEX_PATTERN = Pattern.compile("&x(&[a-fA-F0-9]){6}");

    private ColorUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Translates all color codes in a message to Minecraft-compatible formatting.
     * Supports:
     * <ul>
     *   <li>Legacy codes: &amp;0-9, &amp;a-f, &amp;k-o, &amp;r</li>
     *   <li>Hex codes: #RRGGBB or &amp;#RRGGBB</li>
     *   <li>Spigot hex: &amp;x&amp;r&amp;g&amp;b&amp;r&amp;g&amp;b (case-insensitive)</li>
     * </ul>
     *
     * @param message the raw message
     * @return the colorized message, or empty string if null/empty
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Step 1: Handle Spigot legacy hex format (&x&r&g&b...) first to avoid corruption
        message = translateSpigotHex(message);

        // Step 2: Handle standard hex (#RRGGBB or &#RRGGBB)
        message = translateStandardHex(message);

        // Step 3: Translate legacy color codes (&0-9, &a-f, etc.)
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Translates standard hex patterns (#RRGGBB or &#RRGGBB) to BungeeCord ChatColor.
     *
     * @param text the input text
     * @return the text with hex codes replaced by BungeeCord color objects
     */
    private static String translateStandardHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder(text.length());

        while (matcher.find()) {
            String hexCode = "#" + matcher.group(1);
            matcher.appendReplacement(result, ChatColor.of(hexCode).toString());
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Translates Spigot legacy hex format (&x&r&g&b&r&g&b) to BungeeCord ChatColor.
     *
     * @param text the input text
     * @return the text with legacy hex codes replaced by BungeeCord color objects
     */
    private static String translateSpigotHex(String text) {
        Matcher matcher = SPIGOT_HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder(text.length());

        while (matcher.find()) {
            // Extract hex string from &x&f&f&5&5&5&5 -> remove '&' and 'x' -> "ff5555"
            String rawHex = matcher.group().replace("&", "").replace("x", "");
            String hexCode = "#" + rawHex;

            matcher.appendReplacement(result, ChatColor.of(hexCode).toString());
        }
        matcher.appendTail(result);
        return result.toString();
    }
}