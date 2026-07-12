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

    /**
     * Lấy tên bảng SQL động từ cấu hình Database.name công khai
     * Loại bỏ các ký tự đặc biệt để phòng tránh lỗ hổng SQL Injection cho tên bảng
     */
    public String getTableName() {
        FileConfiguration config = plugin.getConfig();
        String name = config.getString("Database.name", "lobby");
        return name.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Lấy thông tin chứng thực kết nối cơ sở dữ liệu MySQL
     */
    public DatabaseCredentials getDatabaseCredentials() {
        FileConfiguration config = plugin.getConfig();
        return new DatabaseCredentials(
                config.getString("Database.host"),
                config.getInt("Database.port"),
                config.getString("Database.database"),
                config.getString("Database.user"),
                config.getString("Database.password")
        );
    }

    public static record DatabaseCredentials(String host, int port, String database, String user, String password) {}
}
