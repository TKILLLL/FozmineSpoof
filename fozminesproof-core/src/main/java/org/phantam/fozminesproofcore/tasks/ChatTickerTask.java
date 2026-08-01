package org.phantam.fozminesproofcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofcore.chat.BotChatProcessor;
import org.phantam.fozminesproofcore.chat.BotSelector;
import org.phantam.fozminesproofcore.chat.MessageLoader;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.utils.DebugLogger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Ticker task that periodically triggers bot chat cycles.
 * Manages countdown timer and staggers messages across selected bots.
 */
public class ChatTickerTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ChatConfig chatConfig;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final MessageLoader messageLoader;

    private int ticksUntilNextChat;
    private boolean isFirstRun = true;

    public ChatTickerTask(JavaPlugin plugin, ChatConfig chatConfig, BotSelector botSelector,
                          BotChatProcessor chatProcessor, MessageLoader messageLoader) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
        this.messageLoader = messageLoader;
        resetCountdown();

        DebugLogger.log(plugin.getLogger(), "ChatTickerTask: initialized, first cycle in %d ticks", ticksUntilNextChat);
    }

    @Override
    public void run() {
        if (!chatConfig.isEnabled()) {
            plugin.getLogger().log(Level.WARNING,
                    "[ChatTickerTask] Chat system is disabled. Cancelling task.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: cancelled (disabled)");
            this.cancel();
            return;
        }

        // Decrement by one second (20 ticks)
        ticksUntilNextChat -= 20;

        if (isFirstRun) {
            plugin.getLogger().log(Level.INFO,
                    "[ChatTickerTask] First run. Next chat in " + (ticksUntilNextChat / 20) + " seconds.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: first run, remaining %d seconds",
                    ticksUntilNextChat / 20);
            isFirstRun = false;
        }

        if (ticksUntilNextChat <= 0) {
            plugin.getLogger().log(Level.INFO, "[ChatTickerTask] Starting new chat cycle.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: executing chat cycle");
            executeChatCycle();
            resetCountdown();
            plugin.getLogger().log(Level.INFO,
                    "[ChatTickerTask] Next cycle in " + (ticksUntilNextChat / 20) + " seconds.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: next cycle in %d seconds",
                    ticksUntilNextChat / 20);
        }
    }

    /**
     * Resets the countdown timer with a random interval from config.
     * If the interval is 0 minutes, falls back to 10-59 seconds to avoid spam.
     */
    private void resetCountdown() {
        int minutes = chatConfig.getRandomIntervalMinutes();
        int totalSeconds;

        if (minutes == 0) {
            totalSeconds = ThreadLocalRandom.current().nextInt(10, 60);
            DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: minutes=0, using fallback %ds", totalSeconds);
        } else {
            totalSeconds = minutes * 60;
            DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: minutes=%d, totalSeconds=%d", minutes, totalSeconds);
        }

        this.ticksUntilNextChat = totalSeconds * 20;
        DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: reset countdown to %d ticks", ticksUntilNextChat);
    }

    /**
     * Selects bots and schedules their chat messages with staggered delays.
     */
    private void executeChatCycle() {
        List<Player> speakingBots = botSelector.selectRandomBots(chatConfig.getRandomBotsPerInterval());

        if (speakingBots.isEmpty()) {
            plugin.getLogger().log(Level.WARNING,
                    "[ChatTickerTask] No bots available to chat.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: no bots selected for chat");
            return;
        }

        plugin.getLogger().log(Level.INFO,
                "[ChatTickerTask] Selected " + speakingBots.size() + " bot(s) to chat.");

        int staggerSeconds = chatConfig.getRandomDelaySeconds();
        long currentDelay = 0;

        DebugLogger.log(plugin.getLogger(), "ChatTickerTask: selected %d bots, stagger=%ds",
                speakingBots.size(), staggerSeconds);

        for (Player bot : speakingBots) {
            String rawMessage = messageLoader.getRandomMessage();
            if (rawMessage == null) {
                plugin.getLogger().log(Level.WARNING,
                        "[ChatTickerTask] No message available for bot: " + bot.getName());
                DebugLogger.log(plugin.getLogger(), "ChatTickerTask: no message for %s", bot.getName());
                continue;
            }

            long delayTicks = currentDelay * 20L;
            DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: scheduling %s to chat in %d ticks",
                    bot.getName(), delayTicks);

            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> chatProcessor.processChatAsync(bot, rawMessage, chatConfig),
                    delayTicks);

            currentDelay += staggerSeconds;
        }
    }

    /**
     * Returns the remaining ticks until the next chat cycle.
     *
     * @return ticks remaining
     */
    public int getTicksUntilNextChat() {
        return ticksUntilNextChat;
    }
}