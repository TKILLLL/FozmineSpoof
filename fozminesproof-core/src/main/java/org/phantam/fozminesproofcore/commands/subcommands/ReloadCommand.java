package org.phantam.fozminesproofcore.commands.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.world.VoidWorldFactory;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.Collections;
import java.util.List;

public class ReloadCommand implements SubCommand {

    private final FozmineSproofCore plugin;

    public ReloadCommand(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return "Reload plugin configuration and bot system"; }

    @Override
    public String getSyntax() { return "/sproof reload"; }

    @Override
    public String getPermission() { return "fozminesproof.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        DebugLogger.log(plugin.getLogger(), "ReloadCommand: executed by %s", sender.getName());

        sender.sendMessage(config.getMessages().getMessage("system.prefix") + "§eReloading system configuration...");

        try {
            config.reloadAllConfigs();
            DebugLogger.log(plugin.getLogger(), "ReloadCommand: config reloaded");

            VoidWorldFactory.createVoidWorld(plugin, config.getBotWorldName());

            if (plugin.getMessageLoader() != null) {
                plugin.getMessageLoader().loadMessages();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: messages reloaded");
            }

            if (plugin.getChatScheduler() != null) {
                plugin.getChatScheduler().stop();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: chat scheduler stopped");
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (plugin.getFakePlayerManager() != null) {
                        plugin.getFakePlayerManager().reloadSystem();
                        DebugLogger.log(plugin.getLogger(), "ReloadCommand: fake player system reloaded");
                    }

                    if (plugin.getChatScheduler() != null) {
                        plugin.getChatScheduler().start(config.getChatConfig());
                        DebugLogger.log(plugin.getLogger(), "ReloadCommand: chat scheduler restarted");
                    }

                    sender.sendMessage(config.getMessages().getMessage("system.reload-success"));
                    DebugLogger.log(plugin.getLogger(), "ReloadCommand: reload completed successfully");

                } catch (Exception ex) {
                    sender.sendMessage(config.getMessages().getOnlyMessage("system.prefix") +
                            "§cAn error occurred during delayed reload phase.");
                    plugin.getLogger().severe("Error during reload delayed task:");
                    ex.printStackTrace();
                    DebugLogger.log(plugin.getLogger(), "ReloadCommand: error in delayed phase: %s", ex.getMessage());
                }
            }, 1L);

        } catch (Exception e) {
            sender.sendMessage(config.getMessages().getOnlyMessage("system.prefix") +
                    "§cA critical error occurred during reload. Check console.");
            plugin.getLogger().severe("Error executing reload command:");
            e.printStackTrace();
            DebugLogger.log(plugin.getLogger(), "ReloadCommand: critical error: %s", e.getMessage());
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}