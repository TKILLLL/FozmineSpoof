package org.phantam.fozminesproofCore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofApi.FozminesproofApi;
import org.phantam.fozminesproofCore.commands.CommandManager;
import org.phantam.fozminesproofCore.config.ConfigManager;
import org.phantam.fozminesproofCore.papi.FakePlayerPapiExpansion;

public class FozmineSproofCore extends JavaPlugin {

    private ConfigManager configManager;
    private FozminesproofApi bridge;

    @Override
    public void onEnable() {
        // 1. Khởi tạo cấu hình hệ thống
        this.saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // 2. Khởi tạo kết nối NMS (Bridge)
        if (!this.setupBridge()) {
            this.disablePluginDueToError("Không tìm thấy Module NMS tương thích với phiên bản Server hiện tại!");
            return;
        }
        this.getLogger().info("FozmineSproof đã kích hoạt Bridge NMS thành công!");

        // 3. Đăng ký Hook hệ thống mở rộng (PlaceholderAPI)
        this.registerPlaceholderAPI();

        // 4. Đăng ký Hệ thống Lệnh (Commands)
        this.registerCommands();
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
            this.getLogger().warning("Không tìm thấy lớp xử lý hệ thống cho phiên bản thực tế: " + rawVersion);
            this.getLogger().warning("Đường dẫn tìm kiếm thất bại: " + className);
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
        if (rawVersion.startsWith("1.19")) {
            return "1_19_4";
        }
        if (rawVersion.startsWith("1.20")) {
            return "1_20_2";
        }
        return rawVersion.replace('.', '_');
    }

    /**
     * Đăng ký tính năng đánh lừa PlaceholderAPI thông qua ConfigManager tập trung
     */
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new FakePlayerPapiExpansion(this, this.configManager, this.bridge).register();
        this.getLogger().info("Hệ thống tự động đếm Fake Player và đánh lừa PAPI đã sẵn sàng!");
    }

    /**
     * Đăng ký bộ xử lý lệnh chính cho Plugin
     */
    private void registerCommands() {
        if (this.getCommand("sproof") != null) {
            this.getCommand("sproof").setExecutor(new CommandManager(this));
        }
    }

    /**
     * Tắt plugin an toàn khi xảy ra lỗi khởi tạo nghiêm trọng
     */
    private void disablePluginDueToError(String reason) {
        this.getLogger().severe(reason);
        this.getLogger().severe("Plugin sẽ tự động tắt để tránh gây lỗi dữ liệu.");
        this.getServer().getPluginManager().disablePlugin(this);
    }

    // --- GETTERS (OOP Encapsulation) ---

    public FozminesproofApi getBridge() {
        return this.bridge;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }
}
