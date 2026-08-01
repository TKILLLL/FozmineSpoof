package org.phantam.fozminesproofcore.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofcore.FozmineSproofCore;

import java.util.logging.Level;

/**
 * Periodically sends keep-alive packets to refresh fake player visibility.
 * This prevents client-side desync issues when players log in/out.
 */
public class KeepAliveTask extends BukkitRunnable {

    private final FozmineSproofCore plugin;

    public KeepAliveTask(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Safety check: cancel if plugin is disabled or bridge is unavailable
        if (!plugin.isEnabled() || plugin.getBridge() == null) {
            plugin.getLogger().log(Level.INFO,
                    "[KeepAliveTask] Plugin disabled or bridge missing. Cancelling task.");
            this.cancel();
            return;
        }

        try {
            plugin.getBridge().sendKeepAlivePackets();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[KeepAliveTask] Error sending keep-alive packets: " + e.getMessage(), e);
        }
    }
}