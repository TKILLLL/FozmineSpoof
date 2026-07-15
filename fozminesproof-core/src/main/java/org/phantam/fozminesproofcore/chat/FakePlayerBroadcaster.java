package org.phantam.fozminesproofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.phantam.fozminesproofcore.config.ConfigManager;

public class FakePlayerBroadcaster {
    private final ConfigManager configManager;

    public FakePlayerBroadcaster(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void broadcastJoin(String botName) {
        if (!configManager.isJoinLeaveMessageEnable() || configManager.getJoinMessage() == null) return;

        String msg = configManager.getJoinMessage().replace("%fakeplayer_name%", botName);
        broadcast(msg);
    }

    public void broadcastLeave(String botName) {
        if (!configManager.isJoinLeaveMessageEnable() || configManager.getLeaveMessage() == null) return;

        String msg = configManager.getLeaveMessage().replace("%fakeplayer_name%", botName);
        broadcast(msg);
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
