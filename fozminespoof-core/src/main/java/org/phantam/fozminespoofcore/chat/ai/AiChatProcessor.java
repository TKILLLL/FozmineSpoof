package org.phantam.fozminespoofcore.chat.ai;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.AiConfig;
import org.phantam.fozminespoofcore.utils.ColorUtils;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class AiChatProcessor {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiPersonalityManager personalityManager;
    private final AiProviderService providerService;

    private final Map<UUID, Queue<Long>> rateLimits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();

    // RAM Conversation Memory: Player/Bot UUID -> List<ChatMessage>
    private final Map<UUID, List<AiProviderService.ChatMessage>> conversationMemory = new ConcurrentHashMap<>();

    public AiChatProcessor(FozmineSpoofCore plugin, AiConfig aiConfig, AiPersonalityManager personalityManager) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.personalityManager = personalityManager;
        this.providerService = new AiProviderService(plugin.getLogger());
    }

    public void processPlayerToAiChatAsync(Player sender, Player bot, String rawMessage, boolean isHelpMode) {
        processPlayerToAiChatAsync(sender, bot, rawMessage, isHelpMode, false);
    }

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
            systemPrompt = aiConfig.getAiHelpServerPrompt()
                    .replace("{listener}", bot.getName())
                    .replace("{sender}", sender.getName())
                    .replace("{server.knowledge-base}", aiConfig.getFormattedServerKnowledgeBase());
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

        UUID senderUuid = sender.getUniqueId();
        List<AiProviderService.ChatMessage> history = getValidHistory(senderUuid);

        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, history, rawMessage)
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

                    saveToMemory(senderUuid, rawMessage, sanitized);

                    long delayTicks = aiConfig.getTypingDelayTicks();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!sender.isOnline() || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                            return;
                        }

                        if (isPrivateMsg) {
                            if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                                String formattedPm = aiConfig.getPmIncomingFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("{message}", sanitized);
                                sender.sendMessage(ColorUtils.colorize(formattedPm));
                            } else {
                                bot.chat("/msg " + sender.getName() + " " + sanitized);
                            }
                            DebugLogger.log(plugin.getLogger(), "AiChatProcessor: sent PM reply to %s from %s", sender.getName(), bot.getName());
                        } else {
                            if (isHelpMode && aiConfig.getAiHelpResponseFormat() != null && !aiConfig.getAiHelpResponseFormat().isBlank()) {
                                String formattedHelp = aiConfig.getAiHelpResponseFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("{name}", bot.getName())
                                        .replace("{message}", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(formattedHelp));
                            } else if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                                String customFormat = aiConfig.getChatFormat()
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("%fakeplayer_message%", sanitized)
                                        .replace("{bot}", bot.getName())
                                        .replace("{name}", bot.getName())
                                        .replace("{message}", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                            } else if (plugin.getConfigManager().isMessageFormatEnable()) {
                                String customFormat = plugin.getConfigManager().getChatFormat()
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("%fakeplayer_message%", sanitized)
                                        .replace("{bot}", bot.getName())
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

    // --- AI-TO-AI CONVERSATION SYSTEM ---

    public void processAiToAiInitiationAsync(Player botA, Player botB) {
        if (botA == null || botB == null || !botA.isOnline() || !botB.isOnline()) return;

        var profileA = personalityManager.getProfile(botA.getName());
        String lang = aiConfig.getDefaultLanguage();

        String prompt = aiConfig.getAiToAiInitiationPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = "[CONTEXT & ROLE] You are a real human Minecraft player named {listener} interacting on a live server with {selectedTarget}.\n" +
                    "[PERSONALITY VIBE] {personality}\n" +
                    "[SPEAKING STYLE] {speaking_style}\n" +
                    "[SITUATION] {description}\n" +
                    "[TASK] Initiate a short, natural in-game conversation starter with them in language: {default_language}.\n" +
                    "[TOPIC POOL] Mining trip, building a base, finding diamonds, fighting mobs, farming, or crafting.\n" +
                    "[CONSTRAINTS] Maximum 6 words. Lowercase only. No punctuation. Fast gamer style.";
        }

        String systemPrompt = prompt
                .replace("{listener}", botA.getName())
                .replace("{selectedTarget}", botB.getName())
                .replace("{personality}", profileA.personality())
                .replace("{speaking_style}", profileA.speakingStyle())
                .replace("{description}", profileA.currentSituation())
                .replace("{default_language}", lang)
                .replace("{language_hint}", aiConfig.getLanguageHint(lang));

        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, Collections.emptyList(), "hey " + botB.getName())
                .thenAccept(response -> {
                    if (response == null || response.isBlank()) return;

                    String sanitized = sanitizeOutput(response, false);
                    if (sanitized == null || sanitized.isBlank()) return;

                    long delayTicks = aiConfig.getTypingDelayTicks();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!botA.isOnline() || !botB.isOnline()) return;

                        if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                            String customFormat = aiConfig.getChatFormat()
                                    .replace("%fakeplayer_name%", botA.getName())
                                    .replace("%fakeplayer_message%", sanitized)
                                    .replace("{bot}", botA.getName())
                                    .replace("{name}", botA.getName())
                                    .replace("{message}", sanitized);
                            plugin.getBridge().broadcastNMSChat(botA, ColorUtils.colorize(customFormat));
                        } else {
                            botA.chat(sanitized);
                        }

                        DebugLogger.log(plugin.getLogger(), "AiToAi: %s initiated to %s: '%s'", botA.getName(), botB.getName(), sanitized);

                        // Bot B phản hồi lại Bot A
                        if (ThreadLocalRandom.current().nextDouble() <= aiConfig.getAiToAiResponseChance()) {
                            long responseDelay = delayTicks + ThreadLocalRandom.current().nextLong(30L, 80L);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                processAiToAiResponseAsync(botB, botA, sanitized);
                            }, responseDelay);
                        }
                    }, delayTicks);
                });
    }

    public void processAiToAiResponseAsync(Player botB, Player botA, String incomingMsg) {
        if (botA == null || botB == null || !botA.isOnline() || !botB.isOnline()) return;

        var profileB = personalityManager.getProfile(botB.getName());
        String lang = detectLanguage(incomingMsg);

        String systemPrompt = aiConfig.getSystemRule()
                .replace("{listener}", botB.getName())
                .replace("{sender}", botA.getName())
                .replace("{personality}", profileB.personality())
                .replace("{speaking_style}", profileB.speakingStyle())
                .replace("{description}", profileB.currentSituation())
                .replace("{language_hint}", aiConfig.getLanguageHint(lang))
                .replace("{default_language}", lang);

        UUID botBUuid = botB.getUniqueId();
        List<AiProviderService.ChatMessage> history = getValidHistory(botBUuid);

        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, history, incomingMsg)
                .thenAccept(response -> {
                    if (response == null || response.isBlank()) return;

                    String sanitized = sanitizeOutput(response, false);
                    if (sanitized == null || sanitized.isBlank()) return;

                    saveToMemory(botBUuid, incomingMsg, sanitized);

                    long delayTicks = aiConfig.getTypingDelayTicks();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!botA.isOnline() || !botB.isOnline()) return;

                        if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                            String customFormat = aiConfig.getChatFormat()
                                    .replace("%fakeplayer_name%", botB.getName())
                                    .replace("%fakeplayer_message%", sanitized)
                                    .replace("{bot}", botB.getName())
                                    .replace("{name}", botB.getName())
                                    .replace("{message}", sanitized);
                            plugin.getBridge().broadcastNMSChat(botB, ColorUtils.colorize(customFormat));
                        } else {
                            botB.chat(sanitized);
                        }

                        DebugLogger.log(plugin.getLogger(), "AiToAi: %s replied to %s: '%s'", botB.getName(), botA.getName(), sanitized);
                    }, delayTicks);
                });
    }

    // --- RAM Memory Helper ---

    private List<AiProviderService.ChatMessage> getValidHistory(UUID playerUuid) {
        List<AiProviderService.ChatMessage> history = conversationMemory.get(playerUuid);
        if (history == null || history.isEmpty()) return Collections.emptyList();

        long now = System.currentTimeMillis();
        long expiryMs = aiConfig.getConversationExpiryMs();

        history.removeIf(msg -> (now - msg.timestamp()) > expiryMs);

        int maxMessages = aiConfig.getMaxResponsesPerSession() * 2;
        if (history.size() > maxMessages) {
            return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
        }
        return new ArrayList<>(history);
    }

    private void saveToMemory(UUID playerUuid, String userMsg, String aiReply) {
        long now = System.currentTimeMillis();
        List<AiProviderService.ChatMessage> history = conversationMemory.computeIfAbsent(playerUuid, k -> Collections.synchronizedList(new ArrayList<>()));

        history.add(new AiProviderService.ChatMessage("user", userMsg, now));
        history.add(new AiProviderService.ChatMessage("assistant", aiReply, now));

        int maxMessages = aiConfig.getMaxResponsesPerSession() * 2;
        while (history.size() > maxMessages) {
            history.remove(0);
        }
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
        Bukkit.getScheduler().runTask(plugin, () -> {
            String msg = aiConfig.getTimeoutMessage();
            if (msg == null || msg.isEmpty()) return;

            boolean isCustom = "custom".equalsIgnoreCase(aiConfig.getChatFormatMethod());
            boolean isHelp = bot.getName().equalsIgnoreCase(aiConfig.getAiHelpBotName());

            if (isPrivateMsg) {
                if (isCustom) {
                    String formattedPm = aiConfig.getPmIncomingFormat()
                            .replace("{bot}", bot.getName())
                            .replace("{message}", msg);
                    sender.sendMessage(ColorUtils.colorize(formattedPm));
                } else {
                    bot.chat("/msg " + sender.getName() + " " + msg);
                }
            } else {
                if (isHelp && aiConfig.getAiHelpResponseFormat() != null && !aiConfig.getAiHelpResponseFormat().isBlank()) {
                    String formattedHelp = aiConfig.getAiHelpResponseFormat()
                            .replace("{bot}", bot.getName())
                            .replace("{name}", bot.getName())
                            .replace("{message}", msg);
                    plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(formattedHelp));
                } else if (isCustom) {
                    String customFormat = aiConfig.getChatFormat()
                            .replace("%fakeplayer_name%", bot.getName())
                            .replace("%fakeplayer_message%", msg)
                            .replace("{bot}", bot.getName())
                            .replace("{name}", bot.getName())
                            .replace("{message}", msg);
                    plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                } else {
                    bot.chat(msg);
                }
            }
        });
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