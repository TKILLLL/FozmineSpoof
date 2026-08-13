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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

        // 1. Tag AI Bot trợ giúp (@FozmineBot)
        if (aiConfig.isAiHelpEnabled() && rawMessage.contains(aiConfig.getAiHelpTagPrefix() + aiConfig.getAiHelpBotName())) {
            Player helpBot = plugin.getFakePlayerManager().getOnlineBotEntity(aiConfig.getAiHelpBotName());
            if (helpBot != null && helpBot.isOnline()) {
                // Phản hồi công khai trong kênh chat
                aiProcessor.processPlayerToAiChatAsync(sender, helpBot, rawMessage, true, false);
                return;
            }
        }

        // 2. Chat nhắc tên bot tự nhiên hoặc tag @BotName bất kỳ
        if (aiConfig.isPlayerToAiEnabled()) {
            List<Player> onlineBots = botSelector.selectRandomBots(5);

            for (Player bot : onlineBots) {
                if (isNameMentioned(rawMessage, bot.getName())) {
                    if (ThreadLocalRandom.current().nextDouble() <= aiConfig.getPlayerToAiChance()) {
                        // Phản hồi công khai trong kênh chat
                        aiProcessor.processPlayerToAiChatAsync(sender, bot, rawMessage, false, false);
                        break;
                    }
                }
            }
        }
    }

    private boolean isNameMentioned(String message, String botName) {
        String lowerMsg = message.toLowerCase();
        String lowerName = botName.toLowerCase();

        if (lowerMsg.contains(aiConfig.getAiHelpTagPrefix() + lowerName) || lowerMsg.contains(lowerName)) {
            return true;
        }

        String[] words = lowerMsg.split(" ");
        for (String w : words) {
            if (w.length() >= 3) {
                int dist = org.phantam.fozminespoofcore.utils.StringUtils.levenshteinDistance(w, lowerName);
                double similarity = 1.0 - ((double) dist / Math.max(w.length(), lowerName.length()));
                if (similarity >= aiConfig.getNameSimilarityThreshold()) {
                    return true;
                }
            }
        }
        return false;
    }
}