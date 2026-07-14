package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import org.phantam.fozminesproofCore.config.ConfigManager;

import java.util.Collections;
import java.util.List;

public class AddCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public AddCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "add"; }

    @Override
    public String getDescription() { return "Thêm một Fake Player mới vào cơ sở dữ liệu (Trạng thái tĩnh, không spawn)"; }

    @Override
    public String getSyntax() { return "/sproof add <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Chỉ có người chơi trong game mới có thể sử dụng lệnh này!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Sai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];
        if (targetName.length() > 16) {
            player.sendMessage(ChatColor.RED + "Tên của Fake Player không được phép vượt quá 16 ký tự!");
            return;
        }

        // Kiểm tra xem tên bot này đã bị trùng lặp trong database của cụm server hiện tại chưa
        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(targetName));

        if (exists) {
            player.sendMessage(ChatColor.RED + "Fake Player có tên '" + targetName + "' đã tồn tại trong hệ thống dữ liệu!");
            return;
        }

        Location loc = player.getLocation();

        // Gọi Manager để đóng gói dữ liệu thô và đẩy trực tiếp xuống câu lệnh INSERT INTO của SQL
        plugin.getFakePlayerManager().addBot(targetName, loc);

        // Lấy tên thế giới Void thực tế đã được Manager ép buộc gán vào dữ liệu Bot
        ConfigManager config = plugin.getConfigManager();
        String assignedWorld = config.getBotWorldName();

        player.sendMessage(ChatColor.GREEN + "Đã khởi tạo thành công dữ liệu tĩnh cho bot " + ChatColor.YELLOW + targetName);
        player.sendMessage(ChatColor.GRAY + "Vị trí lưu trữ cố định: " + ChatColor.AQUA + assignedWorld +
                " (" + (int)loc.getX() + ", " + (int)loc.getY() + ", " + (int)loc.getZ() + ")");
        player.sendMessage(ChatColor.GRAY + "Dùng lệnh '/sproof spawn " + targetName + "' để kích hoạt mô hình hiển thị 3D.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<tên_muốn_đặt>");
        }
        return Collections.emptyList();
    }
}
