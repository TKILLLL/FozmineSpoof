package org.phantam.fozminesproofCore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class ConfigManager {

    private final JavaPlugin plugin;
    private List<String> targetPlaceholders;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    /**
     * Nạp hoặc làm mới dữ liệu từ file config.yml
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Đọc danh sách các biến PAPI cần đánh lừa
        this.targetPlaceholders = config.getStringList("parse-papi");
    }

    /**
     * Kiểm tra xem một biến PAPI có nằm trong danh sách cần đánh lừa không
     *
     * @param placeholder Tên biến PAPI truyền vào (không kèm dấu %)
     * @return true nếu biến cần được xử lý
     */
    public boolean isTargetPlaceholder(String placeholder) {
        return this.targetPlaceholders != null && this.targetPlaceholders.contains(placeholder);
    }
}
