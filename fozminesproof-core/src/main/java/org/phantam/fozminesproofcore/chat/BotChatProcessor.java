package org.phantam.fozminesproofcore.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.manager.FakePlayerManager;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Handles asynchronous processing of a bot's chat message.
 * <p>
 * This class manages translation, placeholder replacement, and broadcasting of the final message.
 * Depending on configuration, it either sends using NMS broadcast or triggers a normal chat event.
 */
public class BotChatProcessor {

    private final FozmineSproofCore plugin;
    private final FakePlayerManager playerManager;
    private final ConfigManager configManager;
    private final TranslatorService translator;
    private final Logger logger;

    public BotChatProcessor(FozmineSproofCore plugin, FakePlayerManager playerManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.configManager = configManager;
        this.translator = new TranslatorService();
        this.logger = plugin.getLogger();
    }

    /**
     * Processes a chat message asynchronously for the given bot.
     *
     * @param bot          the bot player entity
     * @param rawMessage   the raw message template (may contain [name] placeholder)
     * @param chatConfig   the current chat configuration
     */
    public void processChatAsync(Player bot, String rawMessage, ChatConfig chatConfig) {
        if (bot == null || rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        String botName = bot.getName();
        if (!playerManager.isBotOnline(botName)) {
            return;
        }

        String processed = replaceNamePlaceholder(rawMessage, new ArrayList<>(Bukkit.getOnlinePlayers()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!playerManager.isBotOnline(botName)) {
                    return;
                }

                String targetLang = (chatConfig != null && chatConfig.getTranslationTarget() != null)
                        ? chatConfig.getTranslationTarget() : "vi";

                String translated = translator.translate(processed, targetLang);
                if (translated == null || translated.trim().isEmpty()) {
                    return;
                }

                boolean useCustomFormat = configManager.isMessageFormatEnable();

                if (useCustomFormat) {
                    String formatted = buildCustomFormatMessage(bot, translated);
                    String finalMessage = ColorUtils.colorize(formatted);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Use NMS broadcast for consistency
                        plugin.getBridge().broadcastNMSChat(bot, finalMessage);
                    });
                } else {
                    // Let other chat plugins (e.g., LPC) handle formatting via normal chat event
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (bot.isOnline()) {
                            bot.chat(translated);
                        }
                    });
                }

            } catch (Exception e) {
                logger.warning("[BotChatProcessor] Error processing chat for bot " + botName + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private String replaceNamePlaceholder(String message, List<Player> onlinePlayers) {
        if (!message.contains("[name]")) {
            return message;
        }

        String result = message;
        while (result.contains("[name]")) {
            if (onlinePlayers.isEmpty()) {
                result = result.replaceFirst("\\[name\\]", "");
            } else {
                int index = ThreadLocalRandom.current().nextInt(onlinePlayers.size());
                String selected = onlinePlayers.get(index).getName();
                result = result.replaceFirst("\\[name\\]", selected);
            }
        }
        return result;
    }

    private String buildCustomFormatMessage(Player bot, String message) {
        String rawFormat = configManager.getChatFormat();
        String formatted = rawFormat
                .replace("%fakeplayer_name%", bot.getName())
                .replace("%fakeplayer_message%", message)
                .replace("{name}", bot.getName())
                .replace("{message}", message)
                .replace("{prefix}", "")
                .replace("&r", "");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            formatted = PlaceholderAPI.setPlaceholders(bot, formatted);
        }
        return formatted;
    }
}