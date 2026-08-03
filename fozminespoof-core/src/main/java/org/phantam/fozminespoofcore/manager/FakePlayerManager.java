package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminespoofcore.database.executors.*;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Central manager for fake player operations with high-performance in-memory caching.
 * <p>
 * This class orchestrates all bot lifecycle actions (add, spawn, despawn, remove, reload)
 * and maintains a thread-safe RAM cache for instant data access. All database interactions
 * are delegated to dedicated action classes, ensuring separation of concerns and
 * asynchronous execution where appropriate.
 * </p>
 *
 * <p><b>Thread-safety:</b> All public methods are thread-safe unless explicitly noted.
 * Internal caches use {@link ConcurrentHashMap} for concurrent access.</p>
 *
 * @author Phantam
 * @version 2.0.0
 * @see FakePlayerData
 * @see IFakePlayerDatabase
 * @see FakePlayerRegistry
 */
public class FakePlayerManager {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    /**
     * RAM cache of all bot data (both online and offline) keyed by lowercase name.
     * Provides O(1) read access and is updated asynchronously on database changes.
     */
    private final Map<String, FakePlayerData> botCache = new ConcurrentHashMap<>();

    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;
    private BotLifecycleManager lifecycleManager;

    /**
     * Constructs a new FakePlayerManager with the required dependencies.
     *
     * @param plugin   the core plugin instance
     * @param database the database access layer
     */
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
     * Preloads all database records into the RAM cache asynchronously.
     * <p>
     * This method should be called during plugin startup to ensure the cache is
     * populated before any bot operations are performed.
     * </p>
     *
     * @param onComplete a callback executed on the main thread when loading completes
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

    /**
     * Updates the RAM cache with the given bot data.
     * <p>
     * This method is called after any database change that affects a bot's state.
     * </p>
     *
     * @param data the bot data to cache (must have a non-null name, otherwise ignored)
     */
    public void updateCache(FakePlayerData data) {
        if (data != null && data.getName() != null && data.getName().length() <= 16) {
            botCache.put(data.getName().toLowerCase(), data);
        }
    }

    /**
     * Removes a bot from the RAM cache.
     *
     * @param name the name of the bot to remove
     */
    public void removeFromCache(String name) {
        if (name != null) {
            botCache.remove(name.toLowerCase());
        }
    }

    /**
     * Adds a new fake player to the system (database + cache) without spawning it.
     * <p>
     * The bot is created with an offline-mode UUID derived from its name and stored
     * in the database. The cache is updated immediately to ensure consistency.
     * </p>
     *
     * @param name     the bot name (must be 3–16 alphanumeric characters or underscores)
     * @param location the spawn location (used for initial coordinates; may be ignored)
     * @return {@code true} if the bot was added successfully, {@code false} otherwise
     */
    public boolean addBot(String name, Location location) {
        if (name == null || name.length() < 3 || name.length() > 16 || !name.matches("^[a-zA-Z0-9_]+$")) {
            plugin.getLogger().warning("[FakePlayerManager] Cannot add bot '" + name + "': Name must be between 3 and 16 characters.");
            return false;
        }

        DebugLogger.log(plugin.getLogger(), "FakePlayerManager: addBot(%s)", name);
        addAction.execute(new AddBotAction.Request(name, location));

        // Immediately update cache to avoid race conditions (L-01 fix)
        // Instead of reloading from DB, construct the data directly.
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        String worldName = plugin.getConfigManager().getBotWorldName();
        FakePlayerData data = new FakePlayerData.Builder()
                .name(name)
                .uuid(uuid)
                .world(worldName)
                .location(0.0, 64.0, 0.0, 0.0f, 0.0f)
                .active(false)
                .build();
        updateCache(data);

        return true;
    }

    /**
     * Spawns a fake player asynchronously, making it visible in the world.
     * <p>
     * This method triggers the spawn pipeline, which loads the bot data from the
     * database (or cache), creates the NMS entity, broadcasts spawn packets, and
     * updates the registry. The result is delivered via the callback.
     * </p>
     *
     * @param name     the bot name
     * @param callback consumer that receives {@code true} on success, {@code false} on failure
     */
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

    /**
     * Despawns a bot synchronously, removing it from the world and updating its active state.
     *
     * @param name the bot name
     * @return {@code true} if the bot was despawned, {@code false} otherwise
     */
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

    /**
     * Permanently removes a bot from the system (despawns + deletes from database).
     *
     * @param name the bot name
     * @return {@code true} if the bot was removed successfully
     */
    public boolean removeBot(String name) {
        boolean result = removeAction.execute(name);
        if (result) {
            removeFromCache(name);
        }
        return result;
    }

    /**
     * Reloads the entire bot system from the database and refreshes the cache.
     * <p>
     * This method also triggers a re-synchronization of online bot states with
     * the registry and performs auto‑healing for any missing database entries.
     * </p>
     */
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

    /**
     * Returns the online Bukkit Player entity for the given bot name.
     *
     * @param name the bot name
     * @return the Player entity, or {@code null} if the bot is offline or not found
     */
    public Player getOnlineBotEntity(String name) {
        return registry.getEntity(name);
    }

    /**
     * Returns all cached bot data (both online and offline) as a collection.
     * <p>
     * This is a zero-disk‑I/O operation that reads directly from the RAM cache.
     * </p>
     *
     * @return a collection of all bot data; never null
     */
    public Collection<FakePlayerData> getAllDatabaseBots() {
        return botCache.values();
    }

    /**
     * Returns the data of all currently online bots.
     *
     * @return a collection of online bot data; never null
     */
    public Collection<FakePlayerData> getOnlineBotsData() {
        return registry.getOnlineData();
    }

    /**
     * Checks whether a bot is currently online.
     *
     * @param name the bot name
     * @return {@code true} if the bot is online, {@code false} otherwise
     */
    public boolean isBotOnline(String name) {
        return registry.isOnline(name);
    }

    /**
     * Despawns all online bots during plugin shutdown.
     * <p>
     * This method iterates over a copy of the online bot list to avoid
     * concurrent modification issues.
     * </p>
     */
    public void despawnAllOnShutdown() {
        Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) return;

        for (FakePlayerData bot : new java.util.ArrayList<>(onlineData)) {
            if (bot != null && bot.getName() != null) {
                despawnBot(bot.getName());
            }
        }
    }

    /**
     * Sets the lifecycle manager and propagates it to the spawn and despawn actions.
     *
     * @param lifecycleManager the lifecycle manager instance
     */
    public void setLifecycleManager(BotLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        if (this.spawnAction != null) this.spawnAction.setLifecycleManager(lifecycleManager);
        if (this.despawnAction != null) this.despawnAction.setLifecycleManager(lifecycleManager);
    }

    /**
     * Handles an external quit event (e.g., when a bot is removed by another plugin).
     * <p>
     * This method unregisters the bot from the registry, updates its cached state
     * to inactive, and asynchronously saves the change to the database.
     * </p>
     *
     * @param name the name of the bot that quit
     */
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