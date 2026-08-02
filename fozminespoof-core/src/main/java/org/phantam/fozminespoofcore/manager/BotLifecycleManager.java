package org.phantam.fozminespoofcore.manager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.utils.BotNameProvider;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BotLifecycleManager {

    private final FozmineSpoofCore plugin;
    private final FakePlayerManager manager;
    private final ConfigManager config;

    private final Map<String, Long> botExpirationTime = new ConcurrentHashMap<>();
    private final Set<String> spawning = ConcurrentHashMap.newKeySet();

    public BotLifecycleManager(FozmineSpoofCore plugin, FakePlayerManager manager, ConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;

        startLifecycleCheck();
        startMaintenanceCheck();
    }

    /**
     * Khi reload config: Tự động reset và tính lại mốc thời gian hết hạn
     * cho toàn bộ các bot đang online theo config lifetime-interval mới.
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

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: reloaded. Recalculated lifetime for %d active online bots.", count);
    }

    public void onBotSpawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        long lifetimeMs = config.getLifetimeIntervalMillis();
        long expireAt = System.currentTimeMillis() + lifetimeMs;

        botExpirationTime.put(lowerName, expireAt);
        spawning.remove(lowerName);

        DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: %s spawned, will despawn in %d ms", name, lifetimeMs);
    }

    public void onBotDespawn(String name) {
        if (name == null) return;
        String lowerName = name.toLowerCase();

        botExpirationTime.remove(lowerName);
        spawning.remove(lowerName);

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
                    DebugLogger.log(plugin.getLogger(), "BotLifecycleManager: pre-populated " + toAdd.size() + " bots into database.");
                }

                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, this::spawnInitialBotsInternal);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void spawnInitialBotsInternal() {
        int initial = config.getBaseAmount();
        if (initial <= 0) return;

        DebugLogger.log(plugin.getLogger(), "Spawning " + initial + " initial bots...");

        for (int i = 0; i < initial; i++) {
            long delay = i * config.getJoinQuitIntervalTicks();
            Bukkit.getScheduler().runTaskLater(plugin, this::spawnOneBot, delay);
        }
    }

    /**
     * Vòng lặp kiểm tra bot hết hạn với tần số 1 tick (50ms) - Độ chính xác từng miligiây.
     */
    private void startLifecycleCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredBots();
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1 tick precision
    }

    private void checkExpiredBots() {
        if (botExpirationTime.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(botExpirationTime).entrySet()) {
            String lowerName = entry.getKey();
            long expireAt = entry.getValue();

            if (now >= expireAt) {
                manager.despawnBot(lowerName);
            }
        }
    }

    private void startMaintenanceCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                maintainBotCount();
            }
        }.runTaskTimer(plugin, 60L, 60L); // Periodic fallback check every 3s
    }

    private void maintainBotCount() {
        if (!plugin.isEnabled()) return;

        int realPlayers = Bukkit.getOnlinePlayers().size();
        int botCount = manager.getOnlineBotsData().size();

        int effectiveBase = config.getEffectiveBaseAmount();
        int effectivePercent = config.getEffectivePercentRate();

        int target = effectiveBase + (int) (realPlayers * effectivePercent / 100.0);

        if (botCount < target) {
            spawnOneBot();
        }
    }

    public void spawnOneBot() {
        if (!plugin.isEnabled()) return;

        Collection<FakePlayerData> allBots = manager.getAllDatabaseBots();

        Optional<FakePlayerData> inactiveOpt = allBots.stream()
                .filter(d -> !d.isActive() && !spawning.contains(d.getName().toLowerCase()))
                .findFirst();

        if (inactiveOpt.isPresent()) {
            final String name = inactiveOpt.get().getName();
            if (spawning.add(name.toLowerCase())) {
                manager.spawnBotAsync(name, success -> {
                    if (!success) spawning.remove(name.toLowerCase());
                });
            }
        } else {
            Set<String> usedNamesLower = new HashSet<>();
            for (FakePlayerData d : allBots) {
                usedNamesLower.add(d.getName().toLowerCase());
            }
            for (String sp : spawning) {
                usedNamesLower.add(sp.toLowerCase());
            }

            final String newName = BotNameProvider.getNextAvailableName(usedNamesLower);

            if (spawning.add(newName.toLowerCase())) {
                org.bukkit.Location loc = getSpawnLocation();
                if (loc != null) {
                    manager.addBot(newName, loc);
                    manager.spawnBotAsync(newName, success -> {
                        if (!success) spawning.remove(newName.toLowerCase());
                    });
                } else {
                    spawning.remove(newName.toLowerCase());
                }
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