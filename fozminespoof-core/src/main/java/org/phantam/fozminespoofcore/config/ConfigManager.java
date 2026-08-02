package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.utils.Range;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {

    private static final String DEBUG = "debug";
    private static final String PARSE_PAPI = "parse-papi";
    private static final String BOT_WORLD = "Fakeplayer-setting.botworld";
    private static final String JOIN_LEAVE_ENABLE = "Fakeplayer-setting.join-leave-message-enable";
    private static final String JOIN_MESSAGE = "Fakeplayer-setting.join-message";
    private static final String LEAVE_MESSAGE = "Fakeplayer-setting.leave-message";
    private static final String FAKE_PLUGIN_NAME = "Fakeplayer-setting.fake-plugin-name";
    private static final String HIDE_IN_TAB = "Fakeplayer-setting.hide-in-tab";
    private static final String JOIN_ACTIONS_FAKE_ENABLED = "Fakeplayer-setting.join-actions.fakeplayer.enabled";
    private static final String JOIN_ACTIONS_FAKE_COMMANDS = "Fakeplayer-setting.join-actions.fakeplayer.commands";
    private static final String JOIN_ACTIONS_CONSOLE_ENABLED = "Fakeplayer-setting.join-actions.console.enabled";
    private static final String JOIN_ACTIONS_CONSOLE_COMMANDS = "Fakeplayer-setting.join-actions.console.commands";
    private static final String LIFETIME_INTERVAL = "Fakeplayer-setting.lifetime-interval";
    private static final String BASE_AMOUNT = "Fakeplayer-setting.base-amount";
    private static final String PERCENT_RATE = "Fakeplayer-setting.percent-rate";

    private static final String MSG_FORMAT_ENABLE = "chat-system.message-format.enable";
    private static final String MSG_CHAT_FORMAT = "chat-system.message-format.chat-format";
    private static final String CHAT_FORMAT = "chat-system.chat-format";

    private static final String PROXY_ENABLE = "Database.bridging-setting.enable-proxy";
    private static final String BUNGEE_NAME = "Database.bridging-setting.bungee_name";
    private static final String DB_ENABLE = "Database.enable";
    private static final String DB_NAME = "Database.name";

    private final JavaPlugin plugin;
    private final MessageManager messageManager;

    // Core
    private List<String> targetPlaceholders;
    private String botWorldName;
    private boolean joinLeaveMessageEnable;
    private String joinMessage;
    private String leaveMessage;
    private String fakePluginName;
    private boolean hideInTab;
    private boolean debug;

    // Join actions
    private boolean fakePlayerJoinCommandsEnabled;
    private List<String> fakePlayerJoinCommands;
    private boolean consoleJoinCommandsEnabled;
    private List<String> consoleJoinCommands;

    // Lifecycle
    private String lifetimeInterval;
    private int baseAmount;
    private int percentRate;

    // Fluctuations (Peak Hours)
    private boolean fluctuationEnabled;
    private String fluctuationTimezone;
    private List<String> fluctuationActiveHours;
    private int fluctuationBaseAmount;
    private int fluctuationPercentRate;

    // Chat
    private ChatConfig chatConfig;
    private String chatFormat;
    private boolean messageFormatEnable;
    private String messageChatFormat;

    // Database & Proxy
    private Boolean proxyEnable;
    private String bungeeName;
    private boolean databaseEnabled;
    private String rawDatabaseName;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = new MessageManager(plugin);
        this.reloadAllConfigs();
    }

    public void reloadAllConfigs() {
        try {
            plugin.reloadConfig();
            FileConfiguration config = plugin.getConfig();

            messageManager.reload();
            debug = config.getBoolean(DEBUG, false);
            DebugLogger.setDebugEnabled(debug);

            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: reloading configuration...");
            }

            // Core
            targetPlaceholders = config.getStringList(PARSE_PAPI);
            botWorldName = config.getString(BOT_WORLD, "botworld");
            joinLeaveMessageEnable = config.getBoolean(JOIN_LEAVE_ENABLE, true);
            joinMessage = config.getString(JOIN_MESSAGE, "%fakeplayer_name% join the game");
            leaveMessage = config.getString(LEAVE_MESSAGE, "%fakeplayer_name% left the game");
            fakePluginName = config.getString(FAKE_PLUGIN_NAME, "FozmineSpawner");
            hideInTab = config.getBoolean(HIDE_IN_TAB, false);

            // Join actions
            fakePlayerJoinCommandsEnabled = config.getBoolean(JOIN_ACTIONS_FAKE_ENABLED, false);
            fakePlayerJoinCommands = config.getStringList(JOIN_ACTIONS_FAKE_COMMANDS);
            consoleJoinCommandsEnabled = config.getBoolean(JOIN_ACTIONS_CONSOLE_ENABLED, false);
            consoleJoinCommands = config.getStringList(JOIN_ACTIONS_CONSOLE_COMMANDS);

            // Lifecycle
            lifetimeInterval = config.getString(LIFETIME_INTERVAL, "1800-3600");
            baseAmount = config.getInt(BASE_AMOUNT, 10);
            percentRate = config.getInt(PERCENT_RATE, 10);

            // Fluctuations (Peak Hours)
            fluctuationEnabled = config.getBoolean("fluctuations.enabled", false);
            fluctuationTimezone = config.getString("fluctuations.timezone", "Asia/Ho_Chi_Minh");
            fluctuationActiveHours = config.getStringList("fluctuations.active-hours");
            fluctuationBaseAmount = config.getInt("fluctuations.base-amount", 10);
            fluctuationPercentRate = config.getInt("fluctuations.percent-rate", 50);

            // Chat
            messageFormatEnable = config.getBoolean(MSG_FORMAT_ENABLE, false);
            messageChatFormat = config.getString(MSG_CHAT_FORMAT, "&7[&a%fakeplayer_name%&7]&f: %fakeplayer_message%");
            chatFormat = config.getString(CHAT_FORMAT, "<%fakeplayer_name%> %fakeplayer_message%");
            chatConfig = new ChatConfig(config);

            // Database & Proxy
            proxyEnable = config.getBoolean(PROXY_ENABLE, false);
            bungeeName = config.getString(BUNGEE_NAME, "fozminespoof");
            databaseEnabled = config.getBoolean(DB_ENABLE, true);
            rawDatabaseName = config.getString(DB_NAME, "fozminespoof");

            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: reload complete.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[FozmineSpoof] Failed to load config.yml: " + e.getMessage(), e);
        }
    }

    // ---- Peak Hours / Fluctuation Logic ----

    public boolean isFluctuationEnabled() {
        return fluctuationEnabled;
    }

    public boolean isInFluctuationActiveHours() {
        if (!fluctuationEnabled || fluctuationActiveHours == null || fluctuationActiveHours.isEmpty()) {
            return false;
        }
        try {
            ZoneId zoneId = parseZoneId(fluctuationTimezone);
            LocalTime now = LocalTime.now(zoneId);

            for (String rangeStr : fluctuationActiveHours) {
                if (rangeStr == null || !rangeStr.contains("-")) continue;
                String[] parts = rangeStr.split("-");
                LocalTime start = LocalTime.parse(parts[0].trim());
                LocalTime end = LocalTime.parse(parts[1].trim());

                if (start.isBefore(end)) {
                    // Khung giờ trong ngày (VD: 12:00-14:00)
                    if (!now.isBefore(start) && now.isBefore(end)) {
                        return true;
                    }
                } else {
                    // Khung giờ qua đêm (VD: 22:00-02:00)
                    if (!now.isBefore(start) || now.isBefore(end)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (debug) {
                DebugLogger.log(plugin.getLogger(), "ConfigManager: error checking fluctuation hours: %s", e.getMessage());
            }
        }
        return false;
    }

    private ZoneId parseZoneId(String zoneStr) {
        if (zoneStr == null || zoneStr.isBlank()) return ZoneId.of("Asia/Ho_Chi_Minh");
        String normalized = zoneStr.trim();
        if (normalized.equalsIgnoreCase("Vietnam") || normalized.equalsIgnoreCase("VN")) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
        try {
            return ZoneId.of(normalized);
        } catch (Exception e) {
            try {
                return ZoneId.of("GMT" + (normalized.startsWith("+") || normalized.startsWith("-") ? normalized : "+" + normalized));
            } catch (Exception ex) {
                return ZoneId.systemDefault();
            }
        }
    }

    public int getEffectiveBaseAmount() {
        if (isInFluctuationActiveHours()) {
            return fluctuationBaseAmount;
        }
        return baseAmount;
    }

    public int getEffectivePercentRate() {
        if (isInFluctuationActiveHours()) {
            return fluctuationPercentRate;
        }
        return percentRate;
    }

    // ---- Time Parsers (Milliseconds / Ticks) ----

    public long getJoinQuitIntervalTicks() {
        String raw = plugin.getConfig().getString("Fakeplayer-setting.join-quit-interval", "1-5");
        return Range.parse(raw, 1.0, 5.0).getRandomTicks();
    }

    public long getLifetimeIntervalMillis() {
        return Range.parse(lifetimeInterval, 1800.0, 3600.0).getRandomMillis();
    }

    public int getProxyUpdateInterval() {
        String value = plugin.getConfig().getString("Database.bridging-setting.update-interval", "2-3");
        return (int) Range.parse(value, 2.0, 3.0).getRandomTicks();
    }

    // ---- Getters ----

    public boolean isDebug() { return debug; }
    public MessageManager getMessages() { return messageManager; }
    public String getBotWorldName() { return botWorldName; }
    public boolean isJoinLeaveMessageEnable() { return joinLeaveMessageEnable; }
    public String getJoinMessage() { return joinMessage; }
    public String getLeaveMessage() { return leaveMessage; }
    public String getFakePluginName() { return fakePluginName; }
    public boolean isHideInTab() { return hideInTab; }
    public boolean isFakePlayerJoinCommandsEnabled() { return fakePlayerJoinCommandsEnabled; }
    public List<String> getFakePlayerJoinCommands() { return fakePlayerJoinCommands; }
    public boolean isConsoleJoinCommandsEnabled() { return consoleJoinCommandsEnabled; }
    public List<String> getConsoleJoinCommands() { return consoleJoinCommands; }
    public String getLifetimeInterval() { return lifetimeInterval; }
    public int getBaseAmount() { return baseAmount; }
    public int getPercentRate() { return percentRate; }
    public ChatConfig getChatConfig() { return chatConfig; }
    public String getChatFormat() { return messageFormatEnable ? messageChatFormat : chatFormat; }
    public boolean isMessageFormatEnable() { return messageFormatEnable; }
    public Boolean isProxyEnable() { return proxyEnable; }
    public String getBungeeName() { return bungeeName; }
    public String getRawDatabaseName() { return rawDatabaseName; }
    public boolean isDatabaseEnabled() { return databaseEnabled; }
}