package org.phantam.fozminesproofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private List<String> targetPlaceholders;
    private String botWorldName;
    private boolean joinLeaveMessageEnable;
    private String joinMessage;
    private String leaveMessage;
    private ChatConfig chatConfig;
    private String chatFormat;
    private String tabFormat;
    private Boolean proxyEnable;
    private String bungeeName;
    private boolean databaseEnabled;
    private String rawDatabaseName;

    // Thêm biến cho message-format
    private boolean messageFormatEnable;
    private String messageChatFormat;
    private String messageTabFormat;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = new MessageManager(plugin);
        this.reloadAllConfigs();
    }

    public void reloadAllConfigs() {
        try {
            plugin.reloadConfig();
            FileConfiguration config = plugin.getConfig();

            this.messageManager.reload();

            this.targetPlaceholders = config.getStringList("parse-papi");
            this.botWorldName = config.getString("Fakeplayer-setting.botworld", "botworld");
            this.joinLeaveMessageEnable = config.getBoolean("Fakeplayer-setting.join-leave-message-enable", true);
            this.joinMessage = config.getString("Fakeplayer-setting.join-message", "%fakeplayer_name% join the game");
            this.leaveMessage = config.getString("Fakeplayer-setting.leave-message", "%fakeplayer_name% left the game");

            this.messageFormatEnable = config.getBoolean("chat-system.message-format.enable", false);
            this.messageChatFormat = config.getString("chat-system.message-format.chat-format", "&7[&a%fakeplayer_name%&7]&f: %fakeplayer_message%");
            this.messageTabFormat = config.getString("chat-system.message-format.tab-format", "%fake_vault_prefix%&f%fakeplayer_name%");

            this.chatFormat = config.getString("chat-system.chat-format", "<%fakeplayer_name%> %fakeplayer_message%");
            this.tabFormat = config.getString("chat-system.tab-format", "%fake_vault_prefix%&f%fakeplayer_name%");

            this.proxyEnable = config.getBoolean("Database.bridging-setting.enable-proxy", false);
            this.bungeeName = config.getString("Database.bridging-setting.bungee_name", "fozminesproof");
            this.chatConfig = new ChatConfig(config);

            this.databaseEnabled = config.getBoolean("Database.enable", true);
            this.rawDatabaseName = config.getString("Database.name", "fozminesproof");

        } catch (Exception e) {
            plugin.getLogger().severe("🚨 Lỗi khi nạp dữ liệu từ file config.yml: " + e.getMessage());
        }
    }

    public boolean isTargetPlaceholder(String placeholder) {
        return this.targetPlaceholders != null && this.targetPlaceholders.contains(placeholder);
    }

    public int getJoinQuitIntervalTicks() {
        String value = plugin.getConfig().getString("Fakeplayer-setting.join-quit-interval", "0-1");
        if (value == null) return 20;

        try {
            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
        } catch (Exception e) {
            return 20;
        }
    }

    public int getProxyUpdateInterval() {
        String value = plugin.getConfig().getString("Database.bridging-setting.update-interval", "2-3");
        if (value == null) return 20;

        try {
            String[] parts = value.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        } catch (Exception e) {
            return 20;
        }
    }

    // --- GETTERS ---
    public MessageManager getMessages() { return this.messageManager; }
    public String getBotWorldName() { return this.botWorldName; }
    public boolean isJoinLeaveMessageEnable() { return this.joinLeaveMessageEnable; }
    public String getJoinMessage() { return this.joinMessage; }
    public String getLeaveMessage() { return this.leaveMessage; }
    public ChatConfig getChatConfig() { return this.chatConfig; }
    public String getChatFormat() {
        return this.messageFormatEnable ? this.messageChatFormat : this.chatFormat;
    }
    public String getTabFormat() {
        return this.messageFormatEnable ? this.messageTabFormat : this.tabFormat;
    }
    public boolean isMessageFormatEnable() { return this.messageFormatEnable; }
    public Boolean isProxyEnable() { return this.proxyEnable; }
    public String getBungeeName() { return this.bungeeName; }
    public String getRawDatabaseName() { return this.rawDatabaseName; }
    public boolean isDatabaseEnabled() { return this.databaseEnabled; }
}