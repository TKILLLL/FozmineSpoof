package org.phantam.fozminesproofcore.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.executors.*;
import org.phantam.fozminesproofcore.utils.DebugLogger;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Central manager for fake player operations.
 * Orchestrates actions (add, spawn, despawn, remove, reload) and maintains the registry.
 */
public class FakePlayerManager {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.registry = new FakePlayerRegistry();
        FakePlayerBroadcaster broadcaster = new FakePlayerBroadcaster(plugin.getConfigManager());

        this.addAction = new AddBotAction(plugin, database);
        this.spawnAction = new SpawnBotAction(plugin, database, registry, broadcaster);
        this.despawnAction = new DespawnBotAction(plugin, database, registry, broadcaster);
        this.removeAction = new RemoveBotAction(database, this.despawnAction);
        this.reloadAction = new ReloadSystemAction(plugin, database, registry);
    }

    // --- Synchronous actions (for backward compatibility) ---

    public void addBot(String name, Location location) {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: addBot(%s)", name);
        addAction.execute(new AddBotAction.Request(name, location));
    }

    /**
     * Synchronous spawn with a 5-second timeout.
     * Prefer using {@link #spawnBotAsync(String, Consumer)} to avoid blocking the main thread.
     *
     * @param name bot name
     * @return true if spawned within timeout
     */
    public boolean spawnBot(String name) {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: spawnBot(sync) %s", name);
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        spawnAction.executeAsync(name, future::complete);
        try {
            boolean result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            DebugLogger.log(plugin.getLogger(), "FakePlayerManager: spawnBot(sync) %s -> %s", name, result);
            return result;
        } catch (Exception e) {
            DebugLogger.log(plugin.getLogger(), "FakePlayerManager: spawnBot(sync) %s timed out or failed", name);
            return false;
        }
    }

    /**
     * Asynchronously spawns a bot.
     *
     * @param name     bot name
     * @param callback callback with success flag
     */
    public void spawnBotAsync(String name, Consumer<Boolean> callback) {
        DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: spawnBotAsync %s", name);
        spawnAction.executeAsync(name, callback);
    }

    public boolean despawnBot(String name) {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: despawnBot %s", name);
        boolean result = despawnAction.execute(name);
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: despawnBot %s -> %s", name, result);
        return result;
    }

    public boolean removeBot(String name) {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: removeBot %s", name);
        boolean result = removeAction.execute(name);
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: removeBot %s -> %s", name, result);
        return result;
    }

    public void reloadSystem() {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: reloadSystem");
        reloadAction.execute(null);
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: reloadSystem completed");
    }

    // --- Queries ---

    public Player getOnlineBotEntity(String name) {
        Player entity = registry.getEntity(name);
        if (entity != null) {
            DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: getOnlineBotEntity %s found", name);
        } else {
            DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: getOnlineBotEntity %s not found", name);
        }
        return entity;
    }

    public Collection<FakePlayerData> getAllDatabaseBots() {
        Collection<FakePlayerData> all = database.loadAllPlayers();
        DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: getAllDatabaseBots -> %d bots", all.size());
        return all;
    }

    public Collection<FakePlayerData> getOnlineBotsData() {
        Collection<FakePlayerData> online = registry.getOnlineData();
        DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: getOnlineBotsData -> %d bots", online.size());
        return online;
    }

    public boolean isBotOnline(String name) {
        boolean online = registry.isOnline(name);
        DebugLogger.logFine(plugin.getLogger(), "FakePlayerManager: isBotOnline %s -> %s", name, online);
        return online;
    }

    // --- Shutdown ---

    /**
     * Despawns all currently online bots during plugin shutdown.
     * Waits briefly for database operations to complete.
     */
    public void despawnAllOnShutdown() {
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: despawnAllOnShutdown");
        Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "FakePlayerManager: no online bots to despawn");
            return;
        }

        java.util.List<FakePlayerData> targetBots = new java.util.ArrayList<>(onlineData);
        plugin.getLogger().log(Level.INFO,
                "[FozmineSproof] Found " + targetBots.size() + " online bots. Despawning...");

        for (FakePlayerData bot : targetBots) {
            if (bot == null || bot.getName() == null) continue;
            boolean success = this.despawnBot(bot.getName());
            String status = success ? "Success" : "Failed";
            plugin.getLogger().log(success ? Level.INFO : Level.WARNING,
                    "[Shutdown Cleanup] -> " + bot.getName() + " (" + status + ")");
        }

        try {
            plugin.getLogger().log(Level.INFO,
                    "[FozmineSproof] Waiting for SQL sync...");
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: despawnAllOnShutdown completed");
    }

    // --- Startup ---

    /**
     * Spawns all offline bots during plugin startup with staggered delays.
     *
     * @param configManager the config manager for interval settings
     */
    public void spawnAllOnStartup(org.phantam.fozminesproofcore.config.ConfigManager configManager) {
        if (configManager == null) {
            DebugLogger.log(plugin.getLogger(), "FakePlayerManager: spawnAllOnStartup called with null configManager");
            return;
        }

        java.util.List<String> offlineNames = this.getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !this.isBotOnline(name))
                .collect(java.util.stream.Collectors.toList());

        if (offlineNames.isEmpty()) {
            plugin.getLogger().log(Level.INFO,
                    "[FozmineSproof] No offline bots found to spawn.");
            DebugLogger.log(plugin.getLogger(), "FakePlayerManager: no offline bots to spawn");
            return;
        }

        plugin.getLogger().log(Level.INFO,
                "[FozmineSproof] Found " + offlineNames.size() +
                        " offline bots. Starting staggered spawn...");

        java.util.Queue<String> queue = new java.util.LinkedList<>(offlineNames);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.isEmpty()) {
                    plugin.getLogger().log(Level.INFO,
                            "[FozmineSproof] Startup spawn queue completed.");
                    DebugLogger.log(plugin.getLogger(), "FakePlayerManager: startup spawn queue completed");
                    return;
                }

                String next = queue.poll();

                if (!isBotOnline(next)) {
                    spawnBotAsync(next, success -> {
                        String status = success ? "Success" : "Failed";
                        plugin.getLogger().log(success ? Level.INFO : Level.WARNING,
                                "[Startup Spawn] -> " + next + " (" + status + ")");
                        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: spawnAllOnStartup %s -> %s",
                                next, status);
                    });
                }

                if (!queue.isEmpty()) {
                    long delay = configManager.getJoinQuitIntervalTicks();
                    if (delay <= 0) delay = 20L;

                    final BukkitRunnable current = this;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            current.run();
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskLater(plugin, 40L);
    }
}