package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import org.phantam.fozminesproofApi.database.FakePlayerData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

        // 2. Lấy thông tin đối tượng bot từ dữ liệu cache của Manager để lấy UUID gốc
        Optional<FakePlayerData> botDataOpt = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .filter(bot -> bot.getName().equalsIgnoreCase(targetName))
                .findFirst();

        if (botDataOpt.isPresent()) {
            FakePlayerData data = botDataOpt.get();

            // 3. Gọi API hủy hiển thị thực thể NMS bằng UUID cố định chuẩn cấu trúc Interface của bạn
            plugin.getBridge().despawnPlayer(data.getUuid());

            // 4. Cập nhật trạng thái xuống Database SQL (Chuyển cờ hoạt động về false)
            plugin.getFakePlayerManager().despawnBot(targetName);

            sender.sendMessage(ChatColor.YELLOW + "Đã thu hồi gói tin NMS, hủy hiển thị thành công Fake Player: " + ChatColor.GOLD + targetName);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            // Tự động gợi ý chuẩn xác danh sách các bot đang ONLINE trên Server để Despawn nhanh
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
