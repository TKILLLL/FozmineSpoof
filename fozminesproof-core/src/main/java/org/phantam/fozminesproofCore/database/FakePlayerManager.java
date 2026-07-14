package org.phantam.fozminesproofCore.database;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.phantam.fozminesproofApi.database.FakePlayerData;
import org.phantam.fozminesproofApi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import org.phantam.fozminesproofCore.config.ConfigManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerManager {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;

    private final Map<String, FakePlayerData> onlineBots = new ConcurrentHashMap<>();

    public FakePlayerManager(FozmineSproofCore plugin, IFakePlayerDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Lệnh ADD: Khởi tạo và lưu thông tin thô của bot xuống database (Trạng thái tĩnh, không spawn)
     * Cập nhật mới: Ép buộc tọa độ cố định về vị trí trung tâm 0, 64, 0 tại thế giới Void (botworld).
     * (Sử dụng Y = 64 làm độ cao mặc định chuẩn của Minecraft để bot không bị rơi vào hư vô void nếu có khối block bên dưới).
     */
    public void addBot(String name, Location loc) {
        ConfigManager config = plugin.getConfigManager();
        String targetWorld = config.getBotWorldName(); // Lấy tên thế giới từ config (mặc định: botworld)

        FakePlayerData data = new FakePlayerData(
                name,
                UUID.randomUUID(),
                targetWorld,   // Tên thế giới: botworld
                0.0,           // Ép trục X về 0
                64.0,          // Ép trục Y về 64 (độ cao an toàn của thế giới trống)
                0.0,           // Ép trục Z về 0
                0.0f,          // Hướng nhìn ngang (Yaw) mặc định về 0
                0.0f,          // Hướng nhìn lên/xuống (Pitch) mặc định về 0
                false          // Cờ hoạt động tĩnh (Chưa spawn)
        );
        database.saveFakePlayer(data);
    }

    /**
     * Lệnh SPAWN: Cập nhật cờ hoạt động xuống SQL, nạp vào Cache RAM và gọi NMS hiển thị mô hình 3D
     * Cập nhật: Tự động gửi thông điệp thông báo Join Game toàn máy chủ khi Spawn Bot thành công.
     */
    public boolean spawnBot(String name) {
        Optional<FakePlayerData> opt = database.loadFakePlayer(name);
        if (opt.isEmpty()) return false;

        FakePlayerData data = opt.get();
        data.setActive(true);
        database.saveFakePlayer(data);
        onlineBots.put(data.getName().toLowerCase(), data);

        World world = Bukkit.getWorld(data.getWorld());
        if (world != null && plugin.getBridge() != null) {
            Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());

            plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);

            // BẮT ĐẦU XỬ LÝ PHÁT TIN NHẮN JOIN GAME TOÀN SERVER
            ConfigManager config = plugin.getConfigManager();
            if (config.isJoinLeaveMessageEnable() && config.getJoinMessage() != null) {
                String msg = config.getJoinMessage().replace("%fakeplayer_name%", data.getName());
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
        }
        return true;
    }

    /**
     * Lệnh DESPAWN: Ẩn thực thể NPC khỏi máy chủ, cập nhật trạng thái Offline xuống SQL dữ liệu
     * Cập nhật: Tự động gửi thông điệp thông báo Left Game toàn máy chủ khi Despawn Bot thành công.
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

        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(data.getUuid());

            // BẮT ĐẦU XỬ LÝ PHÁT TIN NHẮN LEAVE GAME TOÀN SERVER
            ConfigManager config = plugin.getConfigManager();
            if (config.isJoinLeaveMessageEnable() && config.getLeaveMessage() != null) {
                String msg = config.getLeaveMessage().replace("%fakeplayer_name%", data.getName());
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
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
        for (String botName : new HashSet<>(onlineBots.keySet())) {
            this.despawnBot(botName);
        }
        onlineBots.clear();

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
