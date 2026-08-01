package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

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
    public String getDescription() { return "Kích hoạt hiển thị Fake Player theo tên, toàn bộ '*' hoặc theo số lượng cụ thể"; }

    @Override
    public String getSyntax() { return "/sproof spawn <tên | * | số_lượng>"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager msgManager = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cSai cú pháp! Vui lòng dùng: " + getSyntax());
            return;
        }

        String targetName = args[1];

        if (targetName.equals("*")) {
            handleSpawnMultiple(sender, -1, msgManager);
            return;
        }

        try {
            int amount = Integer.parseInt(targetName);
            if (amount <= 0) {
                sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cSố lượng Bot cần spawn phải lớn hơn 0!");
                return;
            }
            handleSpawnMultiple(sender, amount, msgManager);
            return;
        } catch (NumberFormatException ignored) {
        }

        handleSpawnSingle(sender, targetName, msgManager);
    }

    /**
     * Xử lý spawn hàng loạt (tất cả hoặc số lượng cụ thể)
     */
    private void handleSpawnMultiple(CommandSender sender, int maxAmount, MessageManager msgManager) {
        List<String> offlineBots = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        if (offlineBots.isEmpty()) {
            sender.sendMessage(msgManager.getMessage("bot.already-spawned").replace("%fakeplayer_name%", "Tất cả"));
            return;
        }

        if (maxAmount > 0) {
            Collections.shuffle(offlineBots);
            if (offlineBots.size() > maxAmount) {
                offlineBots = offlineBots.subList(0, maxAmount);
            }
        }

        int intervalTicks = plugin.getConfigManager().getJoinQuitIntervalTicks();

        sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§aBắt đầu xếp hàng đợi nạp §e"
                + offlineBots.size() + " §abot từ DB lên máy chủ (Giãn cách: §f" + (intervalTicks / 20.0) + "s§a)...");

        Queue<String> spawnQueue = new LinkedList<>(offlineBots);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (spawnQueue.isEmpty() || !plugin.isEnabled()) {
                    sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§a| Đã hoàn tất! Thành công: §e"
                            + successCount.get() + " §a, Thất bại: §c" + failCount.get());
                    this.cancel();
                    return;
                }

                String nextBot = spawnQueue.poll();

                if (!plugin.getFakePlayerManager().isBotOnline(nextBot)) {
                    plugin.getFakePlayerManager().spawnBotAsync(nextBot, success -> {
                        if (success) {
                            successCount.incrementAndGet();
                            sender.sendMessage(" §7-> Đang nạp: §e" + nextBot + " §a(Thành công)");
                        } else {
                            failCount.incrementAndGet();
                            sender.sendMessage(" §7-> Đang nạp: §e" + nextBot + " §c(Thất bại)");
                        }
                    });
                }

                if (!spawnQueue.isEmpty()) {
                    long nextDelayTicks = plugin.getConfigManager().getJoinQuitIntervalTicks();
                    if (nextDelayTicks <= 0) nextDelayTicks = 20L;
                    final BukkitRunnable currentTask = this;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            currentTask.run();
                        }
                    }.runTaskLater(plugin, nextDelayTicks);
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    /**
     * Xử lý spawn một bot cụ thể
     */
    private void handleSpawnSingle(CommandSender sender, String targetName, MessageManager msgManager) {
        if (plugin.getFakePlayerManager().isBotOnline(targetName)) {
            sender.sendMessage(msgManager.getMessage("bot.already-spawned").replace("%fakeplayer_name%", targetName));
            return;
        }

        boolean existsInDb = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(targetName));

        if (!existsInDb) {
            sender.sendMessage(msgManager.getMessage("bot.not-found").replace("%fakeplayer_name%", targetName));
            return;
        }

        sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§eĐang kích hoạt bot " + targetName + "...");
        plugin.getFakePlayerManager().spawnBotAsync(targetName, success -> {
            if (success) {
                sender.sendMessage(msgManager.getMessage("bot.spawn-success").replace("%fakeplayer_name%", targetName));
            } else {
                sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cKích hoạt thất bại! Vui lòng kiểm tra log.");
            }
        });
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
                suggestions.add("<số_lượng>");
            }

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