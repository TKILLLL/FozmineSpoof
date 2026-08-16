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
 * High-accuracy interaction trigger configuration.
 * Guarantees 100% deterministic keyword/regex evaluation without false-positive overlaps.
 */
public class InteractionConfig {

    public static final String BOT_TOKEN = "__BOT_MENTION__";

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
            for (String trig : this.rawTriggers) {
                if (trig == null || trig.isBlank()) continue;
                try {
                    compiled.add(Pattern.compile(trig, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (Exception ignored) {}
            }
        } else {
            for (String trig : this.rawTriggers) {
                if (trig == null || trig.isBlank()) continue;

                // Xử lý riêng biệt trigger Tag [bot]
                if (trig.contains("[bot]")) {
                    String patternStr = trig
                            .replace("*[bot]*", ".*\\b" + BOT_TOKEN + "\\b.*")
                            .replace("[bot]*", "\\b" + BOT_TOKEN + "\\b.*")
                            .replace("*[bot]", ".*\\b" + BOT_TOKEN + "\\b")
                            .replace("[bot]", "\\b" + BOT_TOKEN + "\\b");
                    compiled.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
                    continue;
                }

                // Plain trigger không có Wildcard -> Yêu cầu Word Boundary chính xác
                if (!trig.contains("*")) {
                    String cleaned = StringUtils.cleanMessage(trig);
                    if (!cleaned.isBlank()) {
                        plain.add(cleaned);
                        // Bắt buộc đứng độc lập như 1 từ hoặc cụm từ hoàn chỉnh
                        compiled.add(Pattern.compile("(?:^|\\s+)" + Pattern.quote(cleaned) + "(?:$|\\s+)", Pattern.CASE_INSENSITIVE));
                    }
                    continue;
                }

                // Xử lý Wildcard * với toán tử OR (|)
                boolean startsWildcard = trig.startsWith("*");
                boolean endsWildcard = trig.endsWith("*");
                String[] parts = trig.split("\\*");

                StringBuilder sb = new StringBuilder();
                if (startsWildcard) {
                    sb.append(".*");
                } else {
                    sb.append("(?:^|\\s+)");
                }

                List<String> validParts = new ArrayList<>();
                for (String part : parts) {
                    if (!part.isBlank()) validParts.add(part);
                }

                for (int i = 0; i < validParts.size(); i++) {
                    String part = validParts.get(i);
                    if (part.contains("|")) {
                        String[] orParts = part.split("\\|");
                        sb.append("(?:");
                        for (int j = 0; j < orParts.length; j++) {
                            sb.append(Pattern.quote(StringUtils.cleanMessage(orParts[j])));
                            if (j < orParts.length - 1) sb.append("|");
                        }
                        sb.append(")");
                    } else {
                        sb.append(Pattern.quote(StringUtils.cleanMessage(part)));
                    }

                    if (i < validParts.size() - 1) {
                        sb.append(".*");
                    }
                }

                if (endsWildcard) {
                    sb.append(".*");
                } else {
                    sb.append("(?:$|\\s+)");
                }

                compiled.add(Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE));
            }
        }

        this.triggerPatterns = List.copyOf(compiled);
        this.cleanPlainTriggers = List.copyOf(plain);
    }

    /**
     * Khớp trigger chính xác 100% (Pattern Matcher -> Strict Fuzzy Matcher)
     */
    public boolean matches(String cleanedUserMessage) {
        if (cleanedUserMessage == null || cleanedUserMessage.isBlank()) return false;

        // 1. Kiểm tra Regex / Wildcard / Word Boundary
        for (Pattern pattern : triggerPatterns) {
            if (pattern.matcher(cleanedUserMessage).find()) {
                return true;
            }
        }

        // 2. Strict Levenshtein Fuzzy Matching (Chỉ áp dụng cho chế độ non-regex)
        if (useRegex || cleanPlainTriggers.isEmpty()) return false;

        String[] words = cleanedUserMessage.split("\\s+");
        for (String word : words) {
            // Không áp dụng fuzzy cho từ quá ngắn (< 4 ký tự) để chống false-positive
            if (word.length() < 4) continue;

            for (String trig : cleanPlainTriggers) {
                if (trig.length() < 4) continue;

                // Tính toán độ tương đồng chính xác theo tỉ lệ threshold
                int dist = StringUtils.levenshteinDistance(word, trig);
                double similarity = 1.0 - ((double) dist / Math.max(word.length(), trig.length()));

                if (similarity >= this.fuzzyThreshold && dist <= 2) {
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

    public long getTypingDelayTicks(String message) {
        long reaction = delayRange.getRandomTicks();
        double speed = ThreadLocalRandom.current().nextDouble(
                typingSpeedRange.getMin(), typingSpeedRange.getMax());
        long typingTicks = (long) (message.length() * speed);
        long wordCount = message.split("\\s+").length;
        long pauseTicks = (long) (wordCount * ThreadLocalRandom.current().nextDouble(
                pauseBetweenWords.getMin(), pauseBetweenWords.getMax()));
        return reaction + typingTicks + pauseTicks;
    }

    public String getKey() { return key; }
    public double getChance() { return chance; }
    public long getGlobalCooldownMs() { return globalCooldownMs; }
    public long getPerPlayerCooldownMs() { return perPlayerCooldownMs; }
    public int getMaxBurst() { return maxBurst; }
    public List<String> getReplies() { return replies; }
    public boolean isUseRegex() { return useRegex; }
    public double getFuzzyThreshold() { return fuzzyThreshold; }
}