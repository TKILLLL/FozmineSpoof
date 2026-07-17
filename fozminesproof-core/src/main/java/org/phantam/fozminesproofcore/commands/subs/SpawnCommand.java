package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SpawnCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public SpawnCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() { return "Kích hoạt hiển thị một Fake Player hoặc nhập '*' để spawn toàn bộ bot từ database"; }

    @Override
    public String getSyntax() { return "/sproof spawn <tên | *>"; }

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

        // =========================================================================
        // XỬ LÝ LỆNH: /sproof spawn * (Xếp hàng đợi nạp toàn bộ)
        // =========================================================================
        if (targetName.equals("*")) {
            handleSpawnAll(sender, msgManager);
            return;
        }

        // =========================================================================
        // XỬ LÝ LỆNH: /sproof spawn <TÊN CỤ THỂ>
        // =========================================================================
        handleSpawnSingle(sender, targetName, msgManager);
    }

    /**
     * Logic xử lý kích hoạt hàng loạt FakePlayer từ Database
     */
    private void handleSpawnAll(CommandSender sender, MessageManager msgManager) {
        List<String> offlineBots = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        if (offlineBots.isEmpty()) {
            sender.sendMessage(msgManager.getMessage("bot.already-spawned").replace("%fakeplayer_name%", "Tất cả"));
            return;
        }

        int intervalTicks = plugin.getConfigManager().getJoinQuitIntervalTicks();

        sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§aBắt đầu xếp hàng đợi nạp §e"
                + offlineBots.size() + " §abot từ DB lên máy chủ (Giãn cách: §f" + (intervalTicks / 20.0) + "s§a)...");

        Queue<String> spawnQueue = new LinkedList<>(offlineBots);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (spawnQueue.isEmpty() || !plugin.isEnabled()) {
                    sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§a| Đã hoàn tất tiến trình nạp toàn bộ bot từ hàng đợi!");
                    // SỬA TẠI ĐÂY: Đã xoá triggerPostSpawnChatTest() để không ép bot chat nữa
                    this.cancel();
                    return;
                }

                String nextBot = spawnQueue.poll();

                if (!plugin.getFakePlayerManager().isBotOnline(nextBot)) {
                    boolean success = plugin.getFakePlayerManager().spawnBot(nextBot);
                    if (success) {
                        sender.sendMessage(" §7-> Đang nạp: §e" + nextBot + " §a(Thành công)");
                    } else {
                        sender.sendMessage(" §7-> Đang nạp: §e" + nextBot + " §c(Thất bại)");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    /**
     * Logic xử lý kích hoạt một FakePlayer cụ thể dựa theo định danh
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

        boolean success = plugin.getFakePlayerManager().spawnBot(targetName);

        if (success) {
            sender.sendMessage(msgManager.getMessage("bot.spawn-success").replace("%fakeplayer_name%", targetName));
        } else {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cKích hoạt thất bại! Vui lòng kiểm tra cấu hình thế giới hoặc hệ thống log.");
        }
    }

    // SỬA TẠI ĐÂY: Đã xoá hoàn toàn phương thức triggerPostSpawnChatTest

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            if ("*".startsWith(input)) {
                suggestions.add("*");
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