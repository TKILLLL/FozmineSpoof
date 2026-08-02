package org.phantam.fozminespoofcore.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Loads and provides random chat messages from the configured YAML file.
 */
public class MessageLoader {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<String> messagePool = new CopyOnWriteArrayList<>();

    public MessageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void loadMessages() {
        try {
            DebugLogger.log(logger, "MessageLoader: loading messages...");
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

            DebugLogger.log(logger, "MessageLoader: loaded %d messages (from %d raw entries)",
                    messagePool.size(), messages != null ? messages.size() : 0);

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

    public String getRandomMessage() {
        if (messagePool.isEmpty()) {
            DebugLogger.logFine(logger, "MessageLoader: getRandomMessage called but pool is empty");
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(messagePool.size());
        String msg = messagePool.get(index);
        DebugLogger.logFine(logger, "MessageLoader: selected message #%d: %s", index, msg);
        return msg;
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