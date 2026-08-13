package org.phantam.fozminespoofcore.chat.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.config.AiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service for fetching AI responses from various providers (OpenAI, Gemini, Custom).
 * Uses Gson for JSON parsing and handles errors gracefully.
 */
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
        DebugLogger.log(logger, "AiProviderService: fetching with provider: %s", provider);

        return switch (provider) {
            case "GEMINI" -> fetchGeminiAsync(config, systemPrompt, userMessage);
            case "CUSTOM" -> fetchCustomLocalAsync(config, systemPrompt, userMessage);
            default -> fetchOpenAiAsync(config, systemPrompt, userMessage);
        };
    }

    // --------------------- OpenAI ---------------------

    private CompletableFuture<String> fetchOpenAiAsync(AiConfig config, String systemPrompt, String userMessage) {
        DebugLogger.log(logger, "AiProviderService: fetchOpenAiAsync called");
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

        DebugLogger.log(logger, "AiProviderService: OpenAI request payload: %s", jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    DebugLogger.log(logger, "AiProviderService: OpenAI response status: %d", response.statusCode());
                    if (response.statusCode() == 200) {
                        String parsed = parseOpenAIResponse(response.body());
                        DebugLogger.log(logger, "AiProviderService: OpenAI parsed response: %s", parsed);
                        return parsed;
                    } else {
                        DebugLogger.log(logger, "AiProviderService: OpenAI HTTP Error %d: %s", response.statusCode(), response.body());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.log(logger, "AiProviderService: OpenAI exception: %s", ex.getMessage());
                    return null;
                });
    }

    private String parseOpenAIResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            if (message == null) return null;
            return message.get("content").getAsString();
        } catch (Exception e) {
            DebugLogger.log(logger, "AiProviderService: Failed to parse OpenAI response: %s", e.getMessage());
            return null;
        }
    }

    // --------------------- Gemini ---------------------

    private CompletableFuture<String> fetchGeminiAsync(AiConfig config, String systemPrompt, String userMessage) {
        DebugLogger.log(logger, "AiProviderService: fetchGeminiAsync called");
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

        DebugLogger.log(logger, "AiProviderService: Gemini request URL: %s", url);
        DebugLogger.log(logger, "AiProviderService: Gemini payload: %s", jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    DebugLogger.log(logger, "AiProviderService: Gemini response status: %d", response.statusCode());
                    if (response.statusCode() == 200) {
                        String parsed = parseGeminiResponse(response.body());
                        DebugLogger.log(logger, "AiProviderService: Gemini parsed: %s", parsed);
                        return parsed;
                    } else {
                        DebugLogger.log(logger, "AiProviderService: Gemini HTTP Error %d: %s", response.statusCode(), response.body());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.log(logger, "AiProviderService: Gemini exception: %s", ex.getMessage());
                    return null;
                });
    }

    private String parseGeminiResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            JsonObject first = candidates.get(0).getAsJsonObject();
            JsonObject content = first.getAsJsonObject("content");
            if (content == null) return null;
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) return null;
            return parts.get(0).getAsJsonObject().get("text").getAsString();
        } catch (Exception e) {
            DebugLogger.log(logger, "AiProviderService: Failed to parse Gemini response: %s", e.getMessage());
            return null;
        }
    }

    // --------------------- Custom Local ---------------------

    private CompletableFuture<String> fetchCustomLocalAsync(AiConfig config, String systemPrompt, String userMessage) {
        DebugLogger.log(logger, "AiProviderService: fetchCustomLocalAsync called");
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

        DebugLogger.log(logger, "AiProviderService: Custom URL: %s", url);
        DebugLogger.log(logger, "AiProviderService: Custom payload: %s", jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    DebugLogger.log(logger, "AiProviderService: Custom response status: %d", response.statusCode());
                    if (response.statusCode() == 200) {
                        String parsed = parseCustomResponse(response.body());
                        DebugLogger.log(logger, "AiProviderService: Custom parsed: %s", parsed);
                        return parsed;
                    } else {
                        DebugLogger.log(logger, "AiProviderService: Custom API HTTP Error %d", response.statusCode());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.log(logger, "AiProviderService: Custom API exception: %s", ex.getMessage());
                    return null;
                });
    }

    private String parseCustomResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            if (message == null) return null;
            return message.get("content").getAsString();
        } catch (Exception e) {
            DebugLogger.log(logger, "AiProviderService: Failed to parse Custom response: %s", e.getMessage());
            return null;
        }
    }

    // --------------------- Utilities ---------------------

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}