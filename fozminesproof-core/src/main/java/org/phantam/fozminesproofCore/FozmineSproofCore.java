package org.phantam.fozminesproofCore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
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
        // 1. Khởi tạo tệp cấu hình
        this.saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // 2. Nạp mô-đun NMS tương ứng qua kỹ thuật Reflection
        if (!this.setupBridge()) {
            this.disablePluginDueToError("Không tìm thấy Module NMS tương thích với phiên bản Server hiện tại!");
            return;
        }

        // 3. Kết nối Cơ sở dữ liệu và RAM Cache quản lý
        this.setupDatabase();

        // 4. Khởi tạo thế giới dạng Void dựa trên dữ liệu Config an toàn
        this.configManager.createVoidWorld();

        // 5. Đồng bộ nạp lại trạng thái thực tế của các bot từ Database
        if (this.fakePlayerManager != null) {
            this.fakePlayerManager.reloadSystem();
        }

        // 6. Đăng ký Tiện ích và Điều phối Lệnh
        this.registerPlaceholderAPI();
        this.registerCommands();

        // 7. KHỞI CHẠY HỆ THỐNG RUNNABLE DUY TRÌ HIỂN THỊ VÀ TABLIST VĨNH VIỄN
        this.startKeepAliveTask();

        this.getLogger().info("FozmineSproofCore đã khởi chạy hoàn tất và sẵn sàng!");
    }

    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.close();
        }
    }

    /**
     * Khởi tạo hệ thống định kỳ gửi gói tin làm mới hiển thị Bot và Tablist.
     * Tần suất 30 Ticks (1.5 giây) giúp khắc phục triệt để lỗi Bot biến mất khi người chơi Re-log/Teleport.
     */
    private void startKeepAliveTask() {
        if (this.bridge == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                // Tự động ngắt Task ngầm nếu plugin bị ép dừng đột ngột hoặc Reload nửa chừng
                if (getBridge() == null) {
                    this.cancel();
                    return;
                }
                // Gọi API Bridge đẩy Packet duy trì trạng thái xuống Pipeline mạng
                getBridge().sendKeepAlivePackets();
            }
        }.runTaskTimer(this, 20L, 30L); // Delay 1 giây đầu sau khi bật máy chủ, lặp lại sau mỗi 1.5 giây
    }

    private void setupDatabase() {
        this.database = new DatabaseManager(configManager.getDatabaseCredentials(), configManager.getTableName());
        this.database.setup();

        this.fakePlayerManager = new FakePlayerManager(this, this.database);
    }

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

    private String resolveVersionKey(String rawVersion) {
        if (rawVersion.startsWith("1.19")) return "1_19_4";
        if (rawVersion.startsWith("1.20")) return "1_20_2";
        return rawVersion.replace('.', '_');
    }

    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;

        new FakePlayerPapiExpansion(this, this.configManager, this.bridge).register();
        this.getLogger().info("Đã đồng bộ hóa thành công hệ thống tiện ích mở rộng với PlaceholderAPI!");
    }

    private void registerCommands() {
        if (this.getCommand("sproof") != null) {
            CommandManager commandManager = new CommandManager(this);
            this.getCommand("sproof").setExecutor(commandManager);
            this.getCommand("sproof").setTabCompleter(commandManager);
        }
    }

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
