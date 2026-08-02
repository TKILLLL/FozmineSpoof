package org.phantam.fozminespoofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.MessageManager;

import java.util.*;
import java.util.stream.Collectors;

public class DespawnCommand implements SubCommand {

    private final FozmineSpoofCore plugin;

    public DespawnCommand(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "despawn"; }

    @Override
    public String getDescription() { return "Despawn a fake player (hide from the world)"; }

    @Override
    public String getSyntax() { return "/spoof despawn <name|*>"; }

    @Override
    public String getPermission() { return "fozminespoof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cInvalid syntax! Use: " + getSyntax());
            return;
        }

        String input = args[1];
        DebugLogger.log(plugin.getLogger(), "DespawnCommand: input=%s by %s", input, sender.getName());

        if (input.equals("*")) {
            despawnAll(sender);
        } else {
            despawnSingle(sender, input);
        }
    }

    private void despawnSingle(CommandSender sender, String name) {
        MessageManager messages = plugin.getConfigManager().getMessages();
        DebugLogger.log(plugin.getLogger(), "DespawnCommand: despawning single bot %s", name);

        if (!plugin.getFakePlayerManager().isBotOnline(name)) {
            sender.sendMessage(messages.getMessage("bot.already-despawned").replace("%fakeplayer_name%", name));
            DebugLogger.log(plugin.getLogger(), "DespawnCommand: bot %s already offline", name);
            return;
        }

        boolean success = plugin.getFakePlayerManager().despawnBot(name);
        if (success) {
            sender.sendMessage(messages.getMessage("bot.despawn-success").replace("%fakeplayer_name%", name));
            DebugLogger.log(plugin.getLogger(), "DespawnCommand: bot %s despawned successfully", name);
        } else {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cFailed to despawn " + name + ". Check console logs.");
            DebugLogger.log(plugin.getLogger(), "DespawnCommand: bot %s despawn failed", name);
        }
    }

    private void despawnAll(CommandSender sender) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        List<String> online = plugin.getFakePlayerManager().getOnlineBotsData().stream()
                .map(FakePlayerData::getName)
                .collect(Collectors.toList());

        DebugLogger.log(plugin.getLogger(), "DespawnCommand: despawn all, online bots=%d", online.size());

        if (online.isEmpty()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cNo bots are currently online.");
            return;
        }

        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§eStarting despawn queue for §6" + online.size() + " §abots...");

        Queue<String> queue = new LinkedList<>(online);
        processDespawnQueue(sender, queue, messages);
    }

    private void processDespawnQueue(CommandSender sender, Queue<String> queue, MessageManager messages) {
        if (queue.isEmpty() || !plugin.isEnabled()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§a| Despawn queue completed.");
            DebugLogger.log(plugin.getLogger(), "DespawnCommand: despawn all completed");
            return;
        }

        String next = queue.poll();
        if (next != null && plugin.getFakePlayerManager().isBotOnline(next)) {
            boolean success = plugin.getFakePlayerManager().despawnBot(next);
            DebugLogger.logFine(plugin.getLogger(), "DespawnCommand: despawned %s, success=%s", next, success);
        }

        if (!queue.isEmpty()) {
            long delayTicks = Math.max(1L, plugin.getConfigManager().getJoinQuitIntervalTicks());
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    processDespawnQueue(sender, queue, messages), delayTicks);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("*".startsWith(input)) suggestions.add("*");

            suggestions.addAll(plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
            return suggestions;
        }
        return Collections.emptyList();
    }
}