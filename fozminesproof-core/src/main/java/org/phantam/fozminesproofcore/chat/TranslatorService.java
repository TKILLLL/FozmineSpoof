package org.phantam.fozminesproofcore.chat;

import org.phantam.fozminesproofcore.utils.DebugLogger;

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
 * Translates text using a free Google Translate API endpoint.
 */
public class TranslatorService {

    private static final Pattern JSON_TEXT_PATTERN = Pattern.compile("^\\[\\[\\[\"([^\"]+)\"");
    private static final Logger LOGGER = Logger.getLogger(TranslatorService.class.getName());

    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        if (targetLang == null || targetLang.trim().equalsIgnoreCase("none")) {
            DebugLogger.logFine(LOGGER, "TranslatorService: translation disabled (targetLang=none)");
            return text;
        }

        DebugLogger.logFine(LOGGER, "TranslatorService: translating text length %d to %s", text.length(), targetLang);

        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                    + targetLang + "&dt=t&q=" + encoded;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                LOGGER.warning("[TranslatorService] HTTP error: " + conn.getResponseCode() + " - returning original text.");
                DebugLogger.log(LOGGER, "TranslatorService: HTTP error %d, fallback to original", conn.getResponseCode());
                return text;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                Matcher matcher = JSON_TEXT_PATTERN.matcher(response.toString());
                if (matcher.find()) {
                    String result = decodeUnicode(matcher.group(1));
                    DebugLogger.logFine(LOGGER, "TranslatorService: translation successful: '%s'", result);
                    return result;
                } else {
                    DebugLogger.log(LOGGER, "TranslatorService: no translation found in response");
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[TranslatorService] Translation failed: " + e.getMessage(), e);
            DebugLogger.log(LOGGER, "TranslatorService: exception, fallback to original: %s", e.getMessage());
        }

        return text;
    }

    private String decodeUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        str = str.replace("\\\"", "\"").replace("\\\\", "\\");

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
                } catch (NumberFormatException ignored) {}
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }
}