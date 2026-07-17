package org.phantam.fozminesproofcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofcore.chat.BotChatProcessor;
import org.phantam.fozminesproofcore.chat.BotSelector;
import org.phantam.fozminesproofcore.chat.MessageLoader;
import org.phantam.fozminesproofcore.config.ChatConfig;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChatTickerTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final ChatConfig chatConfig;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final MessageLoader messageLoader;

    private int ticksUntilNextChat = 0;

    public ChatTickerTask(JavaPlugin plugin, ChatConfig chatConfig, BotSelector botSelector,
                          BotChatProcessor chatProcessor, MessageLoader messageLoader) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
        this.messageLoader = messageLoader;

        resetCountdown();
    }

    @Override
    public void run() {
        if (!chatConfig.isEnabled()) {
            this.cancel();
            return;
        }

        ticksUntilNextChat -= 20; // Giảm 1 giây (20 Ticks) sau mỗi chu kỳ Task chạy
        if (ticksUntilNextChat <= 0) {
            executeChatCycle();
            resetCountdown(); // Đặt lại bộ đếm ngẫu nhiên chuẩn xác cho chu kỳ kế tiếp
        }
    }

    private void resetCountdown() {
        // SỬA TẠI ĐÂY: Lấy ngẫu nhiên số phút, đổi sang giây.
        // Nếu bốc trúng 0 phút, ép dải ngẫu nhiên từ 10 - 59 giây để tránh cooldown bằng 0 gây spam.
        int minutes = chatConfig.getRandomIntervalMinutes();
        int totalSeconds;

        if (minutes == 0) {
            totalSeconds = ThreadLocalRandom.current().nextInt(10, 60); // Ngẫu nhiên từ 10 đến 59 giây
        } else {
            totalSeconds = minutes * 60;
        }

        this.ticksUntilNextChat = totalSeconds * 20;
    }

    private void executeChatCycle() {
        List<Player> speakingBots = botSelector.selectRandomBots(chatConfig.getRandomBotsPerInterval());
        if (speakingBots.isEmpty()) {
            plugin.getLogger().warning("[ChatSystem] Không có bot nào online để thực hiện chat.");
            return;
        }

        // SỬA TẠI ĐÂY: Khóa một khoảng thời gian delay cố định cho chu kỳ này (Ví dụ: bốc trúng số 5)
        // Việc này đảm bảo bot 1 delay 0s, bot 2 delay 5s, bot 3 delay 10s... Đúng chuẩn tịnh tiến.
        int fixedDelaySeconds = chatConfig.getRandomDelaySeconds();
        long currentStaggerDelaySeconds = 0;

        for (Player bot : speakingBots) {
            String rawEnglishMessage = messageLoader.getRandomMessage();
            if (rawEnglishMessage == null) continue;

            long delayTicks = currentStaggerDelaySeconds * 20L;

            // Lập lịch so le an toàn trên luồng chính
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                chatProcessor.processChatAsync(bot, rawEnglishMessage, chatConfig);
            }, delayTicks);

            // Cộng dồn tịnh tiến theo bước nhảy cố định đã bốc ở trên
            currentStaggerDelaySeconds += fixedDelaySeconds;
        }
    }

    public int getTicksUntilNextChat() {
        return ticksUntilNextChat;
    }
}