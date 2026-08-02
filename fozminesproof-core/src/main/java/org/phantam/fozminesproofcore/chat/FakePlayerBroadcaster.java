package org.phantam.fozminesproofcore.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.logging.Logger;

public class FakePlayerBroadcaster {

    private final ConfigManager configManager;
    private final Logger logger;

    public FakePlayerBroadcaster(ConfigManager configManager) {
        this.configManager = configManager;
        this.logger = Bukkit.getLogger();
    }

    public void broadcastJoin(String botName) {
        if (!configManager.isJoinLeaveMessageEnable()) {
            DebugLogger.log(logger, "broadcastJoin: disabled (config false)");
            return;
        }
        String raw = configManager.getJoinMessage();
        if (raw == null || raw.isEmpty()) {
            DebugLogger.log(logger, "broadcastJoin: raw message is null/empty");
            return;
        }
        String msg = raw.replace("%fakeplayer_name%", botName);
        DebugLogger.log(logger, "broadcastJoin: broadcasting -> " + msg);
        broadcast(msg);
    }

    public void broadcastLeave(String botName) {
        DebugLogger.log(logger, "broadcastLeave: called for " + botName);
        if (!configManager.isJoinLeaveMessageEnable()) {
            DebugLogger.log(logger, "broadcastLeave: disabled (config false)");
            return;
        }
        String raw = configManager.getLeaveMessage();
        DebugLogger.log(logger, "broadcastLeave: raw message = '" + raw + "'");
        if (raw == null || raw.isEmpty()) {
            DebugLogger.log(logger, "broadcastLeave: raw message is null/empty, skipping");
            return;
        }
        String msg = raw.replace("%fakeplayer_name%", botName);
        DebugLogger.log(logger, "broadcastLeave: final message = '" + msg + "'");
        broadcast(msg);
    }

    private void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            DebugLogger.log(logger, "broadcast: message empty, skipping");
            return;
        }
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        Bukkit.broadcastMessage(colored);
        DebugLogger.log(logger, "broadcast: sent -> " + colored);
    }
}