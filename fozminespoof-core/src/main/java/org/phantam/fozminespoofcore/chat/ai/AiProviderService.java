package org.phantam.fozminespoofcore.chat.ai;

import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.config.AiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class AiProviderService {

    private final HttpClient httpClient;
    private final Logger logger;

    public AiProviderService(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public CompletableFuture<String> fetchAiResponseAsync(AiConfig config, String systemPrompt, String userMessage) {
        String provider = config.getModelProvider();

        return switch (provider) {
            case "GEMINI" -> fetchGeminiAsync(config, systemPrompt, userMessage);
            case "CUSTOM" -> fetchCustomLocalAsync(config, systemPrompt, userMessage);
            default -> fetchOpenAiAsync(config, systemPrompt, userMessage); // Default GPT
        };
    }

    private CompletableFuture<String> fetchOpenAiAsync(AiConfig config, String systemPrompt, String userMessage) {
        var gpt = config.getGptConfig();
        String jsonPayload = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ],
                  "max_tokens": %d,
                  "temperature": %.2f,
                  "presence_penalty": %.2f,
                  "frequency_penalty": %.2f
                }
                """.formatted(
                gpt.modelName(),
                escapeJson(systemPrompt),
                escapeJson(userMessage),
                gpt.maxTokens(),
                gpt.temperature(),
                gpt.presencePenalty(),
                gpt.frequencyPenalty()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseJsonContentByKey(response.body(), "\"content\":");
                    } else {
                        DebugLogger.log(logger, "AiProviderService: OpenAI HTTP Error %d: %s", response.statusCode(), response.body());
                        return null;
                    }
                }).exceptionally(ex -> {
                    DebugLogger.log(logger, "AiProviderService: OpenAI exception: %s", ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<String> fetchGeminiAsync(AiConfig config, String systemPrompt, String userMessage) {
        var gemini = config.getGeminiConfig();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + gemini.modelName()
                + ":generateContent?key=" + config.getApiKey();

        String jsonPayload = """
                {
                  "system_instruction": {
                    "parts": [{"text": "%s"}]
                  },
                  "contents": [
                    {"parts": [{"text": "%s"}]}
                  ],
                  "generationConfig": {
                    "maxOutputTokens": %d,
                    "temperature": %.2f
                  }
                }
                """.formatted(
                escapeJson(systemPrompt),
                escapeJson(userMessage),
                gemini.maxTokens(),
                gemini.temperature()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseJsonContentByKey(response.body(), "\"text\":");
                    } else {
                        DebugLogger.log(logger, "AiProviderService: Gemini HTTP Error %d: %s", response.statusCode(), response.body());
                        return null;
                    }
                }).exceptionally(ex -> null);
    }

    private CompletableFuture<String> fetchCustomLocalAsync(AiConfig config, String systemPrompt, String userMessage) {
        var custom = config.getCustomConfig();
        String url = custom.apiUrl().endsWith("/chat/completions") ? custom.apiUrl() : custom.apiUrl() + "/chat/completions";

        String jsonPayload = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ],
                  "max_tokens": %d,
                  "temperature": %.2f
                }
                """.formatted(
                custom.modelName(),
                escapeJson(systemPrompt),
                escapeJson(userMessage),
                custom.maxTokens(),
                custom.temperature()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseJsonContentByKey(response.body(), "\"content\":");
                    } else {
                        return null;
                    }
                }).exceptionally(ex -> null);
    }

    /**
     * State-machine JSON String Extractor (Xử lý an toàn ngoặc kép, dấu xuyệt ngược và Unicode)
     */
    private String parseJsonContentByKey(String json, String key) {
        if (json == null || json.isBlank()) return null;
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;

        int startQuote = json.indexOf('"', keyIdx + key.length());
        if (startQuote == -1) return null;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                int code = Integer.parseInt(json.substring(i + 1, i + 5), 16);
                                sb.append((char) code);
                                i += 4;
                            } catch (Exception ignored) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}