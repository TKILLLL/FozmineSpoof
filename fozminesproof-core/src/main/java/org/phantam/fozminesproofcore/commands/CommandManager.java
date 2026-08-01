package org.phantam.fozminesproofcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.commands.subcommands.*;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofcore.utils.DebugLogger;

import java.util.*;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final FozmineSproofCore plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandManager(FozmineSproofCore plugin) {
        this.plugin = plugin;
        registerSubCommand(new AddCommand(plugin));
        registerSubCommand(new SpawnCommand(plugin));
        registerSubCommand(new DespawnCommand(plugin));
        registerSubCommand(new RemoveCommand(plugin));
        registerSubCommand(new ListCommand(plugin));
        registerSubCommand(new InfoCommand(plugin));
        registerSubCommand(new ReloadCommand(plugin));
    }

    private void registerSubCommand(SubCommand cmd) {
        subCommands.put(cmd.getName().toLowerCase(), cmd);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subName = args[0].toLowerCase();
        DebugLogger.log(plugin.getLogger(), "CommandManager: /%s %s by %s", label, subName, sender.getName());

        SubCommand sub = subCommands.get(subName);
        if (sub == null) {
            sender.sendMessage(messages.getMessage("system.unknown-command"));
            DebugLogger.log(plugin.getLogger(), "CommandManager: unknown subcommand %s", subName);
            return true;
        }

        if (!sender.hasPermission(sub.getPermission())) {
            sender.sendMessage(messages.getMessage("system.no-permission"));
            DebugLogger.log(plugin.getLogger(), "CommandManager: permission denied for %s", subName);
            return true;
        }

        try {
            sub.execute(sender, args);
        } catch (Exception e) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                    "§cAn error occurred while executing this command.");
            plugin.getLogger().severe("Error executing subcommand '/sproof " + subName + "':");
            e.printStackTrace();
            DebugLogger.log(plugin.getLogger(), "CommandManager: error in %s: %s", subName, e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();

        String input = args[0].toLowerCase();

        if (args.length == 1) {
            return subCommands.values().stream()
                    .filter(sub -> sub.getName().toLowerCase().startsWith(input))
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(SubCommand::getName)
                    .collect(Collectors.toList());
        }

        SubCommand sub = subCommands.get(input);
        if (sub != null && sender.hasPermission(sub.getPermission())) {
            return sub.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        MessageManager messages = plugin.getConfigManager().getMessages();
        sender.sendMessage(messages.getOnlyMessage("commands.help.header"));

        for (SubCommand sub : subCommands.values()) {
            if (!sender.hasPermission(sub.getPermission())) continue;

            String descPath = "commands.help.list." + sub.getName().toLowerCase();
            String description = messages.getOnlyMessage(descPath);
            if (description.startsWith("§cMissing")) description = sub.getDescription();

            String line = messages.getOnlyMessage("commands.help.format")
                    .replace("%syntax%", sub.getSyntax())
                    .replace("%description%", description);
            sender.sendMessage(line);
        }
        sender.sendMessage(messages.getOnlyMessage("commands.help.footer"));
    }
}