package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.phantam.fozminespoofcore.utils.Range;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration holder for the chat system.
 * <p>
 * Parses ranges (e.g., "2.5-5.0") into randomized values with exact tick/ms precision.
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
 */
public class ChatConfig {

    private final boolean enabled;
    private final int minRealPlayers;
    private final String translationTarget;
    private final String translationProvider;
    private final String translationApiKey;
    private final String intervalMinutesStr;
    private final String botsPerIntervalStr;
    private final String delayBetweenBotsSecondsStr;
    private final String mode;

    /**
     * Constructs a ChatConfig from the given FileConfiguration.
     *
     * @param config the Bukkit configuration
     */
    public ChatConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("chat-system.enable", config.getBoolean("chat-system.enabled", false));
        this.mode = config.getString("chat-system.mode", "normal");
        this.minRealPlayers = Math.max(0, config.getInt("chat-system.min-real-players", 1));
        this.translationTarget = config.getString("chat-system.translation-target", "en");
        this.translationProvider = config.getString("chat-system.translation-provider", "google");
        this.translationApiKey = config.getString("chat-system.translation-api-key", "");

        this.intervalMinutesStr = config.getString("chat-system.interval-minutes", "5-15");
        this.botsPerIntervalStr = config.getString("chat-system.bots-per-interval", "1-2");
        this.delayBetweenBotsSecondsStr = config.getString("chat-system.delay-between-bots-seconds", "2-5");
    }

    // --- Getters ---

    public boolean isEnabled() { return enabled; }
    public String getMode() { return mode; }

    public int getMinRealPlayers() {
        return minRealPlayers;
    }

    public String getTranslationTarget() {
        return translationTarget;
    }

    public String getTranslationProvider() {
        return translationProvider;
    }

    public String getTranslationApiKey() {
        return translationApiKey;
    }

    public long getRandomIntervalTicks() {
        Range range = Range.parse(intervalMinutesStr, 2.0, 5.0);
        double min = range.getMin();
        double max = range.getMax();
        double minutes = (min >= max) ? min : ThreadLocalRandom.current().nextDouble(min, max);
        double seconds = Math.max(0.05, minutes * 60.0);
        return Math.max(1L, (long) (seconds * 20.0));
    }

    public int getRandomBotsPerInterval() {
        Range range = Range.parse(botsPerIntervalStr, 1.0, 2.0);
        int min = (int) range.getMin();
        int max = (int) range.getMax();
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public long getRandomDelayTicks() {
        Range range = Range.parse(delayBetweenBotsSecondsStr, 2.0, 5.0);
        return range.getRandomTicks();
    }
}