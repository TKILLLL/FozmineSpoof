package org.phantam.fozminespoofcore.config;

import org.phantam.fozminespoofcore.utils.Range;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

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

    public InteractionConfig(String key, List<String> rawTriggers, double chance,
                             long globalCooldownSec, long perPlayerCooldownSec,
                             int maxBurst, String delayRangeStr, String activeHoursStr,
                             List<String> replies) {
        this.key = key;
        this.rawTriggers = rawTriggers != null ? List.copyOf(rawTriggers) : List.of();
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.globalCooldownMs = Math.max(0L, globalCooldownSec * 1000L);
        this.perPlayerCooldownMs = Math.max(0L, perPlayerCooldownSec * 1000L);
        this.maxBurst = Math.max(1, maxBurst);
        this.delayRange = Range.parse(delayRangeStr, 1.5, 2.5);
        this.activeHoursStr = activeHoursStr != null ? activeHoursStr.trim() : "00:00-23:59";
        this.replies = replies != null ? List.copyOf(replies) : List.of();

        List<Pattern> compiled = new ArrayList<>();
        List<String> plain = new ArrayList<>();

        for (String trig : this.rawTriggers) {
            if (trig == null || trig.isBlank()) continue;
            String cleanedTrig = StringUtils.cleanMessage(trig);

            if (trig.contains("*")) {
                // Xử lý Wildcard * (Ví dụ: *how*sell*)
                String[] parts = trig.split("\\*");
                StringBuilder sb = new StringBuilder(".*");
                for (String part : parts) {
                    if (!part.isBlank()) {
                        String cleanPart = StringUtils.cleanMessage(part);
                        sb.append(Pattern.quote(cleanPart)).append(".*");
                    }
                }
                compiled.add(Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE));
            } else {
                // Xử lý Word Boundary \b (Tránh bắt nhầm từ ngắn)
                plain.add(cleanedTrig);
                String patternStr = "\\b" + Pattern.quote(cleanedTrig) + "\\b";
                compiled.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
            }
        }

        this.triggerPatterns = List.copyOf(compiled);
        this.cleanPlainTriggers = List.copyOf(plain);
    }

    public boolean matches(String cleanedUserMessage) {
        if (cleanedUserMessage == null || cleanedUserMessage.isBlank()) return false;

        // 1. So khớp Regex / Word Boundary / Wildcard
        for (Pattern pattern : triggerPatterns) {
            if (pattern.matcher(cleanedUserMessage).find()) {
                return true;
            }
        }

        // 2. So khớp sai chính tả nhẹ (Levenshtein Distance)
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

    public long getRandomDelayTicks() {
        return delayRange.getRandomTicks();
    }

    // Getters
    public String getKey() { return key; }
    public long getGlobalCooldownMs() { return globalCooldownMs; }
    public long getPerPlayerCooldownMs() { return perPlayerCooldownMs; }
    public int getMaxBurst() { return maxBurst; }
    public List<String> getReplies() { return replies; }
}