package org.phantam.fozminesproofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.manager.FakePlayerRegistry;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SpawnBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {

    private final FozmineSproofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;

    public SpawnBotAction(FozmineSproofCore plugin, IFakePlayerDatabase database,
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
        DebugLogger.log(plugin.getLogger(), "SpawnBotAction: starting async spawn for '%s'", name);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        "[SpawnBotAction] Bot '" + name + "' not found in database");
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            FakePlayerData data = opt.get();
            InetAddress address = InetAddress.getLoopbackAddress();
            UUID uuid = data.getUuid();

            // Kích hoạt AsyncPlayerPreLoginEvent
            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(), address, uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                plugin.getLogger().log(Level.WARNING,
                        "[SpawnBotAction] PreLogin denied for '" + name + "'");
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            // Spawn trên Main Thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                FakePlayerData updatedData = data.withActive(true);
                database.saveFakePlayer(updatedData);

                // NMS placeNewPlayer trong spawnNpc sẽ tự động kích hoạt PlayerJoinEvent
                Player entity = spawnNpc(updatedData);
                if (entity == null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[SpawnBotAction] Failed to spawn NPC for '" + name + "'");
                    callback.accept(false);
                    return;
                }

                // Đăng ký bot vào Registry ngay sau khi spawn
                registry.register(updatedData, entity);

                // Kích hoạt PlayerLoginEvent
                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    registry.unregister(name);
                    plugin.getLogger().log(Level.WARNING,
                            "[SpawnBotAction] Login denied for '" + name + "'");
                    callback.accept(false);
                    return;
                }

                // Phát custom join message nếu config bật
                if (plugin.getConfigManager().isJoinLeaveMessageEnable()) {
                    broadcaster.broadcastJoin(entity.getName());
                }

                DebugLogger.log(plugin.getLogger(), "SpawnBotAction: spawn completed successfully for %s", name);
                callback.accept(true);
            });
        });
    }

    private Player spawnNpc(FakePlayerData data) {
        if (plugin.getBridge() == null) {
            return null;
        }

        String worldName = data.getWorldName();
        if (worldName == null || worldName.isEmpty()) {
            worldName = plugin.getConfigManager().getBotWorldName();
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(),
                data.getYaw(), data.getPitch());

        Location below = loc.clone().subtract(0, 1, 0);
        if (below.getBlock().getType() == org.bukkit.Material.AIR) {
            below.getBlock().setType(org.bukkit.Material.BEDROCK);
        }

        boolean hideTab = plugin.getConfigManager().isHideInTab();
        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc, hideTab);
    }
}