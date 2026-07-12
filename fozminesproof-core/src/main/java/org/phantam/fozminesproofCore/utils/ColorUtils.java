package org.phantam.fozminesproofCore.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    // Khớp các định dạng: #RRGGBB, &#RRGGBB, hoặc &{#RRGGBB}
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([a-fA-F0-9]{6})");

    private ColorUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Chuyển đổi mã màu HEX và mã màu truyền thống (&) thành chuỗi có màu trong Minecraft.
     *
     * @param message Chuỗi văn bản thô chưa xử lý màu.
     * @return Chuỗi đã được định dạng màu hoàn chỉnh.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // 1. Xử lý mã màu HEX (#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            String hexCode = "#" + matcher.group(1);
            matcher.appendReplacement(builder, ChatColor.of(hexCode).toString());
        }
        matcher.appendTail(builder);

        // 2. Xử lý mã màu truyền thống (&0-9, &a-f, &k-o, &r)
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
