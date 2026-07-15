package org.phantam.fozminesproofcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.chat.ChatScheduler;
import org.phantam.fozminesproofcore.chat.MessageLoader;
import org.phantam.fozminesproofcore.commands.CommandManager;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.DatabaseCredentialFactory;
import org.phantam.fozminesproofcore.database.DatabaseManager;
import org.phantam.fozminesproofcore.database.FakePlayerManager;
import org.phantam.fozminesproofcore.factory.VoidWorldFactory;
import org.phantam.fozminesproofcore.papi.FakePlayerPapiExpansion;
import org.phantam.fozminesproofcore.tasks.KeepAliveTask;
import org.phantam.fozminesproofcore.utils.NMSBridgeLoader;

public class FozmineSproofCore extends JavaPlugin {

    private ConfigManager configManager;
    private FozminesproofApi bridge;
    private IFakePlayerDatabase database;
    private FakePlayerManager fakePlayerManager;
    private MessageLoader messageLoader;
    private ChatScheduler chatScheduler;
    private VoidWorldFactory voidWorldFactory;

    @Override
    public void onEnable() {
        try {
            // 1. Khởi tạo dữ liệu cấu hình thô
            this.saveDefaultConfig();
            this.configManager = new ConfigManager(this);

            // 2. Nạp mô-đun NMS
            this.bridge = NMSBridgeLoader.loadBridge(this.getLogger());
            if (this.bridge == null) {
                this.disablePluginDueToError("Không nạp được Module NMS!");
                return;
            }

            // 3. Khởi tạo Database dựa trên Factory bóc tách
            String tableName = DatabaseCredentialFactory.getSafeTableName(configManager.getRawDatabaseName());
            DatabaseCredentialFactory.DatabaseCredentials credentials = DatabaseCredentialFactory.createCredentials(this);

            // Truyền dữ liệu vào DatabaseManager
            this.database = new DatabaseManager(credentials, tableName);
            this.database.setup();

            this.fakePlayerManager = new FakePlayerManager(this, this.database);
            this.fakePlayerManager.reloadSystem();

            // 4. Gọi Factory dựng thế giới trống (Thay vì để ConfigManager tự làm như trước)
            VoidWorldFactory.createVoidWorld(this, configManager.getBotWorldName());

            // 5. Khởi chạy Chat và các phân hệ khác...
            this.setupChatSystem();
            this.registerExternalExtensions();
            new KeepAliveTask(this).runTaskTimer(this, 100L, 200L);

        } catch (Exception e) {
            e.printStackTrace();
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }


    @Override
    public void onDisable() {
        // Hủy vòng lặp chat ngầm một cách an toàn tránh rò rỉ bộ nhớ (Memory Leak)
        if (this.chatScheduler != null) {
            try {
                this.chatScheduler.stop();
            } catch (Exception e) {
                this.getLogger().warning("Không thể dừng ChatScheduler một cách sạch sẽ: " + e.getMessage());
            }
        }

        // Đóng kết nối Database an toàn
        if (this.database != null) {
            try {
                this.database.close();
            } catch (Exception e) {
                this.getLogger().warning("Lỗi xảy ra khi đóng kết nối cơ sở dữ liệu: " + e.getMessage());
            }
        }
        this.getLogger().info("❌ FozmineSproofCore đã ngừng hoạt động an toàn!");
    }

    /**
     * Khởi tạo khung sườn và nạp các tệp tin tin nhắn định kỳ của Bot
     */
    private void setupChatSystem() {
        this.messageLoader = new MessageLoader(this);
        this.messageLoader.loadMessages();

        this.chatScheduler = new ChatScheduler(this, this.fakePlayerManager, this.messageLoader);
        this.chatScheduler.start(this.configManager.getChatConfig());
    }

    /**
     * Gom cụm đăng ký lệnh và tích hợp với các Plugin API bên thứ ba công khai
     */
    private void registerExternalExtensions() {
        // Đăng ký hệ thống xử lý tập lệnh Lệnh
        if (this.getCommand("sproof") != null) {
            CommandManager commandManager = new CommandManager(this);
            this.getCommand("sproof").setExecutor(commandManager);
            this.getCommand("sproof").setTabCompleter(commandManager);
        }

        // Tích hợp hệ thống biến mở rộng với PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // CẬP NHẬT: Truyền trực tiếp fakePlayerManager thay vì truyền bridge cũ
            new FakePlayerPapiExpansion(this, this.configManager, this.fakePlayerManager).register();
            this.getLogger().info("🔗 Đã liên kết và đồng bộ hóa thành công với PlaceholderAPI!");
        }
    }

    /**
     * Hàm ngắt hoạt động Plugin khẩn cấp khi gặp lỗi logic chí mạng để bảo vệ dữ liệu SQL
     */
    private void disablePluginDueToError(String reason) {
        this.getLogger().severe("❌ " + reason);
        this.getLogger().severe("🛡 Hệ thống tự động ngắt hoạt động plugin để bảo vệ an toàn dữ liệu.");
        this.getServer().getPluginManager().disablePlugin(this);
    }

    // --- GETTERS (OOP Encapsulation & Read-Only Access) ---
    public FozminesproofApi getBridge() { return this.bridge; }
    public ConfigManager getConfigManager() { return this.configManager; }
    public IFakePlayerDatabase getFakePlayerDatabase() { return this.database; }
    public FakePlayerManager getFakePlayerManager() { return this.fakePlayerManager; }
    public MessageLoader getMessageLoader() { return this.messageLoader; }
    public ChatScheduler getChatScheduler() { return this.chatScheduler; }
}
