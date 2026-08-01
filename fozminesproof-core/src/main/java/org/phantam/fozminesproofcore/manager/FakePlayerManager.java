package org.phantam.fozminesproofcore.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.executors.*;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Central manager for fake player operations.
 * Orchestrates actions (add, spawn, despawn, remove, reload) and maintains the registry.
 */
public class FakePlayerManager {

    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
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
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        spawnAction.executeAsync(name, future::complete);
        try {
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
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
        spawnAction.executeAsync(name, callback);
    }

    public boolean despawnBot(String name) {
        return despawnAction.execute(name);
    }

    public boolean removeBot(String name) {
        return removeAction.execute(name);
    }

    public void reloadSystem() {
        reloadAction.execute(null);
    }

    // --- Queries ---

    public Player getOnlineBotEntity(String name) {
        return registry.getEntity(name);
    }

    public Collection<FakePlayerData> getAllDatabaseBots() {
        return database.loadAllPlayers();
    }

    public Collection<FakePlayerData> getOnlineBotsData() {
        return registry.getOnlineData();
    }

    public boolean isBotOnline(String name) {
        return registry.isOnline(name);
    }

    // --- Shutdown ---

    /**
     * Despawns all currently online bots during plugin shutdown.
     * Waits briefly for database operations to complete.
     */
    public void despawnAllOnShutdown() {
        Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) {
            return;
        }

        java.util.List<FakePlayerData> targetBots = new java.util.ArrayList<>(onlineData);
        org.bukkit.Bukkit.getLogger().log(Level.INFO,
                "[FozmineSproof] Found " + targetBots.size() + " online bots. Despawning...");

        for (FakePlayerData bot : targetBots) {
            if (bot == null || bot.getName() == null) continue;
            boolean success = this.despawnBot(bot.getName());
            String status = success ? "Success" : "Failed";
            org.bukkit.Bukkit.getLogger().log(success ? Level.INFO : Level.WARNING,
                    "[Shutdown Cleanup] -> " + bot.getName() + " (" + status + ")");
        }

        try {
            org.bukkit.Bukkit.getLogger().log(Level.INFO,
                    "[FozmineSproof] Waiting for SQL sync...");
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Startup ---

    /**
     * Spawns all offline bots during plugin startup with staggered delays.
     *
     * @param configManager the config manager for interval settings
     */
    public void spawnAllOnStartup(org.phantam.fozminesproofcore.config.ConfigManager configManager) {
        if (configManager == null) return;

        java.util.List<String> offlineNames = this.getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !this.isBotOnline(name))
                .collect(java.util.stream.Collectors.toList());

        if (offlineNames.isEmpty()) {
            org.bukkit.Bukkit.getLogger().log(Level.INFO,
                    "[FozmineSproof] No offline bots found to spawn.");
            return;
        }

        org.bukkit.Bukkit.getLogger().log(Level.INFO,
                "[FozmineSproof] Found " + offlineNames.size() +
                        " offline bots. Starting staggered spawn...");

        java.util.Queue<String> queue = new java.util.LinkedList<>(offlineNames);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.isEmpty()) {
                    org.bukkit.Bukkit.getLogger().log(Level.INFO,
                            "[FozmineSproof] Startup spawn queue completed.");
                    return;
                }

                String next = queue.poll();

                if (!isBotOnline(next)) {
                    spawnBotAsync(next, success -> {
                        String status = success ? "Success" : "Failed";
                        org.bukkit.Bukkit.getLogger().log(success ? Level.INFO : Level.WARNING,
                                "[Startup Spawn] -> " + next + " (" + status + ")");
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
                    }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), delay);
                }
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), 40L);
    }
}