package org.phantam.fozminespoofcore.tasks;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.config.ConfigManager;

import java.util.logging.Level;

/**
 * Periodically synchronises fake player statistics with the proxy database.
 * Updates active and inactive bot counts for BungeeCord/Waterfall network visibility.
 */
public class ProxySyncTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final IFakePlayerDatabase database;
    private final ConfigManager configManager;

    public ProxySyncTask(JavaPlugin plugin, IFakePlayerDatabase database, ConfigManager configManager) {
        this.plugin = plugin;
        this.database = database;
        this.configManager = configManager;
        DebugLogger.log(plugin.getLogger(), "ProxySyncTask: initialized");
    }

    /**
     * Periodically synchronises fake player statistics with the proxy database.
     * <p>
     * This task runs asynchronously and updates the proxy sync table with the
     * current active and inactive bot counts for this server node. The interval
     * is configurable via {@code Database.bridging-setting.update-interval}.
     * </p>
     *
     * @author Phantam
     * @version 2.0.0
     */
    @Override
    public void run() {
        DebugLogger.log(plugin.getLogger(), "ProxySyncTask: sync cycle started");

        try {
            int activeCount = database.getActiveBotCount();
            int inactiveCount = database.getInactiveBotCount();

            DebugLogger.log(plugin.getLogger(), "ProxySyncTask: counts: active=%d, inactive=%d", activeCount, inactiveCount);

            // Use the actual server node name (bungee_name) instead of the database table name.
            database.sendProxySyncData(
                    configManager.getBungeeName(),      // bungeeName (table prefix)
                    configManager.getBungeeName(),      // serverNodeName (the unique identifier)
                    activeCount,
                    inactiveCount
            );

            plugin.getLogger().log(Level.FINE,
                    "[ProxySyncTask] Synced proxy data: active=" + activeCount + ", inactive=" + inactiveCount);

            DebugLogger.logFine(plugin.getLogger(), "ProxySyncTask: sync data sent");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[ProxySyncTask] Error syncing proxy data: " + e.getMessage(), e);
            DebugLogger.log(plugin.getLogger(), "ProxySyncTask: error: %s", e.getMessage());
        } finally {
            if (plugin.isEnabled()) {
                reschedule();
            } else {
                DebugLogger.log(plugin.getLogger(), "ProxySyncTask: plugin disabled, not rescheduling");
            }
        }
    }

    /**
     * Schedules the next execution with a random interval from config.
     */
    private void reschedule() {
        int delaySeconds = configManager.getProxyUpdateInterval();
        long delayTicks = delaySeconds * 20L;

        new ProxySyncTask(plugin, database, configManager)
                .runTaskLaterAsynchronously(plugin, delayTicks);

        plugin.getLogger().log(Level.FINE,
                "[ProxySyncTask] Next sync scheduled in " + delaySeconds + " seconds.");

        DebugLogger.logFine(plugin.getLogger(), "ProxySyncTask: rescheduled in %d seconds", delaySeconds);
    }
}