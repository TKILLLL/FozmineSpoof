package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SpawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public SpawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() { return "Spawn a fake player or multiple bots"; }

    @Override
    public String getSyntax() { return "/sproof spawn <name|*|number>"; }

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

        List<String> offline = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        DebugLogger.log(plugin.getLogger(), "SpawnCommand: bulk spawn, limit=%d, offline bots=%d", limit, offline.size());

        if (offline.isEmpty()) {
            sender.sendMessage(messages.getMessage("bot.already-spawned").replace("%fakeplayer_name%", "All bots"));
            return;
        }

        if (limit > 0 && offline.size() > limit) {
            Collections.shuffle(offline);
            offline = offline.subList(0, limit);
            DebugLogger.log(plugin.getLogger(), "SpawnCommand: limited to %d bots", offline.size());
        }

        int intervalTicks = plugin.getConfigManager().getJoinQuitIntervalTicks();
        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§aStarting spawn queue for §e" + offline.size() +
                " §abots (delay: §f" + (intervalTicks / 20.0) + "s§a)...");

        Queue<String> queue = new LinkedList<>(offline);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.isEmpty() || !plugin.isEnabled()) {
                    sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                            "§a| Completed! Success: §e" + successCount.get() +
                            " §a, Failed: §c" + failCount.get());
                    DebugLogger.log(plugin.getLogger(), "SpawnCommand: bulk spawn completed, success=%d, fail=%d",
                            successCount.get(), failCount.get());
                    this.cancel();
                    return;
                }

                String next = queue.poll();
                if (!plugin.getFakePlayerManager().isBotOnline(next)) {
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
                    long delay = Math.max(1, plugin.getConfigManager().getJoinQuitIntervalTicks());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            this.run();
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskTimer(plugin, 20L, Math.max(1, intervalTicks));
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
                    .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
            return suggestions;
        }
        return Collections.emptyList();
    }
}