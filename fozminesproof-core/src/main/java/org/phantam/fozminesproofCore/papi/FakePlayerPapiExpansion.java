package org.phantam.fozminesproofCore.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofApi.FozminesproofApi;
import org.phantam.fozminesproofCore.config.ConfigManager;

public class FakePlayerPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FozminesproofApi apiBridge;

    public FakePlayerPapiExpansion(JavaPlugin plugin, ConfigManager configManager, FozminesproofApi apiBridge) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.apiBridge = apiBridge;
    }

    @Override
    public String getIdentifier() {
        return "fake";
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
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // 1. Kiểm tra xem tham số có nằm trong cấu hình parse-papi không
        if (!configManager.isTargetPlaceholder(params)) {
            return null;
        }

        int realCount = 0;
        int currentFakePlayers = (apiBridge != null) ? apiBridge.getFakePlayersCount() : 0;

        // Xác định đối tượng người chơi tốt nhất đang online để làm cầu nối gọi PAPI (nếu cần)
        OfflinePlayer targetPlayer = (player != null && player.isOnline()) ? player :
                (!Bukkit.getOnlinePlayers().isEmpty() ? Bukkit.getOnlinePlayers().iterator().next() : null);

        // =========================================================================
        // CƠ CHẾ 1: XỬ LÝ CÁC BIẾN CỤC BỘ CỦA SERVER CON (Ví dụ: server_online)
        // =========================================================================
        if (!params.startsWith("bungee_")) {

            if (params.equalsIgnoreCase("server_online")) {
                // Lấy trực tiếp từ Bukkit API: Chính xác 100%, cực nhẹ và không phụ thuộc PAPI gốc
                realCount = Bukkit.getOnlinePlayers().size();
            } else {
                // Các biến local khác (nếu sau này thêm vào config): Gọi PAPI gốc cục bộ
                realCount = getRealCountFromPapi(targetPlayer, params);
            }

        }
        // =========================================================================
        // CƠ CHẾ 2: XỬ LÝ CÁC BIẾN LIÊN QUAN ĐẾN PROXY/BUNGEECORD (Ví dụ: bungee_lobby)
        // =========================================================================
        else {
            // KIỂM TRA MÔI TRƯỜNG TEST: Nếu không có ai online, biến mạng Bungee chắc chắn trả về 0 hoặc lỗi
            if (targetPlayer == null) {
                // Trong môi trường TEST cục bộ (Offline/Một mình), tạm thời lấy 0 hoặc số người thật hiện tại (nếu có)
                realCount = Bukkit.getOnlinePlayers().size();
            } else {
                // Trong môi trường CHẠY THẬT (Khi đã đưa server vào lại mạng Bungee và có người chơi làm cầu nối):
                realCount = getRealCountFromPapi(targetPlayer, params);

                // Giải pháp Fallback chống trả về 0 lỗi:
                // Nếu Bungee trả về 0 do lag mạng, nhưng tên biến trùng với tên cụm hiện tại (Ví dụ: biến là bungee_lobby và tên server này là lobby)
                // Lấy tên server từ file server.properties bằng getName()
                String currentServerName = Bukkit.getServer().getName();
                if (realCount == 0 && params.equalsIgnoreCase("bungee_" + currentServerName)) {
                    realCount = Bukkit.getOnlinePlayers().size();
                }
            }
        }

        // 3. CỘNG DỒN HOÀN CHỈNH: Trả về Tổng số (Người chơi thực tế + Số lượng Bot)
        return String.valueOf(realCount + currentFakePlayers);
    }

    /**
     * Hàm hỗ trợ lấy số lượng người chơi thật từ biến PAPI gốc một cách an toàn
     */
    private int getRealCountFromPapi(OfflinePlayer targetPlayer, String params) {
        if (targetPlayer == null) return 0;

        String originalPlaceholder = "%" + params + "%";
        String originalValue = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(targetPlayer, originalPlaceholder);

        if (originalValue == null || originalValue.equals(originalPlaceholder) || originalValue.trim().isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(originalValue.trim());
        } catch (NumberFormatException e) {
            return 0; // Nếu trả về chữ (Offline/Error/N/A) thì coi như bằng 0 để tính toán
        }
    }

}
