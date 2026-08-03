package org.phantam.fozminespoofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.MessageManager;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class AddCommand implements SubCommand {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private final FozmineSpoofCore plugin;

    public AddCommand(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "add";
    }

    @Override
    public String getDescription() {
        return "Add a new fake player to the database (inactive, not spawned)";
    }

    @Override
    public String getSyntax() {
        return "/spoof add <name>";
    }

    @Override
    public String getPermission() {
        return "fozminespoof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getConfigManager().getMessages();

        if (args.length < 2) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cInvalid syntax! Use: " + getSyntax());
            return;
        }

        String name = args[1];
        DebugLogger.log(plugin.getLogger(), "AddCommand: executing for name=%s by %s", name, sender.getName());

        if (name.length() > 16) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cBot name cannot exceed 16 characters!");
            return;
        }

        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            sender.sendMessage(messages.getMessage("bot.invalid-name"));
            DebugLogger.log(plugin.getLogger(), "AddCommand: invalid name format: %s", name);
            return;
        }

        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(name));

        if (exists) {
            sender.sendMessage(messages.getMessage("bot.already-exists").replace("%fakeplayer_name%", name));
            DebugLogger.log(plugin.getLogger(), "AddCommand: bot %s already exists", name);
            return;
        }

        Location location = resolveSpawnLocation(sender);
        if (location == null) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cCould not determine a valid world.");
            DebugLogger.log(plugin.getLogger(), "AddCommand: cannot resolve spawn location");
            return;
        }

        boolean success = plugin.getFakePlayerManager().addBot(name, location);
        if (!success) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") + "§cFailed to add bot. Invalid name format.");
            return;
        }

        String worldName = plugin.getConfigManager().getBotWorldName();
        sender.sendMessage(messages.getMessage("bot.add-success")
                .replace("%fakeplayer_name%", name)
                .replace("%world%", worldName));

        DebugLogger.log(plugin.getLogger(), "AddCommand: added bot %s at %s,%s,%s in %s",
                name, location.getBlockX(), location.getBlockY(), location.getBlockZ(), worldName);

        if (plugin.getConfigManager().isDebug()) {
            sender.sendMessage(" §7▪ Location: §b" + (int) location.getX() + "§7, §b" +
                    (int) location.getY() + "§7, §b" + (int) location.getZ());
            sender.sendMessage(" §7▪ Tip: Use §e/spoof spawn " + name + " §7to activate 3D model.");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return List.of("<name>");
        return Collections.emptyList();
    }

    private Location resolveSpawnLocation(CommandSender sender) {
        String worldName = plugin.getConfigManager().getBotWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return (world != null) ? world.getSpawnLocation() : null;
    }
}