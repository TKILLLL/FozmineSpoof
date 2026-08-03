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
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload plugin configuration and bot system";
    }

    @Override
    public String getSyntax() {
        return "/spoof reload";
    }

    @Override
    public String getPermission() {
        return "fozminespoof.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        DebugLogger.log(plugin.getLogger(), "ReloadCommand: executed by %s", sender.getName());

        sender.sendMessage(config.getMessages().getMessage("system.prefix") + "§eReloading system configuration...");

        try {
            // 1. Reload file config.yml & messages.yml
            config.reloadAllConfigs();
            DebugLogger.log(plugin.getLogger(), "ReloadCommand: config reloaded");

            // 2. Đảm bảo World void cho bot tồn tại
            VoidWorldFactory.createVoidWorld(plugin, config.getBotWorldName());

            // 3. Reload chats/random-messages.yml
            if (plugin.getMessageLoader() != null) {
                plugin.getMessageLoader().loadMessages();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: random messages reloaded");
            }

            // 4. Reload chats/join-messages.yml (Đã bổ sung)
            if (plugin.getJoinMessageConfig() != null) {
                plugin.getJoinMessageConfig().reload();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: join messages reloaded");
            }

            // 5. Reload chats/interactive-messages.yml
            if (plugin.getInteractiveMessageConfig() != null) {
                plugin.getInteractiveMessageConfig().reload();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: interactive messages reloaded");
            }

            // 6. Restart Chat Scheduler với cấu hình mới
            if (plugin.getChatScheduler() != null) {
                plugin.getChatScheduler().stop();
                plugin.getChatScheduler().start(config.getChatConfig());
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: chat scheduler restarted");
            }

            // 7. Reload Fake Player System, RAM Cache & Auto-Heal DB
            if (plugin.getFakePlayerManager() != null) {
                plugin.getFakePlayerManager().reloadSystem();

                // Cập nhật trạng thái TabList động (Hiển thị hoặc Ẩn theo hide-in-tab mới)
                boolean hideTab = config.isHideInTab();
                for (FakePlayerData botData : plugin.getFakePlayerManager().getOnlineBotsData()) {
                    Player entity = plugin.getFakePlayerManager().getOnlineBotEntity(botData.getName());
                    if (entity != null) {
                        if (hideTab) {
                            entity.setPlayerListName(null);
                        } else {
                            entity.setPlayerListName(ColorUtils.colorize(botData.getName()));
                        }
                    }
                }
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: fake player system reloaded");
            }

            // 8. Reload Lifecycle Manager & Tính lại thời gian sống cho Bot đang Online
            if (plugin.getBotLifecycleManager() != null) {
                plugin.getBotLifecycleManager().reload();
                DebugLogger.log(plugin.getLogger(), "ReloadCommand: lifecycle manager reloaded");
            }

            if (plugin.getAiConfig() != null) {
                plugin.getAiConfig().reload();
            }
            if (plugin.getAiPersonalityManager() != null) {
                plugin.getAiPersonalityManager().reload();
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