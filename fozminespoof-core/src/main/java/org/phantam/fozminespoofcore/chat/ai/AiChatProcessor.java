package org.phantam.fozminespoofcore.chat.ai;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

/**
 * Core processor for AI-driven chat interactions between players and simulated bots.
 * Features advanced cross-lingual adaptation and unaccented language detection.
 */
public class AiChatProcessor {

    private final FozmineSpoofCore plugin;
    private final AiConfig aiConfig;
    private final AiPersonalityManager personalityManager;
    private final AiProviderService providerService;

    // Rate limiting tracking: Player UUID -> Queue of request epoch timestamps
    private final Map<UUID, Queue<Long>> rateLimits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRateLimitWarnTime = new ConcurrentHashMap<>();

    // Millisecond-precision cooldowns
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> botSenderCooldowns = new ConcurrentHashMap<>();

    // Active multi-turn conversation sessions: Key format "PlayerUUID:botname"
    private final Map<String, ConversationSession> activeSessions = new ConcurrentHashMap<>();

    // Common non-accented Vietnamese keywords/particles for precise language detection
    private static final Set<String> VIETNAMESE_UNACCENTED_KEYWORDS = Set.of(
            "nap", "the", "sao", "khong", "dc", "ko", "hok", "lam", "o", "dau", "cho", "hoi",
            "huong", "dan", "sever", "sv", "ad", "admin", "giup", "ban", "minh", "tui", "trai",
            "ac", "quy", "mua", "tien", "xu", "lenh", "lag", "the", "nao", "nhe", "nha", "nhi",
            "xin", "chao", "di", "cave", "khoang", "cuop", "farm", "do", "kham", "pha"
    );

    public record ConversationSession(
            UUID playerUuid,
            String botName,
            long startTime,
            long lastInteractionTime,
            int turnsCompleted,
            List<AiProviderService.ChatMessage> history
    ) {
        public ConversationSession withNewTurn(String userMsg, String botReply, long timestamp) {
            List<AiProviderService.ChatMessage> updated = new ArrayList<>(this.history);
            updated.add(new AiProviderService.ChatMessage("user", userMsg, timestamp));
            updated.add(new AiProviderService.ChatMessage("assistant", botReply, timestamp));
            return new ConversationSession(playerUuid, botName, startTime, timestamp, turnsCompleted + 1, updated);
        }
    }

    public AiChatProcessor(FozmineSpoofCore plugin, AiConfig aiConfig, AiPersonalityManager personalityManager) {
        this.plugin = plugin;
        this.aiConfig = aiConfig;
        this.personalityManager = personalityManager;
        this.providerService = new AiProviderService(plugin.getLogger());

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredSessionsAndLimits, 1200L, 1200L);
    }

    public void processPlayerToAiChatAsync(Player sender, Player bot, String rawMessage, boolean isHelpMode, boolean isPrivateMsg) {
        if (sender == null || bot == null || !sender.isOnline() || !bot.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        String botNameLower = bot.getName().toLowerCase(Locale.ROOT);
        UUID senderUuid = sender.getUniqueId();
        String sessionKey = senderUuid + ":" + botNameLower;

        DebugLogger.log(plugin.getLogger(), "AiChatProcessor: Evaluating chat from %s to %s (PM=%b, Help=%b)",
                sender.getName(), bot.getName(), isPrivateMsg, isHelpMode);

        // 1. Operating status check
        if (!aiConfig.isEnabled() || !aiConfig.isInActiveHours()) {
            return;
        }

        // 2. Character length limit
        if (rawMessage.length() > aiConfig.getMaxInputLength()) {
            return;
        }

        // 3. Blacklist verification
        if (isInputBlocked(rawMessage, sender)) {
            return;
        }

        // 4. Rate-limit verification
        if (!checkRateLimit(sender)) {
            handleRateLimit(sender);
            return;
        }

        // 5. Spatial boundaries
        if (!isHelpMode) {
            if (aiConfig.isAnswerInSameWorld() && !sender.getWorld().equals(bot.getWorld())) {
                return;
            }

            int maxDistance = aiConfig.getMaxHearingDistance();
            if (maxDistance > 0) {
                if (!sender.getWorld().equals(bot.getWorld())) {
                    return;
                }
                Location senderLoc = sender.getLocation();
                Location botLoc = bot.getLocation();
                if (senderLoc.distanceSquared(botLoc) > ((long) maxDistance * maxDistance)) {
                    return;
                }
            }
        }

        // 6. Cooldown checks
        if (!isHelpMode) {
            Long botNextAvailable = botSenderCooldowns.get(botNameLower);
            if (botNextAvailable != null && now < botNextAvailable) {
                return;
            }

            Long playerNextAvailable = playerCooldowns.get(senderUuid);
            if (playerNextAvailable != null && now < playerNextAvailable) {
                return;
            }
        }

        // 7. Conversation session management
        ConversationSession session = activeSessions.get(sessionKey);
        boolean isExistingSession = (session != null && (now - session.lastInteractionTime()) <= aiConfig.getConversationExpiryMs());

        if (isExistingSession) {
            if (session.turnsCompleted() >= aiConfig.getMaxResponsesPerSession()) {
                activeSessions.remove(sessionKey);
                return;
            }
        } else {
            if (!isHelpMode && !isPrivateMsg) {
                if (ThreadLocalRandom.current().nextDouble() > aiConfig.getPlayerToAiChance()) {
                    return;
                }
            }
            session = new ConversationSession(senderUuid, botNameLower, now, now, 0, new ArrayList<>());
            activeSessions.put(sessionKey, session);
        }

        if (!isHelpMode) {
            playerCooldowns.put(senderUuid, now + aiConfig.getCooldownReceiverMs());
            botSenderCooldowns.put(botNameLower, now + aiConfig.getCooldownSenderMs());
        }

        // 8. Clean up user query by removing bot tag prefix for purer LLM intent understanding
        String cleanedUserPrompt = cleanBotMention(rawMessage, bot.getName(), aiConfig.getAiHelpTagPrefix());

        // 9. Multi-language detection & Dynamic Prompt Construction
        String detectedLang = detectLanguage(cleanedUserPrompt);
        String langHint = aiConfig.getLanguageHint(detectedLang);
        var profile = personalityManager.getProfile(bot.getName());

        String systemPrompt;
        if (isHelpMode) {
            systemPrompt = aiConfig.getAiHelpServerPrompt()
                    .replace("{listener}", bot.getName())
                    .replace("{sender}", sender.getName())
                    .replace("{server.knowledge-base}", aiConfig.getFormattedServerKnowledgeBase())
                    .replace("{language_hint}", langHint)
                    .replace("{default_language}", detectedLang);
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

        List<AiProviderService.ChatMessage> history = getCompressedHistory(session);
        final ConversationSession currentSession = session;

        // 10. Fetch completion from AI Provider
        providerService.fetchAiResponseAsync(aiConfig, systemPrompt, history, cleanedUserPrompt)
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

                    activeSessions.put(sessionKey, currentSession.withNewTurn(cleanedUserPrompt, sanitized, System.currentTimeMillis()));

                    long delayTicks = calculateTypingDelayTicks(sanitized);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!sender.isOnline() || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                            return;
                        }

                        if (isPrivateMsg) {
                            if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                                String formattedPm = aiConfig.getPmIncomingFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("{message}", sanitized)
                                        .replace("%fakeplayer_message%", sanitized);
                                sender.sendMessage(ColorUtils.colorize(formattedPm));
                            } else {
                                bot.chat("/msg " + sender.getName() + " " + sanitized);
                            }
                            DebugLogger.log(plugin.getLogger(), "AiChatProcessor: Dispatched PM reply from %s to %s", bot.getName(), sender.getName());
                        } else {
                            if (isHelpMode && aiConfig.getAiHelpResponseFormat() != null && !aiConfig.getAiHelpResponseFormat().isBlank()) {
                                String formattedHelp = aiConfig.getAiHelpResponseFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("{name}", bot.getName())
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("{message}", sanitized)
                                        .replace("%fakeplayer_message%", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(formattedHelp));
                            } else if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                                String customFormat = aiConfig.getChatFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("{name}", bot.getName())
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("{message}", sanitized)
                                        .replace("%fakeplayer_message%", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                            } else if (plugin.getConfigManager().isMessageFormatEnable()) {
                                String customFormat = plugin.getConfigManager().getChatFormat()
                                        .replace("{bot}", bot.getName())
                                        .replace("{name}", bot.getName())
                                        .replace("%fakeplayer_name%", bot.getName())
                                        .replace("{message}", sanitized)
                                        .replace("%fakeplayer_message%", sanitized);
                                plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                            } else {
                                bot.chat(sanitized);
                            }
                            DebugLogger.log(plugin.getLogger(), "AiChatProcessor: Dispatched public chat response from %s", bot.getName());
                        }
                    }, delayTicks);
                })
                .exceptionally(ex -> {
                    DebugLogger.log(plugin.getLogger(), "AiChatProcessor: AI completion failed for %s: %s", bot.getName(), ex.getMessage());
                    sendTimeoutMessage(sender, bot, isPrivateMsg);
                    return null;
                });
    }

    /**
     * Advanced language detection supporting accented and unaccented Vietnamese keywords.
     */
    private String detectLanguage(String text) {
        String mode = aiConfig.getLangMode();
        if (!"auto".equalsIgnoreCase(mode) || text == null || text.isBlank()) {
            return aiConfig.getDefaultLanguage();
        }

        // 1. Check for standard Vietnamese diacritics
        if (text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ].*")) {
            return "vi";
        }

        // 2. Check for common unaccented Vietnamese keywords/slang
        String cleaned = StringUtils.cleanMessage(text);
        String[] words = cleaned.split("\\s+");
        int vietnameseMatchCount = 0;

        for (String word : words) {
            if (VIETNAMESE_UNACCENTED_KEYWORDS.contains(word.toLowerCase(Locale.ROOT))) {
                vietnameseMatchCount++;
            }
        }

        if (vietnameseMatchCount >= 1) {
            return "vi";
        }

        return aiConfig.getDefaultLanguage();
    }

    /**
     * Cleans bot tag mentions from the input query.
     */
    private String cleanBotMention(String message, String botName, String tagPrefix) {
        if (message == null) return "";
        String prefix = (tagPrefix != null) ? tagPrefix : "@";
        String cleaned = message.replaceAll("(?i)" + Pattern.quote(prefix + botName), "");
        cleaned = cleaned.replaceAll("(?i)\\b" + Pattern.quote(botName) + "\\b", "");
        return cleaned.trim().isEmpty() ? message : cleaned.trim();
    }

    private List<AiProviderService.ChatMessage> getCompressedHistory(ConversationSession session) {
        if (session == null || session.history().isEmpty()) {
            return Collections.emptyList();
        }
        int maxHistoryMessages = aiConfig.getMaxResponsesPerSession() * 2;
        List<AiProviderService.ChatMessage> fullHistory = session.history();
        if (fullHistory.size() <= maxHistoryMessages) {
            return new ArrayList<>(fullHistory);
        }
        return new ArrayList<>(fullHistory.subList(fullHistory.size() - maxHistoryMessages, fullHistory.size()));
    }

    private long calculateTypingDelayTicks(String text) {
        long baseTicks = aiConfig.getTypingDelayTicks();
        long lengthBonusTicks = Math.min(40L, (long) (text.length() * 0.4));
        return Math.max(10L, baseTicks + lengthBonusTicks);
    }

    private boolean checkRateLimit(Player sender) {
        long now = System.currentTimeMillis();
        UUID uuid = sender.getUniqueId();
        Queue<Long> timestamps = rateLimits.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>());
        timestamps.removeIf(t -> now - t > 60_000L);

        if (timestamps.size() >= aiConfig.getRateLimitMaxPerMin()) {
            return false;
        }
        timestamps.add(now);
        return true;
    }

    private void handleRateLimit(Player sender) {
        long now = System.currentTimeMillis();
        UUID uuid = sender.getUniqueId();

        Long lastWarn = lastRateLimitWarnTime.get(uuid);
        if (lastWarn != null && now - lastWarn < 5000L) {
            return;
        }
        lastRateLimitWarnTime.put(uuid, now);

        if (aiConfig.isRateLimitWarnEnabled()) {
            String msg = aiConfig.getRateLimitWarnMessage().replace("{time}", "60");
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
        if (response == null) return null;
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

        if (!aiConfig.isOverrideBySpeakingStyle()) {
            if (aiConfig.isForceLowercase()) {
                result = result.toLowerCase(Locale.ROOT);
            }
            if (aiConfig.isForceNoPunctuation()) {
                result = result.replaceAll("[.!?,;:]", "");
            }
        }

        return result.trim();
    }

    private void sendTimeoutMessage(Player sender, Player bot, boolean isPrivateMsg) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String msg = aiConfig.getTimeoutMessage();
            if (msg == null || msg.isBlank()) return;

            if (isPrivateMsg) {
                if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                    String formattedPm = aiConfig.getPmIncomingFormat()
                            .replace("{bot}", bot.getName())
                            .replace("%fakeplayer_name%", bot.getName())
                            .replace("{message}", msg)
                            .replace("%fakeplayer_message%", msg);
                    sender.sendMessage(ColorUtils.colorize(formattedPm));
                } else {
                    bot.chat("/msg " + sender.getName() + " " + msg);
                }
            } else {
                if ("custom".equalsIgnoreCase(aiConfig.getChatFormatMethod())) {
                    String customFormat = aiConfig.getChatFormat()
                            .replace("{bot}", bot.getName())
                            .replace("{name}", bot.getName())
                            .replace("%fakeplayer_name%", bot.getName())
                            .replace("{message}", msg)
                            .replace("%fakeplayer_message%", msg);
                    plugin.getBridge().broadcastNMSChat(bot, ColorUtils.colorize(customFormat));
                } else {
                    bot.chat(msg);
                }
            }
        });
    }

    private void cleanExpiredSessionsAndLimits() {
        long now = System.currentTimeMillis();
        long expiryMs = aiConfig.getConversationExpiryMs();

        activeSessions.entrySet().removeIf(entry -> (now - entry.getValue().lastInteractionTime()) > expiryMs);
        playerCooldowns.entrySet().removeIf(entry -> now > entry.getValue());
        botSenderCooldowns.entrySet().removeIf(entry -> now > entry.getValue());
        lastRateLimitWarnTime.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
    }
}