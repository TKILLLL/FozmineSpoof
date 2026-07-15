package org.phantam.fozminesproofcore.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    // 1. Khớp định dạng Hex chuẩn: #RRGGBB hoặc &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#?([a-fA-F0-9]{6})");

    // 2. Khớp định dạng Vanilla Spigot Hex Legacy: &x&r&g&b&r&g&b (Ví dụ: &x&f&f&5&5&5&5)
    private static final Pattern SPIGOT_HEX_PATTERN = Pattern.compile("&x(&[a-fA-F0-9]){6}");

    private ColorUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Chuyển đổi toàn diện các hệ màu HEX (#, &#, &x) và màu truyền thống (&) thành chuỗi màu Minecraft.
     * Thích hợp cho cả tin nhắn Chat, Tablist, NameTag của FakePlayer.
     *
     * @param message Chuỗi văn bản thô.
     * @return Chuỗi đã được xử lý màu hoàn chỉnh.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // BƯỚC 1: Xử lý định dạng Spigot Hex Legacy (&x&a&b&c&d&e&f) trước tiên để tránh bị xé lẻ mã màu
        message = translateSpigotHex(message);

        // BƯỚC 2: Xử lý các định dạng Hex phổ biến nhất (#RRGGBB và &#RRGGBB)
        message = translateStandardHex(message);

        // BƯỚC 3: Xử lý mã màu truyền thống (&0-9, &a-f, &k-o, &r) cuối cùng
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Chuyển đổi định dạng &#RRGGBB hoặc #RRGGBB thành ChatColor Bungee
     */
    private static String translateStandardHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder(text.length());

        while (matcher.find()) {
            String hexColor = "#" + matcher.group(1);
            // Thay thế chuỗi Hex bằng mã màu hợp lệ của hệ thống BungeeCord
            matcher.appendReplacement(builder, ChatColor.of(hexColor).toString());
        }
        return matcher.appendTail(builder).toString();
    }

    /**
     * Chuyển đổi định dạng phức tạp &x&1&2&3&4&5&6 thành mã màu Hex chuẩn của Bungee
     */
    private static String translateSpigotHex(String text) {
        Matcher matcher = SPIGOT_HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder(text.length());

        while (matcher.find()) {
            // Lấy chuỗi khớp dạng &x&f&f&5&5&5&5, loại bỏ tất cả ký tự '&' và 'x' để lấy chuỗi hex "ff5555"
            String rawHex = matcher.group().replace("&", "").replace("x", "");
            String hexColor = "#" + rawHex;

            matcher.appendReplacement(builder, ChatColor.of(hexColor).toString());
        }
        return matcher.appendTail(builder).toString();
    }
}