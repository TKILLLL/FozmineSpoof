package org.phantam.fozminesproofcore.commands.subs;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

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
    public String getDescription() { return "Ẩn một Fake Player hoặc nhập '*' để despawn toàn bộ bot đang trực tuyến"; }

    @Override
    public String getSyntax() { return "/sproof despawn <tên | *>"; }

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
        // XỬ LÝ LỆNH: /sproof despawn * (Thu hồi hàng loạt)
        // =========================================================================
        if (targetName.equals("*")) {
            handleDespawnAll(sender, msgManager);
            return;
        }

        // =========================================================================
        // XỬ LÝ LỆNH: /sproof despawn <TÊN CỤ THỂ>
        // =========================================================================
        handleDespawnSingle(sender, targetName, msgManager);
    }

    /**
     * Logic xử lý thu hồi hàng loạt FakePlayer đang trực tuyến
     */
    private void handleDespawnAll(CommandSender sender, MessageManager msgManager) {
        List<String> onlineBots = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .map(FakePlayerData::getName)
                .filter(name -> plugin.getFakePlayerManager().isBotOnline(name))
                .collect(Collectors.toList());

        if (onlineBots.isEmpty()) {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cHiện tại không có Fake Player nào đang trực tuyến!");
            return;
        }

        int intervalTicks = plugin.getConfigManager().getJoinQuitIntervalTicks();

        sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§eBắt đầu xếp hàng đợi thu hồi §6"
                + onlineBots.size() + " §abot (Giãn cách: §f" + (intervalTicks / 20.0) + "s§e)...");

        Queue<String> despawnQueue = new LinkedList<>(onlineBots);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (despawnQueue.isEmpty() || !plugin.isEnabled()) {
                    sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§a| Đã hoàn tất tiến trình thu hồi toàn bộ bot khỏi thế giới!");
                    this.cancel();
                    return;
                }

                String nextBot = despawnQueue.poll();

                if (plugin.getFakePlayerManager().isBotOnline(nextBot)) {
                    boolean success = plugin.getFakePlayerManager().despawnBot(nextBot);
                    if (success) {
                        sender.sendMessage(" §7-> Đang ngắt kết nối: §6" + nextBot + " §a(Thành công)");
                    } else {
                        sender.sendMessage(" §7-> Đang ngắt kết nối: §6" + nextBot + " §c(Thất bại)");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    /**
     * Logic xử lý thu hồi một FakePlayer cụ thể dựa theo định danh
     */
    private void handleDespawnSingle(CommandSender sender, String targetName, MessageManager msgManager) {
        if (!plugin.getFakePlayerManager().isBotOnline(targetName)) {
            sender.sendMessage(msgManager.getMessage("bot.already-despawned").replace("%fakeplayer_name%", targetName));
            return;
        }

        boolean success = plugin.getFakePlayerManager().despawnBot(targetName);

        if (success) {
            sender.sendMessage(msgManager.getMessage("bot.despawn-success").replace("%fakeplayer_name%", targetName));
        } else {
            sender.sendMessage(msgManager.getOnlyMessage("system.prefix") + "§cCó lỗi xảy ra trong quá trình hủy kích hoạt thực thể!");
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
