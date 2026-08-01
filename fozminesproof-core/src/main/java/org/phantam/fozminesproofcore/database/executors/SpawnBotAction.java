package org.phantam.fozminesproofcore.database.executors;

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
import org.phantam.fozminesproofapi.utils.DebugLogger;

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
        DebugLogger.log(plugin.getLogger(), "SpawnBotAction: starting async spawn for '%s'", name);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakePlayerData> opt = database.loadFakePlayer(name);
            if (opt.isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        "[SpawnBotAction] Bot '" + name + "' not found in database");
                DebugLogger.log(plugin.getLogger(), "SpawnBotAction: bot '%s' not found in database", name);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            FakePlayerData data = opt.get();
            InetAddress address = InetAddress.getLoopbackAddress();
            UUID uuid = data.getUuid();

            DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: loaded data for %s (uuid=%s)", name, uuid);

            // Trigger pre-login event
            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(), address, uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                plugin.getLogger().log(Level.WARNING,
                        "[SpawnBotAction] PreLogin denied for '" + name + "'");
                DebugLogger.log(plugin.getLogger(), "SpawnBotAction: PreLogin denied for %s", name);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            // Spawn on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Update active status using withActive()
                FakePlayerData updatedData = data.withActive(true);
                database.saveFakePlayer(updatedData);

                DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: saved active=true for %s", name);

                Player entity = spawnNpc(updatedData);
                if (entity == null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[SpawnBotAction] Failed to spawn NPC for '" + name + "'");
                    DebugLogger.log(plugin.getLogger(), "SpawnBotAction: spawnNpc returned null for %s", name);
                    callback.accept(false);
                    return;
                }

                DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: NPC entity created for %s", name);

                // Trigger login event
                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    plugin.getLogger().log(Level.WARNING,
                            "[SpawnBotAction] Login denied for '" + name + "'");
                    DebugLogger.log(plugin.getLogger(), "SpawnBotAction: Login denied for %s", name);
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

                // Set tablist name to a simple colored name (no PlaceholderAPI, no complex format)
                // You can customize this format as needed, e.g., "&a" + updatedData.getName() for green.
                String tabName = "&a" + updatedData.getName(); // Simple green name
                entity.setPlayerListName(ColorUtils.colorize(tabName));

                // Register in registry
                registry.register(updatedData, entity);

                plugin.getLogger().log(Level.INFO,
                        "[SpawnBotAction] Successfully spawned bot '" + name + "'");
                DebugLogger.log(plugin.getLogger(), "SpawnBotAction: spawn completed successfully for %s", name);
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
            DebugLogger.log(plugin.getLogger(), "SpawnBotAction: bridge is null");
            return null;
        }

        String worldName = data.getWorldName();
        if (worldName == null || worldName.isEmpty()) {
            worldName = plugin.getConfigManager().getBotWorldName();
            DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: using configured bot world: %s", worldName);
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
            DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: using fallback world: %s", world.getName());
        }
        if (world == null) {
            DebugLogger.log(plugin.getLogger(), "SpawnBotAction: no world available");
            return null;
        }

        Location loc = new Location(world, data.getX(), data.getY(), data.getZ(),
                data.getYaw(), data.getPitch());

        DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: spawn location %s at %.2f %.2f %.2f",
                world.getName(), data.getX(), data.getY(), data.getZ());

        // Ensure a solid block beneath the player to prevent falling
        Location below = loc.clone().subtract(0, 1, 0);
        if (below.getBlock().getType() == org.bukkit.Material.AIR) {
            below.getBlock().setType(org.bukkit.Material.BEDROCK);
            DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: placed bedrock under %s", data.getName());
        }

        boolean hideTab = plugin.getConfigManager().isHideInTab();
        DebugLogger.logFine(plugin.getLogger(), "SpawnBotAction: hideTab=%s for %s", hideTab, data.getName());

        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), loc, hideTab);
    }
}