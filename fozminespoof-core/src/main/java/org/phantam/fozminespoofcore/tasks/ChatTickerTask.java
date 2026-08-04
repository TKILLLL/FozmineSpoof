package org.phantam.fozminespoofcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.BotChatProcessor;
import org.phantam.fozminespoofcore.chat.BotSelector;
import org.phantam.fozminespoofcore.chat.ChatScheduler;
import org.phantam.fozminespoofcore.chat.MessageLoader;
import org.phantam.fozminespoofcore.config.ChatConfig;

import java.util.List;

/**
 * Executes the bot chat cycle instantly when called.
 * Re-engineered to run exactly on target ticks rather than continuously ticking.
 */
public class ChatTickerTask extends BukkitRunnable {

    private final FozmineSpoofCore plugin;
    private final ChatConfig chatConfig;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final MessageLoader messageLoader;
    private final ChatScheduler scheduler;

    public ChatTickerTask(FozmineSpoofCore plugin, ChatConfig chatConfig, BotSelector botSelector,
                          BotChatProcessor chatProcessor, MessageLoader messageLoader, ChatScheduler scheduler) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
        this.messageLoader = messageLoader;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        try {
            if (!chatConfig.isEnabled()) {
                return;
            }
            executeChatCycle();
        } finally {
            scheduler.scheduleNextCycle();
        }
    }

    private void executeChatCycle() {
        int totalOnline = Bukkit.getOnlinePlayers().size();
        int botOnline = plugin.getFakePlayerManager().getOnlineBotsData().size();
        int realPlayers = Math.max(0, totalOnline - botOnline);

        if (realPlayers < chatConfig.getMinRealPlayers()) {
            DebugLogger.log(plugin.getLogger(),
                    "ChatCycle: Skipped. Real players (%d) < Required min-real-players (%d)",
                    realPlayers, chatConfig.getMinRealPlayers());
            return;
        }

        List<Player> speakingBots = botSelector.selectRandomBots(chatConfig.getRandomBotsPerInterval());

        if (speakingBots.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "ChatCycle: No bots available to chat.");
            return;
        }

        long accumDelayTicks = 0L;

        for (Player bot : speakingBots) {
            String rawMessage = messageLoader.getRandomMessage();
            if (rawMessage == null) continue;

            final long scheduledDelay = accumDelayTicks;

            if (scheduledDelay <= 0) {
                chatProcessor.processChatAsync(bot, rawMessage, chatConfig);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> chatProcessor.processChatAsync(bot, rawMessage, chatConfig),
                        scheduledDelay);
            }

            accumDelayTicks += chatConfig.getRandomDelayTicks();
        }
    }
}