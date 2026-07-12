package org.phantam.fozminesproofCore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.phantam.fozminesproofCore.FozmineSproofCore;
import org.phantam.fozminesproofCore.commands.subs.ReloadCommand;
import org.phantam.fozminesproofCore.commands.subs.RemoveCommand;
import org.phantam.fozminesproofCore.commands.subs.SpawnCommand;
import org.phantam.fozminesproofCore.commands.subs.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final List<SubCommand> subCommands = new ArrayList<>();

    public CommandManager(FozmineSproofCore plugin) {
        // Đăng ký các lệnh con vào hệ thống quản lý
        subCommands.add(new SpawnCommand(plugin));
        subCommands.add(new RemoveCommand(plugin));
        subCommands.add(new ReloadCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        SubCommand sub = subCommands.stream()
                .filter(s -> s.getName().equalsIgnoreCase(args[0]))
                .findFirst()
                .orElse(null);

        if (sub == null) {
            sender.sendMessage("§cKhông tìm thấy lệnh này! Gõ §e/sproof §cđể xem danh sách lệnh hỗ trợ.");
            return true;
        }

        // Kiểm tra quyền hạn của người gõ lệnh
        if (!sender.hasPermission(sub.getPermission())) {
            sender.sendMessage("§cBạn không có quyền hạn để thực hiện lệnh này!");
            return true;
        }

        // Thực thi lệnh con
        sub.execute(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Tự động gợi ý danh sách lệnh con hợp lệ khi gõ "/sproof "
            return subCommands.stream()
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length > 1) {
            SubCommand sub = subCommands.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(args[0]))
                    .findFirst()
                    .orElse(null);

            if (sub != null && sender.hasPermission(sub.getPermission())) {
                return sub.tabComplete(sender, args);
            }
        }

        return Collections.emptyList();
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§e--------- §bFozmineSproof Lệnh Admin §e---------");
        for (SubCommand sub : subCommands) {
            if (sender.hasPermission(sub.getPermission())) {
                sender.sendMessage("§b" + sub.getSyntax() + " §7- " + sub.getDescription());
            }
        }
        sender.sendMessage("§e------------------------------------------");
    }
}
