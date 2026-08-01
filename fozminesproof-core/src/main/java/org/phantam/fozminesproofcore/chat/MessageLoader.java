package org.phantam.fozminesproofcore.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Loads and provides random chat messages from the configured YAML file.
 * <p>
 * The messages are stored in a thread-safe list to allow concurrent access.
 */
public class MessageLoader {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<String> messagePool = new CopyOnWriteArrayList<>();

    public MessageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Loads or reloads messages from the file {@code chats/random-messages.yml}.
     * Creates a default file if not present.
     */
    public void loadMessages() {
        try {
            ensureDefaultFileExists();
            File file = new File(plugin.getDataFolder(), "chats/random-messages.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> messages = config.getStringList("random-messages");

            messagePool.clear();

            if (messages != null && !messages.isEmpty()) {
                List<String> valid = new ArrayList<>();
                for (String msg : messages) {
                    if (msg != null && !msg.trim().isEmpty()) {
                        valid.add(msg);
                    }
                }
                messagePool.addAll(valid);
            }

            if (messagePool.isEmpty()) {
                logger.warning("[MessageLoader] No messages loaded! Bots will not be able to chat.");
                logger.warning("[MessageLoader] Please add at least one message to chats/random-messages.yml");
            } else {
                logger.info("[MessageLoader] Successfully loaded " + messagePool.size() + " chat messages.");
            }

        } catch (Exception e) {
            logger.severe("[MessageLoader] Failed to load chats/random-messages.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns a random message from the pool, or null if none available.
     *
     * @return a random message, or null if the pool is empty
     */
    public String getRandomMessage() {
        if (messagePool.isEmpty()) {
            return null;
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(messagePool.size());
        return messagePool.get(randomIndex);
    }

    private void ensureDefaultFileExists() {
        File folder = new File(plugin.getDataFolder(), "chats");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(plugin.getDataFolder(), "chats/random-messages.yml");
        if (!file.exists()) {
            plugin.saveResource("chats/random-messages.yml", false);
            logger.info("[MessageLoader] Created default random-messages.yml file.");
        }
    }
}