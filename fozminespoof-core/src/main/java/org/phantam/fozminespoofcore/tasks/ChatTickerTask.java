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
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.config.ChatConfig;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Scheduled task executing periodic simulated chatter (AI-to-AI ambient banter or standard chat cycles).
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

            if ("ai".equalsIgnoreCase(chatConfig.getMode())) {
                executeAiToAiCycle();
            } else {
                executeNormalChatCycle();
            }
        } finally {
            scheduler.scheduleNextCycle();
        }
    }

    /**
     * Executes autonomous ambient conversation between simulated fake players.
     */
    private void executeAiToAiCycle() {
        int totalOnline = Bukkit.getOnlinePlayers().size();
        int botOnline = plugin.getFakePlayerManager().getOnlineBotsData().size();
        int realPlayers = Math.max(0, totalOnline - botOnline);

        // Safeguard: Do not spend API tokens if real player count is below threshold
        if (realPlayers < chatConfig.getMinRealPlayers()) {
            DebugLogger.log(plugin.getLogger(),
                    "AiToAiCycle: Skipped. Real players (%d) < Required min-real-players (%d) - API tokens conserved.",
                    realPlayers, chatConfig.getMinRealPlayers());
            return;
        }

        AiConfig aiConfig = plugin.getAiConfig();
        if (aiConfig == null || !aiConfig.isAiToAiEnabled()) {
            DebugLogger.log(plugin.getLogger(), "AiToAiCycle: Skipped. AI-to-AI mode is disabled in ai-chat-bot.yml");
            return;
        }

        if (!aiConfig.isInActiveHours()) {
            DebugLogger.log(plugin.getLogger(), "AiToAiCycle: Skipped. Current time is outside active operational hours.");
            return;
        }

        // Probability check for initiating ambient banter
        if (ThreadLocalRandom.current().nextDouble() > aiConfig.getAiToAiInitiateChance()) {
            DebugLogger.log(plugin.getLogger(), "AiToAiCycle: Initiation probability roll failed.");
            return;
        }

        // Select candidate bots, strictly excluding the dedicated AI Help support bot
        String helpBotName = aiConfig.isAiHelpEnabled() ? aiConfig.getAiHelpBotName() : "";
        List<Player> availableBots = botSelector.selectRandomBots(10).stream()
                .filter(b -> helpBotName.isEmpty() || !b.getName().equalsIgnoreCase(helpBotName))
                .collect(Collectors.toList());

        if (availableBots.size() < 2) {
            DebugLogger.log(plugin.getLogger(), "AiToAiCycle: Skipped. Need at least 2 non-support bots for conversation.");
            return;
        }

        Player botA = availableBots.get(0);
        Player botB = availableBots.get(1);

        DebugLogger.log(plugin.getLogger(), "AiToAiCycle: Initiating autonomous conversation between %s and %s",
                botA.getName(), botB.getName());

        plugin.getAiChatProcessor().processAiToAiInitiationAsync(botA, botB);
    }

    /**
     * Executes standard periodic chat broadcast using pre-loaded template phrases.
     */
    private void executeNormalChatCycle() {
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