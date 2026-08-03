package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

/**
 * Listener for bot join and quit events, handling message suppression and custom broadcasts.
 * <p>
 * This listener controls whether default join/quit messages are suppressed based on the
 * {@code join-leave-format} configuration. When format is {@code "custom"}, messages are
 * suppressed and custom messages are sent by the spawn/despawn actions. When format is
 * {@code "normal"}, messages are left intact for the server or other plugins to handle.
 * </p>
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
            String format = plugin.getConfigManager().getJoinLeaveFormat();
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();

            // Suppress default message only when format is "custom" and messages are enabled
            if ("custom".equalsIgnoreCase(format) && enable) {
                event.setJoinMessage(null);
            }
            // If format is "normal", do nothing – let server/other plugins handle the message

            if (plugin.getJoinChatProcessor() != null) {
                plugin.getJoinChatProcessor().handleBotSessionJoin(player);
            }
        } else {
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            String format = plugin.getConfigManager().getJoinLeaveFormat();
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();

            // Suppress default message only when format is "custom" and messages are enabled
            if ("custom".equalsIgnoreCase(format) && enable) {
                event.setQuitMessage(null);
            }
            // If format is "normal", do nothing

            plugin.getFakePlayerManager().handleExternalQuit(player.getName());
        }
    }

    private boolean isBot(Player player) {
        return player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(player.getName());
    }
}