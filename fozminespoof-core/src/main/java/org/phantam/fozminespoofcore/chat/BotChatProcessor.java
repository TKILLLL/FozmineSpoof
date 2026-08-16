package org.phantam.fozminespoofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ChatConfig;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.manager.FakePlayerManager;
import org.phantam.fozminespoofcore.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * Handles asynchronous processing and broadcasting of simulated bot chat messages.
 */
public class BotChatProcessor {

    private final FozmineSpoofCore plugin;
    private final FakePlayerManager playerManager;
    private final ConfigManager configManager;
    private final TranslatorService translator;
    private final Logger logger;

    public BotChatProcessor(FozmineSpoofCore plugin, FakePlayerManager playerManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.configManager = configManager;
        this.translator = new TranslatorService();
        this.logger = plugin.getLogger();
    }

    public void processChatAsync(Player bot, String rawMessage, ChatConfig chatConfig) {
        if (bot == null || rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        String botName = bot.getName();
        if (!playerManager.isBotOnline(botName)) {
            DebugLogger.logFine(logger, "BotChatProcessor: bot %s is not online, skipping", botName);
            return;
        }

        DebugLogger.log(logger, "BotChatProcessor: processing chat for %s: '%s'", botName, rawMessage);

        String processed = replaceNamePlaceholder(rawMessage, new ArrayList<>(Bukkit.getOnlinePlayers()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!playerManager.isBotOnline(botName)) {
                    return;
                }

                String targetLang = (chatConfig != null && chatConfig.getTranslationTarget() != null)
                        ? chatConfig.getTranslationTarget() : "en";

                String provider = (chatConfig != null) ? chatConfig.getTranslationProvider() : "google";
                String apiKey = (chatConfig != null) ? chatConfig.getTranslationApiKey() : "";

                DebugLogger.logFine(logger, "BotChatProcessor: translating with provider '%s' to %s", provider, targetLang);
                String translated = translator.translate(processed, targetLang, provider, apiKey);

                if (translated == null || translated.trim().isEmpty()) {
                    DebugLogger.log(logger, "BotChatProcessor: translation result empty for %s", botName);
                    return;
                }

                boolean useCustomFormat = configManager.isMessageFormatEnable();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!bot.isOnline() || !playerManager.isBotOnline(botName)) {
                        DebugLogger.log(logger, "BotChatProcessor: bot %s left before chat broadcast", botName);
                        return;
                    }

                    if (useCustomFormat) {
                        String formatted = buildCustomFormatMessage(bot, translated);
                        String finalMessage = ColorUtils.colorize(formatted);
                        DebugLogger.log(logger, "BotChatProcessor: broadcasting NMS chat for %s: %s", botName, finalMessage);
                        plugin.getBridge().broadcastNMSChat(bot, finalMessage);
                    } else {
                        DebugLogger.log(logger, "BotChatProcessor: triggering normal chat event for %s: %s", botName, translated);
                        bot.chat(translated);
                    }
                });

            } catch (Exception e) {
                logger.warning("[BotChatProcessor] Error processing chat for bot " + botName + ": " + e.getMessage());
            }
        });
    }

    private String replaceNamePlaceholder(String message, List<Player> onlinePlayers) {
        if (!message.contains("[name]") && !message.contains("%name%")) {
            return message;
        }

        String result = message;
        while (result.contains("[name]") || result.contains("%name%")) {
            if (onlinePlayers.isEmpty()) {
                result = result.replaceFirst("\\[name\\]|%name%", "");
            } else {
                int index = ThreadLocalRandom.current().nextInt(onlinePlayers.size());
                String selected = onlinePlayers.get(index).getName();
                String safeReplacement = Matcher.quoteReplacement(selected);
                result = result.replaceFirst("\\[name\\]|%name%", safeReplacement);
                DebugLogger.logFine(logger, "BotChatProcessor: replaced name placeholder with %s", selected);
            }
        }
        return result;
    }

    private String buildCustomFormatMessage(Player bot, String message) {
        String rawFormat = configManager.getChatFormat();
        return rawFormat
                .replace("%fakeplayer_name%", bot.getName())
                .replace("%fakeplayer_message%", message)
                .replace("{name}", bot.getName())
                .replace("{message}", message)
                .replace("{prefix}", "")
                .replace("&r", "");
    }
}