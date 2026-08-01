package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handles spawning fake players individually or in bulk.
 * Supports:
 * - /sproof spawn <name>         : spawn a single bot
 * - /sproof spawn *              : spawn all offline bots
 * - /sproof spawn <number>       : spawn a random set of bots
 */
public class SpawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public SpawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getDescription() {
        return "Spawn a fake player or multiple bots";
    }

    @Override
    public String getSyntax() {
        return "/sproof spawn <name|*|number>";
    }

    @Override
    public String getPermission() {
        return "fozminesproof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cInvalid syntax! Use: " + getSyntax());
            return;
        }

        String input = args[1];

        // Handle bulk spawn: all offline bots
        if (input.equals("*")) {
            spawnBulk(sender, -1);
            return;
        }

        // Handle bulk spawn by count
        int count = parseAmount(input);
        if (count > 0) {
            spawnBulk(sender, count);
            return;
        }

        // Handle single bot spawn
        spawnSingle(sender, input);
    }

    /**
     * Spawns a single bot by name.
     */
    private void spawnSingle(CommandSender sender, String name) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (plugin.getFakePlayerManager().isBotOnline(name)) {
            sender.sendMessage(messages.getMessage("bot.already-spawned")
                    .replace("%fakeplayer_name%", name));
            return;
        }

        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(name));

        if (!exists) {
            sender.sendMessage(messages.getMessage("bot.not-found")
                    .replace("%fakeplayer_name%", name));
            return;
        }

        sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                "§eSpawning bot " + name + "...");

        plugin.getFakePlayerManager().spawnBotAsync(name, success -> {
            if (success) {
                sender.sendMessage(messages.getMessage("bot.spawn-success")
                        .replace("%fakeplayer_name%", name));
            } else {
                sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                        "§cFailed to spawn " + name + ". Check console logs.");
            }
        });
    }

    /**
     * Spawns multiple bots either all offline bots or a specific number.
     *
     * @param sender the command sender
     * @param limit  -1 for all, or a positive integer for limited count
     */
    private void spawnBulk(CommandSender sender, int limit) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        // Collect offline bot names
        List<String> offline = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        if (offline.isEmpty()) {
            sender.sendMessage(messages.getMessage("bot.already-spawned")
                    .replace("%fakeplayer_name%", "All bots"));
            return;
        }

        // Randomize and limit if needed
        if (limit > 0 && offline.size() > limit) {
            Collections.shuffle(offline);
            offline = offline.subList(0, limit);
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
                    this.cancel();
                    return;
                }

                String next = queue.poll();

                if (!plugin.getFakePlayerManager().isBotOnline(next)) {
                    plugin.getFakePlayerManager().spawnBotAsync(next, success -> {
                        if (success) {
                            successCount.incrementAndGet();
                            sender.sendMessage(" §7-> §e" + next + " §a(Success)");
                        } else {
                            failCount.incrementAndGet();
                            sender.sendMessage(" §7-> §e" + next + " §c(Failed)");
                        }
                    });
                }

                // Schedule next if queue not empty
                if (!queue.isEmpty()) {
                    long delay = Math.max(1, plugin.getConfigManager().getJoinQuitIntervalTicks());
                    final BukkitRunnable current = this;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            current.run();
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskTimer(plugin, 20L, Math.max(1, intervalTicks));
    }

    /**
     * Parses a string as a positive integer amount.
     *
     * @param input the string to parse
     * @return the parsed amount, or -1 if invalid
     */
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

            if ("*".startsWith(input)) {
                suggestions.add("*");
            }
            if (input.isEmpty()) {
                suggestions.add("<number>");
            }

            // Add offline bot names
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