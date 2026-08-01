package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.world.VoidWorldFactory;

import java.util.Collections;
import java.util.List;

/**
 * Reloads the plugin configuration, messages, and restores bot states.
 */
public class ReloadCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public ReloadCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload plugin configuration and bot system";
    }

    @Override
    public String getSyntax() {
        return "/sproof reload";
    }

    @Override
    public String getPermission() {
        return "fozminesproof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(config.getMessages().getMessage("system.prefix") +
                "§eReloading system configuration...");

        try {
            // Reload config files
            config.reloadAllConfigs();

            // Recreate void world if needed
            VoidWorldFactory.createVoidWorld(plugin, config.getBotWorldName());

            // Reload message pool
            if (plugin.getMessageLoader() != null) {
                plugin.getMessageLoader().loadMessages();
            }

            // Stop old chat scheduler
            if (plugin.getChatScheduler() != null) {
                plugin.getChatScheduler().stop();
            }

            // Reload bot system and restart chat scheduler after a tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (plugin.getFakePlayerManager() != null) {
                        plugin.getFakePlayerManager().reloadSystem();
                    }

                    if (plugin.getChatScheduler() != null) {
                        plugin.getChatScheduler().start(config.getChatConfig());
                    }

                    sender.sendMessage(config.getMessages().getMessage("system.reload-success"));

                } catch (Exception ex) {
                    sender.sendMessage(config.getMessages().getOnlyMessage("system.prefix") +
                            "§cAn error occurred during delayed reload phase.");
                    plugin.getLogger().severe("Error during reload delayed task:");
                    ex.printStackTrace();
                }
            }, 1L);

        } catch (Exception e) {
            sender.sendMessage(config.getMessages().getOnlyMessage("system.prefix") +
                    "§cA critical error occurred during reload. Check console.");
            plugin.getLogger().severe("Error executing reload command:");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}