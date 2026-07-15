package org.phantam.fozminesproofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.database.FakePlayerManager;

public class BotChatProcessor {
    private final FozmineSproofCore plugin;
    private final FakePlayerManager fakePlayerManager;
    private final TranslatorService translatorService;

    // OOP DI: Truyền trực tiếp FozmineSproofCore thay vì JavaPlugin thô để loại bỏ ép kiểu lúc Runtime
    public BotChatProcessor(FozmineSproofCore plugin, FakePlayerManager fakePlayerManager) {
        this.plugin = plugin;
        this.fakePlayerManager = fakePlayerManager;
        this.translatorService = new TranslatorService();
    }

    /**
     * Thực hiện chuỗi xử lý dịch thuật Async và đẩy Packet Chat an toàn qua Bridge.
     * Hàm này bọc lỗi chặt chẽ, bảo vệ TPS máy chủ khỏi các tác vụ chặn luồng (Blocking I/O).
     */
    public void processChatAsync(Player bot, String rawMessage, ChatConfig chatConfig) {
        if (bot == null || rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        String botName = bot.getName();

        // 1. Kiểm tra phòng vệ nhanh điều kiện trực tuyến của Bot trên luồng chính trước khi khởi động tác vụ Async
        if (!fakePlayerManager.isBotOnline(botName)) {
            return;
        }

        // 2. Chuyển giao tác vụ sang luồng bất đồng bộ (Async Thread pool) để gọi API dịch của Google
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Kiểm tra lại trạng thái Bot một lần nữa khi luồng Async vừa chạy (Đề phòng Bot vừa bị despawn)
                if (!fakePlayerManager.isBotOnline(botName)) {
                    return;
                }

                // Xác định ngôn ngữ đích an toàn
                String targetLang = (chatConfig != null && chatConfig.getTranslationTarget() != null)
                        ? chatConfig.getTranslationTarget() : "vi";

                // Thực thi gọi mạng I/O chặn luồng để lấy chuỗi chữ dịch thuật
                String finalMessage = translatorService.translate(rawMessage, targetLang);
                if (finalMessage == null || finalMessage.trim().isEmpty()) {
                    return; // Bỏ qua nếu nội dung trả về trống
                }

                // 3. Đồng bộ quay trở lại luồng chính (Main Thread) để tương tác an toàn với API Bukkit và mạng Netty
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Kiểm tra tối hậu điều kiện Online của Bot trên luồng chính trước khi phát Packet
                    if (fakePlayerManager.isBotOnline(botName) && plugin.getBridge() != null) {
                        plugin.getBridge().broadcastNMSChat(bot, finalMessage);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("⚠ Lỗi xảy ra trong tiến trình xử lý chat bất đồng bộ của Bot " + botName + ": " + e.getMessage());
            }
        });
    }
}