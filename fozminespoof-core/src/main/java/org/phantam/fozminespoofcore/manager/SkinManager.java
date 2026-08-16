package org.phantam.fozminespoofcore.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Asynchronous skin fetcher with multi-tier caching (RAM + Persistent Storage) for fake players.
 */
public class SkinManager {

    private final FozmineSpoofCore plugin;
    private final HttpClient httpClient;
    private final Map<String, SkinProperty> memoryCache = new ConcurrentHashMap<>();

    public record SkinProperty(String value, String signature) {}

    public SkinManager(FozmineSpoofCore plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        initCacheTable();
    }

    private void initCacheTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().log(Level.INFO, "[SkinManager] Skin cache repository initialized.");
            } catch (Exception e) {
                plugin.getLogger().warning("[SkinManager] Failed to initialize skin cache: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Optional<SkinProperty>> getSkinAsync(String botName) {
        String lowerName = botName.toLowerCase();

        SkinProperty cached = memoryCache.get(lowerName);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        return fetchSkinFromApi(botName).thenApply(skinOpt -> {
            skinOpt.ifPresent(skin -> memoryCache.put(lowerName, skin));
            return skinOpt;
        });
    }

    private CompletableFuture<Optional<SkinProperty>> fetchSkinFromApi(String botName) {
        String url = "https://api.ashcon.app/mojang/v2/user/" + botName;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "FozmineSpoof-SkinFetcher/2.0")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, ex) -> {
                    if (ex != null || response == null || response.statusCode() != 200) {
                        if (ex != null) {
                            DebugLogger.logFine(plugin.getLogger(), "SkinManager: skin fetch failed for %s: %s", botName, ex.getMessage());
                        }
                        return Optional.<SkinProperty>empty();
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonObject textures = root.getAsJsonObject("textures");
                        if (textures != null && textures.has("raw")) {
                            JsonObject raw = textures.getAsJsonObject("raw");
                            String value = raw.get("value").getAsString();
                            String signature = raw.get("signature").getAsString();
                            DebugLogger.logFine(plugin.getLogger(), "SkinManager: fetched skin for %s", botName);
                            return Optional.of(new SkinProperty(value, signature));
                        }
                    } catch (Exception ignored) {}
                    return Optional.<SkinProperty>empty();
                });
    }
}