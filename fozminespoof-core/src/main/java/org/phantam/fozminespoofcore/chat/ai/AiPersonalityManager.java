package org.phantam.fozminespoofcore.chat.ai;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AiPersonalityManager {

    private final JavaPlugin plugin;
    private final List<String> personalities = new ArrayList<>();
    private final List<String> speakingStyles = new ArrayList<>();

    // In-memory assignment per bot name: botName -> BotProfile
    private final Map<String, BotProfile> botProfiles = new ConcurrentHashMap<>();

    public AiPersonalityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        personalities.clear();
        speakingStyles.clear();
        botProfiles.clear();

        loadYamlList("chats/ai/personalities.yml", "personalities", personalities);
        loadYamlList("chats/ai/speaking_styles.yml", "speaking_styles", speakingStyles);

        DebugLogger.log(plugin.getLogger(), "AiPersonalityManager: loaded %d personalities, %d speaking styles.",
                personalities.size(), speakingStyles.size());
    }

    private void loadYamlList(String relativePath, String key, List<String> targetList) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try {
                plugin.saveResource(relativePath, false);
            } catch (Exception e) {
                plugin.getLogger().warning("[AiPersonalityManager] Could not save resource " + relativePath + ": " + e.getMessage());
            }
        }
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> loaded = config.getStringList(key);
            if (loaded != null && !loaded.isEmpty()) {
                targetList.addAll(loaded);
            }
        }
    }

    public BotProfile getProfile(String botName) {
        if (botName == null) return generateRandomProfile();
        return botProfiles.computeIfAbsent(botName.toLowerCase(), k -> generateRandomProfile());
    }

    private BotProfile generateRandomProfile() {
        String p = personalities.isEmpty() ? "friendly survivalist"
                : personalities.get(ThreadLocalRandom.current().nextInt(personalities.size()));

        String s = speakingStyles.isEmpty() ? "never uses any capital letters"
                : speakingStyles.get(ThreadLocalRandom.current().nextInt(speakingStyles.size()));

        List<String> situations = List.of(
                "mining for diamonds at deepslate level",
                "building a base near spawn",
                "fighting mobs in a dark cave",
                "farming wheat and potatoes",
                "exploring a ruined portal",
                "trading with villagers"
        );
        String sit = situations.get(ThreadLocalRandom.current().nextInt(situations.size()));

        return new BotProfile(p, s, sit);
    }

    public record BotProfile(String personality, String speakingStyle, String currentSituation) {
    }
}