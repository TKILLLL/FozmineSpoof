package org.phantam.fozminespoofcore.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.FozmineSpoofCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Injects online fake players into the multiplayer hover sample list and synchronizes player counts.
 */
public class ServerListPingListener implements Listener {

    private final FozmineSpoofCore plugin;

    public ServerListPingListener(FozmineSpoofCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPaperServerListPing(PaperServerListPingEvent event) {
        if (!plugin.isEnabled()) return;

        var onlineBots = plugin.getFakePlayerManager().getOnlineBotsData();
        if (onlineBots == null || onlineBots.isEmpty()) return;

        // Adjust total online count
        int realOnline = event.getNumPlayers();
        event.setNumPlayers(realOnline + onlineBots.size());

        // Add random bot profiles to hover sample list
        List<FakePlayerData> randomizedBots = new ArrayList<>(onlineBots);
        Collections.shuffle(randomizedBots);

        for (int i = 0; i < Math.min(10, randomizedBots.size()); i++) {
            FakePlayerData bot = randomizedBots.get(i);
            PlayerProfile profile = Bukkit.createProfile(bot.getUuid(), bot.getName());
            event.getPlayerSample().add(profile);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBukkitServerListPing(ServerListPingEvent event) {
        if (!plugin.isEnabled()) return;

        var onlineBots = plugin.getFakePlayerManager().getOnlineBotsData();
        if (onlineBots != null && !onlineBots.isEmpty()) {
            event.setMaxPlayers(event.getMaxPlayers());
        }
    }
}