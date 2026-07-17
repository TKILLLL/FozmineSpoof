package org.phantam.fozminesproofcore.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.FakePlayerManager;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BotChatProcessor {
    private final FozmineSproofCore plugin;
    private final FakePlayerManager fakePlayerManager;
    private final TranslatorService translatorService;
    private final ConfigManager configManager;

    public BotChatProcessor(FozmineSproofCore plugin, FakePlayerManager fakePlayerManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.fakePlayerManager = fakePlayerManager;
        this.configManager = configManager;
        this.translatorService = new TranslatorService();
    }

    public void processChatAsync(Player bot, String rawMessage, ChatConfig chatConfig) {
        if (bot == null || rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        String botName = bot.getName();

        if (!fakePlayerManager.isBotOnline(botName)) {
            return;
        }

        // TÍNH NĂNG MỚI: Xử lý thay thế [name] thành một người chơi hoặc bot bất kỳ đang trực tuyến
        String processedMessage = rawMessage;
        if (processedMessage.contains("[name]")) {
            // Gom toàn bộ tên người chơi thực tế và tên của các Bot đang online vào một danh sách chung
            List<String> poolNames = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null) poolNames.add(p.getName());
            }

            // Vòng lặp thay thế liên tục từng thẻ [name] một để hỗ trợ tin nhắn chứa nhiều thẻ tên khác nhau
            while (processedMessage.contains("[name]")) {
                if (poolNames.isEmpty()) {
                    processedMessage = processedMessage.replaceFirst("\\[name\\]", "");
                } else {
                    int randomIndex = ThreadLocalRandom.current().nextInt(poolNames.size());
                    String selectedName = poolNames.get(randomIndex);
                    processedMessage = processedMessage.replaceFirst("\\[name\\]", selectedName);
                }
            }
        }

        final String finalRawMessage = processedMessage;

        // TÁC VỤ CHẠY TRÊN LUỒNG BẤT ĐỒNG BỘ (ASYNC THREAD) - AN TOÀN CHO I/O VÀ DATABASE LUCKPERMS
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!fakePlayerManager.isBotOnline(botName)) {
                    return;
                }

                String targetLang = (chatConfig != null && chatConfig.getTranslationTarget() != null)
                        ? chatConfig.getTranslationTarget() : "vi";

                String finalMessage = translatorService.translate(finalRawMessage, targetLang);
                if (finalMessage == null || finalMessage.trim().isEmpty()) {
                    return;
                }

                // 1. Lấy khung định dạng chat từ file config.yml
                String rawFormat = configManager.getChatFormat();
                boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

                // 2. Thay thế các từ khóa cơ bản trước
                String formattedMessage = rawFormat
                        .replace("%fakeplayer_name%", bot.getName())
                        .replace("%fakeplayer_message%", finalMessage);

                // SỬA TẠI ĐÂY (TỐI ƯU LUỒNG): Tiến hành dịch biến %fake_...% ngay trên luồng Async này.
                // LuckPerms có thể thoải mái đọc Database/File để lấy thông tin Offline Player của Bot mà không bị chặn lỗi ServerThreadLookupException.
                if (hasPapi) {
                    formattedMessage = PlaceholderAPI.setPlaceholders(bot, formattedMessage);
                }

                // Chuyển kết quả chuỗi cuối cùng đã được bóc tách toàn vẹn về luồng chính để vẽ Component và phát Packet mạng
                final String finalFormattedChat = formattedMessage;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (fakePlayerManager.isBotOnline(botName) && plugin.getBridge() != null) {

                        // Chuyển đổi màu sắc (& và HEX) sang định dạng component
                        String colorizedMessage = ColorUtils.colorize(finalFormattedChat);

                        // Bắn packet NMS sạch ra toàn server
                        plugin.getBridge().broadcastNMSChat(bot, colorizedMessage);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("⚠ Lỗi xảy ra trong tiến trình xử lý chat bất đồng bộ của Bot " + botName + ": " + e.getMessage());
            }
        });
    }
}