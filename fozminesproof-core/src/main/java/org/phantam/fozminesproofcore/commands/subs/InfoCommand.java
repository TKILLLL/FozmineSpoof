package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InfoCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public InfoCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Kiểm tra thông tin chi tiết của một Fake Player";
    }

    @Override
    public String getSyntax() {
        return "/sproof info <tên>";
    }

    @Override
    public String getPermission() {
        return "fozminesproof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Sai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        // 1. Tìm kiếm dữ liệu bot trong danh sách tổng của Database cho cụm server hiện tại
        Optional<FakePlayerData> botDataOpt = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .filter(bot -> bot.getName().equalsIgnoreCase(targetName))
                .findFirst();

        if (botDataOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Không tìm thấy dữ liệu Fake Player nào có tên '" + targetName + "' trong database.");
            return;
        }

        FakePlayerData bot = botDataOpt.get();

        // 2. Kiểm tra trạng thái hoạt động trực tuyến thực tế trên RAM
        boolean isOnline = plugin.getFakePlayerManager().isBotOnline(bot.getName());
        String status = isOnline ? ChatColor.GREEN + "ONLINE (Đang hiển thị)" : ChatColor.RED + "OFFLINE (Đang ẩn)";

        // 3. Xuất giao diện thông tin chi tiết một cách trực quan, sạch sẽ
        sender.sendMessage(ChatColor.GOLD + "=== THÔNG TIN FAKE PLAYER ===");
        sender.sendMessage(ChatColor.YELLOW + "• Tên hiển thị: " + ChatColor.AQUA + bot.getName());
        sender.sendMessage(ChatColor.YELLOW + "• UUID định danh: " + ChatColor.GRAY + bot.getUuid().toString());
        sender.sendMessage(ChatColor.YELLOW + "• Trạng thái hoạt động: " + status);
        sender.sendMessage(ChatColor.YELLOW + "• Không gian xuất hiện: ");
        sender.sendMessage(ChatColor.GRAY + "  - Thế giới: " + ChatColor.WHITE + bot.getWorld());
        sender.sendMessage(ChatColor.GRAY + "  - Tọa độ X: " + ChatColor.WHITE + String.format("%.4f", bot.getX()));
        sender.sendMessage(ChatColor.GRAY + "  - Tọa độ Y: " + ChatColor.WHITE + String.format("%.4f", bot.getY()));
        sender.sendMessage(ChatColor.GRAY + "  - Tọa độ Z: " + ChatColor.WHITE + String.format("%.4f", bot.getZ()));
        sender.sendMessage(ChatColor.GRAY + "  - Góc Yaw:  " + ChatColor.WHITE + String.format("%.1f", bot.getYaw()));
        sender.sendMessage(ChatColor.GRAY + "  - Góc Pitch: " + ChatColor.WHITE + String.format("%.1f", bot.getPitch()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            // Tự động gợi ý tên của tất cả các Fake Player hiện có trong bảng dữ liệu để tra cứu nhanh
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
