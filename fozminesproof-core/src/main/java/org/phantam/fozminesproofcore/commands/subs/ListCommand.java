package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ListCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public ListCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "Xem toàn bộ danh sách Fake Player đã khởi tạo trong database";
    }

    @Override
    public String getSyntax() {
        return "/sproof list";
    }

    @Override
    public String getPermission() {
        return "fozminesproof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Lấy toàn bộ danh sách bản ghi từ bảng SQL riêng biệt của cụm server hiện tại
        Collection<FakePlayerData> bots = plugin.getFakePlayerManager().getAllDatabaseBots();

        if (bots.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "[FozmineSproof] Cơ sở dữ liệu trống! Không tìm thấy Fake Player nào.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== DANH SÁCH FAKE PLAYER ===");

        for (FakePlayerData bot : bots) {
            // Kiểm tra trạng thái hoạt động thực tế trên RAM thông qua FakePlayerManager
            boolean isOnline = plugin.getFakePlayerManager().isBotOnline(bot.getName());
            String status = isOnline ? ChatColor.GREEN + "(ONLINE)" : ChatColor.RED + "(OFFLINE)";

            // Định dạng tọa độ lấy 1 chữ số thập phân cho chuỗi hiển thị gọn gàng, sạch sẽ trên chatbox
            String coordinatesFormat = String.format("%.1f %.1f %.1f %s",
                    bot.getX(),
                    bot.getY(),
                    bot.getZ(),
                    bot.getWorld()
            );

            // Xuất ra màn hình chat đúng theo cấu trúc layout được yêu cầu:
            // - tên bot x y z world - (ONLINE/OFFLINE)
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + bot.getName() + " " +
                    ChatColor.YELLOW + coordinatesFormat + ChatColor.GRAY + " - " + status);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Lệnh xem danh sách không yêu cầu tham số phụ, trả về mảng rỗng để không bị rác gợi ý Tab
        return Collections.emptyList();
    }
}
