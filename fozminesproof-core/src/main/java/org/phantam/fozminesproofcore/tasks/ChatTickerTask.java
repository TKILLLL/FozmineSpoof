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
    private boolean isFirstRun = true;

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
            plugin.getLogger().warning("[ChatSystem] Chat bị tắt, hủy task.");
            this.cancel();
            return;
        }

        ticksUntilNextChat -= 20;

        if (isFirstRun) {
            plugin.getLogger().info("[ChatSystem] ChatTickerTask đã chạy lần đầu, còn " + (ticksUntilNextChat / 20) + " giây nữa.");
            isFirstRun = false;
        }

        if (ticksUntilNextChat <= 0) {
            plugin.getLogger().info("[ChatSystem] Bắt đầu chu kỳ chat mới!");
            executeChatCycle();
            resetCountdown();
            plugin.getLogger().info("[ChatSystem] Chu kỳ tiếp theo sau " + (ticksUntilNextChat / 20) + " giây.");
        }
    }

    private void resetCountdown() {
        int minutes = chatConfig.getRandomIntervalMinutes();
        int totalSeconds;

        if (minutes == 0) {
            totalSeconds = ThreadLocalRandom.current().nextInt(10, 60);
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

        plugin.getLogger().info("[ChatSystem] Chọn " + speakingBots.size() + " bot để chat.");

        int fixedDelaySeconds = chatConfig.getRandomDelaySeconds();
        long currentStaggerDelaySeconds = 0;

        for (Player bot : speakingBots) {
            String rawEnglishMessage = messageLoader.getRandomMessage();
            if (rawEnglishMessage == null) {
                plugin.getLogger().warning("[ChatSystem] Không tìm thấy tin nhắn cho bot " + bot.getName());
                continue;
            }

            long delayTicks = currentStaggerDelaySeconds * 20L;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                chatProcessor.processChatAsync(bot, rawEnglishMessage, chatConfig);
            }, delayTicks);

            currentStaggerDelaySeconds += fixedDelaySeconds;
        }
    }

    public int getTicksUntilNextChat() {
        return ticksUntilNextChat;
    }
}