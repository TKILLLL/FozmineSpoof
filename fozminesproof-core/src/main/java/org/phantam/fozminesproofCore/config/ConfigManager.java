package org.phantam.fozminesproofCore.config;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.Random;

public class ConfigManager {

    private final JavaPlugin plugin;
    private List<String> targetPlaceholders;

    // Các trường dữ liệu cấu hình mới cho FakePlayer
    private String botWorldName;
    private boolean joinLeaveMessageEnable;
    private String joinMessage;
    private String leaveMessage;

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

        // Đọc các giá trị cấu hình Fakeplayer-setting mới
        this.botWorldName = config.getString("Fakeplayer-setting.botworld", "botworld");
        this.joinLeaveMessageEnable = config.getBoolean("Fakeplayer-setting.join-leave-message-enable", true);
        this.joinMessage = config.getString("Fakeplayer-setting.join-message", "%fakeplayer_name% join the game");
        this.leaveMessage = config.getString("Fakeplayer-setting.leave-message", "%fakeplayer_name% left the game");

        // Luôn luôn kiểm tra và tự động tạo thế giới trống (Void World) ngay khi nạp cấu hình
        this.createVoidWorld();
    }

    /**
     * Kỹ thuật tạo thế giới trống rỗng (Void World) không sinh block tự nhiên,
     * ngăn chặn triệt để lỗi thiếu không gian khi spawn bot.
     */
    public void createVoidWorld() {
        if (this.botWorldName == null || this.botWorldName.isEmpty()) return;

        // Kiểm tra xem thế giới đã được máy chủ tải hoặc khởi tạo trước đó chưa
        if (Bukkit.getWorld(this.botWorldName) != null) return;

        plugin.getLogger().info("Đang kiểm tra và tự động thiết lập thế giới Void: " + this.botWorldName);

        WorldCreator creator = new WorldCreator(this.botWorldName);
        // Thiết lập bộ sinh chunk trống rỗng (Empty Chunk Generator)
        creator.generator(new ChunkGenerator() {
            @Override
            public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
                return createChunkData(world); // Trả về cấu trúc chunk trống không chứa block
            }
        });

        // Gọi Bukkit API tạo thế giới trực tiếp lúc runtime
        Bukkit.createWorld(creator);
        plugin.getLogger().info("Thế giới Void '" + this.botWorldName + "' đã sẵn sàng hoạt động!");
    }

    /**
     * Kiểm tra xem một biến PAPI có nằm trong danh sách cần đánh lừa không
     */
    public boolean isTargetPlaceholder(String placeholder) {
        return this.targetPlaceholders != null && this.targetPlaceholders.contains(placeholder);
    }

    /**
     * Lấy tên bảng SQL động từ cấu hình Database.name công khai
     */
    public String getTableName() {
        FileConfiguration config = plugin.getConfig();
        String name = config.getString("Database.name", "lobby");
        return name.replaceAll("[^a-zA-Z0-9_]", "");
    }

    // --- GETTERS CHO TÍNH NĂNG MỚI ---
    public String getBotWorldName() { return botWorldName; }
    public boolean isJoinLeaveMessageEnable() { return joinLeaveMessageEnable; }
    public String getJoinMessage() { return joinMessage; }
    public String getLeaveMessage() { return leaveMessage; }

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
