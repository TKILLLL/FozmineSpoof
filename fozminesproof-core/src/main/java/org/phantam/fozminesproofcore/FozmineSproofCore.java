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
import org.phantam.fozminesproofcore.tasks.ProxySyncTask;
import org.phantam.fozminesproofcore.utils.NMSBridgeLoader;
import org.phantam.fozminesproofcore.utils.ColorUtils; // Thêm import tiện ích mã màu
import me.clip.placeholderapi.PlaceholderAPI;

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

            // 4. Gọi Factory dựng thế giới trống
            VoidWorldFactory.createVoidWorld(this, configManager.getBotWorldName());

            // 5. Khởi chạy Chat và các phân hệ khác...
            this.setupChatSystem();
            this.registerExternalExtensions();
            new KeepAliveTask(this).runTaskTimer(this, 100L, 200L);

            // BỔ SUNG TẠI ĐÂY: Kích hoạt bộ lập lịch cập nhật Tablist cho bot tự động mỗi 5 giây (100 ticks)
            // Quá trình parse được bọc luồng Async, sau đó đẩy text sạch về luồng chính để gán an toàn cho máy chủ
            this.setupTabUpdateScheduler();

            // --- BỔ SUNG: KÍCH HOẠT HÀNG ĐỢI TỰ ĐỘNG PHỤC HỒI BOT CÓ DELAY THEO CONFIG ---
            if (this.fakePlayerManager != null) {
                this.fakePlayerManager.spawnAllOnStartup(this.configManager);
            }

            // --- BỔ SUNG: KÍCH HOẠT ĐỒNG BỘ PROXY VỚI INTERVAL NGẪU NHIÊN (NẾU BẬT) ---
            if (this.configManager.isProxyEnable()) {
                int initialDelaySeconds = this.configManager.getProxyUpdateInterval();
                new ProxySyncTask(this, this.database, this.configManager)
                        .runTaskLaterAsynchronously(this, initialDelaySeconds * 20L);
                this.getLogger().info("Da kich hoat tien trinh dong bo Proxy.");
            } else {
                this.getLogger().info("Tinh nang Proxy dang tat. Bo qua dong bo Proxy.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.chatScheduler != null) {
            try { this.chatScheduler.stop(); } catch (Exception ignored) {}
        }

        if (this.fakePlayerManager != null) {
            try {
                this.fakePlayerManager.despawnAllOnShutdown();

                if (this.database != null) {
                    try { this.database.close(); } catch (Exception ignored) {}
                }
                this.getLogger().info("❌ FozmineSproofCore đã ngừng hoạt động an toàn!");
            } catch (Exception e) {
                this.getLogger().severe("⚠ Lỗi xảy ra khi thu hồi bot lúc tắt máy chủ: " + e.getMessage());
            }
        }
    }

    private void setupChatSystem() {
        this.messageLoader = new MessageLoader(this);
        this.messageLoader.loadMessages();

        this.chatScheduler = new ChatScheduler(this, this.fakePlayerManager, this.messageLoader, this.configManager);
        this.chatScheduler.start(this.configManager.getChatConfig());
    }

    /**
     * Khởi tạo bộ đếm tự động đồng bộ Tablist bất đồng bộ định kỳ cho Bot
     */
    private void setupTabUpdateScheduler() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (fakePlayerManager == null || fakePlayerManager.getOnlineBotsData().isEmpty()) return;

                boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
                String rawTabFormat = configManager.getTabFormat();

                fakePlayerManager.getOnlineBotsData().forEach(botData -> {
                    String botName = botData.getName();
                    org.bukkit.entity.Player botEntity = fakePlayerManager.getOnlineBotEntity(botName);
                    if (botEntity == null) return;

                    // Bước A: Tính toán, gán tên và giải mã các biến %fake_...% trên luồng Async để giải phóng CPU luồng chính
                    String formattedTab = rawTabFormat.replace("%fakeplayer_name%", botName);
                    if (hasPapi) {
                        formattedTab = PlaceholderAPI.setPlaceholders(botEntity, formattedTab);
                    }

                    final String finalTabName = formattedTab;

                    // Bước B: Đồng bộ về Luồng chính (Main Thread) để ép máy chủ cập nhật giao diện hiển thị danh sách người chơi
                    Bukkit.getScheduler().runTask(FozmineSproofCore.this, () -> {
                        if (fakePlayerManager.isBotOnline(botName)) {
                            String coloredTabName = ColorUtils.colorize(finalTabName);
                            botEntity.setPlayerListName(coloredTabName);
                        }
                    });
                });
            }
        }.runTaskTimerAsynchronously(this, 100L, 100L); // Lặp định kỳ mỗi 5 giây
        this.getLogger().info("✅ Hệ thống tự động đồng bộ Tablist cho Fake Player đã kích hoạt!");
    }

    private void registerExternalExtensions() {
        if (this.getCommand("sproof") != null) {
            CommandManager commandManager = new CommandManager(this);
            this.getCommand("sproof").setExecutor(commandManager);
            this.getCommand("sproof").setTabCompleter(commandManager);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FakePlayerPapiExpansion(this, this.configManager, this.fakePlayerManager).register();
            this.getLogger().info("🔗 Đã liên kết và đồng bộ hóa thành công với PlaceholderAPI!");
        }
    }

    private void disablePluginDueToError(String reason) {
        this.getLogger().severe("❌ " + reason);
        this.getLogger().severe("🛡 Hệ thống tự động ngắt hoạt động plugin để bảo vệ an toàn dữ liệu.");
        this.getServer().getPluginManager().disablePlugin(this);
    }

    public FozminesproofApi getBridge() { return this.bridge; }
    public ConfigManager getConfigManager() { return this.configManager; }
    public IFakePlayerDatabase getFakePlayerDatabase() { return this.database; }
    public FakePlayerManager getFakePlayerManager() { return this.fakePlayerManager; }
    public MessageLoader getMessageLoader() { return this.messageLoader; }
    public ChatScheduler getChatScheduler() { return this.chatScheduler; }
}