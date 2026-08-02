package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminespoofcore.database.executors.*;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Central manager for fake player operations with high-performance In-Memory Caching.
 */
public class FakePlayerManager {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    private final Map<String, FakePlayerData> botCache = new ConcurrentHashMap<>();

    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;
    private BotLifecycleManager lifecycleManager;

    public FakePlayerManager(FozmineSpoofCore plugin, IFakePlayerDatabase database) {
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

    /**
     * Preloads all DB records into RAM cache at startup asynchronously.
     */
    public void loadCacheFromDatabaseAsync(Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Collection<FakePlayerData> all = database.loadAllPlayers();
                botCache.clear();
                for (FakePlayerData data : all) {
                    if (data != null && data.getName() != null && data.getName().length() <= 16) {
                        botCache.put(data.getName().toLowerCase(), data);
                    }
                }
                plugin.getLogger().info("[FakePlayerManager] Cached " + botCache.size() + " bots into RAM.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[FakePlayerManager] Error loading RAM cache: " + e.getMessage(), e);
            } finally {
                if (onComplete != null) {
                    Bukkit.getScheduler().runTask(plugin, onComplete);
                }
            }
        });
    }

    public void updateCache(FakePlayerData data) {
        if (data != null && data.getName() != null && data.getName().length() <= 16) {
            botCache.put(data.getName().toLowerCase(), data);
        }
    }

    public void removeFromCache(String name) {
        if (name != null) {
            botCache.remove(name.toLowerCase());
        }
    }

    public boolean addBot(String name, Location location) {
        if (name == null || name.length() < 3 || name.length() > 16 || !name.matches("^[a-zA-Z0-9_]+$")) {
            plugin.getLogger().warning("[FakePlayerManager] Cannot add bot '" + name + "': Name must be between 3 and 16 characters.");
            return false;
        }

        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: addBot(%s)", name);
        addAction.execute(new AddBotAction.Request(name, location));

        // Cache update
        database.loadFakePlayer(name).ifPresent(this::updateCache);
        return true;
    }

    public void spawnBotAsync(String name, Consumer<Boolean> callback) {
        if (name == null || name.length() > 16) {
            if (callback != null) callback.accept(false);
            return;
        }

        spawnAction.executeAsync(name, success -> {
            if (success) {
                FakePlayerData cached = botCache.get(name.toLowerCase());
                if (cached != null) {
                    updateCache(cached.withActive(true));
                }
            }
            if (callback != null) callback.accept(success);
        });
    }

    public boolean despawnBot(String name) {
        boolean result = despawnAction.execute(name);
        if (result) {
            FakePlayerData cached = botCache.get(name.toLowerCase());
            if (cached != null) {
                updateCache(cached.withActive(false));
            }
        }
        return result;
    }

    public boolean removeBot(String name) {
        boolean result = removeAction.execute(name);
        if (result) {
            removeFromCache(name);
        }
        return result;
    }

    public void reloadSystem() {
        reloadAction.execute(null);
        try {
            Collection<FakePlayerData> all = database.loadAllPlayers();
            botCache.clear();
            for (FakePlayerData data : all) {
                if (data != null && data.getName() != null && data.getName().length() <= 16) {
                    botCache.put(data.getName().toLowerCase(), data);
                }
            }
            plugin.getLogger().info("[FakePlayerManager] Cached " + botCache.size() + " bots into RAM.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[FakePlayerManager] Error reloading RAM cache: " + e.getMessage(), e);
        }
    }

    // --- High Speed O(1) Memory Queries ---

    public Player getOnlineBotEntity(String name) {
        return registry.getEntity(name);
    }

    /**
     * Returns cached players instantly from RAM (Zero Disk I/O).
     */
    public Collection<FakePlayerData> getAllDatabaseBots() {
        return botCache.values();
    }

    public Collection<FakePlayerData> getOnlineBotsData() {
        return registry.getOnlineData();
    }

    public boolean isBotOnline(String name) {
        return registry.isOnline(name);
    }

    public void despawnAllOnShutdown() {
        Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) return;

        for (FakePlayerData bot : new java.util.ArrayList<>(onlineData)) {
            if (bot != null && bot.getName() != null) {
                despawnBot(bot.getName());
            }
        }
    }

    public void setLifecycleManager(BotLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        if (this.spawnAction != null) this.spawnAction.setLifecycleManager(lifecycleManager);
        if (this.despawnAction != null) this.despawnAction.setLifecycleManager(lifecycleManager);
    }

    public void handleExternalQuit(String name) {
        if (registry.isOnline(name)) {
            registry.unregister(name);
            FakePlayerData cached = botCache.get(name.toLowerCase());
            if (cached != null) {
                updateCache(cached.withActive(false));
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                database.loadFakePlayer(name).ifPresent(data -> database.saveFakePlayer(data.withActive(false)));
            });
        }
    }
}