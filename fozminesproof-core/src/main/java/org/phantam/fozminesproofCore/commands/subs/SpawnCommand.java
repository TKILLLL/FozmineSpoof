package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import org.phantam.fozminesproofApi.database.FakePlayerData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SpawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public SpawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() { return "Kích hoạt hiển thị (spawn) một Fake Player cụ thể từ database vào server"; }

    @Override
    public String getSyntax() { return "/sproof spawn <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Sai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        // 1. Kiểm tra xem bot có đang hoạt động sẵn trên máy chủ chưa (Tránh tạo đè)
        if (plugin.getFakePlayerManager().isBotOnline(targetName)) {
            sender.sendMessage(ChatColor.RED + "Fake Player này hiện đã trực tuyến (ONLINE) trên máy chủ!");
            return;
        }

        // 2. Tìm kiếm dữ liệu thô trong danh sách tổng của Database trước khi kích hoạt
        Optional<FakePlayerData> botDataOpt = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .filter(bot -> bot.getName().equalsIgnoreCase(targetName))
                .findFirst();

        if (botDataOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Không tìm thấy dữ liệu Fake Player nào có tên '" + targetName + "' trong database.");
            sender.sendMessage(ChatColor.GRAY + "Mẹo: Hãy sử dụng lệnh '/sproof add " + targetName + "' tại vị trí của bạn để tạo bot trước.");
            return;
        }

        FakePlayerData data = botDataOpt.get();
        World world = Bukkit.getWorld(data.getWorld());

        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Thế giới '" + data.getWorld() + "' lưu trong dữ liệu của bot hiện không được nạp (Unloaded/Missing)!");
            return;
        }

        // 3. Kích hoạt trạng thái hoạt động trong Manager (Cập nhật cột is_active = true xuống SQL)
        plugin.getFakePlayerManager().spawnBot(targetName);

        // 4. Khởi tạo đối tượng Location từ dữ liệu thô SQL
        Location spawnLoc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());

        // 5. Gọi NMS Bridge thông qua API của bạn với đúng 3 tham số thiết kế rời rạc công khai
        plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), spawnLoc);

        sender.sendMessage(ChatColor.GREEN + "Đã gọi NMS kích hoạt hiển thị thành công Fake Player: " + ChatColor.YELLOW + data.getName());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            // Tự động gợi ý những bot có trong Database nhưng hiện tại đang OFFLINE (chờ spawn)
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
