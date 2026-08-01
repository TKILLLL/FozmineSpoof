package org.phantam.fozminesproofcore.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Configuration holder for the chat system.
 * Parses ranges (e.g., "2-5") into randomized values with fail-safe fallbacks.
 */
public class ChatConfig {

    private final boolean enabled;
    private final String translationTarget;
    private final Range intervalMinutes;
    private final Range botsPerInterval;
    private final Range delayBetweenBotsSeconds;

    public ChatConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("chat-system.enabled", false);
        this.translationTarget = config.getString("chat-system.translation-target", "vi");

        this.intervalMinutes = parseRange(
                config.getString("chat-system.interval-minutes"),
                2, 3
        );
        this.botsPerInterval = parseRange(
                config.getString("chat-system.bots-per-interval"),
                1, 3
        );
        this.delayBetweenBotsSeconds = parseRange(
                config.getString("chat-system.delay-between-bots-seconds"),
                3, 7
        );
    }

    /**
     * Parses a string in "min-max" format into a Range object.
     * If the string is invalid, returns a fallback range.
     *
     * @param value       the input string (e.g., "2-5" or "10")
     * @param fallbackMin fallback minimum if parsing fails
     * @param fallbackMax fallback maximum if parsing fails
     * @return a Range object with valid min/max values
     */
    private Range parseRange(String value, int fallbackMin, int fallbackMax) {
        if (value == null || value.trim().isEmpty()) {
            return new Range(fallbackMin, fallbackMax);
        }

        try {
            if (!value.contains("-")) {
                int single = Integer.parseInt(value.trim());
                return new Range(single, single);
            }

            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new Range(min, max);

        } catch (NumberFormatException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[FozmineSproof] Invalid range value '" + value +
                            "'. Using fallback: " + fallbackMin + "-" + fallbackMax);
            return new Range(fallbackMin, fallbackMax);
        }
    }

    // --- Getters ---
    public boolean isEnabled() {
        return enabled;
    }

    public String getTranslationTarget() {
        return translationTarget;
    }

    public int getRandomIntervalMinutes() {
        return intervalMinutes.getRandom();
    }

    public int getRandomBotsPerInterval() {
        return botsPerInterval.getRandom();
    }

    public int getRandomDelaySeconds() {
        return delayBetweenBotsSeconds.getRandom();
    }

    /**
     * Immutable range container with random value generation.
     */
    private static class Range {
        private final int min;
        private final int max;

        Range(int min, int max) {
            // Ensure min <= max
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
        }

        int getRandom() {
            if (min == max) return min;
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }
}