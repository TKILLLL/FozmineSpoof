package org.phantam.fozminesproofcore.database.executors;

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
import org.phantam.fozminesproofcore.manager.FakePlayerRegistry;
import org.phantam.fozminesproofcore.utils.ColorUtils;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Handles spawning a fake player into the world asynchronously.
 * Triggers all necessary Bukkit events (PreLogin, Login, Join) to simulate a real player.
 */
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
     * Asynchronously spawns a bot by name.
     * Calls the callback with true on success, false otherwise.
     *
     * @param name     the bot name
     * @param callback result consumer
     */
    public void executeAsync(String name, Consumer<Boolean> callback) {
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

            // Trigger pre-login event
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

            // Spawn on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Update active status using withActive()
                FakePlayerData updatedData = data.withActive(true);
                database.saveFakePlayer(updatedData);

                Player entity = spawnNpc(updatedData);
                if (entity == null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[SpawnBotAction] Failed to spawn NPC for '" + name + "'");
                    callback.accept(false);
                    return;
                }

                // Trigger login event
                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    plugin.getLogger().log(Level.WARNING,
                            "[SpawnBotAction] Login denied for '" + name + "'");
                    callback.accept(false);
                    return;
                }

                // Trigger join event
                String joinMsg = plugin.getConfigManager().getJoinMessage()
                        .replace("%fakeplayer_name%", entity.getName());
                PlayerJoinEvent joinEvent = new PlayerJoinEvent(entity, joinMsg);
                Bukkit.getPluginManager().callEvent(joinEvent);
                if (joinEvent.getJoinMessage() != null && !joinEvent.getJoinMessage().isEmpty()) {
                    Bukkit.broadcastMessage(ColorUtils.colorize(joinEvent.getJoinMessage()));
                }

                // Set tablist name with PlaceholderAPI support
                String tabFormat = plugin.getConfigManager().getTabFormat()
                        .replace("%fakeplayer_name%", updatedData.getName());
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    tabFormat = PlaceholderAPI.setPlaceholders(entity, tabFormat);
                }
                entity.setPlayerListName(ColorUtils.colorize(tabFormat));

                // Register in registry
                registry.register(updatedData, entity);

                plugin.getLogger().log(Level.INFO,
                        "[SpawnBotAction] Successfully spawned bot '" + name + "'");
                callback.accept(true);
            });
        });
    }

    /**
     * Internal method to spawn the NPC using the NMS bridge.
     * Ensures a solid block exists beneath the spawn location.
     *
     * @param data the bot data
     * @return the spawned Player, or null if failed
     */
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