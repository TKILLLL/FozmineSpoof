package org.phantam.fozminespoofcore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

/**
 * Listener for bot join and quit events, handling message suppression and custom broadcasts.
 */
public class BotJoinQuitListener implements Listener {

    private final FozmineSpoofCore plugin;

    public BotJoinQuitListener(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            // Chỉ chặn tin nhắn gốc ngay lập tức nếu TẮT hoàn toàn hoặc dùng chế độ CUSTOM
            if (!enable || "custom".equalsIgnoreCase(format)) {
                event.setJoinMessage(null);
            }
            // Nếu là NORMAL, tạm thời để nguyên để các plugin chat khác nhảy vào định dạng (LPC, EssentialsChat...)

            if (plugin.getJoinChatProcessor() != null) {
                plugin.getJoinChatProcessor().handleBotSessionJoin(player);
            }
        } else {
            // Xử lý người chơi thật
            boolean isBotName = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .anyMatch(b -> b != null && b.getName() != null && b.getName().equalsIgnoreCase(name));

            if (isBotName && plugin.getConfigManager().isRankWeightEnabled() && plugin.getRankWeightManager() != null) {
                plugin.getRankWeightManager().resetRank(name);
            }

            if (plugin.getJoinChatProcessor() != null) {
                plugin.getJoinChatProcessor().handleRealPlayerJoin(player);
            }
        }
    }

    /**
     * Chạy CUỐI CÙNG sau khi các plugin chat khác đã xử lý và định dạng xong xuôi tin nhắn Join của Bot.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinMonitor(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            if (enable && "normal".equalsIgnoreCase(format)) {
                String finalFormattedMsg = event.getJoinMessage();
                if (finalFormattedMsg != null && !finalFormattedMsg.isEmpty()) {
                    event.setJoinMessage(null);

                    if (!player.hasMetadata("fozmine_join_processed")) {
                        player.setMetadata("fozmine_join_processed",
                                new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.broadcastMessage(finalFormattedMsg);
                        });
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            if (!enable || "custom".equalsIgnoreCase(format)) {
                event.setQuitMessage(null);
            }

            plugin.getFakePlayerManager().handleExternalQuit(player.getName());
            plugin.getJoinChatProcessor().handleBotQuit(player);
        }
    }

    /**
     * Chạy CUỐI CÙNG sau khi các plugin chat khác đã xử lý và định dạng xong xuôi tin nhắn Quit của Bot.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotQuitMonitor(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            if (enable && "normal".equalsIgnoreCase(format)) {
                String finalFormattedMsg = event.getQuitMessage();
                if (finalFormattedMsg != null && !finalFormattedMsg.isEmpty()) {
                    event.setQuitMessage(null);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.broadcastMessage(finalFormattedMsg);
                    });
                }
            }
        }
    }

    /**
     * Cải tiến hàm kiểm tra Bot để nhận diện chính xác ngay cả khi sự kiện native chạy trước khi nạp registry.
     */
    private boolean isBot(Player player) {
        if (player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(player.getName())) {
            return true;
        }
        return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(b -> b != null && b.getName() != null && b.getName().equalsIgnoreCase(player.getName()));
    }
}