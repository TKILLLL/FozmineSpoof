package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ListCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public ListCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "list"; }

    @Override
    public String getDescription() { return "List all fake players in the database"; }

    @Override
    public String getSyntax() { return "/sproof list"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();
        Collection<FakePlayerData> bots = plugin.getFakePlayerManager().getAllDatabaseBots();

        DebugLogger.log(plugin.getLogger(), "ListCommand: executed by %s, total bots=%d",
                sender.getName(), bots != null ? bots.size() : 0);

        if (bots == null || bots.isEmpty()) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cNo fake players found in the database.");
            DebugLogger.log(plugin.getLogger(), "ListCommand: no bots in database");
            return;
        }

        sender.sendMessage("§6=== FAKE PLAYER LIST ===");
        for (FakePlayerData bot : bots) {
            boolean online = plugin.getFakePlayerManager().isBotOnline(bot.getName());
            String status = online ? "§aONLINE" : "§cOFFLINE";
            String coords = String.format("%.1f %.1f %.1f %s",
                    bot.getX(), bot.getY(), bot.getZ(), bot.getWorldName());
            sender.sendMessage("§7- §b" + bot.getName() + " §7" + coords + " §8- " + status);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}