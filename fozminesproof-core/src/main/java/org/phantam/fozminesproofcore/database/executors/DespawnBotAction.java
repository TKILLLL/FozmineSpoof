package org.phantam.fozminesproofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.manager.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Despawns a fake player (removes from world, sets inactive in database).
 */
public class DespawnBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;

    public DespawnBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
                            FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        // broadcaster is kept for future use but currently unused
    }

    @Override
    public Boolean execute(String name) {
        FakePlayerData data = registry.getData(name);
        registry.unregister(name);

        if (data == null) {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        "[DespawnBotAction] Bot '" + name + "' not found in database");
                return false;
            }
            data = opt.get();
        }

        // Update active status using withActive()
        FakePlayerData updatedData = data.withActive(false);
        database.saveFakePlayer(updatedData);

        // Trigger quit event
        Player botEntity = plugin.getFakePlayerManager().getOnlineBotEntity(name);
        if (botEntity != null) {
            String quitMsg = plugin.getConfigManager().getLeaveMessage()
                    .replace("%fakeplayer_name%", name);
            PlayerQuitEvent quitEvent = new PlayerQuitEvent(botEntity, quitMsg);
            Bukkit.getPluginManager().callEvent(quitEvent);
            if (quitEvent.getQuitMessage() != null && !quitEvent.getQuitMessage().isEmpty()) {
                Bukkit.broadcastMessage(ColorUtils.colorize(quitEvent.getQuitMessage()));
            }
        }

        // Remove from NMS world
        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(updatedData.getUuid());
        }

        plugin.getLogger().log(Level.INFO,
                "[DespawnBotAction] Despawned bot '" + name + "'");
        return true;
    }
}