package org.phantam.fozminespoofcore.database.executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
 * Asynchronous action pipeline that spawns a fake player entity into the server world.
 */
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
        throw new UnsupportedOperationException("SpawnBotAction must be executed asynchronously via executeAsync");
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

            // Fire async pre-login event to allow validation from security plugins
            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                    data.getName(), address, uuid
            );
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                return;
            }

            // Transition to main server thread for entity creation and world placement
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location forcedLocation = getForceBotWorldLocation();
                if (forcedLocation == null) {
                    plugin.getLogger().warning("[SpawnBotAction] Could not resolve botworld spawn location!");
                    callback.accept(false);
                    return;
                }

                FakePlayerData updatedData = new FakePlayerData.Builder()
                        .name(data.getName())
                        .uuid(data.getUuid())
                        .world(forcedLocation.getWorld().getName())
                        .location(forcedLocation.getX(), forcedLocation.getY(), forcedLocation.getZ(),
                                forcedLocation.getYaw(), forcedLocation.getPitch())
                        .active(true)
                        .build();

                // Persist active state asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveFakePlayer(updatedData));

                // Spawn NMS player entity
                Player entity = spawnNpcInBotWorld(updatedData, forcedLocation);
                if (entity == null) {
                    callback.accept(false);
                    return;
                }

                // Ensure entity is positioned in target world
                if (!entity.getWorld().getName().equalsIgnoreCase(forcedLocation.getWorld().getName())) {
                    entity.teleport(forcedLocation);
                }

                registry.register(updatedData, entity);

                // Fire standard player login event
                PlayerLoginEvent loginEvent = new PlayerLoginEvent(entity, "", address);
                Bukkit.getPluginManager().callEvent(loginEvent);
                if (loginEvent.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    if (plugin.getBridge() != null) {
                        plugin.getBridge().despawnPlayer(updatedData.getUuid());
                    }
                    registry.unregister(name);
                    callback.accept(false);
                    return;
                }

                // Notify lifecycle manager on successful spawn
                if (lifecycle != null) {
                    lifecycle.onBotSpawn(entity.getName());
                }

                // Apply rank assignment if enabled
                if (plugin.getConfigManager().isRankWeightEnabled() && plugin.getRankWeightManager() != null) {
                    String chosenRank = plugin.getRankWeightManager().getRandomRank(plugin.getConfigManager().getRankWeights());
                    plugin.getRankWeightManager().assignRank(entity, chosenRank);
                }

                // Broadcast join message if configured
                if (plugin.getConfigManager().isJoinLeaveMessageEnable()
                        && "custom".equalsIgnoreCase(plugin.getConfigManager().getJoinLeaveFormat())) {
                    broadcaster.broadcastJoin(entity.getName());
                }

                // Execute post-login commands
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

                // Apply skin textures asynchronously if SkinManager is available
                if (plugin.getSkinManager() != null) {
                    plugin.getSkinManager().getSkinAsync(data.getName()).thenAccept(skinOpt -> {
                        skinOpt.ifPresent(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (plugin.getFakePlayerManager().isBotOnline(data.getName())) {
                                plugin.getBridge().updatePlayerSkin(data.getUuid(), skin.value(), skin.signature(), plugin.getConfigManager().isHideInTab());
                            }
                        }));
                    });
                }

                callback.accept(true);
            });
        });
    }

    private Location getForceBotWorldLocation() {
        String worldName = plugin.getConfigManager().getBotWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
            plugin.getLogger().log(Level.FINE,
                    "[SpawnBotAction] Bot world '" + worldName + "' not found, falling back to default: " + world.getName());
        }

        if (world == null) {
            plugin.getLogger().severe("[SpawnBotAction] No world available for spawning bots!");
            return null;
        }

        return world.getSpawnLocation();
    }

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

    public void setLifecycleManager(BotLifecycleManager lifecycle) {
        this.lifecycle = lifecycle;
    }
}