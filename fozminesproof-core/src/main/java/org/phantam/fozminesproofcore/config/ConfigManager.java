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

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = new MessageManager(plugin);
        this.reloadAllConfigs();
    }

    /**
     * Chỉ làm nhiệm vụ đọc/nạp lại dữ liệu từ tệp tin ổ đĩa vào bộ nhớ RAM
     */
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

            this.chatConfig = new ChatConfig(config);

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

    // --- GETTERS (Read-Only Data Transfer Objects) ---
    public MessageManager getMessages() { return this.messageManager; }
    public String getBotWorldName() { return this.botWorldName; }
    public boolean isJoinLeaveMessageEnable() { return this.joinLeaveMessageEnable; }
    public String getJoinMessage() { return this.joinMessage; }
    public String getLeaveMessage() { return this.leaveMessage; }
    public ChatConfig getChatConfig() { return this.chatConfig; }

    public String getRawDatabaseName() {
        return plugin.getConfig().getString("Database.name", "lobby");
    }
}
