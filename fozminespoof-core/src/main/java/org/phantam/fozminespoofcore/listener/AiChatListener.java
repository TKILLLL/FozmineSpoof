package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
        Player sender = event.getPlayer();
        if (sender == null || !aiConfig.isEnabled()) return;

        // Bỏ qua nếu là Bot
        if (sender.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(sender.getName())) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.isBlank()) return;

        // 1. KIỂM TRA CHẾ ĐỘ AI-HELP (@FozmineBot)
        if (aiConfig.isAiHelpEnabled() && rawMessage.contains(aiConfig.getAiHelpTagPrefix() + aiConfig.getAiHelpBotName())) {
            Player helpBot = plugin.getFakePlayerManager().getOnlineBotEntity(aiConfig.getAiHelpBotName());
            if (helpBot != null && helpBot.isOnline()) {
                aiProcessor.processPlayerToAiChatAsync(sender, helpBot, rawMessage, true);
                return;
            }
        }

        // 2. KIỂM TRA CHẾ ĐỘ PLAYER-TO-AI (Tương tác tự nhiên khi nhắc tên)
        if (aiConfig.isPlayerToAiEnabled()) {
            List<Player> onlineBots = botSelector.selectRandomBots(5);
            for (Player bot : onlineBots) {
                if (isNameMentioned(rawMessage, bot.getName())) {
                    if (ThreadLocalRandom.current().nextDouble() <= aiConfig.getPlayerToAiChance()) {
                        aiProcessor.processPlayerToAiChatAsync(sender, bot, rawMessage, false);
                        break;
                    }
                }
            }
        }
    }

    private boolean isNameMentioned(String message, String botName) {
        String lowerMsg = message.toLowerCase();
        String lowerName = botName.toLowerCase();

        if (lowerMsg.contains(lowerName)) return true;

        // Similarity match
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