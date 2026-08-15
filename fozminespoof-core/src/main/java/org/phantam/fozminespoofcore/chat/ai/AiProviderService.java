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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service for fetching AI responses from various providers (OpenAI, Gemini, Custom).
 * Supports conversation memory history.
 */
public class AiProviderService {

    private final HttpClient httpClient;
    private final Logger logger;

    public record ChatMessage(String role, String content, long timestamp) {}

    public AiProviderService(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public CompletableFuture<String> fetchAiResponseAsync(AiConfig config, String systemPrompt, List<ChatMessage> history, String userMessage) {
        String provider = config.getModelProvider();
        DebugLogger.log(logger, "AiProviderService: fetching with provider: %s (History size: %d)", provider, history != null ? history.size() : 0);

        return switch (provider) {
            case "GEMINI" -> fetchGeminiAsync(config, systemPrompt, history, userMessage);
            case "CUSTOM" -> fetchCustomLocalAsync(config, systemPrompt, history, userMessage);
            default -> fetchOpenAiAsync(config, systemPrompt, history, userMessage);
        };
    }

    // --------------------- OpenAI ---------------------

    private CompletableFuture<String> fetchOpenAiAsync(AiConfig config, String systemPrompt, List<ChatMessage> history, String userMessage) {
        var gpt = config.getGptConfig();

        JsonArray messagesArray = new JsonArray();
        JsonObject systemObj = new JsonObject();
        systemObj.addProperty("role", "system");
        systemObj.addProperty("content", systemPrompt);
        messagesArray.add(systemObj);

        if (history != null) {
            for (ChatMessage msg : history) {
                JsonObject histObj = new JsonObject();
                histObj.addProperty("role", msg.role());
                histObj.addProperty("content", msg.content());
                messagesArray.add(histObj);
            }
        }

        JsonObject userObj = new JsonObject();
        userObj.addProperty("role", "user");
        userObj.addProperty("content", userMessage);
        messagesArray.add(userObj);

        JsonObject root = new JsonObject();
        root.addProperty("model", gpt.modelName());
        root.add("messages", messagesArray);
        root.addProperty("max_tokens", gpt.maxTokens());
        root.addProperty("temperature", gpt.temperature());
        root.addProperty("presence_penalty", gpt.presencePenalty());
        root.addProperty("frequency_penalty", gpt.frequencyPenalty());

        String jsonPayload = root.toString();
        DebugLogger.log(logger, "AiProviderService: OpenAI payload: %s", jsonPayload);

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
                        return parseOpenAIResponse(response.body());
                    } else {
                        DebugLogger.log(logger, "AiProviderService: OpenAI Error %d: %s", response.statusCode(), response.body());
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
            return null;
        }
    }

    // --------------------- Gemini ---------------------

    private CompletableFuture<String> fetchGeminiAsync(AiConfig config, String systemPrompt, List<ChatMessage> history, String userMessage) {
        var gemini = config.getGeminiConfig();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + gemini.modelName()
                + ":generateContent?key=" + config.getApiKey();

        JsonObject root = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysText = new JsonObject();
        sysText.addProperty("text", systemPrompt);
        sysParts.add(sysText);
        systemInstruction.add("parts", sysParts);
        root.add("system_instruction", systemInstruction);

        JsonArray contentsArray = new JsonArray();

        if (history != null) {
            for (ChatMessage msg : history) {
                JsonObject turn = new JsonObject();
                // Gemini dùng role "user" và "model"
                turn.addProperty("role", "assistant".equalsIgnoreCase(msg.role()) ? "model" : "user");
                JsonArray parts = new JsonArray();
                JsonObject textObj = new JsonObject();
                textObj.addProperty("text", msg.content());
                parts.add(textObj);
                turn.add("parts", parts);
                contentsArray.add(turn);
            }
        }

        JsonObject currentTurn = new JsonObject();
        currentTurn.addProperty("role", "user");
        JsonArray currentParts = new JsonArray();
        JsonObject currentText = new JsonObject();
        currentText.addProperty("text", userMessage);
        currentParts.add(currentText);
        currentTurn.add("parts", currentParts);
        contentsArray.add(currentTurn);

        root.add("contents", contentsArray);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", gemini.maxTokens());
        genConfig.addProperty("temperature", gemini.temperature());
        root.add("generationConfig", genConfig);

        String jsonPayload = root.toString();
        DebugLogger.log(logger, "AiProviderService: Gemini payload: %s", jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseGeminiResponse(response.body());
                    } else {
                        DebugLogger.log(logger, "AiProviderService: Gemini Error %d: %s", response.statusCode(), response.body());
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
            return null;
        }
    }

    // --------------------- Custom Local ---------------------

    private CompletableFuture<String> fetchCustomLocalAsync(AiConfig config, String systemPrompt, List<ChatMessage> history, String userMessage) {
        var custom = config.getCustomConfig();
        String url = custom.apiUrl().endsWith("/chat/completions") ? custom.apiUrl() : custom.apiUrl() + "/chat/completions";

        JsonArray messagesArray = new JsonArray();
        JsonObject systemObj = new JsonObject();
        systemObj.addProperty("role", "system");
        systemObj.addProperty("content", systemPrompt);
        messagesArray.add(systemObj);

        if (history != null) {
            for (ChatMessage msg : history) {
                JsonObject histObj = new JsonObject();
                histObj.addProperty("role", msg.role());
                histObj.addProperty("content", msg.content());
                messagesArray.add(histObj);
            }
        }

        JsonObject userObj = new JsonObject();
        userObj.addProperty("role", "user");
        userObj.addProperty("content", userMessage);
        messagesArray.add(userObj);

        JsonObject root = new JsonObject();
        root.addProperty("model", custom.modelName());
        root.add("messages", messagesArray);
        root.addProperty("max_tokens", custom.maxTokens());
        root.addProperty("temperature", custom.temperature());

        String jsonPayload = root.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseOpenAIResponse(response.body());
                    } else {
                        return null;
                    }
                })
                .exceptionally(ex -> null);
    }
}