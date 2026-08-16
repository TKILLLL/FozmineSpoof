package org.phantam.fozminespoofcore.listener;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.config.ConfigManager;
import org.phantam.fozminespoofcore.utils.ColorUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intercepts plugin-related commands from both players and console to mask plugin information.
 * Outputs realistic, authentic Spigot/Paper formatting and color schemes.
 */
public class PluginListInterceptor implements Listener {

    private static final Set<String> LIST_COMMANDS = new HashSet<>(Arrays.asList(
            "plugins", "pl", "bukkit:plugins", "bukkit:pl"
    ));

    private static final Set<String> DETAIL_COMMANDS = new HashSet<>(Arrays.asList(
            "version", "ver", "about", "bukkit:version", "bukkit:ver", "bukkit:about"
    ));

    private final ConfigManager configManager;
    private final Plugin ownPlugin;

    public PluginListInterceptor(ConfigManager configManager, Plugin ownPlugin) {
        this.configManager = configManager;
        this.ownPlugin = ownPlugin;
        DebugLogger.log(Bukkit.getLogger(), "PluginListInterceptor: initialized, ownPlugin=%s", ownPlugin.getName());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        processCommand(event.getPlayer(), event.getMessage(), event);
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        processCommand(Bukkit.getConsoleSender(), event.getCommand(), event);
    }

    /**
     * Xử lý tập trung logic chặn và tráo đổi gói lệnh
     */
    private void processCommand(CommandSender sender, String fullCommand, Cancellable event) {
        if (!configManager.isFakePluginEnable()) {
            return;
        }

        String cleaned = fullCommand.trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) return;

        String[] parts = cleaned.split("\\s+");
        String baseCmd = parts[0].toLowerCase();

        if (LIST_COMMANDS.contains(baseCmd)) {
            event.setCancelled(true);
            String fakeName = configManager.getFakePluginName();
            sender.sendMessage(buildPluginList(fakeName));
            return;
        }

        if (DETAIL_COMMANDS.contains(baseCmd) && parts.length > 1) {
            String targetPlugin = parts[1];
            String realName = ownPlugin.getName();
            String fakeName = configManager.getFakePluginName();

            if (targetPlugin.equalsIgnoreCase(realName)) {
                event.setCancelled(true);
                sender.sendMessage("This server is not running any plugin by that name.");
                sender.sendMessage("Use /plugins to get a list of plugins.");
                return;
            }

            if (targetPlugin.equalsIgnoreCase(fakeName)) {
                event.setCancelled(true);
                sendFakePluginDetails(sender);
                return;
            }
        }

        String customFakeCmd = configManager.getFakePluginCommand();
        if (customFakeCmd != null && !customFakeCmd.isBlank() && baseCmd.equalsIgnoreCase(customFakeCmd.toLowerCase())) {
            event.setCancelled(true);
            sendFakePluginDetails(sender);
        }
    }

    /**
     * Xây dựng danh sách plugin giả lập chuẩn xác phong cách Spigot/Paper
     */
    private String buildPluginList(String fakeName) {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();

        String pluginNames = Arrays.stream(plugins)
                .map(plugin -> {
                    boolean isOwn = plugin.equals(ownPlugin);
                    String name = isOwn ? fakeName : plugin.getName();
                    boolean isEnabled = isOwn || plugin.isEnabled();

                    return (isEnabled ? "&a" : "&c") + name;
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining("&f, "));

        int total = plugins.length;
        return ColorUtils.colorize("&fPlugins &7(&a" + total + "&7): &r" + pluginNames);
    }

    /**
     * Gửi bảng dữ liệu fake plugin siêu chân thực chuẩn phong cách Spigot /version
     */
    private void sendFakePluginDetails(CommandSender sender) {
        String name = configManager.getFakePluginName();
        String version = configManager.getFakePluginVersion();
        String description = configManager.getFakePluginDescription();
        List<String> authors = configManager.getFakePluginAuthors();

        String authorsString = (authors != null && !authors.isEmpty()) ? String.join(", ", authors) : "";

        sender.sendMessage(ColorUtils.colorize("&a" + name + " &fversion &a" + version));
        if (description != null && !description.isBlank()) {
            sender.sendMessage(ColorUtils.colorize("&7" + description));
        }
        if (!authorsString.isBlank()) {
            sender.sendMessage(ColorUtils.colorize("&fAuthors: &6" + authorsString));
        }
    }
}