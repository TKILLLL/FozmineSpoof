package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofcore.utils.DebugLogger;

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
    public String getName() { return "info"; }

    @Override
    public String getDescription() { return "Show detailed information of a fake player"; }

    @Override
    public String getSyntax() { return "/sproof info <name>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cInvalid syntax! Use: " + getSyntax());
            return;
        }

        String name = args[1];
        DebugLogger.log(plugin.getLogger(), "InfoCommand: querying bot %s by %s", name, sender.getName());

        Optional<FakePlayerData> dataOpt = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .filter(bot -> bot.getName().equalsIgnoreCase(name))
                .findFirst();

        if (dataOpt.isEmpty()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cNo fake player found with name: " + name);
            DebugLogger.log(plugin.getLogger(), "InfoCommand: bot %s not found", name);
            return;
        }

        FakePlayerData data = dataOpt.get();
        boolean online = plugin.getFakePlayerManager().isBotOnline(data.getName());
        String status = online ? "§aONLINE" : "§cOFFLINE";

        DebugLogger.log(plugin.getLogger(), "InfoCommand: displaying info for %s (online=%s)", name, online);

        sender.sendMessage("§6=== FAKE PLAYER INFORMATION ===");
        sender.sendMessage("§e• Name: §b" + data.getName());
        sender.sendMessage("§e• UUID: §7" + data.getUuid());
        sender.sendMessage("§e• Status: " + status);
        sender.sendMessage("§e• World: §7" + data.getWorldName());
        sender.sendMessage("§e• Location: §7" + String.format("%.2f, %.2f, %.2f", data.getX(), data.getY(), data.getZ()));
        sender.sendMessage("§e• Rotation: §7Yaw=" + String.format("%.1f", data.getYaw()) + " Pitch=" + String.format("%.1f", data.getPitch()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}