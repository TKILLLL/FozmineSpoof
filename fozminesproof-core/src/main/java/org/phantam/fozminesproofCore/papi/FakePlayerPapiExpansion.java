package org.phantam.fozminesproofCore.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofApi.FozminesproofApi;
import org.phantam.fozminesproofCore.config.ConfigManager;

public class FakePlayerPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FozminesproofApi apiBridge; // Tiêm thẳng interface API của bạn vào

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
        if (!configManager.isTargetPlaceholder(params)) {
            return null;
        }

        String originalPlaceholder = "%" + params + "%";
        String originalValue = PlaceholderAPI.setPlaceholders(player, originalPlaceholder);

        try {
            int realCount = Integer.parseInt(originalValue.trim());

            // Đếm động: Tự lấy dữ liệu thời gian thực từ activeFakePlayers.size() của NMS Bridge
            int currentFakePlayers = apiBridge.getFakePlayersCount();

            return String.valueOf(realCount + currentFakePlayers);
        } catch (NumberFormatException e) {
            return originalValue;
        }
    }
}
