package org.phantam.fozminesproofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private String prefix = "";

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "messages.yml");
        this.reload(); // Tự động nạp dữ liệu khi khởi tạo
    }

    /**
     * Tải hoặc làm mới dữ liệu từ tệp messages.yml (Phục vụ cho lệnh /reload)
     */
    public void reload() {
        if (!configFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Trích xuất và dịch màu trước cho Prefix hệ thống để tối ưu hiệu năng
        String rawPrefix = config.getString("system.prefix", "[FozmineSproof] ");
        this.prefix = ColorUtils.colorize(rawPrefix);
    }

    /**
     * Kiểu 1: Lấy tin nhắn ĐÃ BAO GỒM PREFIX và dịch màu hoàn chỉnh
     */
    public String getMessage(String path) {
        String rawMsg = config.getString(path);
        if (rawMsg == null) {
            return prefix + "§cMissing message path: " + path;
        }
        return prefix + ColorUtils.colorize(rawMsg);
    }

    /**
     * Kiểu 2: Chỉ lấy tin nhắn thô (ONLY MESSAGE) không kèm prefix, dùng khi chat trực tiếp từ Bot
     */
    public String getOnlyMessage(String path) {
        String rawMsg = config.getString(path);
        if (rawMsg == null) {
            return "§cMissing message path: " + path;
        }
        return ColorUtils.colorize(rawMsg);
    }

    /**
     * Kiểu 3: Lấy một danh sách các dòng văn bản (String List) phục vụ cho lệnh /help hoặc /info
     */
    public List<String> getMessageList(String path) {
        List<String> rawLines = config.getStringList(path);
        if (rawLines.isEmpty()) {
            return Collections.singletonList("§cMissing message list path: " + path);
        }

        List<String> coloredLines = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            coloredLines.add(ColorUtils.colorize(line));
        }
        return coloredLines;
    }
}
