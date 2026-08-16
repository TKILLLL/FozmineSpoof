package org.phantam.fozminespoofcore.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String manipulation and text normalization utilities.
 */
public final class StringUtils {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\w\\s]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Strips Latin and Vietnamese diacritical marks from the input string.
     *
     * @param text the raw input string
     * @return the unaccented normalized string
     */
    public static String stripDiacritics(String text) {
        if (text == null || text.isBlank()) return "";
        String normalized = text.replace('đ', 'd').replace('Đ', 'D');
        String nfkd = Normalizer.normalize(normalized, Normalizer.Form.NFKD);
        return DIACRITICS_PATTERN.matcher(nfkd).replaceAll("");
    }

    /**
     * Normalizes message text by lowercasing, stripping diacritics, removing punctuation,
     * and collapsing multiple whitespaces into a single space.
     *
     * @param raw the raw input text
     * @return the sanitized, clean message string
     */
    public static String cleanMessage(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        String unaccented = stripDiacritics(lower);
        String noPunct = PUNCTUATION_PATTERN.matcher(unaccented).replaceAll("");
        return WHITESPACE_PATTERN.matcher(noPunct).replaceAll(" ").trim();
    }

    /**
     * Computes the Levenshtein distance between two strings.
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return the minimum edit distance
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return Integer.MAX_VALUE;
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;

            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(
                        1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1
                );
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }
}