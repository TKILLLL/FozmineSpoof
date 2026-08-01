package org.phantam.fozminesproofcore.database;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.actions.*;

import java.util.Collection;
import java.util.function.Consumer;

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

    // --- HÀNH ĐỘNG ĐỒNG BỘ (giữ nguyên để tương thích) ---

    public void addBot(String name, Location loc) {
        addAction.execute(new AddBotAction.Request(name, loc));
    }

    /**
     * Phương thức spawn đồng bộ (blocking) – dùng CompletableFuture với timeout.
     * Khuyến cáo dùng spawnBotAsync để tránh lag.
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
     * Phương thức spawn bất đồng bộ – gọi AsyncPlayerPreLoginEvent từ luồng async.
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

    // --- TRUY VẤN ---

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

    // --- SHUTDOWN ---

    public void despawnAllOnShutdown() {
        Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) return;

        java.util.List<FakePlayerData> targetBots = new java.util.ArrayList<>(onlineData);
        org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Phát hiện " + targetBots.size() + " bot đang hoạt động. Tiến hành thu hồi khẩn cấp...");

        for (FakePlayerData bot : targetBots) {
            if (bot == null || bot.getName() == null) continue;
            boolean success = this.despawnBot(bot.getName());
            if (success) {
                org.bukkit.Bukkit.getLogger().info(" [Shutdown Cleanup] -> Đang ngắt kết nối: " + bot.getName() + " (Thành công)");
            } else {
                org.bukkit.Bukkit.getLogger().warning(" [Shutdown Cleanup] -> Đang ngắt kết nối: " + bot.getName() + " (Thất bại)");
            }
        }

        try {
            org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Đang đợi dữ liệu SQL đồng bộ hoàn tất...");
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- STARTUP ---

    public void spawnAllOnStartup(org.phantam.fozminesproofcore.config.ConfigManager configManager) {
        if (configManager == null) return;

        java.util.List<String> allOfflineBots = this.getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !this.isBotOnline(name))
                .collect(java.util.stream.Collectors.toList());

        if (allOfflineBots.isEmpty()) {
            org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Không tìm thấy dữ liệu Bot nào dưới Database để nạp.");
            return;
        }

        org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Phát hiện " + allOfflineBots.size()
                + " Bot trong cơ sở dữ liệu. Bắt đầu xếp hàng nạp tự động...");

        java.util.Queue<String> startupQueue = new java.util.LinkedList<>(allOfflineBots);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (startupQueue.isEmpty()) {
                    org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Đã hoàn tất tiến trình tự động phục hồi toàn bộ Bot khỏi hàng đợi khởi động!");
                    return;
                }

                String nextBot = startupQueue.poll();

                if (!isBotOnline(nextBot)) {
                    spawnBotAsync(nextBot, success -> {
                        if (success) {
                            org.bukkit.Bukkit.getLogger().info(" [Startup Spawn] -> Đang nạp lại: " + nextBot + " (Thành công)");
                        } else {
                            org.bukkit.Bukkit.getLogger().warning(" [Startup Spawn] -> Đang nạp lại: " + nextBot + " (Thất bại)");
                        }
                    });
                }

                if (!startupQueue.isEmpty()) {
                    long nextDelayTicks = configManager.getJoinQuitIntervalTicks();
                    if (nextDelayTicks <= 0) nextDelayTicks = 20L;

                    final BukkitRunnable currentTask = this;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            currentTask.run();
                        }
                    }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), nextDelayTicks);
                }
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), 40L);
    }
}