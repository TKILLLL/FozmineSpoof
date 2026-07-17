package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class AddCommand implements SubCommand {

    private final FozmineSproofCore plugin;
    // Regex phòng vệ: Chỉ cho phép chữ, số và dấu gạch dưới (giống định dạng tên chuẩn của Mojang)
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

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
        MessageManager msgManager = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cSai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        // 1. Kiểm tra độ dài và ký tự hợp lệ của tên Bot để tránh lỗi hiển thị trên NameTag/Tablist
        if (!NAME_PATTERN.matcher(targetName).matches()) {
            sender.sendMessage(msgManager.getMessage("bot.invalid-name"));
            return;
        }

        // 2. Kiểm tra xem tên bot này đã bị trùng lặp trong cơ sở dữ liệu RAM Cache chưa
        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(targetName));

        if (exists) {
            sender.sendMessage(msgManager.getMessage("bot.already-exists").replace("%fakeplayer_name%", targetName));
            return;
        }

        // 3. XỬ LÝ LẤY TỌA ĐỘ ĐA NỀN TẢNG (PLAYER & CONSOLE)
        Location loc;
        if (sender instanceof Player player) {
            // Nếu là người chơi gửi, lấy vị trí đứng thực tế trực tiếp
            loc = player.getLocation();
        } else {
            // Nếu là Console gửi, tiến hành lấy thế giới cấu hình hoặc thế giới mặc định đầu tiên của server
            String assignedWorldName = plugin.getConfigManager().getBotWorldName();
            World defaultWorld = Bukkit.getWorld(assignedWorldName);

            if (defaultWorld == null && !Bukkit.getWorlds().isEmpty()) {
                defaultWorld = Bukkit.getWorlds().get(0);
            }

            if (defaultWorld == null) {
                sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cKhong tìm thấy thế giới hợp lệ trên Server để định vị Bot!");
                return;
            }

            // Lấy tọa độ Spawn mặc định cực kỳ an toàn của thế giới đó làm điểm neo cho Bot
            loc = defaultWorld.getSpawnLocation();
        }

        // 4. Gọi Manager đóng gói DTO dữ liệu thô và đẩy lệnh Async xuống SQL
        plugin.getFakePlayerManager().addBot(targetName, loc);

        // Lấy thông tin tên thế giới Void thực tế từ ConfigManager
        String assignedWorld = plugin.getConfigManager().getBotWorldName();

        // 5. Gửi thông báo thành công đồng bộ đa ngôn ngữ cho Sender (Hỗ trợ cả Console)
        sender.sendMessage(msgManager.getMessage("bot.add-success")
                .replace("%fakeplayer_name%", targetName)
                .replace("%world%", assignedWorld));

        // In các dòng hướng dẫn phụ trợ dạng Text thô sạch sẽ
        sender.sendMessage(" §7▪ Vị trí tọa độ: §b" + (int) loc.getX() + "§7, §b" + (int) loc.getY() + "§7, §b" + (int) loc.getZ());
        sender.sendMessage(" §7▪ Mẹo: Sử dụng §e/sproof spawn " + targetName + " §7để kích hoạt hiển thị mô hình 3D.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<tên_muốn_đặt>");
        }
        return Collections.emptyList();
    }
}
