package org.phantam.fozminespoofcore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.BotChatProcessor;
import org.phantam.fozminespoofcore.chat.BotSelector;
import org.phantam.fozminespoofcore.chat.TranslatorService;
import org.phantam.fozminespoofcore.config.InteractionConfig;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens to player chat and triggers interactive bot responses based on keyword configuration.
 * <p>
 * This listener uses {@link InteractionConfig} to match triggers, enforce cooldowns,
 * and select random replies. Supports mentioning bots directly for targeted responses,
 * and automatically translates replies to the player's language if configured.
 * </p>
 *
 * @author Phantam
 * @version 2.1.0
 */
public class InteractiveChatListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final TranslatorService translator;

    /**
     * Global cooldown per interaction key (server-wide).
     */
    private final Map<String, Long> globalCooldowns = new ConcurrentHashMap<>();

    /**
     * Per-player cooldown map: Player UUID → (Interaction Key → Last Trigger Timestamp).
     */
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    /**
     * Per-player reply cache to avoid repetition (Player UUID → (reply -> timestamp)).
     */
    private final Map<UUID, Map<String, Long>> playerReplyCache = new ConcurrentHashMap<>();

    public InteractiveChatListener(FozmineSpoofCore plugin, BotSelector botSelector,
                                   BotChatProcessor chatProcessor, TranslatorService translator) {
        this.plugin = plugin;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
        this.translator = translator;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        var chatConfig = plugin.getConfigManager().getChatConfig();
        if (chatConfig == null || !chatConfig.isEnabled() || "ai".equalsIgnoreCase(chatConfig.getMode())) {
            return;
        }

        var interactiveConfig = plugin.getInteractiveMessageConfig();
        if (interactiveConfig == null || !interactiveConfig.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        String name = player.getName();

        if (player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(name)) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.isBlank()) return;

        String cleanedMessage = StringUtils.cleanMessage(rawMessage);
        if (cleanedMessage.isBlank()) return;

        DebugLogger.log(plugin.getLogger(),
                "InteractiveChat: from %s: '%s' -> cleaned '%s'",
                name, rawMessage, cleanedMessage);

        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

        // Find mentioned bot (with fuzzy matching)
        Player targetedBot = findMentionedBot(rawMessage, cleanedMessage);
        DebugLogger.log(plugin.getLogger(),
                "InteractiveChat: targetedBot = %s",
                targetedBot != null ? targetedBot.getName() : "null");

        // Get translation settings from chat config
        String targetLang = chatConfig.getTranslationTarget();
        String provider = chatConfig.getTranslationProvider();
        String apiKey = chatConfig.getTranslationApiKey();

        boolean translateEnabled = !"none".equalsIgnoreCase(targetLang)
                && !"none".equalsIgnoreCase(provider);

        for (InteractionConfig interaction : interactiveConfig.getInteractions()) {
            String interactionKey = interaction.getKey();

            DebugLogger.log(plugin.getLogger(),
                    "InteractiveChat: testing interaction '%s'", interactionKey);

            if (!interaction.isInActiveHours(zoneId)) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: %s not in active hours", interactionKey);
                continue;
            }

            String testMessage = cleanedMessage;
            if (targetedBot != null) {
                String cleanBotName = StringUtils.cleanMessage(targetedBot.getName());
                testMessage = testMessage.replace(cleanBotName, "[bot]");
            }

            if (!interaction.matches(testMessage)) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: %s did NOT match", interactionKey);
                continue;
            }

            DebugLogger.log(plugin.getLogger(),
                    "InteractiveChat: %s MATCHED!", interactionKey);

            // --- Cooldowns ---
            long lastGlobal = globalCooldowns.getOrDefault(interactionKey, 0L);
            if (now - lastGlobal < interaction.getGlobalCooldownMs()) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: global cooldown active for %s", interactionKey);
                continue;
            }

            Map<String, Long> pCooldowns = playerCooldowns.computeIfAbsent(playerUuid,
                    k -> new ConcurrentHashMap<>());
            long lastPlayer = pCooldowns.getOrDefault(interactionKey, 0L);
            if (now - lastPlayer < interaction.getPerPlayerCooldownMs()) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: per-player cooldown active for %s", interactionKey);
                continue;
            }

            // --- Chance roll ---
            if (!interaction.rollsChance()) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: chance roll FAILED for %s", interactionKey);
                continue;
            }

            // Update cooldowns
            globalCooldowns.put(interactionKey, now);
            pCooldowns.put(interactionKey, now);

            List<String> replies = interaction.getReplies();
            if (replies == null || replies.isEmpty()) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: no replies for %s", interactionKey);
                continue;
            }

            // Select bots
            List<Player> speakingBots = new ArrayList<>();
            if (targetedBot != null) {
                speakingBots.add(targetedBot);
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: responding with targeted bot %s", targetedBot.getName());
            } else {
                List<Player> selected = botSelector.selectRandomBots(interaction.getMaxBurst());
                speakingBots.addAll(selected);
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: selected %d random bots for %s",
                        selected.size(), interactionKey);
            }

            if (speakingBots.isEmpty()) {
                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: no bots available for %s, continuing", interactionKey);
                continue;
            }

            long baseStaggerTicks = 0L;

            for (Player bot : speakingBots) {
                String reply = selectDeduplicatedReply(playerUuid, replies, now);
                if (reply == null) {
                    DebugLogger.log(plugin.getLogger(),
                            "InteractiveChat: failed to select deduplicated reply for %s", interactionKey);
                    continue;
                }

                // --- Translate reply if enabled ---
                String translatedReply = reply;
                if (translateEnabled) {
                    translatedReply = translateReply(reply, targetLang, provider, apiKey);
                    DebugLogger.log(plugin.getLogger(),
                            "InteractiveChat: translated reply: '%s' -> '%s'", reply, translatedReply);
                }

                // Replace placeholders with the player's name
                String formattedReply = translatedReply.replace("[name]", name).replace("%name%", name);

                long totalDelayTicks = interaction.getTypingDelayTicks(formattedReply) + baseStaggerTicks;

                DebugLogger.log(plugin.getLogger(),
                        "InteractiveChat: scheduling %s to say '%s' in %d ticks",
                        bot.getName(), formattedReply, totalDelayTicks);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        DebugLogger.log(plugin.getLogger(),
                                "InteractiveChat: player %s left, cancelling", name);
                        return;
                    }
                    if (!bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                        DebugLogger.log(plugin.getLogger(),
                                "InteractiveChat: bot %s offline, cancelling", bot.getName());
                        return;
                    }
                    chatProcessor.processChatAsync(bot, formattedReply, plugin.getConfigManager().getChatConfig());
                }, totalDelayTicks);

                baseStaggerTicks += ThreadLocalRandom.current().nextLong(5L, 15L);
            }

            // Stop after first matched group
            break;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerCooldowns.remove(uuid);
        playerReplyCache.remove(uuid);
    }

    /**
     * Finds a bot mentioned in the message with fuzzy (Levenshtein) matching.
     */
    private Player findMentionedBot(String rawMessage, String cleanedMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return null;

        String lowerRaw = rawMessage.toLowerCase(Locale.ROOT);
        String cleanedLower = cleanedMessage.toLowerCase(Locale.ROOT);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.getFakePlayerManager().isBotOnline(online.getName())) continue;

            String botName = online.getName();
            String lowerBot = botName.toLowerCase(Locale.ROOT);

            // Exact match
            if (lowerRaw.contains(lowerBot)) return online;

            // Cleaned match
            String cleanedBot = StringUtils.cleanMessage(botName);
            if (cleanedLower.contains(cleanedBot)) return online;

            // Fuzzy (Levenshtein) match
            String[] words = cleanedLower.split(" ");
            for (String word : words) {
                if (word.length() < 3) continue;
                int dist = StringUtils.levenshteinDistance(word, cleanedBot);
                double maxLen = Math.max(word.length(), cleanedBot.length());
                double similarity = 1.0 - (dist / maxLen);
                if (similarity >= 0.85) {
                    return online;
                }
            }
        }
        return null;
    }

    /**
     * Selects a reply not used recently for this specific player.
     */
    private String selectDeduplicatedReply(UUID playerUuid, List<String> replies, long now) {
        if (replies.isEmpty()) return null;

        Map<String, Long> cache = playerReplyCache.computeIfAbsent(playerUuid,
                k -> new ConcurrentHashMap<>());

        // Remove entries older than 90 seconds
        cache.entrySet().removeIf(e -> now - e.getValue() > 90_000L);

        List<String> fresh = new ArrayList<>();
        for (String r : replies) {
            if (!cache.containsKey(r.toLowerCase())) {
                fresh.add(r);
            }
        }

        String chosen = fresh.isEmpty()
                ? replies.get(ThreadLocalRandom.current().nextInt(replies.size()))
                : fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));

        cache.put(chosen.toLowerCase(), now);
        return chosen;
    }

    /**
     * Translates a reply to the target language while preserving placeholder tokens.
     * <p>
     * Placeholders {@code [name]} and {@code %name%} are temporarily replaced with
     * a token before translation to avoid being altered, then restored after translation.
     * </p>
     *
     * @param reply      the original reply (may contain placeholders)
     * @param targetLang the target language code (e.g., "en", "vi")
     * @param provider   the translation provider
     * @param apiKey     the API key for the provider
     * @return the translated reply with placeholders restored, or the original on failure
     */
    private String translateReply(String reply, String targetLang, String provider, String apiKey) {
        if (reply == null || reply.isEmpty()) return reply;
        if (targetLang == null || targetLang.equalsIgnoreCase("none")) return reply;
        if (provider == null || provider.equalsIgnoreCase("none")) return reply;

        // Replace placeholders with unique tokens
        String token = "__NAME__";
        String withToken = reply.replace("[name]", token).replace("%name%", token);

        String translated = translator.translate(withToken, targetLang, provider, apiKey);
        if (translated == null || translated.isEmpty()) {
            return reply;
        }

        // Restore placeholders
        return translated.replace(token, "[name]");
    }
}