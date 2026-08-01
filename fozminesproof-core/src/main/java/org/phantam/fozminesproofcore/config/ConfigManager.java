package org.phantam.fozminesproofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Central configuration manager for the plugin.
 * Holds all settings and provides reload capability.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final MessageManager messageManager;

    // Core settings
    private List<String> targetPlaceholders;
    private String botWorldName;
    private boolean joinLeaveMessageEnable;
    private String joinMessage;
    private String leaveMessage;
    private String fakePluginName;
    private boolean hideInTab;
    private static final Random RANDOM = new Random();
    private boolean debug;

    // Chat system
    private ChatConfig chatConfig;
    private String chatFormat;
    private boolean messageFormatEnable;
    private String messageChatFormat;

    // Proxy & database
    private Boolean proxyEnable;
    private String bungeeName;
    private boolean databaseEnabled;
    private String rawDatabaseName;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = new MessageManager(plugin);
        this.reloadAllConfigs();
    }

    /**
     * Reloads all configuration from disk and reinitialises settings.
     */
    public void reloadAllConfigs() {
        try {
            plugin.reloadConfig();
            FileConfiguration config = plugin.getConfig();

            // Reload messages
            this.messageManager.reload();
            this.debug = config.getBoolean("debug", false);

            // Debug log: show config file is being reloaded
            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: reloading configuration...");
            }

            // General settings
            this.targetPlaceholders = config.getStringList("parse-papi");
            this.botWorldName = config.getString("Fakeplayer-setting.botworld", "botworld");
            this.joinLeaveMessageEnable = config.getBoolean("Fakeplayer-setting.join-leave-message-enable", true);
            this.joinMessage = config.getString("Fakeplayer-setting.join-message", "%fakeplayer_name% join the game");
            this.leaveMessage = config.getString("Fakeplayer-setting.leave-message", "%fakeplayer_name% left the game");
            this.fakePluginName = config.getString("Fakeplayer-setting.fake-plugin-name", "FozmineSpawner");
            this.hideInTab = config.getBoolean("Fakeplayer-setting.hide-in-tab", false);

            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: botWorldName=%s, joinLeave=%s, hideInTab=%s, fakePluginName=%s",
                        botWorldName, joinLeaveMessageEnable, hideInTab, fakePluginName);
                DebugLogger.log(plugin.getLogger(), "ConfigManager: joinMessage=%s, leaveMessage=%s", joinMessage, leaveMessage);
            }

            // Chat format settings
            this.messageFormatEnable = config.getBoolean("chat-system.message-format.enable", false);
            this.messageChatFormat = config.getString("chat-system.message-format.chat-format",
                    "&7[&a%fakeplayer_name%&7]&f: %fakeplayer_message%");

            // Root fallback formats
            this.chatFormat = config.getString("chat-system.chat-format", "<%fakeplayer_name%> %fakeplayer_message%");

            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: messageFormatEnable=%s, chatFormat=%s",
                        messageFormatEnable, chatFormat);
            }

            // Proxy & database
            this.proxyEnable = config.getBoolean("Database.bridging-setting.enable-proxy", false);
            this.bungeeName = config.getString("Database.bridging-setting.bungee_name", "fozminesproof");
            this.chatConfig = new ChatConfig(config);
            this.databaseEnabled = config.getBoolean("Database.enable", true);
            this.rawDatabaseName = config.getString("Database.name", "fozminesproof");

            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: proxyEnable=%s, databaseEnabled=%s, rawDatabaseName=%s",
                        proxyEnable, databaseEnabled, rawDatabaseName);
                DebugLogger.log(plugin.getLogger(), "ConfigManager: reload complete.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[FozmineSproof] Failed to load config.yml: " + e.getMessage(), e);
            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: error during reload: %s", e.getMessage());
            }
        }
    }

    /**
     * Checks if a given placeholder should be parsed by PlaceholderAPI.
     *
     * @param placeholder the placeholder string
     * @return true if the placeholder is in the target list
     */
    public boolean isTargetPlaceholder(String placeholder) {
        boolean result = this.targetPlaceholders != null && this.targetPlaceholders.contains(placeholder);
        if (debug) {
            DebugLogger.logFine(plugin.getLogger(), "ConfigManager: isTargetPlaceholder(%s)=%s", placeholder, result);
        }
        return result;
    }

    /**
     * Returns a random delay in ticks for join/quit intervals.
     *
     * @return delay in ticks
     */
    public int getJoinQuitIntervalTicks() {
        String value = plugin.getConfig().getString("Fakeplayer-setting.join-quit-interval", "0-1");
        if (value == null) return 20;

        try {
            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int result = ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
            if (debug) {
                DebugLogger.logFine(plugin.getLogger(), "ConfigManager: getJoinQuitIntervalTicks()=%d (range: %s)", result, value);
            }
            return result;
        } catch (Exception e) {
            return 20;
        }
    }

    /**
     * Returns a random interval for proxy updates (in seconds).
     *
     * @return interval in seconds
     */
    public int getProxyUpdateInterval() {
        String value = plugin.getConfig().getString("Database.bridging-setting.update-interval", "2-3");
        if (value == null) return 20;

        try {
            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int result = ThreadLocalRandom.current().nextInt(min, max + 1);
            if (debug) {
                DebugLogger.logFine(plugin.getLogger(), "ConfigManager: getProxyUpdateInterval()=%d (range: %s)", result, value);
            }
            return result;
        } catch (Exception e) {
            return 20;
        }
    }

    // --- Getters ---

    public boolean isDebug() {
        return this.debug;
    }

    public MessageManager getMessages() {
        return this.messageManager;
    }

    public String getBotWorldName() {
        return this.botWorldName;
    }

    public boolean isJoinLeaveMessageEnable() {
        return this.joinLeaveMessageEnable;
    }

    public String getJoinMessage() {
        return this.joinMessage;
    }

    public String getLeaveMessage() {
        return this.leaveMessage;
    }

    public ChatConfig getChatConfig() {
        return this.chatConfig;
    }

    public String getFakePluginName() {
        return this.fakePluginName;
    }

    private int randomLatency() {
        return 5 + RANDOM.nextInt(16); // 5 - 20 ms
    }

    public boolean isHideInTab() {
        return this.hideInTab;
    }

    /**
     * Returns the active chat format based on the message-format.enable flag.
     * If enabled, returns the custom format; otherwise returns the root format.
     */
    public String getChatFormat() {
        return this.messageFormatEnable ? this.messageChatFormat : this.chatFormat;
    }

    /**
     * @return true if a dedicated message format is enabled
     */
    public boolean isMessageFormatEnable() {
        return this.messageFormatEnable;
    }

    public Boolean isProxyEnable() {
        return this.proxyEnable;
    }

    public String getBungeeName() {
        return this.bungeeName;
    }

    public String getRawDatabaseName() {
        return this.rawDatabaseName;
    }

    public boolean isDatabaseEnabled() {
        return this.databaseEnabled;
    }
}