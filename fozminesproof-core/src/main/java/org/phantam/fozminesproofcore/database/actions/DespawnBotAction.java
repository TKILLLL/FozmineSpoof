package org.phantam.fozminesproofcore.database.actions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.util.Optional;

public class DespawnBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {
    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;

    public DespawnBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
                            FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    @Override
    public Boolean execute(String name) {
        FakePlayerData data = registry.getData(name);
        registry.unregister(name);

        if (data == null) {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) return false;
            data = opt.get();
        }

        data.setActive(false);
        database.saveFakePlayer(data);

        Player botEntity = plugin.getFakePlayerManager().getOnlineBotEntity(name);
        if (botEntity != null) {
            String quitMessage = plugin.getConfigManager().getLeaveMessage()
                    .replace("%fakeplayer_name%", name);
            PlayerQuitEvent quitEvent = new PlayerQuitEvent(botEntity, quitMessage);
            Bukkit.getPluginManager().callEvent(quitEvent);
            if (quitEvent.getQuitMessage() != null && !quitEvent.getQuitMessage().isEmpty()) {
                Bukkit.broadcastMessage(ColorUtils.colorize(quitEvent.getQuitMessage()));
            }
        }

        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(data.getUuid());
        }

        return true;
    }
}