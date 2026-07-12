package org.phantam.fozminesproofCore.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.phantam.fozminesproofApi.database.FakePlayerData;
import org.phantam.fozminesproofApi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofCore.FozmineSproofCore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerManager {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;

    // Sử dụng ConcurrentHashMap để an toàn khi đa luồng (Thread-safe) trong hệ thống mạng Async
    private final Map<String, FakePlayerData> onlineBots = new ConcurrentHashMap<>();

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Lệnh ADD: Khởi tạo và lưu thông tin thô của bot xuống database (Trạng thái tĩnh, không spawn)
     */
    public void addBot(String name, Location loc) {
        FakePlayerData data = new FakePlayerData(
                name,
                UUID.randomUUID(),
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                false
        );
        database.saveFakePlayer(data);
    }

    /**
     * Lệnh SPAWN: Cập nhật cờ hoạt động xuống SQL, nạp vào Cache RAM và gọi NMS hiển thị mô hình 3D
     */
    public boolean spawnBot(String name) {
        Optional<FakePlayerData> opt = database.loadFakePlayer(name);
        if (opt.isEmpty()) return false;

        FakePlayerData data = opt.get();
        data.setActive(true);
        database.saveFakePlayer(data);
        onlineBots.put(data.getName().toLowerCase(), data);

        // Xử lý an toàn không gian thế giới trước khi gọi NMS Bridge gửi gói tin
        World world = Bukkit.getWorld(data.getWorld());
        if (world != null && plugin.getBridge() != null) {
            Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());

            // Gọi trực tiếp cầu nối đa phiên bản thông qua Interface API để kích hoạt Factory NMS
            plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
        }
        return true;
    }

    /**
     * Lệnh DESPAWN: Ẩn thực thể NPC khỏi máy chủ, cập nhật trạng thái Offline xuống SQL dữ liệu
     */
    public boolean despawnBot(String name) {
        FakePlayerData data = onlineBots.remove(name.toLowerCase());
        if (data == null) {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) return false;
            data = opt.get();
        }

        data.setActive(false);
        database.saveFakePlayer(data);

        // Gọi API NMS Bridge để gửi packet giải phóng thực thể ra khỏi bộ nhớ máy chủ dựa theo UUID gốc
        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(data.getUuid());
        }
        return true;
    }

    /**
     * Lệnh REMOVE: Ẩn thực thể ra khỏi không gian hiển thị RAM và xóa bản ghi vĩnh viễn khỏi Database
     */
    public boolean removeBot(String name) {
        this.despawnBot(name);
        database.deleteFakePlayer(name);
        return true;
    }

    /**
     * Lệnh RELOAD: Dọn bộ nhớ RAM tạm và nạp tuần tự lại các bot được đánh dấu Active = 1 từ SQL
     */
    public void reloadSystem() {
        // Thu hồi toàn bộ các bot đang hiển thị trước khi xóa trắng bộ nhớ đệm
        for (String botName : new HashSet<>(onlineBots.keySet())) {
            this.despawnBot(botName);
        }
        onlineBots.clear();

        // Đồng bộ nạp lại trạng thái thực tế từ các bản ghi được cấu hình hoạt động trong SQL
        for (FakePlayerData data : database.loadAllPlayers()) {
            if (data.isActive()) {
                onlineBots.put(data.getName().toLowerCase(), data);

                World world = Bukkit.getWorld(data.getWorld());
                if (world != null && plugin.getBridge() != null) {
                    Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
                    plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
                }
            }
        }
    }

    public Collection<FakePlayerData> getAllDatabaseBots() {
        return database.loadAllPlayers();
    }

    public boolean isBotOnline(String name) {
        return onlineBots.containsKey(name.toLowerCase());
    }
}
