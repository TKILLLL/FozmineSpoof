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
import org.phantam.fozminespoofcore.config.InteractionConfig;
import org.phantam.fozminespoofcore.utils.StringUtils;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class InteractiveChatListener implements Listener {

    private final FozmineSpoofCore plugin;
    private final BotSelector botSelector;
    private final BotChatProcessor chatProcessor;

    // Cooldown Maps
    private final Map<String, Long> globalCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();

    // Lọc trùng lặp câu thoại (Deduplication)
    private final Map<String, Long> recentReplies = new ConcurrentHashMap<>();

    public InteractiveChatListener(FozmineSpoofCore plugin, BotSelector botSelector, BotChatProcessor chatProcessor) {
        this.plugin = plugin;
        this.botSelector = botSelector;
        this.chatProcessor = chatProcessor;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        String name = player.getName();

        // 1. CHỐNG VÒNG LẶP VÔ TẬN: Bỏ qua ngay lập tức nếu người chat là Bot
        if (player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(name)) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.isBlank()) return;

        // Chuẩn hóa tin nhắn của người chơi
        String cleanedMessage = StringUtils.cleanMessage(rawMessage);
        if (cleanedMessage.isBlank()) return;

        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

        if (plugin.getInteractiveMessageConfig() == null) return;

        for (InteractionConfig interaction : plugin.getInteractiveMessageConfig().getInteractions()) {
            if (!interaction.isInActiveHours(zoneId)) continue;
            if (!interaction.matches(cleanedMessage)) continue;

            // Kiểm tra Cooldowns
            String interactionKey = interaction.getKey();

            long lastGlobal = globalCooldowns.getOrDefault(interactionKey, 0L);
            if (now - lastGlobal < interaction.getGlobalCooldownMs()) {
                DebugLogger.log(plugin.getLogger(), "InteractiveChat: global cooldown active for %s", interactionKey);
                continue;
            }

            String playerCdKey = interactionKey + ":" + playerUuid;
            long lastPlayer = playerCooldowns.getOrDefault(playerCdKey, 0L);
            if (now - lastPlayer < interaction.getPerPlayerCooldownMs()) {
                DebugLogger.log(plugin.getLogger(), "InteractiveChat: per-player cooldown active for %s on %s", interactionKey, name);
                continue;
            }

            // Tỷ lệ phản hồi (Chance roll)
            if (!interaction.rollsChance()) {
                DebugLogger.log(plugin.getLogger(), "InteractiveChat: chance roll failed for %s", interactionKey);
                continue;
            }

            // Đã tìm thấy từ khóa hợp lệ -> Cập nhật Cooldowns
            globalCooldowns.put(interactionKey, now);
            playerCooldowns.put(playerCdKey, now);

            List<String> replies = interaction.getReplies();
            if (replies == null || replies.isEmpty()) break;

            // Chọn danh sách Bot phản hồi
            List<Player> speakingBots = botSelector.selectRandomBots(interaction.getMaxBurst());
            if (speakingBots.isEmpty()) break;

            long accumDelayTicks = 0L;

            for (Player bot : speakingBots) {
                // Chọn câu trả lời không trùng lặp gần đây
                String reply = selectDeduplicatedReply(replies, now);
                if (reply == null) continue;

                String formattedReply = reply.replace("[name]", name).replace("%name%", name);
                long scheduledDelay = accumDelayTicks + interaction.getRandomDelayTicks();

                // Lên lịch phản hồi trên Main Thread với cơ chế Chống Ghost Chat & Null Pointer
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // CHỐNG GHOST CHAT: Nếu người chơi thật đã thoát server -> Hủy câu trả lời
                    if (Bukkit.getPlayerExact(name) == null || !player.isOnline()) {
                        DebugLogger.log(plugin.getLogger(), "InteractiveChat: real player %s left, cancelling bot reply", name);
                        return;
                    }

                    // CHỐNG NULL POINTER: Nếu Bot đã bị despawn -> Hủy câu trả lời
                    if (bot == null || !bot.isOnline() || !plugin.getFakePlayerManager().isBotOnline(bot.getName())) {
                        DebugLogger.log(plugin.getLogger(), "InteractiveChat: bot disconnected before replying", name);
                        return;
                    }

                    chatProcessor.processChatAsync(bot, formattedReply, plugin.getConfigManager().getChatConfig());
                }, scheduledDelay);

                accumDelayTicks += interaction.getRandomDelayTicks();
            }

            break; // Dừng lại sau nhóm từ khóa khớp đầu tiên
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // CHỐNG RÒ RỈ BỘ NHỚ: Xóa bộ nhớ Cooldown của người chơi khi họ thoát game
        UUID playerUuid = event.getPlayer().getUniqueId();
        playerCooldowns.keySet().removeIf(key -> key.endsWith(":" + playerUuid));
    }

    private String selectDeduplicatedReply(List<String> replies, long now) {
        if (replies.isEmpty()) return null;

        // Xóa các câu thoại đã gõ quá 90 giây trước
        recentReplies.entrySet().removeIf(e -> now - e.getValue() > 90_000L);

        List<String> freshReplies = new ArrayList<>();
        for (String r : replies) {
            if (!recentReplies.containsKey(r.toLowerCase())) {
                freshReplies.add(r);
            }
        }

        String chosen = freshReplies.isEmpty()
                ? replies.get(ThreadLocalRandom.current().nextInt(replies.size()))
                : freshReplies.get(ThreadLocalRandom.current().nextInt(freshReplies.size()));

        recentReplies.put(chosen.toLowerCase(), now);
        return chosen;
    }
}