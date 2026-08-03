package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.phantam.fozminespoofapi.action.IBotAction;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminespoofcore.manager.BotLifecycleManager;
import org.phantam.fozminespoofcore.manager.FakePlayerRegistry;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Action that despawns a fake player from the world and updates its active state.
 * <p>
 * This action handles:
 * <ul>
 *   <li>Updating the bot's active flag to {@code false} in the database</li>
 *   <li>Firing a {@link PlayerQuitEvent} for plugin compatibility</li>
 *   <li>Broadcasting custom leave messages (if configured)</li>
 *   <li>Resetting ranks (if rank management is enabled)</li>
 *   <li>Unregistering the bot from the online registry</li>
 *   <li>Removing the entity via the NMS bridge</li>
 * </ul>
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
 * @see SpawnBotAction
 * @see FakePlayerRegistry
 */
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
            boolean enable = plugin.getConfigManager().isJoinLeaveMessageEnable();
            String format = plugin.getConfigManager().getJoinLeaveFormat();

            String quitMessage;
            if (!enable) {
                quitMessage = null;
            } else if ("custom".equalsIgnoreCase(format)) {
                quitMessage = null;
            } else {
                quitMessage = botEntity.getName() + " left the game";
            }

            PlayerQuitEvent quitEvent = new PlayerQuitEvent(botEntity, quitMessage);
            Bukkit.getPluginManager().callEvent(quitEvent);

            if (enable) {
                if ("custom".equalsIgnoreCase(format)) {
                    broadcaster.broadcastLeave(name);
                }
                else if ("normal".equalsIgnoreCase(format)) {
                    if (quitEvent.getQuitMessage() != null && !quitEvent.getQuitMessage().isEmpty()) {
                        Bukkit.broadcastMessage(quitEvent.getQuitMessage());
                    }
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
        } else {
            DebugLogger.log(plugin.getLogger(), "DespawnBotAction: bridge is null, cannot despawn player %s", name);
        }

        DebugLogger.log(plugin.getLogger(), "DespawnBotAction: despawn completed for %s", name);
        return true;
    }

    public void setLifecycleManager(BotLifecycleManager lifecycle) {
        this.lifecycle = lifecycle;
    }
}