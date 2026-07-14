package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofApi.database.FakePlayerData;
import org.phantam.fozminesproofCore.FozmineSproofCore;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DespawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public DespawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "despawn"; }

    @Override
    public String getDescription() { return "Ẩn (despawn) một Fake Player cụ thể đang trực tuyến khỏi máy chủ"; }

    @Override
    public String getSyntax() { return "/sproof despawn <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Sai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        // 1. Kiểm tra xem bot có đang thực sự online trên server không
        if (!plugin.getFakePlayerManager().isBotOnline(targetName)) {
            sender.sendMessage(ChatColor.RED + "Fake Player này hiện không trực tuyến (OFFLINE) hoặc không tồn tại!");
            return;
        }

        // 2. Kích hoạt thu hồi qua Manager.
        // Hàm này tự xử lý xóa cache RAM, gỡ packet thực thể 3D qua NMS, cập nhật database và phát thông báo Leave cấu hình từ config.
        boolean success = plugin.getFakePlayerManager().despawnBot(targetName);

        if (success) {
            sender.sendMessage(ChatColor.YELLOW + "Đã thu hồi gói tin NMS, hủy hiển thị thành công Fake Player: " + ChatColor.GOLD + targetName);
        } else {
            sender.sendMessage(ChatColor.RED + "Có lỗi xảy ra trong quá trình hủy kích hoạt thực thể!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
