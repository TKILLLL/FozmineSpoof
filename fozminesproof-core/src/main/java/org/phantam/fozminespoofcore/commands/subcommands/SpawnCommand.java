package org.phantam.fozminespoofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.MessageManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SpawnCommand implements SubCommand {

    private final FozmineSpoofCore plugin;

    public SpawnCommand(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() { return "Spawn a fake player or multiple bots"; }

    @Override
    public String getSyntax() { return "/spoof spawn <name|*|number>"; }

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
        DebugLogger.log(plugin.getLogger(), "SpawnCommand: input=%s by %s", input, sender.getName());

        if (input.equals("*")) {
            spawnBulk(sender, -1);
            return;
        }

        int count = parseAmount(input);
        if (count > 0) {
            spawnBulk(sender, count);
            return;
        }

        spawnSingle(sender, input);
    }

    private void spawnSingle(CommandSender sender, String name) {
        MessageManager messages = plugin.getConfigManager().getMessages();
        DebugLogger.log(plugin.getLogger(), "SpawnCommand: spawning single bot %s", name);

        if (name.length() > 16) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cBot name cannot exceed 16 characters!");
            return;
        }

        int maxPlayers = Bukkit.getMaxPlayers();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (onlinePlayers >= maxPlayers) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cServer is full! Cannot spawn more bots.");
            return;
        }

        if (plugin.getFakePlayerManager().isBotOnline(name)) {
            sender.sendMessage(messages.getMessage("bot.already-spawned").replace("%fakeplayer_name%", name));
            DebugLogger.log(plugin.getLogger(), "SpawnCommand: bot %s already online", name);
            return;
        }

        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(name));

        if (!exists) {
            sender.sendMessage(messages.getMessage("bot.not-found").replace("%fakeplayer_name%", name));
            DebugLogger.log(plugin.getLogger(), "SpawnCommand: bot %s not in database", name);
            return;
        }

        sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§eSpawning bot " + name + "...");

        plugin.getFakePlayerManager().spawnBotAsync(name, success -> {
            if (success) {
                sender.sendMessage(messages.getMessage("bot.spawn-success").replace("%fakeplayer_name%", name));
                DebugLogger.log(plugin.getLogger(), "SpawnCommand: bot %s spawned successfully", name);
            } else {
                sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cFailed to spawn " + name + ". Check console logs.");
                DebugLogger.log(plugin.getLogger(), "SpawnCommand: bot %s spawn failed", name);
            }
        });
    }

    private void spawnBulk(CommandSender sender, int limit) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        int maxPlayers = Bukkit.getMaxPlayers();
        int currentOnline = Bukkit.getOnlinePlayers().size();
        int availableSlots = maxPlayers - currentOnline;

        if (availableSlots <= 0) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cServer is full (" + currentOnline + "/" + maxPlayers + ")! Cannot spawn bots.");
            return;
        }

        List<String> offline = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> name.length() <= 16)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        DebugLogger.log(plugin.getLogger(), "SpawnCommand: bulk spawn requested=%d, offline bots=%d, availableSlots=%d", limit, offline.size(), availableSlots);

        if (offline.isEmpty()) {
            sender.sendMessage(messages.getMessage("bot.already-spawned").replace("%fakeplayer_name%", "All bots"));
            return;
        }

        int spawnTargetCount = offline.size();
        if (limit > 0) {
            spawnTargetCount = Math.min(limit, offline.size());
        }

        if (spawnTargetCount > availableSlots) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                    "§eOnly §a" + availableSlots + " §eslot(s) remaining on the server (" + currentOnline + "/" + maxPlayers + "). Adjusting spawn amount to §a" + availableSlots + "§e.");
            spawnTargetCount = availableSlots;
        }

        Collections.shuffle(offline);
        List<String> finalQueueList = offline.subList(0, spawnTargetCount);

        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§aStarting spawn queue for §e" + finalQueueList.size() + " §abots...");

        Queue<String> queue = new LinkedList<>(finalQueueList);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        processQueue(sender, queue, successCount, failCount, messages);
    }

    private void processQueue(CommandSender sender, Queue<String> queue, AtomicInteger successCount, AtomicInteger failCount, MessageManager messages) {
        if (queue.isEmpty() || !plugin.isEnabled() || Bukkit.getOnlinePlayers().size() >= Bukkit.getMaxPlayers()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                    "§a| Completed! Success: §e" + successCount.get() +
                    " §a, Failed: §c" + failCount.get());
            DebugLogger.log(plugin.getLogger(), "SpawnCommand: bulk spawn completed, success=%d, fail=%d",
                    successCount.get(), failCount.get());
            return;
        }

        String next = queue.poll();
        if (next != null && !plugin.getFakePlayerManager().isBotOnline(next)) {
            plugin.getFakePlayerManager().spawnBotAsync(next, success -> {
                if (success) {
                    successCount.incrementAndGet();
                    if (plugin.getConfigManager().isDebug()) {
                        sender.sendMessage(" §7-> §e" + next + " §a(Success)");
                    }
                } else {
                    failCount.incrementAndGet();
                    if (plugin.getConfigManager().isDebug()) {
                        sender.sendMessage(" §7-> §e" + next + " §c(Failed)");
                    }
                }
            });
        }

        if (!queue.isEmpty()) {
            long delayTicks = Math.max(1L, plugin.getConfigManager().getJoinQuitIntervalTicks());
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    processQueue(sender, queue, successCount, failCount, messages), delayTicks);
        }
    }

    private int parseAmount(String input) {
        try {
            int val = Integer.parseInt(input);
            return (val > 0) ? val : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("*".startsWith(input)) suggestions.add("*");
            if (input.isEmpty()) suggestions.add("<number>");

            suggestions.addAll(plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                    .map(FakePlayerData::getName)
                    .filter(name -> name.length() <= 16)
                    .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
            return suggestions;
        }
        return Collections.emptyList();
    }
}