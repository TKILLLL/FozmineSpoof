package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofapi.action.IBotAction;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminespoofcore.manager.BotLifecycleManager;
import org.phantam.fozminespoofcore.manager.FakePlayerRegistry;
import org.phantam.fozminespoofapi.utils.DebugLogger;

import java.util.Optional;
import java.util.logging.Level;

public class DespawnBotAction implements IBotAction<String, Boolean> {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;
    private BotLifecycleManager lifecycle;

    public DespawnBotAction(FozmineSpoofCore plugin, IFakePlayerDatabase database,
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

        if (plugin.getConfigManager().isRankWeightEnabled() && plugin.getRankWeightManager() != null) {
            plugin.getRankWeightManager().resetRank(name);
        }

        if (lifecycle != null) {
            lifecycle.onBotDespawn(name);
        }

        registry.unregister(name);

        if (plugin.getBridge() != null) {
            plugin.getBridge().despawnPlayer(updatedData.getUuid());
        }

        DebugLogger.log(plugin.getLogger(), "DespawnBotAction: despawn completed for %s", name);
        return true;
    }

    public void setLifecycleManager(BotLifecycleManager lifecycle) {
        this.lifecycle = lifecycle;
    }
}