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

        ticksUntilNextChat -= 20; // Trừ đi 1 giây (20 Ticks) mỗi lần Task này chạy
        if (ticksUntilNextChat <= 0) {
            executeChatCycle();
            resetCountdown(); // Đặt lại bộ đếm ngẫu nhiên cho chu kỳ kế tiếp
        }
    }

    private void resetCountdown() {
        this.ticksUntilNextChat = chatConfig.getRandomIntervalMinutes() * 60 * 20;
    }

    private void executeChatCycle() {
        List<Player> speakingBots = botSelector.selectRandomBots(chatConfig.getRandomBotsPerInterval());
        if (speakingBots.isEmpty()) {
            plugin.getLogger().warning("[ChatSystem] Không có bot nào online để thực hiện chat.");
            return;
        }

        long currentStaggerDelaySeconds = 0;

        for (Player bot : speakingBots) {
            String rawEnglishMessage = messageLoader.getRandomMessage();
            if (rawEnglishMessage == null) continue;

            long delayTicks = currentStaggerDelaySeconds * 20L;

            // Lập lịch so le trên luồng chính để các bot không chat cùng một mili-giây
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                chatProcessor.processChatAsync(bot, rawEnglishMessage, chatConfig);
            }, delayTicks);

            currentStaggerDelaySeconds += chatConfig.getRandomDelaySeconds();
        }
    }

    public int getTicksUntilNextChat() {
        return ticksUntilNextChat;
    }
}
