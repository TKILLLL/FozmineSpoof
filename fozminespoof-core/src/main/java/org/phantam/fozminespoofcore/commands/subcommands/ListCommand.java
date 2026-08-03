package org.phantam.fozminespoofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.MessageManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ListCommand implements SubCommand {

    private static final int MAX_DISPLAY = 30;
    private final FozmineSpoofCore plugin;

    public ListCommand(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List all fake players in the database";
    }

    @Override
    public String getSyntax() {
        return "/spoof list";
    }

    @Override
    public String getPermission() {
        return "fozminespoof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        // Fast Memory Cache Read - O(1) Response Time
        Collection<FakePlayerData> bots = plugin.getFakePlayerManager().getAllDatabaseBots();

        if (bots == null || bots.isEmpty()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cNo fake players found in the database.");
            return;
        }

        List<String> onlineNames = bots.stream()
                .filter(bot -> plugin.getFakePlayerManager().isBotOnline(bot.getName()))
                .map(FakePlayerData::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        List<String> offlineNames = bots.stream()
                .filter(bot -> !plugin.getFakePlayerManager().isBotOnline(bot.getName()))
                .map(FakePlayerData::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        sender.sendMessage("§6=== FAKE PLAYER LIST (" + bots.size() + ") ===");

        if (!onlineNames.isEmpty()) {
            sender.sendMessage("§aOnline (§f" + onlineNames.size() + "§a): §f" + formatList(onlineNames));
        } else {
            sender.sendMessage("§aOnline: §7(none)");
        }

        if (!offlineNames.isEmpty()) {
            sender.sendMessage("§cOffline (§f" + offlineNames.size() + "§c): §f" + formatList(offlineNames));
        } else {
            sender.sendMessage("§cOffline: §7(none)");
        }
    }

    private String formatList(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() <= MAX_DISPLAY) {
            return String.join(", ", names);
        }
        List<String> first = names.subList(0, MAX_DISPLAY);
        int remaining = names.size() - MAX_DISPLAY;
        return String.join(", ", first) + ", ... (" + remaining + " more bots)";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}