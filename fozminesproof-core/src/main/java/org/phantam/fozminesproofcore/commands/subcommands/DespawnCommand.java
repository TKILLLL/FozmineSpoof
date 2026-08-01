package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofcore.utils.DebugLogger;

import java.util.*;
import java.util.stream.Collectors;

public class DespawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public DespawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "despawn"; }

    @Override
    public String getDescription() { return "Despawn a fake player (hide from the world)"; }

    @Override
    public String getSyntax() { return "/sproof despawn <name|*>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

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

        int intervalTicks = Math.max(1, plugin.getConfigManager().getJoinQuitIntervalTicks());
        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§eStarting despawn queue for §6" + online.size() +
                " §abots (delay: §f" + (intervalTicks / 20.0) + "s§e)...");

        Queue<String> queue = new LinkedList<>(online);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.isEmpty() || !plugin.isEnabled()) {
                    sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§a| Despawn queue completed.");
                    DebugLogger.log(plugin.getLogger(), "DespawnCommand: despawn all completed");
                    this.cancel();
                    return;
                }

                String next = queue.poll();
                if (plugin.getFakePlayerManager().isBotOnline(next)) {
                    boolean success = plugin.getFakePlayerManager().despawnBot(next);
                    String status = success ? "§a(Success)" : "§c(Failed)";
                    sender.sendMessage(" §7-> §6" + next + " " + status);
                    DebugLogger.logFine(plugin.getLogger(), "DespawnCommand: despawned %s, success=%s", next, success);
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
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