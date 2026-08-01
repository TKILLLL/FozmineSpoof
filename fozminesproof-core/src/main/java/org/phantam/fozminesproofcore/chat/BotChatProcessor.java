package org.phantam.fozminesproofcore.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ChatConfig;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.FakePlayerManager;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BotChatProcessor {
    private final FozmineSproofCore plugin;
    private final FakePlayerManager fakePlayerManager;
    private final TranslatorService translatorService;
    private final ConfigManager configManager;

    public BotChatProcessor(FozmineSproofCore plugin, FakePlayerManager fakePlayerManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.fakePlayerManager = fakePlayerManager;
        this.configManager = configManager;
        this.translatorService = new TranslatorService();
    }

    public void processChatAsync(Player bot, String rawMessage, ChatConfig chatConfig) {
        if (bot == null || rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        String botName = bot.getName();

        if (!fakePlayerManager.isBotOnline(botName)) {
            return;
        }

        String processedMessage = rawMessage;
        if (processedMessage.contains("[name]")) {
            List<String> poolNames = new java.util.ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null) poolNames.add(p.getName());
            }
            while (processedMessage.contains("[name]")) {
                if (poolNames.isEmpty()) {
                    processedMessage = processedMessage.replaceFirst("\\[name\\]", "");
                } else {
                    int randomIndex = ThreadLocalRandom.current().nextInt(poolNames.size());
                    String selectedName = poolNames.get(randomIndex);
                    processedMessage = processedMessage.replaceFirst("\\[name\\]", selectedName);
                }
            }
        }

        final String finalRawMessage = processedMessage;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!fakePlayerManager.isBotOnline(botName)) {
                    return;
                }

                String targetLang = (chatConfig != null && chatConfig.getTranslationTarget() != null)
                        ? chatConfig.getTranslationTarget() : "vi";

                String finalMessage = translatorService.translate(finalRawMessage, targetLang);
                if (finalMessage == null || finalMessage.trim().isEmpty()) {
                    return;
                }

                boolean useCustomFormat = configManager.isMessageFormatEnable();

                if (useCustomFormat) {
                    String rawFormat = configManager.getChatFormat();
                    String formattedMessage = rawFormat
                            .replace("%fakeplayer_name%", bot.getName())
                            .replace("%fakeplayer_message%", finalMessage)
                            .replace("{name}", bot.getName())
                            .replace("{message}", finalMessage)
                            .replace("{prefix}", "")
                            .replace("&r", "");

                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        formattedMessage = PlaceholderAPI.setPlaceholders(bot, formattedMessage);
                    }
                    final String messageToBroadcast = ColorUtils.colorize(formattedMessage);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(messageToBroadcast);
                        }
                        Bukkit.getConsoleSender().sendMessage(messageToBroadcast);
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (bot.isOnline()) {
                        bot.chat(finalMessage);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("⚠ Lỗi xảy ra trong tiến trình xử lý chat bất đồng bộ của Bot " + botName + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}