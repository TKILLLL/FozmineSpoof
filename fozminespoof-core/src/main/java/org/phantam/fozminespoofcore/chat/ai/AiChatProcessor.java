package org.phantam.fozminespoofcore.chat.ai;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.ColorUtils;
import org.phantam.fozminespoofcore.utils.Range;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class AiChatProcessor {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiPersonalityManager personalityManager;
    private final AiProviderService providerService;

    private final Map<UUID, Queue<Long>> rateLimits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();

    public AiChatProcessor(FozmineSpoofCore plugin, AiConfig aiConfig, AiPersonalityManager personalityManager) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.personalityManager = personalityManager;
        this.providerService = new AiProviderService(plugin.getLogger());
    }

    /**
     * Backward-compatible overload method.
     */
    public void processPlayerToAiChatAsync(Player sender, Player bot, String rawMessage, boolean isHelpMode) {
        processPlayerToAiChatAsync(sender, bot, rawMessage, isHelpMode, false);
    }

    /**
     * Processes player chat, queries AI engine, and delivers private or public response.
     */
    public void processPlayerToAiChatAsync(Player sender, Player bot, String rawMessage, boolean isHelpMode, boolean isPrivateMsg) {
        DebugLogger.log(plugin.getLogger(), "AiChatProcessor: processing for %s (bot=%s, help=%b, PM=%b)",
                sender.getName(), bot.getName(), isHelpMode, isPrivateMsg);

        if (!aiConfig.isEnabled() || !aiConfig.isInActiveHours()) {
            return;
        }

        if (rawMessage.length() > aiConfig.getMaxInputLength()) {
            return;
        }

        if (isInputBlocked(rawMessage, sender)) {
            return;
        }

        if (!checkRateLimit(sender)) {
            handleRateLimit(sender);
            return;
        }

        String detectedLang = detectLanguage(rawMessage);
        String langHint = aiConfig.getLanguageHint(detectedLang);
        var profile = personalityManager.getProfile(bot.getName());

        String systemPrompt;
        if (isHelpMode) {
            systemPrompt = aiConfig.getAiHelpMinecraftPrompt()
                    .replace("{listener}", bot.getName())
                    .replace("{sender}", sender.getName());
        } else {
            systemPrompt = aiConfig.getSystemRule()
                    .replace("{listener}", bot.getName())
                    .replace("{sender}", sender.getName())
                    .replace("{personality}", profile.personality())
                    .replace("{speaking_style}", profile.speakingStyle())
                    .replace("{description}", profile.currentSituation())
                    .replace("{language_hint}", langHint)
                    .replace("{default_language}", detectedLang);
        }

        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, rawMessage)
                .thenAccept(response -> {
                    if (response == null || response.isBlank()) {
                        sendTimeoutMessage(sender, bot, isPrivateMsg);
                        return;
                    }

                    String sanitized = sanitizeOutput(response, isHelpMode);
                    if (sanitized == null || sanitized.isBlank()) {
                        sendTimeoutMessage(sender, bot, isPrivateMsg);
                        return;
                    }

                    long delayTicks = Range.parse(aiConfig.getTypingDelayStr(), 1.0, 3.0).getRandomTicks();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!sender.isOnline() || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                            return;
                        }

                        if (isPrivateMsg) {
                            // Phản hồi riêng tư qua /msg -> [Bot -> me] message
                            String formattedPm = aiConfig.getPmIncomingFormat()
                                    .replace("{bot}", bot.getName())
                                    .replace("{message}", sanitized);
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedPm));
                            DebugLogger.log(plugin.getLogger(), "AiChatProcessor: sent PM reply to %s from %s", sender.getName(), bot.getName());
                        } else {
                            // Chat công khai như người chơi bình thường
                            if (plugin.getConfigManager().isMessageFormatEnable()) {
                                String customFormat = plugin.getConfigManager().getChatFormat()
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("%fakeplayer_message%", sanitized)
                                        .replace("{name}", bot.getName())
                                        .replace("{message}", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                            } else {
                                bot.chat(sanitized);
                            }
                            DebugLogger.log(plugin.getLogger(), "AiChatProcessor: sent public reply from %s", bot.getName());
                        }
                    }, delayTicks);
                })
                .exceptionally(ex -> {
                    DebugLogger.log(plugin.getLogger(), "AiChatProcessor: AI request failed for %s: %s", bot.getName(), ex.getMessage());
                    sendTimeoutMessage(sender, bot, isPrivateMsg);
                    return null;
                });
    }

    private boolean checkRateLimit(Player sender) {
        long now = System.currentTimeMillis();
        UUID uuid = sender.getUniqueId();
        Queue<Long> timestamps = rateLimits.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>());
        timestamps.removeIf(t -> now - t > 60_000L);
        if (timestamps.size() >= aiConfig.getRateLimitMaxPerMin()) {
            lastRequestTime.put(uuid, now);
            return false;
        }
        timestamps.add(now);
        lastRequestTime.remove(uuid);
        return true;
    }

    private void handleRateLimit(Player sender) {
        Long last = lastRequestTime.get(sender.getUniqueId());
        long waitTime = (last == null) ? 0 : Math.max(0, (60_000L - (System.currentTimeMillis() - last)) / 1000);

        if (aiConfig.isRateLimitWarnEnabled()) {
            String msg = aiConfig.getRateLimitWarnMessage().replace("{time}", String.valueOf(waitTime));
            String colored = ChatColor.translateAlternateColorCodes('&', msg);
            if (aiConfig.isRateLimitWarnActionBar()) {
                sender.sendActionBar(colored);
            } else {
                sender.sendMessage(colored);
            }
        }

        if (aiConfig.isRateLimitPunishmentEnabled()) {
            for (String cmd : aiConfig.getRateLimitPunishmentCommands()) {
                String processed = cmd.replace("{player}", sender.getName());
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed));
            }
        }
    }

    private boolean isInputBlocked(String rawMessage, Player sender) {
        String cleanedInput = StringUtils.cleanMessage(rawMessage);
        boolean blocked = false;
        String blockedWord = "";

        if (aiConfig.isInputBlacklistRegex()) {
            for (String pattern : aiConfig.getInputBlacklistWords()) {
                try {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(rawMessage).find()) {
                        blocked = true;
                        blockedWord = pattern;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        } else {
            for (String word : aiConfig.getInputBlacklistWords()) {
                String cleanedWord = StringUtils.cleanMessage(word);
                if (cleanedInput.contains(cleanedWord)) {
                    blocked = true;
                    blockedWord = word;
                    break;
                }
            }
        }

        if (blocked) {
            String msg = aiConfig.getInputBlacklistMessage().replace("{word}", blockedWord);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        return blocked;
    }

    private String sanitizeOutput(String response, boolean isHelpMode) {
        String result = response.trim();

        if (aiConfig.isBlockCodeBlocks() && (result.contains("```") || result.contains("{") || result.contains("}"))) {
            return null;
        }

        String lower = result.toLowerCase(Locale.ROOT);
        for (String sens : aiConfig.getBlockSensitiveWords()) {
            String pattern = "\\b" + Pattern.quote(sens.toLowerCase(Locale.ROOT)) + "\\b";
            if (Pattern.compile(pattern).matcher(lower).find()) {
                return null;
            }
        }

        if (isHelpMode && aiConfig.isDisableSanitizationForHelp()) {
            return result;
        }

        if (aiConfig.isForceLowercase()) {
            result = result.toLowerCase(Locale.ROOT);
        }

        if (aiConfig.isForceNoPunctuation()) {
            result = result.replaceAll("[.!?,;:]", "");
        }

        return result.trim();
    }

    private void sendTimeoutMessage(Player sender, Player bot, boolean isPrivateMsg) {
        String msg = aiConfig.getTimeoutMessage();
        if (msg == null || msg.isEmpty()) return;

        if (isPrivateMsg) {
            String formattedPm = aiConfig.getPmIncomingFormat()
                    .replace("{bot}", bot.getName())
                    .replace("{message}", msg);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedPm));
        } else {
            bot.chat(msg);
        }
    }

    private String detectLanguage(String text) {
        String mode = aiConfig.getLangMode();
        if (!"auto".equalsIgnoreCase(mode) || text == null || text.isEmpty()) {
            return aiConfig.getDefaultLanguage();
        }
        if (text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ].*")) {
            return "vi";
        }
        return aiConfig.getDefaultLanguage();
    }
}