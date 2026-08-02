package org.phantam.fozminespoofcore.chat.ai;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.Range;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AiChatProcessor {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiPersonalityManager personalityManager;
    private final AiProviderService providerService;

    // Rate limiting: playerUUID -> List<TimestampMs>
    private final Map<UUID, List<Long>> rateLimits = new ConcurrentHashMap<>();

    public AiChatProcessor(FozmineSpoofCore plugin, AiConfig aiConfig, AiPersonalityManager personalityManager) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.personalityManager = personalityManager;
        this.providerService = new AiProviderService(plugin.getLogger());
    }

    public void processPlayerToAiChatAsync(Player sender, Player bot, String rawMessage, boolean isHelpMode) {
        if (!aiConfig.isEnabled() || !aiConfig.isInActiveHours()) return;

        // 1. Kiểm tra độ dài & Blacklist Input
        if (rawMessage.length() > aiConfig.getMaxInputLength()) return;

        String cleanedInput = StringUtils.cleanMessage(rawMessage);
        for (String black : aiConfig.getInputBlacklistKeywords()) {
            if (cleanedInput.contains(StringUtils.cleanMessage(black))) {
                DebugLogger.log(plugin.getLogger(), "AiChatProcessor: input blacklisted '%s' from %s", black, sender.getName());
                return;
            }
        }

        // 2. Rate Limiting
        if (!checkRateLimit(sender)) {
            if (aiConfig.getPunishmentCommand() != null && !aiConfig.getPunishmentCommand().isBlank()) {
                String cmd = aiConfig.getPunishmentCommand().replace("{player}", sender.getName());
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            }
            return;
        }

        // 3. Xây dựng System Prompt
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

        // 4. Gọi API Async
        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, rawMessage)
                .thenAccept(response -> {
                    if (response == null || response.isBlank()) {
                        handleFallback(bot, detectedLang);
                        return;
                    }

                    // 5. Output Sanitization & Security Filtration
                    String sanitized = sanitizeOutput(response, isHelpMode);
                    if (sanitized == null || sanitized.isBlank()) {
                        handleFallback(bot, detectedLang);
                        return;
                    }

                    // 6. Typing Delay & Phát tin nhắn lên Server
                    long delayTicks = Range.parse(aiConfig.getTypingDelayStr(), 1.0, 3.0).getRandomTicks();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!sender.isOnline() || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                            return;
                        }

                        if (plugin.getConfigManager().isMessageFormatEnable()) {
                            String formatted = plugin.getConfigManager().getChatFormat()
                                    .replace("%fakeplayer_name%", bot.getName())
                                    .replace("%fakeplayer_message%", sanitized)
                                    .replace("{name}", bot.getName())
                                    .replace("{message}", sanitized);
                            plugin.getBridge().broadcastNMSChat(bot, formatted);
                        } else {
                            bot.chat(sanitized);
                        }
                    }, delayTicks);
                });
    }

    private String sanitizeOutput(String response, boolean isHelpMode) {
        String result = response.trim();

        // Filtration
        if (aiConfig.isBlockCodeBlocks() && (result.contains("```") || result.contains("{") || result.contains("}"))) {
            return null;
        }

        String lower = result.toLowerCase(Locale.ROOT);
        for (String sens : aiConfig.getBlockSensitiveWords()) {
            if (lower.contains(sens.toLowerCase(Locale.ROOT))) {
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

    private void handleFallback(Player bot, String lang) {
        if (!aiConfig.isFallbackEnabled()) return;
        List<String> pool = aiConfig.getFallbackResponses(lang);
        if (pool.isEmpty()) return;

        String fallback = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (bot.isOnline() && plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                bot.chat(fallback);
            }
        });
    }

    private boolean checkRateLimit(Player sender) {
        long now = System.currentTimeMillis();
        UUID uuid = sender.getUniqueId();
        List<Long> timestamps = rateLimits.computeIfAbsent(uuid, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (timestamps) {
            timestamps.removeIf(t -> now - t > 60_000L);
            if (timestamps.size() >= aiConfig.getRateLimitMaxPerMin()) {
                return false;
            }
            timestamps.add(now);
        }
        return true;
    }

    private String detectLanguage(String text) {
        if (!aiConfig.getLangMode().equalsIgnoreCase("auto")) {
            return aiConfig.getDefaultLanguage();
        }
        // Đơn giản hóa nhận diện Tiếng Việt nếu có dấu tiếng Việt
        if (text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ].*")) {
            return "vi";
        }
        return aiConfig.getDefaultLanguage();
    }
}