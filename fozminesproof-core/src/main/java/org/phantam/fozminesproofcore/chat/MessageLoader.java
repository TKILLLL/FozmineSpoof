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

    // TỐI ƯU: Sử dụng CopyOnWriteArrayList để đảm bảo an toàn tuyệt đối khi đa luồng (Async Thread)
    // vừa đọc tin nhắn để chat, vừa dọn RAM để nạp lại dữ liệu (Reload) cùng một thời điểm.
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
                // Tự động giải nén file mẫu từ bên trong file JAR nếu chưa tồn tại
                plugin.saveResource("chats/random-messages.yml", false);
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> messages = config.getStringList("random-messages");

            // Dọn bộ nhớ RAM cũ một cách an toàn
            messagePool.clear();

            if (messages != null && !messages.isEmpty()) {
                List<String> validMessages = new ArrayList<>(messages.size());
                for (String msg : messages) {
                    // Loại bỏ hoàn toàn các dòng trống vô nghĩa do người dùng nhập nhầm
                    if (msg != null && !msg.trim().isEmpty()) {
                        validMessages.add(msg);
                    }
                }
                messagePool.addAll(validMessages);
            }

            plugin.getLogger().info("✅ Đã nạp thành công " + messagePool.size() + " tin nhắn chat");

        } catch (Exception e) {
            plugin.getLogger().severe("🚨 Lỗi nghiêm trọng khi nạp tệp chats/random-messages.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Bốc ngẫu nhiên một tin nhắn từ kho dữ liệu (Hàm này an toàn khi gọi từ luồng Async)
     * @return Chuỗi văn bản tin nhắn thô hoặc null nếu kho trống
     */
    public String getRandomMessage() {
        // Kiểm tra nhanh, tránh lỗi ném ngoại lệ khi kho tin nhắn trống rỗng
        if (messagePool.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(messagePool.size());
        return messagePool.get(randomIndex);
    }
}
