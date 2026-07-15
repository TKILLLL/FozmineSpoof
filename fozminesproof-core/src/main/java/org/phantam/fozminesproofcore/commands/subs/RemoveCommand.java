package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RemoveCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public RemoveCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "remove"; }

    @Override
    public String getDescription() { return "Xóa hoàn toàn một Fake Player khỏi hệ thống và cơ sở dữ liệu"; }

    @Override
    public String getSyntax() { return "/sproof remove <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Sai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        // 1. Kiểm tra xem bot có tồn tại trong danh sách tổng của Database không
        boolean hasBot = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(targetName));

        if (!hasBot) {
            sender.sendMessage(ChatColor.RED + "Không tìm thấy dữ liệu Fake Player nào có tên là: " + targetName);
            return;
        }

        // 2. Thực hiện xóa bot thông qua Manager (Hàm này tự động gọi despawn NMS và DELETE SQL)
        plugin.getFakePlayerManager().removeBot(targetName);

        sender.sendMessage(ChatColor.GREEN + "Đã xóa hoàn toàn Fake Player " + ChatColor.YELLOW + targetName + ChatColor.GREEN + " ra khỏi máy chủ và cơ sở dữ liệu!");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            // Tự động gợi ý chuẩn xác tên của tất cả các Fake Player đã tạo trong database
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
