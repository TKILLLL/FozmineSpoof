package org.phantam.fozminespoofcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

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
            if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                event.setJoinMessage(null);
            }

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
            if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                event.setQuitMessage(null);
            }

            plugin.getFakePlayerManager().handleExternalQuit(player.getName());
        }
    }

    private boolean isBot(Player player) {
        return player.hasMetadata("NPC") || plugin.getFakePlayerManager().isBotOnline(player.getName());
    }
}