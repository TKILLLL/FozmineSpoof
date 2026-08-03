package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.utils.BotNameProvider;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of fake players, including spawning, despawning, and expiration.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Automatic spawning of bots to maintain the target player count</li>
 *   <li>Despawning bots after their lifetime expires</li>
 *   <li>Dynamic adjustment based on real player count and peak hours</li>
 * </ul>
 * The expiration check runs every 5 seconds (configurable via {@link #EXPIRATION_CHECK_INTERVAL_TICKS}),
 * and the maintenance check runs every 3 seconds.
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
 */
public class BotLifecycleManager {

    /**
     * Interval (in ticks) for checking expired bots. 100 ticks = 5 seconds.
     * This is sufficient because bot lifetimes are in minutes/hours.
     */
    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 100L;

    /**
     * Interval (in ticks) for maintaining the bot count. 60 ticks = 3 seconds.
     * This ensures the count stays close to the target without excessive CPU usage.
     */
    private static final long MAINTENANCE_CHECK_INTERVAL_TICKS = 60L;

    private final FozmineSpoofCore plugin;
    private final FakePlayerManager manager;
    private final ConfigManager config;

    /**
     * Map of bot name (lowercase) to expiration timestamp (milliseconds).
     * Thread-safe via {@link ConcurrentHashMap}.
     */
    private final Map<String, Long> botExpirationTime = new ConcurrentHashMap<>();

    /**
     * Set of bot names currently in the process of spawning (to avoid duplicate spawns).
     */
    private final Set<String> spawning = ConcurrentHashMap.newKeySet();

    private BukkitRunnable lifecycleCheckTask;
    private BukkitRunnable maintenanceCheckTask;

    /**
     * Constructs a new BotLifecycleManager.
     *
     * @param plugin  the core plugin instance
     * @param manager the fake player manager
     * @param config  the configuration manager
     */
    public BotLifecycleManager(FozmineSpoofCore plugin, FakePlayerManager manager, ConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;

        startLifecycleCheck();
        startMaintenanceCheck();
    }

    /**
     * Reloads the configuration and recalculates expiration times for all online bots.
     * <p>
     * This method is called when the plugin is reloaded, updating the lifetime
     * values for all currently active bots based on the new configuration.
     * </p>
     */
    public void reload() {
        long now = System.currentTimeMillis();
        int count = 0;

        for (FakePlayerData botData : manager.getOnlineBotsData()) {
            if (botData != null && botData.getName() != null) {
                String lowerName = botData.getName().toLowerCase();
                long newLifetimeMs = config.getLifetimeIntervalMillis();
                botExpirationTime.put(lowerName, now + newLifetimeMs);
                count++;
            }
        }

        DebugLogger.log(plugin.getLogger(),
                "BotLifecycleManager: reloaded. Recalculated lifetime for %d active online bots.", count);
    }

    /**
     * Called when a bot is spawned. Sets its expiration timestamp.
     *
     * @param name the name of the spawned bot
     */
    public void onBotSpawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        long lifetimeMs = config.getLifetimeIntervalMillis();
        long expireAt = System.currentTimeMillis() + lifetimeMs;

        botExpirationTime.put(lowerName, expireAt);
        spawning.remove(lowerName);

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: %s spawned, will despawn in %d ms", name, lifetimeMs);
    }

    /**
     * Called when a bot is despawned. Removes it from the expiration map.
     *
     * @param name the name of the despawned bot
     */
    public void onBotDespawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        botExpirationTime.remove(lowerName);
        spawning.remove(lowerName);

        // Trigger maintenance to check if we need to spawn a replacement
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::maintainBotCount);
        }
    }

    /**
     * Initializes the bot system by loading the cache from the database
     * and then spawning the initial bots.
     */
    public void initializeAndSpawn() {
        manager.loadCacheFromDatabaseAsync(this::addAllBotsToDatabaseAsync);
    }

    /**
     * Pre-populates the database with default bot names if they don't already exist.
     * This runs asynchronously to avoid blocking the main thread.
     */
    private void addAllBotsToDatabaseAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.isEnabled()) return;

                String worldName = config.getBotWorldName();
                Set<String> existingNames = manager.getAllDatabaseBots().stream()
                        .map(FakePlayerData::getName)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());

                List<FakePlayerData> toAdd = new ArrayList<>();

                for (String name : BotNameProvider.getMinecraftNames()) {
                    if (!plugin.isEnabled()) return;

                    if (!existingNames.contains(name.toLowerCase())) {
                        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                        FakePlayerData data = new FakePlayerData.Builder()
                                .name(name)
                                .uuid(uuid)
                                .world(worldName)
                                .location(0, 64, 0, 0, 0)
                                .active(false)
                                .build();
                        toAdd.add(data);
                        manager.updateCache(data);
                    }
                }

                if (!toAdd.isEmpty() && plugin.isEnabled()) {
                    plugin.getFakePlayerDatabase().saveFakePlayers(toAdd);
                    DebugLogger.log(plugin.getLogger(),
                            "BotLifecycleManager: pre-populated " + toAdd.size() + " bots into database.");
                }

                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, this::spawnInitialBotsInternal);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[BotLifecycleManager] Error pre-populating database: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Spawns the initial batch of bots based on the configuration.
     */
    private void spawnInitialBotsInternal() {
        int initial = config.getBaseAmount();
        if (initial <= 0) return;

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: Spawning " + initial + " initial bots...");

        for (int i = 0; i < initial; i++) {
            long delay = i * config.getJoinQuitIntervalTicks();
            Bukkit.getScheduler().runTaskLater(plugin, this::spawnOneBot, delay);
        }
    }

    /**
     * Starts the periodic task that checks for expired bots.
     * Runs every {@value #EXPIRATION_CHECK_INTERVAL_TICKS} ticks (5 seconds).
     */
    private void startLifecycleCheck() {
        if (lifecycleCheckTask != null) {
            lifecycleCheckTask.cancel();
        }
        lifecycleCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredBots();
            }
        };
        lifecycleCheckTask.runTaskTimer(plugin, EXPIRATION_CHECK_INTERVAL_TICKS, EXPIRATION_CHECK_INTERVAL_TICKS);
        DebugLogger.log(plugin.getLogger(),
                "BotLifecycleManager: expiration check started (interval: %d ticks / %.1f seconds)",
                EXPIRATION_CHECK_INTERVAL_TICKS, EXPIRATION_CHECK_INTERVAL_TICKS / 20.0);
    }

    /**
     * Checks and despawns any bots whose expiration time has passed.
     */
    private void checkExpiredBots() {
        if (botExpirationTime.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Use a copy to avoid ConcurrentModificationException if despawn triggers removal
        for (Map.Entry<String, Long> entry : new HashMap<>(botExpirationTime).entrySet()) {
            String lowerName = entry.getKey();
            long expireAt = entry.getValue();

            if (now >= expireAt) {
                DebugLogger.log(plugin.getLogger(),
                        "BotLifecycleManager: bot '%s' expired, despawning...", lowerName);
                manager.despawnBot(lowerName);
            }
        }
    }

    /**
     * Starts the periodic task that maintains the bot count.
     * Runs every {@value #MAINTENANCE_CHECK_INTERVAL_TICKS} ticks (3 seconds).
     */
    private void startMaintenanceCheck() {
        if (maintenanceCheckTask != null) {
            maintenanceCheckTask.cancel();
        }
        maintenanceCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                maintainBotCount();
            }
        };
        maintenanceCheckTask.runTaskTimer(plugin, MAINTENANCE_CHECK_INTERVAL_TICKS, MAINTENANCE_CHECK_INTERVAL_TICKS);
        DebugLogger.log(plugin.getLogger(),
                "BotLifecycleManager: maintenance check started (interval: %d ticks / %.1f seconds)",
                MAINTENANCE_CHECK_INTERVAL_TICKS, MAINTENANCE_CHECK_INTERVAL_TICKS / 20.0);
    }

    /**
     * Ensures the number of online bots matches the target (base + dynamic percentage).
     * If the current count is below the target, triggers spawning of a new bot.
     */
    private void maintainBotCount() {
        if (!plugin.isEnabled()) return;

        int realPlayers = Bukkit.getOnlinePlayers().size();
        int botCount = manager.getOnlineBotsData().size();

        // Dynamic target based on peak hours
        int effectiveBase = config.getEffectiveBaseAmount();
        int effectivePercent = config.getEffectivePercentRate();
        int target = effectiveBase + (int) (realPlayers * effectivePercent / 100.0);

        DebugLogger.logFine(plugin.getLogger(),
                "BotLifecycleManager: maintainBotCount: botCount=%d, target=%d (base=%d, %%=%d, real=%d)",
                botCount, target, effectiveBase, effectivePercent, realPlayers);

        if (botCount < target) {
            spawnOneBot();
        }
    }

    /**
     * Spawns a single bot, either by activating an inactive one or creating a new one.
     * <p>
     * If there is an inactive bot in the database, it will be spawned.
     * Otherwise, a new name is generated and a new bot is added to the database and spawned.
     * </p>
     */
    public void spawnOneBot() {
        if (!plugin.isEnabled()) return;

        Collection<FakePlayerData> allBots = manager.getAllDatabaseBots();

        // Try to spawn an existing inactive bot first
        Optional<FakePlayerData> inactiveOpt = allBots.stream()
                .filter(d -> !d.isActive() && !spawning.contains(d.getName().toLowerCase()))
                .findFirst();

        if (inactiveOpt.isPresent()) {
            final String name = inactiveOpt.get().getName();
            if (spawning.add(name.toLowerCase())) {
                manager.spawnBotAsync(name, success -> {
                    if (!success) {
                        spawning.remove(name.toLowerCase());
                        DebugLogger.log(plugin.getLogger(),
                                "BotLifecycleManager: failed to spawn inactive bot '%s'", name);
                    }
                });
            }
            return;
        }

        // No inactive bot available, create a new one
        Set<String> usedNamesLower = new HashSet<>();
        for (FakePlayerData d : allBots) {
            usedNamesLower.add(d.getName().toLowerCase());
        }
        usedNamesLower.addAll(spawning); // Include names currently being spawned

        final String newName = BotNameProvider.getNextAvailableName(usedNamesLower);

        if (spawning.add(newName.toLowerCase())) {
            org.bukkit.Location loc = getSpawnLocation();
            if (loc != null) {
                manager.addBot(newName, loc);
                manager.spawnBotAsync(newName, success -> {
                    if (!success) {
                        spawning.remove(newName.toLowerCase());
                        DebugLogger.log(plugin.getLogger(),
                                "BotLifecycleManager: failed to spawn new bot '%s'", newName);
                    }
                });
            } else {
                spawning.remove(newName.toLowerCase());
                plugin.getLogger().warning("[BotLifecycleManager] No spawn location available, cannot spawn new bot.");
            }
        }
    }

    /**
     * Returns the spawn location for new bots, using the configured bot world.
     *
     * @return the spawn location, or {@code null} if no world is available
     */
    private org.bukkit.Location getSpawnLocation() {
        String worldName = config.getBotWorldName();
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world != null ? new org.bukkit.Location(world, 0, 64, 0) : null;
    }
}