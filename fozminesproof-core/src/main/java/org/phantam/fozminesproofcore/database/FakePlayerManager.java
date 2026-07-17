package org.phantam.fozminesproofcore.database;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.actions.*;

import java.util.Collection;

public class FakePlayerManager {

    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    // Đóng gói các lớp thực thi hành động độc lập
    private final AddBotAction addAction;
    private final SpawnBotAction spawnAction;
    private final DespawnBotAction despawnAction;
    private final RemoveBotAction removeAction;
    private final ReloadSystemAction reloadAction;

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.database = database;
        this.registry = new FakePlayerRegistry();
        FakePlayerBroadcaster broadcaster = new FakePlayerBroadcaster(plugin.getConfigManager());

        // Đóng gói phụ thuộc (Dependency Injection thủ công)
        this.addAction = new AddBotAction(plugin, database);
        this.spawnAction = new SpawnBotAction(plugin, database, registry, broadcaster);
        this.despawnAction = new DespawnBotAction(plugin, database, registry, broadcaster);
        this.removeAction = new RemoveBotAction(database, this.despawnAction);
        this.reloadAction = new ReloadSystemAction(plugin, database, registry);
    }

    // --- Ủy quyền thực thi qua các Command Classes biệt lập ---

    public void addBot(String name, Location loc) {
        addAction.execute(new AddBotAction.Request(name, loc));
    }

    public boolean spawnBot(String name) {
        return spawnAction.execute(name);
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

    // --- Các hàm truy vấn dữ liệu từ Registry / Database ---

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

    /**
     * Gỡ bỏ hoàn toàn và lập tức toàn bộ FakePlayer đang trực tuyến khi tắt server (onDisable).
     * Hàm này bọc và đợi các tiến trình Async lưu Database chạy xong trước khi đóng kết nối.
     */
    public void despawnAllOnShutdown() {
        java.util.Collection<FakePlayerData> onlineData = this.getOnlineBotsData();
        if (onlineData == null || onlineData.isEmpty()) {
            return;
        }

        java.util.List<FakePlayerData> targetBots = new java.util.ArrayList<>(onlineData);
        org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Phát hiện " + targetBots.size() + " bot đang hoạt động. Tiến hành thu hồi khẩn cấp...");

        for (FakePlayerData bot : targetBots) {
            if (bot == null || bot.getName() == null) continue;

            String botName = bot.getName();

            // Gọi hàm hủy bot đơn lẻ (hàm này sinh ra tác vụ Async lưu DB)
            boolean success = this.despawnBot(botName);

            if (success) {
                org.bukkit.Bukkit.getLogger().info(" [Shutdown Cleanup] -> Đang ngắt kết nối: " + botName + " (Thành công)");
            } else {
                org.bukkit.Bukkit.getLogger().warning(" [Shutdown Cleanup] -> Đang ngắt kết nối: " + botName + " (Thất bại)");
            }
        }

        // TỐI ƯU CHÍ MẠNG TẠI ĐÂY: Trì hoãn luồng chính một khoảng thời gian ngắn (ví dụ: 500ms)
        // để đảm bảo Pool kết nối cũ (HikariPool-4) hoàn thành việc ghi dữ liệu Async xuống MySQL
        try {
            org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Đang đợi dữ liệu SQL đồng bộ hoàn tất...");
            Thread.sleep(600); // Chờ 0.6 giây để dọn sạch hàng đợi lưu SQL
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Tự động quét Database và xếp hàng nạp TOÀN BỘ Bot hiện có trong hệ thống dữ liệu
     * lên máy chủ khi khởi động (onEnable), tuân thủ dải giãn cách thời gian ngẫu nhiên từ cấu hình.
     * (Đã lược bỏ bộ lọc kiểm tra trạng thái active do bot đã bị deactivate lúc shutdown)
     */
    public void spawnAllOnStartup(org.phantam.fozminesproofcore.config.ConfigManager configManager) {
        if (configManager == null) return;

        // SỬA TẠI ĐÂY: Bỏ hoàn toàn .filter(FakePlayerData::isActive)
        // Quét và lấy ra toàn bộ danh sách tên Bot có trong Database nhưng chưa trực tuyến
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

        // Đưa toàn bộ danh sách vào hàng đợi (Queue) xử lý
        java.util.Queue<String> startupQueue = new java.util.LinkedList<>(allOfflineBots);

        // Sử dụng cấu trúc lập lịch đệ quy để tự động tạo delay ngẫu nhiên cho từng bot
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                // Điều kiện dừng: Khi hàng đợi đã rỗng
                if (startupQueue.isEmpty()) {
                    org.bukkit.Bukkit.getLogger().info("[FozmineSproof] Đã hoàn tất tiến trình tự động phục hồi toàn bộ Bot khỏi hàng đợi khởi động!");
                    return;
                }

                String nextBot = startupQueue.poll();

                // Kiểm tra phòng vệ trạng thái online trước khi phát Packet Spawn
                if (!isBotOnline(nextBot)) {
                    boolean success = spawnBot(nextBot);
                    if (success) {
                        org.bukkit.Bukkit.getLogger().info(" [Startup Spawn] -> Đang nạp lại: " + nextBot + " (Thành công)");
                    } else {
                        org.bukkit.Bukkit.getLogger().warning(" [Startup Spawn] -> Đang nạp lại: " + nextBot + " (Thất bại)");
                    }
                }

                // Lập lịch đệ quy bốc delay ngẫu nhiên mới từ Config cho bot kế tiếp
                if (!startupQueue.isEmpty()) {
                    long nextDelayTicks = configManager.getJoinQuitIntervalTicks();
                    if (nextDelayTicks <= 0) nextDelayTicks = 20L; // Phòng vệ tối thiểu 1 giây

                    final org.bukkit.scheduler.BukkitRunnable currentTask = this;
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            currentTask.run(); // Gọi lại tác vụ cha để xử lý bot tiếp theo
                        }
                    }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), nextDelayTicks);
                }
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("fozminesproof-core"), 40L); // Delay 2 giây lượt đầu
    }
}
