package org.phantam.fozminesproofcore.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;

import java.util.logging.Level;

/**
 * PlaceholderAPI expansion for fake players.
 * Provides placeholders like %fake_server_online% (real + fake players count)
 * and supports parsing bot-specific placeholders with Vault/LuckPerms fallback.
 */
public class FakePlayerPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FakePlayerManager fakePlayerManager;

    public FakePlayerPapiExpansion(JavaPlugin plugin, ConfigManager configManager,
                                   FakePlayerManager fakePlayerManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.fakePlayerManager = fakePlayerManager;
    }

    @Override
    public String getIdentifier() {
        return "fake";
    }

    @Override
    public String getAuthor() {
        return "phantam";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        // If the placeholder is not whitelisted, return empty for bots, null otherwise
        if (!configManager.isTargetPlaceholder(params)) {
            if (player != null && fakePlayerManager != null && fakePlayerManager.isBotOnline(player.getName())) {
                return "";
            }
            return null;
        }

        String botName = (player != null) ? player.getName() : null;

        // ---- Handle bot-specific placeholders ----
        if (botName != null && fakePlayerManager != null && fakePlayerManager.isBotOnline(botName)) {
            return resolveBotPlaceholder(botName, params, player);
        }

        // ---- Handle real player counts (server online players) ----
        // Safe thread check to avoid concurrency issues
        if (!Bukkit.isPrimaryThread()) {
            return "0";
        }

        int realCount = resolveRealPlayerCount(params, player);
        int fakeCount = fakePlayerManager != null ? fakePlayerManager.getOnlineBotsData().size() : 0;
        return String.valueOf(realCount + fakeCount);
    }

    /**
     * Resolves a placeholder specifically for an online bot.
     * Handles Vault/LuckPerms placeholders with a safe fallback.
     *
     * @param botName the bot's name
     * @param params  the placeholder parameter
     * @param player  the OfflinePlayer representing the bot
     * @return the resolved value, or empty string if not found
     */
    private String resolveBotPlaceholder(String botName, String params, OfflinePlayer player) {
        // Special handling for Vault and LuckPerms placeholders
        if (params.startsWith("vault_") || params.startsWith("luckperms_")) {
            String type = "prefix";
            if (params.contains("suffix")) {
                type = "suffix";
            } else if (params.contains("primary_group") || params.contains("group")) {
                type = "primary_group_name";
            }

            // Build safe LuckPerms placeholder for the specific bot
            String safePlaceholder = "%luckperms_user_" + type + "_" + botName + "%";
            String resolved = PlaceholderAPI.setPlaceholders(player, safePlaceholder);

            if (resolved != null && !resolved.equals(safePlaceholder)) {
                return resolved;
            }
            return "";
        }

        // Generic fallback for any other plugin placeholder
        String rawPlaceholder = "%" + params + "%";
        String resolved = PlaceholderAPI.setPlaceholders(
                Bukkit.getOfflinePlayer(player.getUniqueId()), rawPlaceholder
        );

        if (resolved != null && !resolved.equals(rawPlaceholder)) {
            return resolved;
        }
        return "";
    }

    /**
     * Resolves the count of real players on the server or on a specific proxy server.
     *
     * @param params the placeholder parameter (e.g., "server_online" or "bungee_*")
     * @param player the player context for PAPI parsing
     * @return the real player count
     */
    private int resolveRealPlayerCount(String params, OfflinePlayer player) {
        OfflinePlayer target = (player != null && player.isOnline()) ? player : getFallbackPlayer();

        if (params.startsWith("bungee_")) {
            return resolveProxyCount(params, target);
        } else {
            return resolveLocalCount(params, target);
        }
    }

    /**
     * Resolves local server online counts.
     *
     * @param params the placeholder
     * @param target the player context
     * @return the count
     */
    private int resolveLocalCount(String params, OfflinePlayer target) {
        if (params.equalsIgnoreCase("server_online")) {
            return Bukkit.getOnlinePlayers().size();
        }
        return parsePlaceholderToInt(params, target);
    }

    /**
     * Resolves proxy (BungeeCord) player counts.
     *
     * @param params the placeholder (e.g., "bungee_lobby")
     * @param target the player context
     * @return the count, or fallback to current server if the proxy placeholder fails
     */
    private int resolveProxyCount(String params, OfflinePlayer target) {
        if (target == null) {
            return Bukkit.getOnlinePlayers().size();
        }

        int count = parsePlaceholderToInt(params, target);
        if (count == 0) {
            // Fallback: if the proxy placeholder didn't resolve, try current server name
            String currentServer = Bukkit.getServer().getName();
            if (params.equalsIgnoreCase("bungee_" + currentServer)) {
                return Bukkit.getOnlinePlayers().size();
            }
        }
        return count;
    }

    /**
     * Parses a placeholder as an integer using PAPI.
     *
     * @param params the placeholder parameter
     * @param target the player context
     * @return the parsed integer, or 0 if parsing fails
     */
    private int parsePlaceholderToInt(String params, OfflinePlayer target) {
        if (target == null) {
            return 0;
        }

        try {
            String placeholder = "%" + params + "%";
            String value = PlaceholderAPI.setPlaceholders(target, placeholder);
            if (value == null || value.equals(placeholder) || value.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[FakePlayerPapiExpansion] Failed to parse placeholder '" + params + "': " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns a fallback player (first online player) if no player context is available.
     *
     * @return an online OfflinePlayer, or null if none
     */
    private OfflinePlayer getFallbackPlayer() {
        return Bukkit.getOnlinePlayers().isEmpty()
                ? null
                : Bukkit.getOnlinePlayers().iterator().next();
    }
}