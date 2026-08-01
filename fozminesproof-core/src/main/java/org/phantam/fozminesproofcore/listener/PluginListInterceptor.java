package org.phantam.fozminesproofcore.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.phantam.fozminesproofcore.config.ConfigManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intercepts /plugins and /pl commands to fake the plugin name.
 * Uses config option fake-plugin-name; if set to "none", the interceptor is disabled.
 */
public class PluginListInterceptor implements Listener {

    private final ConfigManager configManager;
    private final Plugin ownPlugin;

    private static final Set<String> TARGET_COMMANDS = new HashSet<>(Arrays.asList(
            "plugins", "pl", "bukkit:plugins", "bukkit:pl"
    ));

    public PluginListInterceptor(ConfigManager configManager, Plugin ownPlugin) {
        this.configManager = configManager;
        this.ownPlugin = ownPlugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        String[] parts = cmd.split(" ");
        String baseCmd = parts[0];

        if (!TARGET_COMMANDS.contains(baseCmd)) {
            return;
        }

        String fakeName = configManager.getFakePluginName();
        if (fakeName == null || fakeName.equalsIgnoreCase("none")) {
            return; // disabled
        }

        Player player = event.getPlayer();
        String message = buildPluginList(fakeName);
        player.sendMessage(message);
        event.setCancelled(true);
    }

    /**
     * Builds the plugin list string, replacing own plugin name with the fake name.
     * Matches Bukkit format: "Plugins (total): name1, name2, ..."
     */
    private String buildPluginList(String fakeName) {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();

        String pluginNames = Arrays.stream(plugins)
                .map(plugin -> {
                    String name = plugin.getName();
                    if (plugin.equals(ownPlugin)) {
                        return fakeName;
                    }
                    return name;
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        int total = plugins.length;
        return ChatColor.GRAY + "Plugins (" + total + "): " + ChatColor.WHITE + pluginNames;
    }
}