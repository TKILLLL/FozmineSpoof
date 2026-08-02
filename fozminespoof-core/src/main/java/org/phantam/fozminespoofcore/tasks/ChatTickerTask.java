package org.phantam.fozminespoofcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofcore.chat.BotChatProcessor;
import org.phantam.fozminespoofcore.chat.BotSelector;
import org.phantam.fozminespoofcore.chat.MessageLoader;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.List;
import java.util.logging.Level;

/**
 * Ticker task that periodically triggers bot chat cycles.
 * Operates at 1 tick (50ms) frequency for exact millisecond accuracy.
 */
public class ChatTickerTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ChatConfig chatConfig;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final MessageLoader messageLoader;

    private long ticksUntilNextChat;

    public ChatTickerTask(JavaPlugin plugin, ChatConfig chatConfig, BotSelector botSelector,
                          BotChatProcessor chatProcessor, MessageLoader messageLoader) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
        this.messageLoader = messageLoader;

        resetCountdown();
        DebugLogger.log(plugin.getLogger(), "ChatTickerTask: initialized, first cycle in %d ticks (%.2f s)",
                ticksUntilNextChat, ticksUntilNextChat / 20.0);
    }

    @Override
    public void run() {
        if (!chatConfig.isEnabled()) {
            plugin.getLogger().log(Level.WARNING, "[ChatTickerTask] Chat system is disabled. Cancelling task.");
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: cancelled (disabled)");
            this.cancel();
            return;
        }

        ticksUntilNextChat--;

        if (ticksUntilNextChat <= 0) {
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: executing chat cycle");
            executeChatCycle();
            resetCountdown();
        }
    }

    private void resetCountdown() {
        this.ticksUntilNextChat = chatConfig.getRandomIntervalTicks();
        DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: reset countdown to %d ticks (%.2f s)",
                ticksUntilNextChat, ticksUntilNextChat / 20.0);
    }

    private void executeChatCycle() {
        List<Player> speakingBots = botSelector.selectRandomBots(chatConfig.getRandomBotsPerInterval());

        if (speakingBots.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "ChatTickerTask: no bots selected for chat");
            return;
        }

        long accumDelayTicks = 0L;

        for (Player bot : speakingBots) {
            String rawMessage = messageLoader.getRandomMessage();
            if (rawMessage == null) {
                continue;
            }

            final long scheduledDelay = accumDelayTicks;
            DebugLogger.logFine(plugin.getLogger(), "ChatTickerTask: scheduling %s to chat in %d ticks",
                    bot.getName(), scheduledDelay);

            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> chatProcessor.processChatAsync(bot, rawMessage, chatConfig),
                    scheduledDelay);

            accumDelayTicks += chatConfig.getRandomDelayTicks();
        }
    }

    public long getTicksUntilNextChat() {
        return ticksUntilNextChat;
    }
}