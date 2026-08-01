package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofcore.utils.DebugLogger;

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
    public String getDescription() { return "Permanently delete a fake player from the system and database"; }

    @Override
    public String getSyntax() { return "/sproof remove <name>"; }

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
        DebugLogger.log(plugin.getLogger(), "RemoveCommand: removing bot %s by %s", name, sender.getName());

        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(name));

        if (!exists) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cNo fake player found with name: " + name);
            DebugLogger.log(plugin.getLogger(), "RemoveCommand: bot %s not found", name);
            return;
        }

        plugin.getFakePlayerManager().removeBot(name);
        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§aSuccessfully removed §e" + name + " §afrom the system and database.");
        DebugLogger.log(plugin.getLogger(), "RemoveCommand: bot %s removed", name);
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