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
    // BỔ SUNG TẠI ĐÂY: Trường lưu trữ định dạng Tablist cho bot
    private String tabFormat;
    private Boolean proxyEnable;
    private String bungeeName;
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
            this.chatFormat = config.getString("chat-system.chat-format", "<%fakeplayer_name%> %fakeplayer_message%");
            // BỔ SUNG TẠI ĐÂY: Đọc chuỗi cấu hình tab-format từ đường dẫn chỉ định trong config.yml
            this.tabFormat = config.getString("chat-system.tab-format", "%fake_vault_prefix%&f%fakeplayer_name%");
            this.proxyEnable = config.getBoolean("Database.bridging-setting.enable-proxy", false);
            this.bungeeName = config.getString("Database.bridging-setting.bungee_name", "fozminesproof");
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

    // --- GETTERS (Read-Only Data Transfer Objects) ---
    public MessageManager getMessages() { return this.messageManager; }
    public String getBotWorldName() { return this.botWorldName; }
    public boolean isJoinLeaveMessageEnable() { return this.joinLeaveMessageEnable; }
    public String getJoinMessage() { return this.joinMessage; }
    public String getLeaveMessage() { return this.leaveMessage; }
    public ChatConfig getChatConfig() { return this.chatConfig; }
    public String getChatFormat() { return this.chatFormat; }
    // BỔ SUNG TẠI ĐÂY: Hàm getter công khai để lấy chuỗi định dạng Tablist
    public String getTabFormat() { return this.tabFormat; }
    public Boolean isProxyEnable() { return  this.proxyEnable; }
    public String getBungeeName() { return this.bungeeName; }

    public String getRawDatabaseName() {
        return plugin.getConfig().getString("Database.name", "lobby");
    }
}