package org.phantam.fozminesproofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.logging.Logger;

/**
 * Broadcasts join/leave messages for fake players.
 */
public class FakePlayerBroadcaster {

    private final ConfigManager configManager;
    private final Logger logger;

    public FakePlayerBroadcaster(ConfigManager configManager) {
        this.configManager = configManager;
        this.logger = Bukkit.getLogger();
    }

    public void broadcastJoin(String botName) {
        if (!configManager.isJoinLeaveMessageEnable() || configManager.getJoinMessage() == null) {
            DebugLogger.logFine(logger, "FakePlayerBroadcaster: join message disabled or null");
            return;
        }
        String msg = configManager.getJoinMessage().replace("%fakeplayer_name%", botName);
        DebugLogger.log(logger, "FakePlayerBroadcaster: broadcasting join: %s", msg);
        broadcast(msg);
    }

    public void broadcastLeave(String botName) {
        if (!configManager.isJoinLeaveMessageEnable() || configManager.getLeaveMessage() == null) {
            DebugLogger.logFine(logger, "FakePlayerBroadcaster: leave message disabled or null");
            return;
        }
        String msg = configManager.getLeaveMessage().replace("%fakeplayer_name%", botName);
        DebugLogger.log(logger, "FakePlayerBroadcaster: broadcasting leave: %s", msg);
        broadcast(msg);
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}