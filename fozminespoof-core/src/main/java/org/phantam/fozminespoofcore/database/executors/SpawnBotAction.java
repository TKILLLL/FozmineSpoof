package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
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
import java.util.logging.Level;

/**
 * Asynchronous action that spawns a fake player into the world.
 * <p>
 * This action handles the entire spawn pipeline:
 * <ol>
 *   <li>Loads the bot data from the database</li>
 *   <li>Fires an {@link AsyncPlayerPreLoginEvent} to allow other plugins to validate</li>
 *   <li>Creates the NMS entity in the configured bot world</li>
 *   <li>Registers the bot in the registry and fires a {@link PlayerLoginEvent}</li>
 *   <li>Applies rank, broadcasts join messages, and executes join commands</li>
 * </ol>
 * All heavy operations are executed asynchronously, with the final entity creation
 * performed on the main thread.
 * </p>
 *
 * @author Phantam
 * @version 2.0.0
 * @see DespawnBotAction
 * @see FakePlayerRegistry
 */
public class SpawnBotAction implements org.phantam.fozminespoofapi.action.IBotAction<String, Boolean> {

    private final FozmineSpoofCore plugin;
    private final IFakePlayerDatabase database;
    private final FakePlayerRegistry registry;
    private final FakePlayerBroadcaster broadcaster;
    private BotLifecycleManager lifecycle;

    /**
     * Constructs a new SpawnBotAction with the required dependencies.
     *
     * @param plugin      the core plugin instance
     * @param database    the database access layer
     * @param registry    the registry for online bots
     * @param broadcaster the broadcaster for join messages
     */
    public SpawnBotAction(FozmineSpoofCore plugin, IFakePlayerDatabase database,
                          FakePlayerRegistry registry, FakePlayerBroadcaster broadcaster) {
        this.plugin = plugin;
        this.database = database;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    /**
     * This action must be executed asynchronously.
     *
     * @param name the bot name (unused)
     * @return never returns; throws UnsupportedOperationException
     * @throws UnsupportedOperationException always
     */
    @Override
    public Boolean execute(String name) {
        throw new UnsupportedOperationException("Use executeAsync instead");
    }

    /**
     * Asynchronously spawns a fake player.
     * <p>
     * The spawn process is divided into asynchronous (database and pre-login) and
     * synchronous (entity creation and post-login) stages to ensure proper
     * thread-safety with Bukkit's event system.
     * </p>
     *
     * @param name     the name of the bot to spawn
     * @param callback callback that receives {@code true} on success, {@code false} on failure
     */
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

            // Fire async pre-login event to allow other plugins to validate
            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(), address, uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            // Switch to main thread for entity creation
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location forcedLocation = getForceBotWorldLocation();
                if (forcedLocation == null) {
                    plugin.getLogger().warning("[SpawnBotAction] Could not resolve botworld spawn location!");
                    callback.accept(false);
                    return;
                }

                // Update bot data with the actual spawn location and set active status
                FakePlayerData updatedData = new FakePlayerData.Builder()
                        .name(data.getName())
                        .uuid(data.getUuid())
                        .world(forcedLocation.getWorld().getName())
                        .location(forcedLocation.getX(), forcedLocation.getY(), forcedLocation.getZ(),
                                forcedLocation.getYaw(), forcedLocation.getPitch())
                        .active(true)
                        .build();

                // Update database asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveFakePlayer(updatedData));

                // Spawn the NPC entity via NMS bridge
                Player entity = spawnNpcInBotWorld(updatedData, forcedLocation);
                if (entity == null) {
                    callback.accept(false);
                    return;
                }

                // Ensure the entity is in the correct world
                if (!entity.getWorld().getName().equalsIgnoreCase(forcedLocation.getWorld().getName())) {
                    entity.teleport(forcedLocation);
                }

                // Register in the online registry
                registry.register(updatedData, entity);

                // Notify lifecycle manager
                if (lifecycle != null) {
                    lifecycle.onBotSpawn(entity.getName());
                }

                // Fire login event to allow plugins to accept/reject
                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    // Despawn the entity and clean up if login was rejected
                    if (plugin.getBridge() != null) {
                        plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    } else {
                        plugin.getLogger().warning("[SpawnBotAction] Bridge is null; cannot despawn player " + name);
                    }
                    registry.unregister(name);
                    callback.accept(false);
                    return;
                }

                // Apply rank if configured
                if (plugin.getConfigManager().isRankWeightEnabled() && plugin.getRankWeightManager() != null) {
                    String chosenRank = plugin.getRankWeightManager().getRandomRank(plugin.getConfigManager().getRankWeights());
                    plugin.getRankWeightManager().assignRank(entity, chosenRank);
                }

                // Broadcast custom join message only if format is "custom" and enabled
                if (plugin.getConfigManager().isJoinLeaveMessageEnable()
                        && "custom".equalsIgnoreCase(plugin.getConfigManager().getJoinLeaveFormat())) {
                    broadcaster.broadcastJoin(entity.getName());
                }

                // Execute join commands (fakeplayer and/or console)
                boolean fakeEnabled = plugin.getConfigManager().isFakePlayerJoinCommandsEnabled();
                boolean consoleEnabled = plugin.getConfigManager().isConsoleJoinCommandsEnabled();

                if (fakeEnabled || consoleEnabled) {
                    List<String> fakeCommands = plugin.getConfigManager().getFakePlayerJoinCommands();
                    List<String> consoleCommands = plugin.getConfigManager().getConsoleJoinCommands();

                    if ((fakeCommands != null && !fakeCommands.isEmpty()) ||
                            (consoleCommands != null && !consoleCommands.isEmpty())) {
                        JoinActionExecutor.execute(entity, fakeCommands, fakeEnabled,
                                consoleCommands, consoleEnabled, plugin.getLogger());
                    }
                }

                // Spawn successful
                callback.accept(true);
            });
        });
    }

    /**
     * Resolves the spawn location in the configured bot world.
     * <p>
     * If the configured world does not exist, falls back to the server's default world.
     * </p>
     *
     * @return the spawn location, or {@code null} if no world is available
     */
    private Location getForceBotWorldLocation() {
        String worldName = plugin.getConfigManager().getBotWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
            plugin.getLogger().log(Level.FINE,
                    "[SpawnBotAction] Bot world '" + worldName + "' not found, using default world: "
                            + world.getName());
        }

        if (world == null) {
            plugin.getLogger().severe("[SpawnBotAction] No world available for spawning bots!");
            return null;
        }

        return world.getSpawnLocation();
    }

    /**
     * Spawns the NPC entity via the NMS bridge.
     *
     * @param data           the bot data
     * @param forcedLocation the spawn location
     * @return the Bukkit Player entity, or {@code null} if spawning failed
     */
    private Player spawnNpcInBotWorld(FakePlayerData data, Location forcedLocation) {
        if (plugin.getBridge() == null) {
            plugin.getLogger().severe("[SpawnBotAction] NMS bridge is not available!");
            return null;
        }
        if (forcedLocation == null) {
            return null;
        }

        boolean hideTab = plugin.getConfigManager().isHideInTab();
        return plugin.getBridge().spawnPlayer(data.getName(), data.getUuid(), forcedLocation, hideTab);
    }

    /**
     * Sets the lifecycle manager for this action.
     *
     * @param lifecycle the lifecycle manager
     */
    public void setLifecycleManager(BotLifecycleManager lifecycle) {
        this.lifecycle = lifecycle;
    }
}