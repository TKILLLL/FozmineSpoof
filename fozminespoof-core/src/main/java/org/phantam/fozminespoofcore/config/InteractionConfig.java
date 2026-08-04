package org.phantam.fozminespoofcore.config;

import org.phantam.fozminespoofcore.utils.Range;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Configuration for a single interactive chat trigger group.
 * <p>
 * Supports both plain-text triggers with wildcards and raw regex patterns.
 * </p>
 */
public class InteractionConfig {

    private final String key;
    private final List<String> rawTriggers;
    private final List<Pattern> triggerPatterns;
    private final List<String> cleanPlainTriggers;
    private final double chance;
    private final long globalCooldownMs;
    private final long perPlayerCooldownMs;
    private final int maxBurst;
    private final Range delayRange;
    private final String activeHoursStr;
    private final List<String> replies;
    private final boolean useRegex;
    private final double fuzzyThreshold;
    private final Range typingSpeedRange;
    private final Range pauseBetweenWords;

    /**
     * Full constructor with all parameters.
     */
    public InteractionConfig(String key, List<String> rawTriggers, double chance,
                             long globalCooldownSec, long perPlayerCooldownSec,
                             int maxBurst, String delayRangeStr, String activeHoursStr,
                             List<String> replies,
                             boolean useRegex, double fuzzyThreshold,
                             String typingSpeedRangeStr, String pauseBetweenWordsStr) {
        this.key = key;
        this.rawTriggers = rawTriggers != null ? List.copyOf(rawTriggers) : List.of();
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.globalCooldownMs = Math.max(0L, globalCooldownSec * 1000L);
        this.perPlayerCooldownMs = Math.max(0L, perPlayerCooldownSec * 1000L);
        this.maxBurst = Math.max(1, maxBurst);
        this.delayRange = Range.parse(delayRangeStr, 1.5, 2.5);
        this.activeHoursStr = activeHoursStr != null ? activeHoursStr.trim() : "00:00-23:59";
        this.replies = replies != null ? List.copyOf(replies) : List.of();
        this.useRegex = useRegex;
        this.fuzzyThreshold = Math.max(0.0, Math.min(1.0, fuzzyThreshold));
        this.typingSpeedRange = Range.parse(typingSpeedRangeStr, 0.8, 1.8);
        this.pauseBetweenWords = Range.parse(pauseBetweenWordsStr, 2.0, 4.0);

        List<Pattern> compiled = new ArrayList<>();
        List<String> plain = new ArrayList<>();

        if (useRegex) {
            // Raw regex mode – no cleaning, compile directly
            for (String trig : this.rawTriggers) {
                if (trig == null || trig.isBlank()) continue;
                try {
                    compiled.add(Pattern.compile(trig, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    // invalid regex – skip
                }
            }
        } else {
            // Wildcard mode with OR support
            for (String trig : this.rawTriggers) {
                if (trig == null || trig.isBlank()) continue;

                // For non-wildcard triggers, we keep them for fuzzy matching
                if (!trig.contains("*")) {
                    String cleaned = StringUtils.cleanMessage(trig);
                    plain.add(cleaned);
                    // Also add a word-boundary pattern for exact matching
                    compiled.add(Pattern.compile("\\b" + Pattern.quote(cleaned) + "\\b", Pattern.CASE_INSENSITIVE));
                    continue;
                }

                // Wildcard: split by "*"
                String[] parts = trig.split("\\*");
                StringBuilder sb = new StringBuilder(".*");
                for (String part : parts) {
                    if (part.isBlank()) continue;

                    // Check if this part contains an OR operator
                    if (part.contains("|")) {
                        // Split by "|" and clean each option individually
                        String[] orParts = part.split("\\|");
                        StringBuilder orGroup = new StringBuilder("(?:");
                        for (int i = 0; i < orParts.length; i++) {
                            String cleanedOption = StringUtils.cleanMessage(orParts[i]);
                            orGroup.append(Pattern.quote(cleanedOption));
                            if (i < orParts.length - 1) orGroup.append("|");
                        }
                        orGroup.append(")");
                        sb.append(orGroup).append(".*");
                    } else {
                        // Regular part – clean and quote
                        String cleaned = StringUtils.cleanMessage(part);
                        sb.append(Pattern.quote(cleaned)).append(".*");
                    }
                }
                compiled.add(Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE));
            }
        }

        this.triggerPatterns = List.copyOf(compiled);
        this.cleanPlainTriggers = List.copyOf(plain);
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public InteractionConfig(String key, List<String> rawTriggers, double chance,
                             long globalCooldownSec, long perPlayerCooldownSec,
                             int maxBurst, String delayRangeStr, String activeHoursStr,
                             List<String> replies) {
        this(key, rawTriggers, chance, globalCooldownSec, perPlayerCooldownSec,
                maxBurst, delayRangeStr, activeHoursStr, replies,
                false, 0.85, "0.8-1.8", "2-4");
    }

    /**
     * Checks if the cleaned user message matches any trigger.
     */
    public boolean matches(String cleanedUserMessage) {
        if (cleanedUserMessage == null || cleanedUserMessage.isBlank()) return false;

        // 1. Pattern matching (regex or wildcard)
        for (Pattern pattern : triggerPatterns) {
            if (pattern.matcher(cleanedUserMessage).find()) {
                return true;
            }
        }

        // 2. Levenshtein fuzzy matching (only for non-regex triggers)
        if (useRegex) return false;

        String[] words = cleanedUserMessage.split(" ");
        for (String word : words) {
            if (word.length() < 3) continue;
            for (String trig : cleanPlainTriggers) {
                if (trig.length() < 3) continue;
                int maxDist = (trig.length() <= 4) ? 1 : 2;
                if (StringUtils.levenshteinDistance(word, trig) <= maxDist) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInActiveHours(ZoneId zoneId) {
        if (activeHoursStr == null || activeHoursStr.isBlank() || activeHoursStr.equalsIgnoreCase("24/7")) {
            return true;
        }
        try {
            LocalTime now = LocalTime.now(zoneId);
            String[] parts = activeHoursStr.split("-");
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());

            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            return true;
        }
    }

    public boolean rollsChance() {
        if (chance >= 1.0) return true;
        if (chance <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() <= chance;
    }

    /**
     * Returns the random delay in ticks including reaction, typing, and word pauses.
     *
     * @param message the message being typed
     * @return total delay in ticks
     */
    public long getTypingDelayTicks(String message) {
        long reaction = delayRange.getRandomTicks();
        double speed = ThreadLocalRandom.current().nextDouble(
                typingSpeedRange.getMin(), typingSpeedRange.getMax());
        long typingTicks = (long) (message.length() * speed);
        long wordCount = message.split(" ").length;
        long pauseTicks = (long) (wordCount * ThreadLocalRandom.current().nextDouble(
                pauseBetweenWords.getMin(), pauseBetweenWords.getMax()));
        return reaction + typingTicks + pauseTicks;
    }

    // Getters
    public String getKey() { return key; }
    public double getChance() { return chance; }
    public long getGlobalCooldownMs() { return globalCooldownMs; }
    public long getPerPlayerCooldownMs() { return perPlayerCooldownMs; }
    public int getMaxBurst() { return maxBurst; }
    public List<String> getReplies() { return replies; }
    public boolean isUseRegex() { return useRegex; }
    public double getFuzzyThreshold() { return fuzzyThreshold; }
}