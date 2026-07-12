package org.phantam.fozminesproofCore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofApi.FozminesproofApi;
import org.phantam.fozminesproofApi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofCore.commands.CommandManager;
import org.phantam.fozminesproofCore.config.ConfigManager;
import org.phantam.fozminesproofCore.database.DatabaseManager;
import org.phantam.fozminesproofCore.database.FakePlayerManager;
import org.phantam.fozminesproofCore.papi.FakePlayerPapiExpansion;

public class FozmineSproofCore extends JavaPlugin {

    private ConfigManager configManager;
    private FozminesproofApi bridge;
    private IFakePlayerDatabase database;
    private FakePlayerManager fakePlayerManager;

    @Override
    public void onEnable() {
        // 1. Cấu hình
        this.saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // 2. NMS Bridge
        if (!this.setupBridge()) {
            this.disablePluginDueToError("Không tìm thấy Module NMS tương thích với phiên bản Server hiện tại!");
            return;
        }

        // 3. Cơ sở dữ liệu (Đã sửa lỗi khởi tạo trùng và truyền bảng động an toàn)
        this.setupDatabase();

        // 4. Mở rộng & Điều khiển
        this.registerPlaceholderAPI();
        this.registerCommands();

        this.getLogger().info("FozmineSproofCore đã khởi chạy hoàn tất và sẵn sàng!");
    }

    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.close();
        }
    }

    /**
     * Khởi tạo tầng kết nối cơ sở dữ liệu động và RAM cache quản lý thực thể
     */
    private void setupDatabase() {
        this.database = new DatabaseManager(configManager.getDatabaseCredentials(), configManager.getTableName());
        this.database.setup();

        this.fakePlayerManager = new FakePlayerManager(this, this.database);
        this.fakePlayerManager.reloadSystem();
    }

    /**
     * Tự động quét và nạp động lớp NMS tương thích thông qua kỹ thuật Reflection
     */
    private boolean setupBridge() {
        String rawVersion = this.getServer().getMinecraftVersion();
        String targetVersionKey = this.resolveVersionKey(rawVersion);
        String className = "org.phantam.fozminesproofV" + targetVersionKey + ".NMSBridge_v" + targetVersionKey;

        try {
            Class<?> clazz = Class.forName(className);
            this.bridge = (FozminesproofApi) clazz.getConstructor().newInstance();
            return this.bridge != null;
        } catch (ClassNotFoundException e) {
            this.getLogger().severe("Không tìm thấy lớp xử lý hệ thống cho phiên bản: " + rawVersion);
            this.getLogger().severe("Đường dẫn tìm kiếm thất bại: " + className);
        } catch (Exception e) {
            this.getLogger().severe("Lỗi nghiêm trọng khi khởi tạo module NMS Bridge qua kỹ thuật Reflection!");
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Định hướng và gom cụm các phiên bản Minecraft về nhánh Module tương thích ổn định nhất
     */
    private String resolveVersionKey(String rawVersion) {
        if (rawVersion.startsWith("1.19")) return "1_19_4";
        if (rawVersion.startsWith("1.20")) return "1_20_2";
        return rawVersion.replace('.', '_');
    }

    /**
     * Đăng ký tính năng đếm Fake Player mở rộng cho PlaceholderAPI
     */
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;

        new FakePlayerPapiExpansion(this, this.configManager, this.bridge).register();
        this.getLogger().info("Đã đồng bộ hóa thành công hệ thống tiện ích mở rộng với PlaceholderAPI!");
    }

    /**
     * Đăng ký bộ xử lý điều phối lệnh chính cho Plugin
     */
    private void registerCommands() {
        if (this.getCommand("sproof") != null) {
            CommandManager commandManager = new CommandManager(this);
            this.getCommand("sproof").setExecutor(commandManager);
            this.getCommand("sproof").setTabCompleter(commandManager); // Tự động hóa bộ gợi ý phím Tab
        }
    }

    /**
     * Tắt plugin an toàn khi xảy ra lỗi khởi tạo nghiêm trọng
     */
    private void disablePluginDueToError(String reason) {
        this.getLogger().severe(reason);
        this.getLogger().severe("Hệ thống tự động ngắt hoạt động plugin để bảo vệ an toàn dữ liệu.");
        this.getServer().getPluginManager().disablePlugin(this);
    }

    // --- GETTERS (OOP Encapsulation) ---

    public FozminesproofApi getBridge() { return this.bridge; }
    public ConfigManager getConfigManager() { return this.configManager; }
    public IFakePlayerDatabase getFakePlayerDatabase() { return this.database; }
    public FakePlayerManager getFakePlayerManager() { return this.fakePlayerManager; }
}
