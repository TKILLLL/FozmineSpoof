package org.phantam.fozminesproofcore.tasks;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.config.ConfigManager;

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
    }

    @Override
    public void run() {
        try {
            int activeCount = database.getActiveBotCount();
            int inactiveCount = database.getInactiveBotCount();

            database.sendProxySyncData(
                    configManager.getBungeeName(),
                    configManager.getRawDatabaseName(),
                    activeCount,
                    inactiveCount
            );

            plugin.getLogger().log(Level.FINE,
                    "[ProxySyncTask] Synced proxy data: active=" + activeCount + ", inactive=" + inactiveCount);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[ProxySyncTask] Error syncing proxy data: " + e.getMessage(), e);
        } finally {
            // Reschedule only if the plugin is still enabled
            if (plugin.isEnabled()) {
                reschedule();
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
    }
}