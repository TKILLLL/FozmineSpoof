package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SpawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public SpawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() { return "Tạo một Fake Player tại vị trí của bạn"; }

    @Override
    public String getSyntax() { return "/sproof spawn <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ có người chơi trong game mới có thể sử dụng lệnh này!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cSai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];
        if (targetName.length() > 16) {
            player.sendMessage("§cTên người chơi không được vượt quá 16 ký tự!");
            return;
        }

        // Gọi Bridge thông qua đối tượng chính để kích hoạt NMS của phiên bản tương ứng
        plugin.getBridge().spawnPlayer(targetName, UUID.randomUUID(), player.getLocation());
        player.sendMessage("§aĐã tạo thành công Fake Player: §e" + targetName);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<tên_fake_player>");
        }
        return Collections.emptyList();
    }
}
