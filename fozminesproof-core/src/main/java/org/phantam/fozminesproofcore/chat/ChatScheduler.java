package org.phantam.fozminesproofcore.chat;

import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.FakePlayerManager;
import org.phantam.fozminesproofcore.tasks.ChatTickerTask;

public class ChatScheduler {
    private final JavaPlugin plugin;
    private final MessageLoader messageLoader;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final ConfigManager configManager;

    private ChatConfig chatConfig;
    private ChatTickerTask tickerTask;

    public ChatScheduler(FozmineSproofCore plugin, FakePlayerManager fakePlayerManager, MessageLoader messageLoader, ConfigManager configManager) {
        this.plugin = plugin;
        this.messageLoader = messageLoader;
        this.configManager = configManager;

        // Khởi tạo các phân hệ con (Dependency Injection cục bộ) - Khớp kiểu dữ liệu 100%
        this.botSelector = new BotSelector(fakePlayerManager);
        this.chatProcessor = new BotChatProcessor(plugin, fakePlayerManager, configManager);
    }

    /**
     * Kích hoạt hệ thống lập lịch Chat tự động cho Bot
     */
    public void start(ChatConfig config) {
        this.chatConfig = config;
        this.stop(); // Bảo vệ hệ thống: Hủy task cũ nếu đang chạy trước khi nạp cấu hình mới

        if (!chatConfig.isEnabled()) {
            plugin.getLogger().warning("[ChatSystem] Hệ thống chat đang bị TẮT trong config.yml!");
            return;
        }

        // Khởi tạo thực thể Task đếm ngược chuyên biệt
        this.tickerTask = new ChatTickerTask(plugin, chatConfig, botSelector, chatProcessor, messageLoader);

        plugin.getLogger().info("[ChatSystem] Hệ thống chat đã kích hoạt! Chu kỳ đầu tiên sau: "
                + (tickerTask.getTicksUntilNextChat() / 20) + " giây.");

        // Chạy lặp lại định kỳ mỗi giây (20 Ticks) trên Luồng chính
        this.tickerTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Dừng hệ thống Chat an toàn giải phóng tài nguyên máy chủ
     */
    public void stop() {
        if (this.tickerTask != null) {
            this.tickerTask.cancel();
            this.tickerTask = null;
        }
    }
}
