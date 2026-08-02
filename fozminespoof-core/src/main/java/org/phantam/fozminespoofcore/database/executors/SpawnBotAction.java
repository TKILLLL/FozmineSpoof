package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofcore.FozmineSpoofCore;
import org.phantam.fozminespoofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminespoofcore.manager.BotLifecycleManager;
import org.phantam.fozminespoofcore.manager.FakePlayerRegistry;
import org.phantam.fozminespoofcore.utils.JoinActionExecutor;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class SpawnBotAction implements org.phantam.fozminespoofapi.action.IBotAction<String, Boolean> {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;
    private BotLifecycleManager lifecycle;

    public SpawnBotAction(FozmineSpoofCore plugin, IFakePlayerDatabase database,
                          FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    @Override
    public Boolean execute(String name) {
        throw new UnsupportedOperationException("Use executeAsync instead");
    }

    public void executeAsync(String name, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            FakePlayerData data = opt.get();
            InetAddress address = InetAddress.getLoopbackAddress();
            UUID uuid = data.getUuid();

            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(), address, uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                FakePlayerData updatedData = data.withActive(true);

                // Async save to prevent main thread tick spikes
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveFakePlayer(updatedData));

                Player entity = spawnNpc(updatedData);
                if (entity == null) {
                    callback.accept(false);
                    return;
                }

                registry.register(updatedData, entity);

                if (lifecycle != null) {
                    lifecycle.onBotSpawn(entity.getName());
                }

                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    registry.unregister(name);
                    callback.accept(false);
                    return;
                }

                if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                    broadcaster.broadcastJoin(entity.getName());
                }

                boolean fakeEnabled = plugin.getConfigManager().isFakePlayerJoinCommandsEnabled();
                boolean consoleEnabled = plugin.getConfigManager().isConsoleJoinCommandsEnabled();

                if (fakeEnabled || consoleEnabled) {
                    List<String> fakeCommands = plugin.getConfigManager().getFakePlayerJoinCommands();
                    List<String> consoleCommands = plugin.getConfigManager().getConsoleJoinCommands();

                    if ((fakeCommands != null && !fakeCommands.isEmpty()) ||
                            (consoleCommands != null && !consoleCommands.isEmpty())) {
                        JoinActionExecutor.execute(entity, fakeCommands, fakeEnabled, consoleCommands, consoleEnabled, plugin.getLogger());
                    }
                }

                callback.accept(true);
            });
        });
    }

    private Player spawnNpc(FakePlayerData data) {
        if (plugin.getBridge() == null) return null;

        String worldName = data.getWorldName();
        if (worldName == null || worldName.isEmpty()) {
            worldName = plugin.getConfigManager().getBotWorldName();
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
        boolean hideTab = plugin.getConfigManager().isHideInTab();
        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc, hideTab);
    }

    public void setLifecycleManager(BotLifecycleManager lifecycle) {
        this.lifecycle = lifecycle;
    }
}