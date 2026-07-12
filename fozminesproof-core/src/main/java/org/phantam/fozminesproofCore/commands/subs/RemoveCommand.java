package org.phantam.fozminesproofCore.commands.subs;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofCore.FozmineSproofCore;
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
    public String getDescription() { return "Xóa một Fake Player khỏi máy chủ"; }

    @Override
    public String getSyntax() { return "/sproof remove <tên>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cSai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);

        if (targetPlayer == null) {
            sender.sendMessage("§cKhông tìm thấy Fake Player nào có tên là: " + targetName);
            return;
        }

        // Gọi hàm hủy thực thể NMS
        plugin.getBridge().despawnPlayer(targetPlayer.getUniqueId());
        sender.sendMessage("§aĐã xóa Fake Player §e" + targetName + " §ara khỏi máy chủ!");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // Tự động gợi ý tên tất cả người chơi đang online để xóa nhanh
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
