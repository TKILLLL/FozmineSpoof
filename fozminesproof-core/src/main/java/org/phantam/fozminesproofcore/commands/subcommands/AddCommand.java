package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.MessageManager;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handles the "/sproof add" command to create a new fake player entry in the database.
 * The new bot remains inactive (despawned) until spawned manually.
 */
public class AddCommand implements SubCommand {

    private final FozmineSproofCore plugin;
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    public AddCommand(FozmineSproofCore plugin) {
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
        return "/sproof add <name>";
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

        String name = args[1];

        // Validate name format (Mojang-compatible)
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            sender.sendMessage(messages.getMessage("bot.invalid-name"));
            return;
        }

        // Check for duplicate
        boolean exists = plugin.getFakePlayerManager().getAllDatabaseBots().stream()
                .anyMatch(bot -> bot.getName().equalsIgnoreCase(name));

        if (exists) {
            sender.sendMessage(messages.getMessage("bot.already-exists")
                    .replace("%fakeplayer_name%", name));
            return;
        }

        // Determine spawn location
        Location location = resolveSpawnLocation(sender);
        if (location == null) {
            sender.sendMessage(messages.getOnlyMessage("system.prefix") +
                    "§cCould not determine a valid world to place the bot.");
            return;
        }

        // Add to database
        plugin.getFakePlayerManager().addBot(name, location);

        // Confirm success
        String worldName = plugin.getConfigManager().getBotWorldName();
        sender.sendMessage(messages.getMessage("bot.add-success")
                .replace("%fakeplayer_name%", name)
                .replace("%world%", worldName));

        sender.sendMessage(" §7▪ Location: §b" + (int) location.getX() + "§7, §b" +
                (int) location.getY() + "§7, §b" + (int) location.getZ());
        sender.sendMessage(" §7▪ Tip: Use §e/sproof spawn " + name + " §7to activate 3D model.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<name>");
        }
        return Collections.emptyList();
    }

    /**
     * Resolves the spawn location from the sender's context.
     * If the sender is a player, uses their current location.
     * If console, uses the configured bot world or the server's default world.
     *
     * @param sender the command sender
     * @return the resolved Location, or null if no world is available
     */
    private Location resolveSpawnLocation(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation();
        }

        // Console or non-player sender
        String worldName = plugin.getConfigManager().getBotWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }

        return (world != null) ? world.getSpawnLocation() : null;
    }
}