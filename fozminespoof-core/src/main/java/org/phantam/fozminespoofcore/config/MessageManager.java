package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofcore.utils.ColorUtils;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages message loading and formatting from messages.yml.
 * Provides three modes: with prefix, without prefix, and as a list.
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private String prefix = "";

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "messages.yml");
        this.reload();
    }

    /**
     * Reloads the messages.yml file and caches the system prefix.
     */
    public void reload() {
        if (!configFile.exists()) {
            plugin.saveResource("messages.yml", false);
            DebugLogger.log(plugin.getLogger(), "MessageManager: created default messages.yml");
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        String rawPrefix = config.getString("system.prefix", "[FozmineSpoof] ");
        this.prefix = ColorUtils.colorize(rawPrefix);

        if (DebugLogger.isDebugEnabled()) {
            DebugLogger.log(plugin.getLogger(), "MessageManager: reloaded, prefix=%s", rawPrefix.trim());
            DebugLogger.logFine(plugin.getLogger(), "MessageManager: loaded %d top-level keys", config.getKeys(false).size());
        }

        plugin.getLogger().log(Level.INFO,
                "[FozmineSpoof] Messages reloaded");
    }

    /**
     * Returns a full message with the system prefix and color codes translated.
     *
     * @param path the message key
     * @return colored message with prefix, or a fallback if missing
     */
    public String getMessage(String path) {
        String raw = config.getString(path);
        if (raw == null) {
            DebugLogger.log(plugin.getLogger(), "MessageManager: missing message path: %s", path);
            return prefix + "§cMissing message path: " + path;
        }
        return prefix + ColorUtils.colorize(raw);
    }

    /**
     * Returns a message without the system prefix (only the raw message, colored).
     *
     * @param path the message key
     * @return colored message without prefix, or a fallback if missing
     */
    public String getOnlyMessage(String path) {
        String raw = config.getString(path);
        if (raw == null) {
            DebugLogger.log(plugin.getLogger(), "MessageManager: missing message path (only): %s", path);
            return "§cMissing message path: " + path;
        }
        return ColorUtils.colorize(raw);
    }

    /**
     * Returns a list of messages with all lines colorized.
     *
     * @param path the message list key
     * @return a list of colored strings, or a fallback list if missing
     */
    public List<String> getMessageList(String path) {
        List<String> rawLines = config.getStringList(path);
        if (rawLines.isEmpty()) {
            DebugLogger.log(plugin.getLogger(), "MessageManager: missing or empty message list: %s", path);
            return Collections.singletonList("§cMissing message list path: " + path);
        }

        List<String> colored = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            colored.add(ColorUtils.colorize(line));
        }
        return colored;
    }
}