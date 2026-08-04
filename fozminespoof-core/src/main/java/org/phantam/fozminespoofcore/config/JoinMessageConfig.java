package org.phantam.fozminespoofcore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JoinMessageConfig {

    private final JavaPlugin plugin;
    private final File file;

    // New Player Greetings
    private boolean newPlayerGreetingsEnabled;
    private int newPlayerGreetingsMaxBurst;
    private double newPlayerGreetingsDelay;
    private List<String> newPlayerGreetingsPhrases = new ArrayList<>();

    // Player Greetings
    private boolean playerGreetingsEnabled;
    private int playerGreetingsMaxBurst;
    private double playerGreetingsDelay;
    private List<String> playerGreetingsPhrases = new ArrayList<>();

    // Session Join Chats
    private boolean sessionJoinChatsEnabled;
    private int sessionJoinChatsMaxBurst;
    private double sessionJoinChatsDelay;
    private List<String> sessionJoinChatsPhrases = new ArrayList<>();

    public JoinMessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chats/join-messages.yml");
        this.reload();
    }

    public void reload() {
        try {
            ensureDefaultFileExists();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            // New Player Greetings
            newPlayerGreetingsEnabled = config.getBoolean("join-messages.new-player-greetings.enabled", false);
            newPlayerGreetingsMaxBurst = config.getInt("join-messages.new-player-greetings.max-burst", 3);
            newPlayerGreetingsDelay = config.getDouble("join-messages.new-player-greetings.delay", 60.0);
            newPlayerGreetingsPhrases = config.getStringList("join-messages.new-player-greetings.phrases");

            // Player Greetings
            playerGreetingsEnabled = config.getBoolean("join-messages.player-greetings.enabled", false);
            playerGreetingsMaxBurst = config.getInt("join-messages.player-greetings.max-burst", 3);
            playerGreetingsDelay = config.getDouble("join-messages.player-greetings.delay", 60.0);
            playerGreetingsPhrases = config.getStringList("join-messages.player-greetings.phrases");

            // Session Join Chats
            sessionJoinChatsEnabled = config.getBoolean("join-messages.session-join-chats.enabled", false);
            sessionJoinChatsMaxBurst = config.getInt("join-messages.session-join-chats.max-burst", 3);
            sessionJoinChatsDelay = config.getDouble("join-messages.session-join-chats.delay", 120.0);
            sessionJoinChatsPhrases = config.getStringList("join-messages.session-join-chats.phrases");

            DebugLogger.log(plugin.getLogger(),
                    "JoinMessageConfig: reloaded. NewPlayerGreetings=%s (delay=%.1fs), PlayerGreetings=%s (delay=%.1fs), SessionJoinChats=%s (delay=%.1fs)",
                    newPlayerGreetingsEnabled, newPlayerGreetingsDelay,
                    playerGreetingsEnabled, playerGreetingsDelay,
                    sessionJoinChatsEnabled, sessionJoinChatsDelay);

        } catch (Exception e) {
            plugin.getLogger().severe("[JoinMessageConfig] Failed to load chats/join-messages.yml: " + e.getMessage());
        }
    }

    private void ensureDefaultFileExists() {
        File folder = new File(plugin.getDataFolder(), "chats");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource("chats/join-messages.yml", false);
        }
    }

    // --- Getters ---

    // New Player Greetings
    public boolean isNewPlayerGreetingsEnabled() {
        return newPlayerGreetingsEnabled;
    }

    public int getNewPlayerGreetingsMaxBurst() {
        return newPlayerGreetingsMaxBurst;
    }

    public double getNewPlayerGreetingsDelay() {
        return newPlayerGreetingsDelay;
    }

    public List<String> getNewPlayerGreetingsPhrases() {
        return newPlayerGreetingsPhrases;
    }

    // Player Greetings
    public boolean isPlayerGreetingsEnabled() {
        return playerGreetingsEnabled;
    }

    public int getPlayerGreetingsMaxBurst() {
        return playerGreetingsMaxBurst;
    }

    public double getPlayerGreetingsDelay() {
        return playerGreetingsDelay;
    }

    public List<String> getPlayerGreetingsPhrases() {
        return playerGreetingsPhrases;
    }

    // Session Join Chats
    public boolean isSessionJoinChatsEnabled() {
        return sessionJoinChatsEnabled;
    }

    public int getSessionJoinChatsMaxBurst() {
        return sessionJoinChatsMaxBurst;
    }

    public double getSessionJoinChatsDelay() {
        return sessionJoinChatsDelay;
    }

    public List<String> getSessionJoinChatsPhrases() {
        return sessionJoinChatsPhrases;
    }
}