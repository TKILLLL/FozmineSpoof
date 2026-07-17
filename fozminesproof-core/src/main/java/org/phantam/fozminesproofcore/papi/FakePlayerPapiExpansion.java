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
    public String getIdentifier() { return "fake"; }

    @Override
    public String getAuthor() { return "phantam"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        // ĐIỀU KIỆN TIÊN QUYẾT BẮT BUỘC: Nằm trong danh sách parse-papi whitelist của config.yml
        if (!configManager.isTargetPlaceholder(params)) {
            if (player != null && fakePlayerManager != null && fakePlayerManager.isBotOnline(player.getName())) {
                return "";
            }
            return null;
        }

        String botName = (player != null) ? player.getName() : null;

        // ================================================================================= #
        // XỬ LÝ VẠN NĂNG CHO BOT
        // ================================================================================= #
        if (botName != null && fakePlayerManager != null && fakePlayerManager.isBotOnline(botName)) {

            // GIẢI PHÁP SỬA LỖI TẬN GỐC: Tự động đánh chặn toàn bộ các biến liên quan đến Vault / LuckPerms
            // Chuyển hướng hoàn toàn sang định dạng bóc tách theo Tên dựa trên RAM Cache của LuckPerms.
            // Cơ chế này an toàn 100% trên luồng chính (Main Thread), dứt điểm lỗi ServerThreadLookupException khi gọi /papi parse hoặc Tab/Scoreboard.
            if (params.startsWith("vault_") || params.startsWith("luckperms_")) {
                String type = "prefix"; // Mặc định fallback
                if (params.contains("suffix")) {
                    type = "suffix";
                } else if (params.contains("primary_group") || params.contains("group")) {
                    type = "primary_group_name";
                }

                // Cấu trúc chuỗi biến an toàn của LuckPerms: %luckperms_user_prefix_abc%
                String luckpermsSecurePlaceholder = "%luckperms_user_" + type + "_" + botName + "%";
                String resolvedFallback = PlaceholderAPI.setPlaceholders(player, luckpermsSecurePlaceholder);

                if (resolvedFallback != null && !resolvedFallback.equals(luckpermsSecurePlaceholder)) {
                    return resolvedFallback;
                }
                return "";
            }

            // Cơ chế Universal cho mọi plugin bên thứ ba khác (BetterTeams, DeluxeTags...)
            String rawPlaceholder = new StringBuilder(params.length() + 2).append('%').append(params).append('%').toString();
            OfflinePlayer offlineBot = Bukkit.getOfflinePlayer(player.getUniqueId());
            String resolved = PlaceholderAPI.setPlaceholders(offlineBot, rawPlaceholder);

            if (resolved != null && !resolved.equals(rawPlaceholder)) {
                return resolved;
            }

            return "";
        }

        // ================================================================================= #
        // XỬ LÝ SỐ LƯỢNG CỘNG DỒN CHO NGƯỜI CHƠI THẬT
        // ================================================================================= #
        if (!Bukkit.isPrimaryThread()) {
            return "0"; // Bảo vệ an toàn luồng khi đếm người chơi thật
        }

        int fakeCount = (fakePlayerManager != null) ? fakePlayerManager.getOnlineBotsData().size() : 0;
        int realCount = 0;

        OfflinePlayer targetPlayer = (player != null && player.isOnline()) ? player : getFallbackPlayer();

        if (!params.startsWith("bungee_")) {
            realCount = handleLocalPlaceholder(params, targetPlayer);
        } else {
            realCount = handleProxyPlaceholder(params, targetPlayer);
        }

        return String.valueOf(realCount + fakeCount);
    }

    private int handleLocalPlaceholder(String params, OfflinePlayer targetPlayer) {
        if (params.equalsIgnoreCase("server_online")) {
            return Bukkit.getOnlinePlayers().size();
        }
        return getRealCountFromPapi(targetPlayer, params);
    }

    private int handleProxyPlaceholder(String params, OfflinePlayer targetPlayer) {
        if (targetPlayer == null) {
            return Bukkit.getOnlinePlayers().size();
        }
        int count = getRealCountFromPapi(targetPlayer, params);
        if (count == 0) {
            String currentServerName = Bukkit.getServer().getName();
            if (params.equalsIgnoreCase("bungee_" + currentServerName)) {
                return Bukkit.getOnlinePlayers().size();
            }
        }
        return count;
    }

    private int getRealCountFromPapi(OfflinePlayer targetPlayer, String params) {
        if (targetPlayer == null) {
            return 0;
        }
        try {
            String rawPlaceholder = new StringBuilder(params.length() + 2).append('%').append(params).append('%').toString();
            String resolvedValue = PlaceholderAPI.setPlaceholders(targetPlayer, rawPlaceholder);
            if (resolvedValue == null || resolvedValue.equals(rawPlaceholder) || resolvedValue.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(resolvedValue.trim());
        } catch (NumberFormatException e) {
            return 0;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Lỗi không xác định khi phân tích biến PAPI gốc '" + params + "': " + e.getMessage());
            return 0;
        }
    }

    private OfflinePlayer getFallbackPlayer() {
        return Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
    }
}