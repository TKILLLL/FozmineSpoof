package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.utils.BotNameProvider;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of fake players, including spawning, despawning, and expiration.
 */
public class BotLifecycleManager {

    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 100L;
    private static final long MAINTENANCE_CHECK_INTERVAL_TICKS = 60L;

    private static final long DESPAWN_COOLDOWN_MS = 60000L;

    private final FozmineSpoofCore plugin;
    private final FakePlayerManager manager;
    private final ConfigManager config;

    private final Map<String, Long> botSpawnTime = new ConcurrentHashMap<>();

    private final Map<String, Long> despawnCooldowns = new ConcurrentHashMap<>();

    private final Set<String> spawning = ConcurrentHashMap.newKeySet();

    private int pendingDelayedSpawns = 0;
    private int pendingDelayedDespawns = 0;
    private final Queue<String> despawnQueue = new ConcurrentLinkedQueue<>();

    private BukkitRunnable lifecycleCheckTask;
    private BukkitRunnable maintenanceCheckTask;
    private BukkitRunnable despawnQueueTask;

    public BotLifecycleManager(FozmineSpoofCore plugin, FakePlayerManager manager, ConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;

        startLifecycleCheck();
        startMaintenanceCheck();
        startDespawnQueueProcessor();
    }

    public void reload() {
        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: reloaded config settings successfully.");
    }

    public void onBotSpawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        botSpawnTime.put(lowerName, System.currentTimeMillis());
        spawning.remove(lowerName);
        despawnCooldowns.remove(lowerName);

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: %s spawned successfully.", name);
    }

    public void onBotDespawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        botSpawnTime.remove(lowerName);
        spawning.remove(lowerName);

        despawnCooldowns.put(lowerName, System.currentTimeMillis());

        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::maintainBotCount);
        }
    }

    public void initializeAndSpawn() {
        manager.loadCacheFromDatabaseAsync(this::addAllBotsToDatabaseAsync);
    }

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

    private void spawnInitialBotsInternal() {
        int initial = config.getBaseAmount();
        if (initial <= 0) return;

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: Queuing " + initial + " initial bots...");

        for (int i = 0; i < initial; i++) {
            long delay = i * config.getJoinQuitIntervalTicks();
            pendingDelayedSpawns++;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingDelayedSpawns--;

                int realPlayers = getRealPlayersCount();
                int target = config.getEffectiveBaseAmount() + (int) (realPlayers * config.getEffectivePercentRate() / 100.0);
                int currentBots = manager.getOnlineBotsData().size() + spawning.size();

                if (currentBots < target) {
                    spawnOneBot();
                }
            }, delay);
        }
    }

    private void startLifecycleCheck() {
        if (lifecycleCheckTask != null) {
            lifecycleCheckTask.cancel();
        }
        lifecycleCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredBots();
                cleanCooldowns();
            }
        };
        lifecycleCheckTask.runTaskTimer(plugin, EXPIRATION_CHECK_INTERVAL_TICKS, EXPIRATION_CHECK_INTERVAL_TICKS);
    }

    private void checkExpiredBots() {
        if (botSpawnTime.isEmpty()) return;

        long now = System.currentTimeMillis();
        long currentLifetimeMs = config.getLifetimeIntervalMillis();

        for (Map.Entry<String, Long> entry : new HashMap<>(botSpawnTime).entrySet()) {
            String lowerName = entry.getKey();
            long spawnTime = entry.getValue();

            if (now >= spawnTime + currentLifetimeMs) {
                DebugLogger.log(plugin.getLogger(),
                        "BotLifecycleManager: bot '%s' expired, pushing to despawn queue...", lowerName);
                queueDespawn(lowerName);
            }
        }
    }

    private void cleanCooldowns() {
        if (despawnCooldowns.isEmpty()) return;
        long now = System.currentTimeMillis();
        despawnCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > DESPAWN_COOLDOWN_MS);
    }

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
    }

    private int getRealPlayersCount() {
        int realPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && !manager.isBotOnline(player.getName())) {
                realPlayers++;
            }
        }
        return realPlayers;
    }

    private void maintainBotCount() {
        if (!plugin.isEnabled()) return;

        int realPlayers = getRealPlayersCount();
        int onlineCount = manager.getOnlineBotsData().size();

        int botCount = onlineCount + spawning.size() + pendingDelayedSpawns - pendingDelayedDespawns - despawnQueue.size();

        int effectiveBase = config.getEffectiveBaseAmount();
        int effectivePercent = config.getEffectivePercentRate();
        int target = effectiveBase + (int) (realPlayers * effectivePercent / 100.0);

        DebugLogger.logFine(plugin.getLogger(),
                "BotLifecycleManager: maintainBotCount: NetCalcBots=%d (online=%d, spawning=%d, pendingSpawn=%d, pendingDespawn=%d), target=%d",
                botCount, onlineCount, spawning.size(), pendingDelayedSpawns, despawnQueue.size() + pendingDelayedDespawns, target);

        if (botCount < target) {
            spawnOneBot();
        } else if (onlineCount > target && despawnQueue.isEmpty() && pendingDelayedDespawns == 0) {
            queueExcessBotsForDespawn(onlineCount - target);
        }
    }

    private void queueExcessBotsForDespawn(int excessCount) {
        Collection<FakePlayerData> onlineBots = manager.getOnlineBotsData();
        if (onlineBots == null || onlineBots.isEmpty()) return;

        int count = 0;
        for (FakePlayerData bot : onlineBots) {
            if (count >= excessCount) break;
            if (bot != null && bot.getName() != null) {
                queueDespawn(bot.getName());
                count++;
            }
        }
    }

    private void queueDespawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        if (!despawnQueue.contains(lowerName)) {
            despawnQueue.add(lowerName);
            pendingDelayedDespawns++;
        }
    }

    private void startDespawnQueueProcessor() {
        if (despawnQueueTask != null) {
            despawnQueueTask.cancel();
        }
        despawnQueueTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (despawnQueue.isEmpty()) return;

                String name = despawnQueue.poll();
                if (name != null) {
                    pendingDelayedDespawns = Math.max(0, pendingDelayedDespawns - 1);
                    DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: executing scheduled quit for bot '%s'", name);
                    manager.despawnBot(name);
                }
            }
        };
        long intervalTicks = Math.max(1L, config.getJoinQuitIntervalTicks());
        despawnQueueTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    /**
     * NÂNG CẤP ĐỘ ĐÃ DẠNG (MAXIMUM DIVERSITY):
     * Xáo trộn danh sách bot trong database để chọn ngẫu nhiên các bot chưa hoạt động và không nằm trong vùng cooldown.
     */
    public void spawnOneBot() {
        if (!plugin.isEnabled()) return;

        Collection<FakePlayerData> allBots = manager.getAllDatabaseBots();
        long now = System.currentTimeMillis();

        List<FakePlayerData> availableInactiveBots = allBots.stream()
                .filter(d -> !d.isActive())
                .filter(d -> !spawning.contains(d.getName().toLowerCase()))
                .filter(d -> !despawnQueue.contains(d.getName().toLowerCase()))
                .filter(d -> {
                    Long coolTime = despawnCooldowns.get(d.getName().toLowerCase());
                    return coolTime == null || (now - coolTime > DESPAWN_COOLDOWN_MS);
                })
                .collect(Collectors.toList());

        if (!availableInactiveBots.isEmpty()) {
            Collections.shuffle(availableInactiveBots);
            final String name = availableInactiveBots.get(0).getName();

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

        Set<String> usedNamesLower = new HashSet<>();
        for (FakePlayerData d : allBots) {
            usedNamesLower.add(d.getName().toLowerCase());
        }
        usedNamesLower.addAll(spawning);

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

    private org.bukkit.Location getSpawnLocation() {
        String worldName = config.getBotWorldName();
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world != null ? new org.bukkit.Location(world, 0, 64, 0) : null;
    }
}