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
 * High-performance, adaptive Bot Lifecycle Manager.
 * Optimized for low-range/fast-paced configurations without oscillation or double-counting.
 */
public class BotLifecycleManager {

    private final FozmineSpoofCore plugin;
    private final FakePlayerManager manager;
    private final ConfigManager config;

    private final Map<String, Long> botExpirationTimes = new ConcurrentHashMap<>();

    private final Map<String, Long> despawnCooldowns = new ConcurrentHashMap<>();

    private final Set<String> spawning = ConcurrentHashMap.newKeySet();

    private final Queue<String> despawnQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> pendingDespawnSet = ConcurrentHashMap.newKeySet();

    private int pendingDelayedSpawns = 0;

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
        startLifecycleCheck();
        startMaintenanceCheck();
        startDespawnQueueProcessor();
        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: adaptive scheduler reloaded.");
    }

    /**
     * Gán chính xác thời gian sống (Session Lifetime) riêng biệt cho bot khi vừa spawn thành công.
     */
    public void onBotSpawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        long sessionLifetime = config.getLifetimeIntervalMillis();
        botExpirationTimes.put(lowerName, System.currentTimeMillis() + sessionLifetime);

        spawning.remove(lowerName);
        despawnCooldowns.remove(lowerName);

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: %s spawned (Session lifetime: %.1fs).",
                name, sessionLifetime / 1000.0);
    }

    /**
     * Xử lý khi bot despawn: gán cooldown thích ứng trước khi được phép tái xuất hiện.
     */
    public void onBotDespawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        botExpirationTimes.remove(lowerName);
        spawning.remove(lowerName);
        pendingDespawnSet.remove(lowerName);

        // Cooldown thích ứng theo range: tối đa bằng 1/2 lifetime hoặc tối thiểu 5s
        long adaptiveCooldown = Math.max(5000L, config.getLifetimeIntervalMillis() / 2);
        despawnCooldowns.put(lowerName, System.currentTimeMillis() + adaptiveCooldown);

        if (plugin.isEnabled()) {
            // Delay nhẹ trước khi cân bằng lại số lượng để tránh flapping/churn
            long delayTicks = Math.max(1L, config.getJoinQuitIntervalTicks());
            Bukkit.getScheduler().runTaskLater(plugin, this::maintainBotCount, delayTicks);
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
                            "BotLifecycleManager: pre-populated %d bots into database.", toAdd.size());
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

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: Queuing %d initial bots...", initial);

        for (int i = 0; i < initial; i++) {
            long delay = i * Math.max(1L, config.getJoinQuitIntervalTicks());
            pendingDelayedSpawns++;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingDelayedSpawns = Math.max(0, pendingDelayedSpawns - 1);

                int realPlayers = getRealPlayersCount();
                int target = config.getEffectiveBaseAmount() + (int) (realPlayers * config.getEffectivePercentRate() / 100.0);
                int currentEffectiveBots = calculateEffectiveBotCount();

                if (currentEffectiveBots < target) {
                    spawnOneBot();
                }
            }, delay);
        }
    }

    /**
     * Tần số quét tự thích ứng (Adaptive Interval):
     * Nếu lifetime dưới 60s, kiểm tra mỗi 1 giây (20 ticks). Ngược lại kiểm tra mỗi 3-5 giây.
     */
    private void startLifecycleCheck() {
        if (lifecycleCheckTask != null) {
            lifecycleCheckTask.cancel();
        }

        long checkTicks = Math.max(10L, Math.min(60L, config.getLifetimeIntervalMillis() / 1000L * 2L));

        lifecycleCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredBots();
                cleanCooldowns();
            }
        };
        lifecycleCheckTask.runTaskTimer(plugin, checkTicks, checkTicks);
    }

    private void checkExpiredBots() {
        if (botExpirationTimes.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(botExpirationTimes).entrySet()) {
            String lowerName = entry.getKey();
            long expireAt = entry.getValue();

            if (now >= expireAt) {
                DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: bot '%s' reached session end, queuing despawn.", lowerName);
                queueDespawn(lowerName);
            }
        }
    }

    private void cleanCooldowns() {
        if (despawnCooldowns.isEmpty()) return;
        long now = System.currentTimeMillis();
        despawnCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private void startMaintenanceCheck() {
        if (maintenanceCheckTask != null) {
            maintenanceCheckTask.cancel();
        }

        long checkInterval = Math.max(20L, config.getJoinQuitIntervalTicks() * 4L);

        maintenanceCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                maintainBotCount();
            }
        };
        maintenanceCheckTask.runTaskTimer(plugin, checkInterval, checkInterval);
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

    /**
     * Tính toán số lượng bot thực tế chính xác (KHÔNG double-counting).
     */
    private int calculateEffectiveBotCount() {
        int onlineCount = manager.getOnlineBotsData().size();
        return onlineCount + spawning.size() + pendingDelayedSpawns - pendingDespawnSet.size();
    }

    private void maintainBotCount() {
        if (!plugin.isEnabled()) return;

        int realPlayers = getRealPlayersCount();
        int effectiveBotCount = calculateEffectiveBotCount();
        int onlineCount = manager.getOnlineBotsData().size();

        int target = config.getEffectiveBaseAmount() + (int) (realPlayers * config.getEffectivePercentRate() / 100.0);

        DebugLogger.logFine(plugin.getLogger(),
                "BotLifecycleManager: maintainBotCount -> Effective=%d (Online=%d, Spawning=%d, PendingSpawn=%d, PendingDespawn=%d) | Target=%d",
                effectiveBotCount, onlineCount, spawning.size(), pendingDelayedSpawns, pendingDespawnSet.size(), target);

        if (effectiveBotCount < target) {
            int needed = target - effectiveBotCount;
            for (int i = 0; i < needed; i++) {
                spawnOneBot();
            }
        } else if (onlineCount > target && pendingDespawnSet.isEmpty()) {
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

        if (pendingDespawnSet.add(lowerName)) {
            despawnQueue.add(lowerName);
        }
    }

    private void startDespawnQueueProcessor() {
        if (despawnQueueTask != null) {
            despawnQueueTask.cancel();
        }

        long intervalTicks = Math.max(1L, config.getJoinQuitIntervalTicks());

        despawnQueueTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (despawnQueue.isEmpty()) return;

                String name = despawnQueue.poll();
                if (name != null) {
                    DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: executing sequential quit for bot '%s'", name);
                    manager.despawnBot(name);
                }
            }
        };
        despawnQueueTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    /**
     * Spawn 1 bot với thuật toán ưu tiên xoay vòng (Round-robin / Adaptive fallback)
     * Tránh tuyệt đối việc tự sinh bot rác dạng Bot1, Bot2 khi range thấp.
     */
    public void spawnOneBot() {
        if (!plugin.isEnabled()) return;

        Collection<FakePlayerData> allBots = manager.getAllDatabaseBots();
        long now = System.currentTimeMillis();

        List<FakePlayerData> availableInactiveBots = allBots.stream()
                .filter(d -> !d.isActive())
                .filter(d -> !spawning.contains(d.getName().toLowerCase()))
                .filter(d -> !pendingDespawnSet.contains(d.getName().toLowerCase()))
                .filter(d -> {
                    Long coolUntil = despawnCooldowns.get(d.getName().toLowerCase());
                    return coolUntil == null || now >= coolUntil;
                })
                .collect(Collectors.toList());

        if (availableInactiveBots.isEmpty()) {
            availableInactiveBots = allBots.stream()
                    .filter(d -> !d.isActive())
                    .filter(d -> !spawning.contains(d.getName().toLowerCase()))
                    .filter(d -> !pendingDespawnSet.contains(d.getName().toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (!availableInactiveBots.isEmpty()) {
            Collections.shuffle(availableInactiveBots);
            final String name = availableInactiveBots.get(0).getName();

            if (spawning.add(name.toLowerCase())) {
                manager.spawnBotAsync(name, success -> {
                    if (!success) {
                        spawning.remove(name.toLowerCase());
                        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: failed to spawn inactive bot '%s'", name);
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
                        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: failed to spawn new bot '%s'", newName);
                    }
                });
            } else {
                spawning.remove(newName.toLowerCase());
                plugin.getLogger().warning("[BotLifecycleManager] No spawn location available.");
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