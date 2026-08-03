package org.phantam.fozminespoofcore.chat;

import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multilingual translation service for bot chat messages.
 * <p>
 * Supports multiple translation providers:
 * <ul>
 *   <li><b>google</b> – Unofficial Google Translate API (free, no key required)</li>
 *   <li><b>gcloud</b> – Official Google Cloud Translation API (requires API key, paid)</li>
 *   <li><b>deepl</b> – DeepL API (requires API key, paid/free tier)</li>
 *   <li><b>none</b> – Disables translation (returns original text)</li>
 * </ul>
 * If the chosen provider requires a key and none is provided, or the API call fails,
 * the original text is returned.
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
 * @since 1.0.0
 */
public final class TranslatorService {

    private static final Logger LOGGER = Logger.getLogger(TranslatorService.class.getName());

    // Unofficial Google Translate endpoint (free)
    private static final String GOOGLE_TRANSLATE_URL =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=%s&dt=t&q=%s";

    // Google Cloud Translation API v2 (paid)
    private static final String GCLOUD_TRANSLATE_URL =
            "https://translation.googleapis.com/language/translate/v2?key=%s&target=%s&q=%s";

    // DeepL API (paid/free tier)
    private static final String DEEPL_TRANSLATE_URL =
            "https://api.deepl.com/v2/translate?auth_key=%s&target_lang=%s&text=%s";

    private static final int CONNECTION_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;

    // Patterns for extracting translated text from JSON responses
    private static final Pattern GOOGLE_PATTERN = Pattern.compile("\\[\\[\\[\"([^\"]+)\"");
    private static final Pattern GCLOUD_PATTERN = Pattern.compile("\"translatedText\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DEEPL_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Translates the given text into the target language using the specified provider.
     *
     * @param text       the text to translate (may be null or empty)
     * @param targetLang the target language code (e.g., "en", "vi", "es")
     * @param provider   the translation provider ("google", "gcloud", "deepl", or "none")
     * @param apiKey     the API key (required for "gcloud" and "deepl")
     * @return the translated text, or the original if translation is disabled or fails
     */
    public String translate(String text, String targetLang, String provider, String apiKey) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        if (targetLang == null || targetLang.trim().isEmpty() || "none".equalsIgnoreCase(targetLang)) {
            DebugLogger.logFine(LOGGER, "TranslatorService: translation disabled (targetLang=none)");
            return text;
        }

        if (provider == null || "none".equalsIgnoreCase(provider)) {
            DebugLogger.logFine(LOGGER, "TranslatorService: provider is 'none', returning original text");
            return text;
        }

        DebugLogger.logFine(LOGGER, "TranslatorService: translating using provider '%s' to %s", provider, targetLang);

        String result = null;

        switch (provider.toLowerCase()) {
            case "google":
                result = translateWithGoogle(text, targetLang);
                break;
            case "gcloud":
                result = translateWithGCloud(text, targetLang, apiKey);
                break;
            case "deepl":
                result = translateWithDeepL(text, targetLang, apiKey);
                break;
            default:
                LOGGER.warning("[TranslatorService] Unknown provider: " + provider + ". Falling back to original text.");
                return text;
        }

        if (result != null && !result.isEmpty()) {
            return result;
        }

        // If translation failed, return original text.
        DebugLogger.log(LOGGER, "TranslatorService: translation failed, using original text");
        return text;
    }

    // --------------------- Provider Implementations ---------------------

    /**
     * Translates using the unofficial Google Translate API (free).
     *
     * @param text       the text to translate
     * @param targetLang the target language code
     * @return the translated text, or null if failed
     */
    private String translateWithGoogle(String text, String targetLang) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = String.format(GOOGLE_TRANSLATE_URL, targetLang, encoded);

            HttpURLConnection conn = createConnection(urlStr);
            if (conn == null) return null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = readResponse(reader);
                Matcher matcher = GOOGLE_PATTERN.matcher(response);
                if (matcher.find()) {
                    return decodeUnicode(matcher.group(1));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[TranslatorService] Google Translate failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Translates using Google Cloud Translation API v2 (paid).
     *
     * @param text       the text to translate
     * @param targetLang the target language code
     * @param apiKey     the Google Cloud API key
     * @return the translated text, or null if failed
     */
    private String translateWithGCloud(String text, String targetLang, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warning("[TranslatorService] Google Cloud API key missing. Please set translation-api-key in config.");
            return null;
        }
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = String.format(GCLOUD_TRANSLATE_URL, apiKey, targetLang, encoded);

            HttpURLConnection conn = createConnection(urlStr);
            if (conn == null) return null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = readResponse(reader);
                Matcher matcher = GCLOUD_PATTERN.matcher(response);
                if (matcher.find()) {
                    return decodeUnicode(matcher.group(1));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[TranslatorService] Google Cloud Translation failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Translates using DeepL API (paid/free tier).
     *
     * @param text       the text to translate
     * @param targetLang the target language code (must be a valid DeepL language code)
     * @param apiKey     the DeepL API key
     * @return the translated text, or null if failed
     */
    private String translateWithDeepL(String text, String targetLang, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warning("[TranslatorService] DeepL API key missing. Please set translation-api-key in config.");
            return null;
        }
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = String.format(DEEPL_TRANSLATE_URL, apiKey, targetLang, encoded);

            HttpURLConnection conn = createConnection(urlStr);
            if (conn == null) return null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = readResponse(reader);
                Matcher matcher = DEEPL_PATTERN.matcher(response);
                if (matcher.find()) {
                    return decodeUnicode(matcher.group(1));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[TranslatorService] DeepL translation failed: " + e.getMessage(), e);
        }
        return null;
    }

    // --------------------- Helper Methods ---------------------

    /**
     * Creates an HTTP connection with common settings.
     *
     * @param urlStr the URL string
     * @return the HttpURLConnection, or null if an error occurred
     */
    private HttpURLConnection createConnection(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                LOGGER.warning("[TranslatorService] HTTP error: " + conn.getResponseCode() + " for " + urlStr);
                return null;
            }
            return conn;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[TranslatorService] Failed to connect: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reads the entire response from a BufferedReader.
     *
     * @param reader the reader
     * @return the response string
     * @throws java.io.IOException if reading fails
     */
    private String readResponse(BufferedReader reader) throws java.io.IOException {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    }

    /**
     * Decodes Unicode escape sequences (e.g., \u0041) in a string.
     * <p>
     * Handles common escaped characters: \", \\, \n, etc.
     * </p>
     *
     * @param str the input string (may contain Unicode escapes)
     * @return the decoded string
     */
    private String decodeUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        // Handle common escape sequences first
        str = str.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");

        if (!str.contains("\\u")) {
            return str;
        }

        StringBuilder sb = new StringBuilder(str.length());
        int len = str.length();
        int i = 0;

        while (i < len) {
            char ch = str.charAt(i);
            if (ch == '\\' && i + 1 < len && str.charAt(i + 1) == 'u' && i + 5 < len) {
                try {
                    String hex = str.substring(i + 2, i + 6);
                    int code = Integer.parseInt(hex, 16);
                    sb.append((char) code);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // Not a valid Unicode escape, treat as normal characters
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }
}