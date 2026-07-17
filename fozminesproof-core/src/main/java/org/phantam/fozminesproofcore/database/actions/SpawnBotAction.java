package org.phantam.fozminesproofcore.database.actions;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.ColorUtils;
import java.util.Optional;

public class SpawnBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;

    public SpawnBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
                          FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    @Override
    public Boolean execute(String name) {
        Optional<FakePlayerData> opt = database.loadFakePlayer(name);
        if (opt.isEmpty()) return false;

        FakePlayerData data = opt.get();
        data.setActive(true);
        database.saveFakePlayer(data);

        Player entity = internalNmsSpawn(data);

        // Xử lý định dạng tên hiển thị trên Tablist cho bot khi vừa spawn
        if (entity != null) {
            String rawTabFormat = plugin.getConfigManager().getTabFormat();
            String formattedTabName = rawTabFormat.replace("%fakeplayer_name%", data.getName());

            // Biên dịch PlaceholderAPI (Sử dụng biến vạn năng %fake_...% thông qua chính thực thể bot)
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                formattedTabName = PlaceholderAPI.setPlaceholders(entity, formattedTabName);
            }

            // Chuyển đổi mã màu cổ điển và mã HEX, sau đó gán vào danh sách tablist
            entity.setPlayerListName(ColorUtils.colorize(formattedTabName));
        }

        registry.register(data, entity);

        broadcaster.broadcastJoin(data.getName());
        return true;
    }

    private Player internalNmsSpawn(FakePlayerData data) {
        if (plugin.getBridge() == null) return null;

        // SỬA TẠI ĐÂY: Không ép đọc từ ConfigManager nữa, mà lấy đúng tên thế giới lưu trong DB của Bot
        String targetWorldName = data.getWorld();

        // Phòng vệ: Nếu dữ liệu thế giới trong DB trống hoặc null, mới lấy thế giới mặc định/cấu hình để cứu vãn
        if (targetWorldName == null || targetWorldName.trim().isEmpty()) {
            targetWorldName = plugin.getConfigManager().getBotWorldName();
        }

        org.bukkit.World world = Bukkit.getWorld(targetWorldName);

        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }

        if (world == null) return null;

        // Khởi tạo tọa độ đặt Bot đồng bộ hoàn toàn giữa hiển thị 3D và bản thể NMS gốc
        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());

        // Biện pháp đặt block nâng đỡ chống rơi tự do nếu Bot ở tọa độ không khí
        Location blockUnderLoc = loc.clone().subtract(0, 1, 0);
        org.bukkit.block.Block blockUnder = blockUnderLoc.getBlock();
        if (blockUnder.getType() == org.bukkit.Material.AIR || blockUnder.getType() == org.bukkit.Material.CAVE_AIR) {
            blockUnder.setType(org.bukkit.Material.BEDROCK);
        }

        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
    }

}
