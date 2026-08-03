package org.phantam.fozminespoofcore.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.util.logging.Level;

/**
 * Periodically sends keep-alive packets to refresh fake player visibility.
 * <p>
 * This task runs every few seconds and re-sends spawn and tablist packets for
 * all active fake players, preventing client-side desync issues when real players
 * join, leave, or switch worlds.
 * </p>
 *
 * <p><b>Thread-safety:</b> This task is executed on the main thread by Bukkit's scheduler.</p>
 *
 * @author Phantam
 * @version 2.0.0
 * @see org.phantam.fozminespoofapi.FozminespoofApi#sendKeepAlivePackets()
 */
public class KeepAliveTask extends BukkitRunnable {

    private final FozmineSpoofCore plugin;

    /**
     * Constructs a new KeepAliveTask.
     *
     * @param plugin the core plugin instance
     */
    public KeepAliveTask(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Safety check: cancel if plugin is disabled or bridge is unavailable
        if (!plugin.isEnabled() || plugin.getBridge() == null) {
            plugin.getLogger().log(Level.INFO,
                    "[KeepAliveTask] Plugin disabled or bridge missing. Cancelling task.");
            DebugLogger.log(plugin.getLogger(), "KeepAliveTask: cancelled (plugin disabled or bridge null)");
            this.cancel();
            return;
        }

        DebugLogger.logFine(plugin.getLogger(), "KeepAliveTask: running keep-alive cycle");

        try {
            plugin.getBridge().sendKeepAlivePackets();
            DebugLogger.logFine(plugin.getLogger(), "KeepAliveTask: keep-alive packets sent");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[KeepAliveTask] Error sending keep-alive packets: " + e.getMessage(), e);
            DebugLogger.log(plugin.getLogger(), "KeepAliveTask: error sending packets: %s", e.getMessage());
        }
    }
}