package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.BotSelector;
import org.phantam.fozminespoofcore.chat.ai.AiChatProcessor;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Listens for public chat events to detect bot mentions or help-desk queries.
 * Employs optimal bot selection to eliminate concurrent multi-bot response collisions.
 */
public class AiChatListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiChatProcessor aiProcessor;
    private final BotSelector botSelector;

    public AiChatListener(FozmineSpoofCore plugin, AiConfig aiConfig, AiChatProcessor aiProcessor, BotSelector botSelector) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.aiProcessor = aiProcessor;
        this.botSelector = botSelector;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!aiConfig.isEnabled()) {
            return;
        }

        Player sender = event.getPlayer();
        if (sender == null || sender.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(sender.getName())) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        // 1. Evaluate dedicated AI Help Bot mentions
        if (aiConfig.isAiHelpEnabled()) {
            String helpBotName = aiConfig.getAiHelpBotName();
            String tagPrefix = (aiConfig.getAiHelpTagPrefix() != null) ? aiConfig.getAiHelpTagPrefix() : "@";

            if (isBotMentioned(rawMessage, helpBotName, tagPrefix)) {
                Player helpBot = plugin.getFakePlayerManager().getOnlineBotEntity(helpBotName);
                if (helpBot != null && helpBot.isOnline()) {
                    aiProcessor.processPlayerToAiChatAsync(sender, helpBot, rawMessage, true, false);
                    return;
                }
            }
        }

        // 2. Evaluate organic Player-to-AI mentions
        if (aiConfig.isPlayerToAiEnabled()) {
            List<Player> onlineBots = botSelector.selectRandomBots(15);
            Player bestMatchedBot = null;
            double highestScore = 0.0;

            for (Player bot : onlineBots) {
                if (aiConfig.isAiHelpEnabled() && bot.getName().equalsIgnoreCase(aiConfig.getAiHelpBotName())) {
                    continue;
                }

                double score = getMentionScore(rawMessage, bot.getName());
                if (score >= aiConfig.getNameSimilarityThreshold() && score > highestScore) {
                    highestScore = score;
                    bestMatchedBot = bot;
                }
            }

            // Route to single best matching bot
            if (bestMatchedBot != null) {
                DebugLogger.log(plugin.getLogger(), "AiChatListener: Player %s mentioned %s (Score: %.2f)",
                        sender.getName(), bestMatchedBot.getName(), highestScore);
                aiProcessor.processPlayerToAiChatAsync(sender, bestMatchedBot, rawMessage, false, false);
            }
        }
    }

    private boolean isBotMentioned(String message, String botName, String tagPrefix) {
        String lowerMsg = message.toLowerCase(Locale.ROOT);
        String lowerBot = botName.toLowerCase(Locale.ROOT);

        if (!tagPrefix.isEmpty() && lowerMsg.contains(tagPrefix.toLowerCase(Locale.ROOT) + lowerBot)) {
            return true;
        }

        return Pattern.compile("\\b" + Pattern.quote(lowerBot) + "\\b", Pattern.CASE_INSENSITIVE).matcher(lowerMsg).find();
    }

    private double getMentionScore(String message, String botName) {
        String lowerMsg = message.toLowerCase(Locale.ROOT);
        String lowerBot = botName.toLowerCase(Locale.ROOT);

        if (Pattern.compile("\\b" + Pattern.quote(lowerBot) + "\\b", Pattern.CASE_INSENSITIVE).matcher(lowerMsg).find()) {
            return 1.0;
        }

        String[] words = StringUtils.cleanMessage(message).split("\\s+");
        double maxSimilarity = 0.0;

        for (String word : words) {
            if (word.length() >= 3) {
                int dist = StringUtils.levenshteinDistance(word, lowerBot);
                double similarity = 1.0 - ((double) dist / Math.max(word.length(), lowerBot.length()));
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                }
            }
        }
        return maxSimilarity;
    }
}