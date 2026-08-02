package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.phantam.fozminespoofcore.utils.Range;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration holder for the chat system.
 * Parses ranges (e.g., "2.5-5.0") into randomized values with exact tick/ms precision.
 */
public class ChatConfig {

    private final boolean enabled;
    private final String translationTarget;
    private final String intervalMinutesStr;
    private final String botsPerIntervalStr;
    private final String delayBetweenBotsSecondsStr;

    public ChatConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("chat-system.enabled", false);
        this.translationTarget = config.getString("chat-system.translation-target", "en");

        this.intervalMinutesStr = config.getString("chat-system.interval-minutes", "5-15");
        this.botsPerIntervalStr = config.getString("chat-system.bots-per-interval", "1-2");
        this.delayBetweenBotsSecondsStr = config.getString("chat-system.delay-between-bots-seconds", "2-5");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTranslationTarget() {
        return translationTarget;
    }

    /**
     * Lấy thời gian chờ giữa các chu kỳ chat tính bằng Ticks (1 sec = 20 ticks)
     * Đã sửa an toàn: Kiểm tra min >= max tránh lỗi ThreadLocalRandom
     */
    public long getRandomIntervalTicks() {
        Range range = Range.parse(intervalMinutesStr, 2.0, 5.0);
        double min = range.getMin();
        double max = range.getMax();

        double minutes = (min >= max) ? min : ThreadLocalRandom.current().nextDouble(min, max);
        double seconds = Math.max(0.05, minutes * 60.0);
        return Math.max(1L, (long) (seconds * 20.0));
    }

    /**
     * Lấy số lượng bot sẽ phát biểu trong 1 chu kỳ chat
     */
    public int getRandomBotsPerInterval() {
        Range range = Range.parse(botsPerIntervalStr, 1.0, 2.0);
        int min = (int) range.getMin();
        int max = (int) range.getMax();
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Lấy thời gian giãn cách giữa các bot phát biểu tính bằng Ticks (1 sec = 20 ticks)
     */
    public long getRandomDelayTicks() {
        Range range = Range.parse(delayBetweenBotsSecondsStr, 2.0, 5.0);
        return range.getRandomTicks();
    }
}