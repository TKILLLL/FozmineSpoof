package org.phantam.fozminesproofcore.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class MessageLoader {
    private final JavaPlugin plugin;

    private final List<String> messagePool = new CopyOnWriteArrayList<>();

    public MessageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Nạp hoặc làm mới danh sách tin nhắn ngẫu nhiên từ tệp tin chats/random-messages.yml
     */
    public void loadMessages() {
        try {
            File folder = new File(plugin.getDataFolder(), "chats");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File file = new File(plugin.getDataFolder(), "chats/random-messages.yml");
            if (!file.exists()) {
                plugin.saveResource("chats/random-messages.yml", false);
                plugin.getLogger().info("[MessageLoader] Đã tạo file random-messages.yml mặc định.");
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> messages = config.getStringList("random-messages");

            messagePool.clear();

            if (messages != null && !messages.isEmpty()) {
                List<String> validMessages = new ArrayList<>(messages.size());
                for (String msg : messages) {
                    if (msg != null && !msg.trim().isEmpty()) {
                        validMessages.add(msg);
                    }
                }
                messagePool.addAll(validMessages);
            }

            if (messagePool.isEmpty()) {
                plugin.getLogger().warning("[MessageLoader] ⚠ Không có tin nhắn nào được load! Bot sẽ không thể chat.");
                plugin.getLogger().warning("[MessageLoader] Vui lòng thêm ít nhất 1 tin nhắn vào chats/random-messages.yml");
            } else {
                plugin.getLogger().info("[MessageLoader] ✅ Đã nạp thành công " + messagePool.size() + " tin nhắn chat.");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("[MessageLoader] 🚨 Lỗi khi nạp tệp chats/random-messages.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Bốc ngẫu nhiên một tin nhắn từ kho dữ liệu
     */
    public String getRandomMessage() {
        if (messagePool.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(messagePool.size());
        return messagePool.get(randomIndex);
    }
}