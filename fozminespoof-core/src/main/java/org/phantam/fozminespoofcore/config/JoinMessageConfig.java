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

    private boolean newPlayerGreetingsEnabled;
    private int newPlayerGreetingsMaxBurst;
    private List<String> newPlayerGreetingsPhrases = new ArrayList<>();

    private boolean playerGreetingsEnabled;
    private int playerGreetingsMaxBurst;
    private List<String> playerGreetingsPhrases = new ArrayList<>();

    private boolean sessionJoinChatsEnabled;
    private int sessionJoinChatsMaxBurst;
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
            newPlayerGreetingsPhrases = config.getStringList("join-messages.new-player-greetings.phrases");

            // Player Greetings
            playerGreetingsEnabled = config.getBoolean("join-messages.player-greetings.enabled", false);
            playerGreetingsMaxBurst = config.getInt("join-messages.player-greetings.max-burst", 3);
            playerGreetingsPhrases = config.getStringList("join-messages.player-greetings.phrases");

            // Session Join Chats
            sessionJoinChatsEnabled = config.getBoolean("join-messages.session-join-chats.enabled", false);
            sessionJoinChatsMaxBurst = config.getInt("join-messages.session-join-chats.max-burst", 3);
            sessionJoinChatsPhrases = config.getStringList("join-messages.session-join-chats.phrases");

            DebugLogger.log(plugin.getLogger(), "JoinMessageConfig: reloaded. NewPlayerGreetings=%s, PlayerGreetings=%s, SessionJoinChats=%s",
                    newPlayerGreetingsEnabled, playerGreetingsEnabled, sessionJoinChatsEnabled);

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

    // Getters
    public boolean isNewPlayerGreetingsEnabled() { return newPlayerGreetingsEnabled; }
    public int getNewPlayerGreetingsMaxBurst() { return newPlayerGreetingsMaxBurst; }
    public List<String> getNewPlayerGreetingsPhrases() { return newPlayerGreetingsPhrases; }

    public boolean isPlayerGreetingsEnabled() { return playerGreetingsEnabled; }
    public int getPlayerGreetingsMaxBurst() { return playerGreetingsMaxBurst; }
    public List<String> getPlayerGreetingsPhrases() { return playerGreetingsPhrases; }

    public boolean isSessionJoinChatsEnabled() { return sessionJoinChatsEnabled; }
    public int getSessionJoinChatsMaxBurst() { return sessionJoinChatsMaxBurst; }
    public List<String> getSessionJoinChatsPhrases() { return sessionJoinChatsPhrases; }
}