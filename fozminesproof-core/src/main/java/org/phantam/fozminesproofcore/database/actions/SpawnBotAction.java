package org.phantam.fozminesproofcore.database.actions;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.FozmineSproofCore;
import org.phantam.fozminesproofcore.chat.FakePlayerBroadcaster;
import org.phantam.fozminesproofcore.database.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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

    /**
     * Thực thi spawn bot bất đồng bộ, gọi AsyncPlayerPreLoginEvent từ luồng async
     * @param name Tên bot
     * @param callback Nhận kết quả Boolean (true nếu spawn thành công)
     */
    public void executeAsync(String name, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            FakePlayerData data = opt.get();

            final InetAddress address = InetAddress.getLoopbackAddress();
            final UUID uuid = data.getUuid();

            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(),
                    address,
                    uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                data.setActive(true);
                database.saveFakePlayer(data);

                Player entity = internalNmsSpawn(data);
                if (entity == null) {
                    callback.accept(false);
                    return;
                }

                PlayerLoginEvent loginEvent = new PlayerLoginEvent(
                        entity,
                        "",
                        address
                );
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    plugin.getBridge().despawnPlayer(data.getUuid());
                    callback.accept(false);
                    return;
                }

                String joinMessage = plugin.getConfigManager().getJoinMessage()
                        .replace("%fakeplayer_name%", entity.getName());
                PlayerJoinEvent joinEvent = new PlayerJoinEvent(entity, joinMessage);
                Bukkit.getPluginManager().callEvent(joinEvent);
                if (joinEvent.getJoinMessage() != null && !joinEvent.getJoinMessage().isEmpty()) {
                    Bukkit.broadcastMessage(ColorUtils.colorize(joinEvent.getJoinMessage()));
                }

                String rawTabFormat = plugin.getConfigManager().getTabFormat();
                String formattedTabName = rawTabFormat.replace("%fakeplayer_name%", data.getName());
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    formattedTabName = PlaceholderAPI.setPlaceholders(entity, formattedTabName);
                }
                entity.setPlayerListName(ColorUtils.colorize(formattedTabName));

                registry.register(data, entity);
                callback.accept(true);
            });
        });
    }

    private Player internalNmsSpawn(FakePlayerData data) {
        if (plugin.getBridge() == null) return null;

        String targetWorldName = data.getWorld();
        if (targetWorldName == null || targetWorldName.trim().isEmpty()) {
            targetWorldName = plugin.getConfigManager().getBotWorldName();
        }

        World world = Bukkit.getWorld(targetWorldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());

        Location blockUnderLoc = loc.clone().subtract(0, 1, 0);
        if (blockUnderLoc.getBlock().getType() == org.bukkit.Material.AIR) {
            blockUnderLoc.getBlock().setType(org.bukkit.Material.BEDROCK);
        }

        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc);
    }
}