package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InteractiveMessageConfig {

    private final JavaPlugin plugin;
    private final File file;
    private boolean enabled = true;
    private final Map<String, InteractionConfig> interactions = new ConcurrentHashMap<>();

    public InteractiveMessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chats/interactive-messages.yml");
        this.reload();
    }

    public void reload() {
        try {
            ensureDefaultFileExists();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            this.enabled = config.getBoolean("enabled", true);

            Map<String, InteractionConfig> newMap = new LinkedHashMap<>();
            ConfigurationSection root = config.getConfigurationSection("chat-interactions");

            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec == null) continue;

                    List<String> triggers = sec.getStringList("triggers");
                    double chance = sec.getDouble("chance", 0.7);
                    long globalCd = sec.getLong("cooldowns.global", 15);
                    long perPlayerCd = sec.getLong("cooldowns.per-player", 30);
                    int maxBurst = sec.getInt("max-burst", 1);
                    String delayRange = sec.getString("delay-range", "1.5-2.5");
                    String activeHours = sec.getString("active-hours", "00:00-23:59");
                    List<String> replies = sec.getStringList("replies");

                    // New parameters
                    boolean useRegex = sec.getBoolean("use-regex", false);
                    double fuzzyThreshold = sec.getDouble("fuzzy-threshold", 0.85);
                    String typingSpeedRange = sec.getString("typing-speed-range", "0.8-1.8");
                    String pauseBetweenWords = sec.getString("pause-between-words", "2-4");

                    InteractionConfig interaction = new InteractionConfig(
                            key, triggers, chance, globalCd, perPlayerCd,
                            maxBurst, delayRange, activeHours, replies,
                            useRegex, fuzzyThreshold, typingSpeedRange, pauseBetweenWords
                    );
                    newMap.put(key, interaction);
                }
            }

            interactions.clear();
            interactions.putAll(newMap);

            DebugLogger.log(plugin.getLogger(),
                    "InteractiveMessageConfig: loaded %d chat interaction groups (Enabled: %b).",
                    interactions.size(), enabled);

        } catch (Exception e) {
            plugin.getLogger().severe("[InteractiveMessageConfig] Failed to load chats/interactive-messages.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureDefaultFileExists() {
        File folder = new File(plugin.getDataFolder(), "chats");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource("chats/interactive-messages.yml", false);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Collection<InteractionConfig> getInteractions() {
        return interactions.values();
    }
}