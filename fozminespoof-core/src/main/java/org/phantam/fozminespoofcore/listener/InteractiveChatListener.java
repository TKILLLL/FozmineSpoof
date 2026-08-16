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
import java.util.regex.Pattern;

public class InteractiveChatListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;
    private final TranslatorService translator;

    private final Map<String, Long> globalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
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

        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

        // Tìm bot được nhắc đến bằng Word Boundary
        Player targetedBot = findMentionedBot(rawMessage);

        String targetLang = chatConfig.getTranslationTarget();
        String provider = chatConfig.getTranslationProvider();
        String apiKey = chatConfig.getTranslationApiKey();

        boolean translateEnabled = !"none".equalsIgnoreCase(targetLang) && !"none".equalsIgnoreCase(provider);

        for (InteractionConfig interaction : interactiveConfig.getInteractions()) {
            String interactionKey = interaction.getKey();

            if (!interaction.isInActiveHours(zoneId)) {
                continue;
            }

            // Chuẩn bị chuỗi test: thay tên bot bằng Token định danh
            String testMessage = cleanedMessage;
            if (targetedBot != null) {
                String cleanBotName = StringUtils.cleanMessage(targetedBot.getName());
                testMessage = Pattern.compile("\\b" + Pattern.quote(cleanBotName) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(testMessage)
                        .replaceAll(InteractionConfig.BOT_TOKEN);
            }

            if (!interaction.matches(testMessage)) {
                continue;
            }

            // Cooldowns
            long lastGlobal = globalCooldowns.getOrDefault(interactionKey, 0L);
            if (now - lastGlobal < interaction.getGlobalCooldownMs()) {
                continue;
            }

            Map<String, Long> pCooldowns = playerCooldowns.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
            long lastPlayer = pCooldowns.getOrDefault(interactionKey, 0L);
            if (now - lastPlayer < interaction.getPerPlayerCooldownMs()) {
                continue;
            }

            // Probability Chance Roll
            if (!interaction.rollsChance()) {
                continue;
            }

            globalCooldowns.put(interactionKey, now);
            pCooldowns.put(interactionKey, now);

            List<String> replies = interaction.getReplies();
            if (replies == null || replies.isEmpty()) {
                continue;
            }

            List<Player> speakingBots = new ArrayList<>();
            if (targetedBot != null) {
                speakingBots.add(targetedBot);
            } else {
                List<Player> selected = botSelector.selectRandomBots(interaction.getMaxBurst());
                speakingBots.addAll(selected);
            }

            if (speakingBots.isEmpty()) {
                continue;
            }

            long baseStaggerTicks = 0L;

            for (Player bot : speakingBots) {
                String reply = selectDeduplicatedReply(playerUuid, replies, now);
                if (reply == null) continue;

                String translatedReply = reply;
                if (translateEnabled) {
                    translatedReply = translateReply(reply, targetLang, provider, apiKey);
                }

                String formattedReply = translatedReply
                        .replace("[name]", name)
                        .replace("%name%", name)
                        .replace("[bot]", bot.getName())
                        .replace("%bot%", bot.getName());

                long totalDelayTicks = interaction.getTypingDelayTicks(formattedReply) + baseStaggerTicks;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (!bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) return;
                    chatProcessor.processChatAsync(bot, formattedReply, plugin.getConfigManager().getChatConfig());
                }, totalDelayTicks);

                baseStaggerTicks += ThreadLocalRandom.current().nextLong(10L, 25L);
            }

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
     * Tìm bot được nhắc đến chính xác 100% bằng Word Boundary (VD: "@Steve", "Steve:", "Steve")
     */
    private Player findMentionedBot(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return null;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.getFakePlayerManager().isBotOnline(online.getName())) continue;

            String botName = online.getName();

            // 1. Kiểm tra tag trực tiếp: @BotName
            if (Pattern.compile("(?:^|\\s+)@" + Pattern.quote(botName) + "(?:$|\\s+|[.,:!?])", Pattern.CASE_INSENSITIVE).matcher(rawMessage).find()) {
                return online;
            }

            // 2. Kiểm tra tên bot đứng độc lập dưới dạng một từ riêng biệt
            if (Pattern.compile("\\b" + Pattern.quote(botName) + "\\b", Pattern.CASE_INSENSITIVE).matcher(rawMessage).find()) {
                return online;
            }
        }
        return null;
    }

    private String selectDeduplicatedReply(UUID playerUuid, List<String> replies, long now) {
        if (replies.isEmpty()) return null;

        Map<String, Long> cache = playerReplyCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
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

    private String translateReply(String reply, String targetLang, String provider, String apiKey) {
        if (reply == null || reply.isEmpty()) return reply;
        if (targetLang == null || targetLang.equalsIgnoreCase("none")) return reply;
        if (provider == null || provider.equalsIgnoreCase("none")) return reply;

        String token = "__NAME__";
        String withToken = reply.replace("[name]", token).replace("%name%", token);

        String translated = translator.translate(withToken, targetLang, provider, apiKey);
        if (translated == null || translated.isEmpty()) {
            return reply;
        }

        return translated.replace(token, "[name]");
    }
}