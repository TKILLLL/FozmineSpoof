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
    public void onBotJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isBot(player)) {
            if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                // Ẩn tin nhắn Join mặc định của hệ thống / Essentials / CMI
                event.setJoinMessage(null);
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