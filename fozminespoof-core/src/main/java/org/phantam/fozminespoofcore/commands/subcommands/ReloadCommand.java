package org.phantam.fozminespoofcore.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.utils.ColorUtils;
import org.phantam.fozminespoofcore.world.VoidWorldFactory;

import java.util.Collections;
import java.util.List;

public class ReloadCommand implements SubCommand {

    private final FozmineSpoofCore plugin;

    public ReloadCommand(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return "Reload plugin configuration and bot system"; }

    @Override
    public String getSyntax() { return "/spoof reload"; }

    @Override
    public String getPermission() { return "fozminespoof.admin"; }

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
                plugin.getChatScheduler().start(config.getChatConfig());
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: chat scheduler restarted");
            }

            if (plugin.getFakePlayerManager() != null) {
                plugin.getFakePlayerManager().reloadSystem();

                for (FakePlayerData botData : plugin.getFakePlayerManager().getOnlineBotsData()) {
                    Player entity = plugin.getFakePlayerManager().getOnlineBotEntity(botData.getName());
                    if (entity != null) {
                        entity.setPlayerListName(ColorUtils.colorize(botData.getName()));
                    }
                }
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: fake player system reloaded");
            }

            if (plugin.getBotLifecycleManager() != null) {
                plugin.getBotLifecycleManager().reload();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: lifecycle manager reloaded");
            }

            sender.sendMessage(config.getMessages().getMessage("system.reload-success"));
            DebugLogger.log(plugin.getLogger(), "ReloadCommand: reload completed successfully");

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