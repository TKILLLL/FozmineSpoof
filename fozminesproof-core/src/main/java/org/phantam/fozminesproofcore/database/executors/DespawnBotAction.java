package org.phantam.fozminesproofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminesproofapi.action.IBotAction;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.manager.FakePlayerRegistry;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.util.Optional;
import java.util.logging.Level;

public class DespawnBotAction implements IBotAction<String, Boolean> {

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
        DebugLogger.log(plugin.getLogger(), "DespawnBotAction: starting despawn for '%s'", name);

        Player botEntity = plugin.getFakePlayerManager().getOnlineBotEntity(name);

        FakePlayerData data = registry.getData(name);
        if (data == null) {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        "[DespawnBotAction] Bot '" + name + "' not found in database");
                return false;
            }
            data = opt.get();
        }

        FakePlayerData updatedData = data.withActive(false);
        database.saveFakePlayer(updatedData);

        if (botEntity != null) {
            String quitMessage = null;
            if (!plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                quitMessage = botEntity.getName() + " left the game";
            }

            PlayerQuitEvent quitEvent = new PlayerQuitEvent(botEntity, quitMessage);
            Bukkit.getPluginManager().callEvent(quitEvent);

            if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                broadcaster.broadcastLeave(name);
            } else {
                String finalQuitMsg = quitEvent.getQuitMessage();
                if (finalQuitMsg != null && !finalQuitMsg.trim().isEmpty()) {
                    Bukkit.broadcastMessage(finalQuitMsg);
                }
            }
        }

        // Unregister khỏi registry
        registry.unregister(name);

        // Remove khỏi NMS world
        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(updatedData.getUuid());
        }

        DebugLogger.log(plugin.getLogger(), "DespawnBotAction: despawn completed for %s", name);
        return true;
    }
}