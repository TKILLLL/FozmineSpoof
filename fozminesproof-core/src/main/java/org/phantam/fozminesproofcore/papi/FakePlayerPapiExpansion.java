package org.phantam.fozminesproofcore.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.FakePlayerManager;

public class FakePlayerPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FakePlayerManager fakePlayerManager;

    public FakePlayerPapiExpansion(JavaPlugin plugin, ConfigManager configManager, FakePlayerManager fakePlayerManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.fakePlayerManager = fakePlayerManager;
    }

    @Override
    public String getIdentifier() {
        return "fake"; // Sử dụng dạng: %fake_<placeholder_gốc>% (Ví dụ: %fake_server_online%)
    }

    @Override
    public String getAuthor() {
        return "phantam";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Giữ expansion luôn đăng ký kể cả khi PlaceholderAPI reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        // 1. Kiểm tra xem biến này có nằm trong danh sách trắng (Whitelist) cấu hình parse-papi không
        if (!configManager.isTargetPlaceholder(params)) {
            return null;
        }

        // 2. Lấy số lượng Bot đang hoạt động trực tuyến từ bộ nhớ RAM Cache của Core
        int fakeCount = (fakePlayerManager != null) ? fakePlayerManager.getOnlineBotsData().size() : 0;
        int realCount = 0;

        // Xác định đối tượng người chơi tốt nhất đang trực tuyến để làm cầu nối (Bridge) phân tích cú pháp PAPI
        OfflinePlayer targetPlayer = (player != null && player.isOnline()) ? player : getFallbackPlayer();

        // 3. Phân tách cơ chế xử lý biến: Local Server vs Proxy/BungeeCord
        if (!params.startsWith("bungee_")) {
            realCount = handleLocalPlaceholder(params, targetPlayer);
        } else {
            realCount = handleProxyPlaceholder(params, targetPlayer);
        }

        // 4. CỘNG DỒN HOÀN CHỈNH: Trả về kết quả tổng số (Người chơi thực tế + Số lượng Bot)
        return String.valueOf(realCount + fakeCount);
    }

    /**
     * Xử lý các biến cục bộ trong cùng cụm Server
     */
    private int handleLocalPlaceholder(String params, OfflinePlayer targetPlayer) {
        if (params.equalsIgnoreCase("server_online")) {
            // Tối ưu hiệu năng: Lấy trực tiếp từ Bukkit API cực nhẹ, không cần thông qua PlaceholderAPI gốc
            return Bukkit.getOnlinePlayers().size();
        }
        return getRealCountFromPapi(targetPlayer, params);
    }

    /**
     * Xử lý các biến mạng liên thông Proxy BungeeCord/Velocity
     */
    private int handleProxyPlaceholder(String params, OfflinePlayer targetPlayer) {
        if (targetPlayer == null) {
            // Môi trường thử nghiệm cục bộ (Một mình): Trả về số người chơi thực tế hiện tại
            return Bukkit.getOnlinePlayers().size();
        }

        int count = getRealCountFromPapi(targetPlayer, params);

        // Cơ chế Fallback tự động: Nếu mạng Proxy bị lag/trả về 0 nhưng biến trùng với tên cụm máy chủ này
        if (count == 0) {
            String currentServerName = Bukkit.getServer().getName();
            if (params.equalsIgnoreCase("bungee_" + currentServerName)) {
                return Bukkit.getOnlinePlayers().size();
            }
        }

        return count;
    }

    /**
     * Hàm phụ trợ phân tích cú pháp lấy số lượng người thật một cách an toàn, chống crash luồng chính
     */
    private int getRealCountFromPapi(OfflinePlayer targetPlayer, String params) {
        if (targetPlayer == null) {
            return 0;
        }

        try {
            // Định dạng chuỗi StringBuilder tối ưu bộ nhớ hơn phép cộng chuỗi "+" truyền thống
            String rawPlaceholder = new StringBuilder(params.length() + 2).append('%').append(params).append('%').toString();
            String resolvedValue = PlaceholderAPI.setPlaceholders(targetPlayer, rawPlaceholder);

            if (resolvedValue == null || resolvedValue.equals(rawPlaceholder) || resolvedValue.trim().isEmpty()) {
                return 0;
            }

            return Integer.parseInt(resolvedValue.trim());
        } catch (NumberFormatException e) {
            // Nếu PAPI gốc trả về lỗi chữ (N/A, Offline, Error), trả về 0 để tiếp tục tính toán cộng dồn
            return 0;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Lỗi không xác định khi phân tích biến PAPI gốc '" + params + "': " + e.getMessage());
            return 0;
        }
    }

    /**
     * Lấy ra người chơi đầu tiên trực tuyến làm cầu nối I/O dữ liệu
     */
    private OfflinePlayer getFallbackPlayer() {
        return Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
    }
}
