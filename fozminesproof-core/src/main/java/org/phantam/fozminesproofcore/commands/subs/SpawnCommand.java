package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.*;
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

        // CÚ PHÁP 1: /sproof spawn * (Nạp toàn bộ)
        if (targetName.equals("*")) {
            handleSpawnMultiple(sender, -1, msgManager);
            return;
        }

        // CÚ PHÁP 2: /sproof spawn <số_lượng> (Kiểm tra xem có phải định dạng số không)
        try {
            int amount = Integer.parseInt(targetName);
            if (amount <= 0) {
                sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cSố lượng Bot cần spawn phải lớn hơn 0!");
                return;
            }
            handleSpawnMultiple(sender, amount, msgManager);
            return;
        } catch (NumberFormatException e) {
            // Không phải số -> Chuyển sang xử lý Cú pháp 3: Gọi tên cụ thể
        }

        // CÚ PHÁP 3: /sproof spawn <TÊN CỤ THỂ>
        handleSpawnSingle(sender, targetName, msgManager);
    }

    /**
     * Logic xử lý nạp hàng loạt (Hỗ trợ cả tất cả hoặc giới hạn theo số lượng cụ thể)
     */
    private void handleSpawnMultiple(CommandSender sender, int maxAmount, MessageManager msgManager) {
        // Lấy danh sách toàn bộ bot hiện đang offline
        List<String> offlineBots = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> !plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        if (offlineBots.isEmpty()) {
            sender.sendMessage(msgManager.getMessage("bot.already-spawned").replace("%fakeplayer_name%", "Tất cả"));
            return;
        }

        // TÍNH NĂNG MỚI: Nếu có giới hạn số lượng, xáo trộn ngẫu nhiên danh sách và cắt bớt phần thừa
        if (maxAmount > 0) {
            Collections.shuffle(offlineBots); // Xáo trộn để bốc ngẫu nhiên Bot trong DB
            if (offlineBots.size() > maxAmount) {
                offlineBots = offlineBots.subList(0, maxAmount);
            }
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
     * Logic xử lý kích hoạt một FakePlayer cụ thể dựa theo định danh tên
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

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            if ("*".startsWith(input)) {
                suggestions.add("*");
            }

            // Gợi ý tượng trưng cho tính năng số lượng cụ thể trong TabComplete
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