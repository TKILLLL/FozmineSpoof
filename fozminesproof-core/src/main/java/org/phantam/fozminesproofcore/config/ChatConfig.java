package org.phantam.fozminesproofcore.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.concurrent.ThreadLocalRandom;

public class ChatConfig {
    private final boolean enabled;
    private final String translationTarget;
    private final Range intervalMinutes;
    private final Range botsPerInterval;
    private final Range delayBetweenBotsSeconds;

    public ChatConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("chat-system.enabled", false);
        this.translationTarget = config.getString("chat-system.translation-target", "vi");

        // Bổ sung các giá trị Fallback an toàn phòng trường hợp người dùng xóa file hoặc cấu hình lỗi
        this.intervalMinutes = parseRange(config.getString("chat-system.interval-minutes"), 2, 3);
        this.botsPerInterval = parseRange(config.getString("chat-system.bots-per-interval"), 1, 3);
        this.delayBetweenBotsSeconds = parseRange(config.getString("chat-system.delay-between-bots-seconds"), 3, 7);
    }

    /**
     * Phân tích chuỗi ký tự định dạng "Min-Max" thành đối tượng Range.
     * Tích hợp khối Try/Catch phòng vệ chống lỗi nhập sai ký tự (NumberFormatException).
     */
    private Range parseRange(String value, int fallbackMin, int fallbackMax) {
        if (value == null || value.trim().isEmpty()) {
            return new Range(fallbackMin, fallbackMax);
        }

        try {
            // Trường hợp cấu hình chỉ nhập 1 số cố định (Không chứa dấu gạch ngang '-')
            if (!value.contains("-")) {
                int singleValue = Integer.parseInt(value.trim());
                return new Range(singleValue, singleValue);
            }

            // Trường hợp cấu hình nhập dải số "Min-Max" chuẩn
            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new Range(min, max);

        } catch (NumberFormatException e) {
            // Cơ chế tự phục hồi (Fail-Safe): Gán giá trị mặc định an toàn và cảnh báo ra Console
            Bukkit.getLogger().warning("⚠ [FozmineSproof] Cấu hình dải số '" + value
                    + "' sai định dạng số! Đang dùng giá trị mặc định: " + fallbackMin + "-" + fallbackMax);
            return new Range(fallbackMin, fallbackMax);
        }
    }

    // --- GETTERS (Encapsulation) ---
    public boolean isEnabled() { return enabled; }
    public String getTranslationTarget() { return translationTarget; }
    public int getRandomIntervalMinutes() { return intervalMinutes.getRandom(); }
    public int getRandomBotsPerInterval() { return botsPerInterval.getRandom(); }
    public int getRandomDelaySeconds() { return delayBetweenBotsSeconds.getRandom(); }

    /**
     * Lớp cấu trúc dữ liệu bọc khoảng Min-Max an toàn vùng nhớ.
     * Sử dụng lớp lồng tĩnh (Static Nested Class) để không bị neo giữ tham chiếu tới class cha.
     */
    private static class Range {
        private final int min;
        private final int max;

        public Range(int min, int max) {
            // Tự động đảo vị trí nếu người dùng nhập ngược (Ví dụ nhập "7-3" thay vì "3-7")
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
        }

        public int getRandom() {
            if (min == max) return min;
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }
}
