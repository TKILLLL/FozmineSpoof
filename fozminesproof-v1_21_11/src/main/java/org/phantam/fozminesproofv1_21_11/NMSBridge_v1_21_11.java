package org.phantam.fozminesproofv1_21_11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofapi.utils.DebugLogger;
import org.phantam.fozminesproofv1_21_11.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_21_11.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * NMS bridge implementation for Minecraft 1.21.11.
 */
public class NMSBridge_v1_21_11 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();
    private Plugin pluginInstance;

    private Plugin getPluginInstance() {
        if (pluginInstance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("fozminesproof-core");
            if (plugin == null) {
                plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass());
            }
            pluginInstance = plugin;
        }
        return pluginInstance;
    }

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc, boolean hideTab) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: spawnPlayer(%s, %s, hideTab=%s)", name, uuid, hideTab);

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);
        activeFakePlayers.put(uuid, fakePlayer);

        Connection connection = fakePlayer.connection.connection;
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(fakePlayer.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, fakePlayer, cookie);

        Player bukkitPlayer = fakePlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer, name, hideTab);

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Spawned fake player '" + name + "' in version 1.21.11");
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: spawnPlayer completed for %s", name);

        return bukkitPlayer;
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: despawnPlayer(%s)", uuid);

        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: despawnPlayer(%s) not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        server.getPlayerList().players.remove(fakePlayer);
        ServerLevel level = fakePlayer.level(); // level() returns ServerLevel in 1.21.11
        level.removePlayerImmediately(fakePlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        fakePlayer.discard();

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Despawned fake player with UUID: " + uuid);
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: despawnPlayer completed for %s", uuid);
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature, boolean hideTab) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: updatePlayerSkin(%s, texture=%s, hideTab=%s)",
                uuid, texture != null ? "provided" : "null", hideTab);

        ServerPlayer oldPlayer = activeFakePlayers.get(uuid);
        if (oldPlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: updatePlayerSkin(%s) player not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = oldPlayer.level();
        Location currentLoc = oldPlayer.getBukkitEntity().getLocation();
        // GameProfile is a record → use name()
        String name = oldPlayer.getGameProfile().name();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        packetSender.sendDespawnPackets(uuid, oldPlayer.getId());
        server.getPlayerList().players.remove(oldPlayer);
        level.removePlayerImmediately(oldPlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        oldPlayer.discard();

        ServerPlayer newPlayer = FakePlayerFactory.create(server, level, name, uuid, currentLoc);

        GameProfile profile = newPlayer.getGameProfile();
        // GameProfile is a record → use properties()
        profile.properties().removeAll("textures");
        profile.properties().put("textures", new Property("textures", texture, signature));

        activeFakePlayers.put(uuid, newPlayer);

        Connection connection = newPlayer.connection.connection;
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(newPlayer.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, newPlayer, cookie);

        Player bukkitPlayer = newPlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        // profile.name() is the record accessor
        packetSender.sendSpawnPackets(newPlayer, profile.name(), hideTab);

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Updated skin for player '" + name + "'");
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: updatePlayerSkin completed for %s", name);
    }

    @Override
    public void sendKeepAlivePackets() {
        if (activeFakePlayers.isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: sendKeepAlivePackets - no active players");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: sendKeepAlivePackets - %d active players",
                activeFakePlayers.size());

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        if (server == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: sendKeepAlivePackets - server is null");
            return;
        }

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        activeFakePlayers.forEach((uuid, fakePlayer) ->
                packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().name(), false)
        );

        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: sendKeepAlivePackets completed");
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: broadcastNMSChat - invalid input");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: broadcastNMSChat - %s: %s", player.getName(), message);

        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

            net.minecraft.network.chat.Component[] components = CraftChatMessage.fromString(message);
            if (components.length == 0) {
                DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: broadcastNMSChat - no components");
                return;
            }

            net.minecraft.network.chat.MutableComponent finalComponent =
                    net.minecraft.network.chat.Component.empty();

            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            server.getPlayerList().broadcastSystemMessage(finalComponent, false);
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: broadcastNMSChat - sent");

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[NMSBridge] Failed to broadcast NMS chat for player "
                            + player.getName() + ": " + e.getMessage(), e);
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_21_11: broadcastNMSChat - error: %s", e.getMessage());
        }
    }

    @Override
    public int getFakePlayersCount() {
        int count = activeFakePlayers.size();
        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_21_11: getFakePlayersCount = %d", count);
        return count;
    }
}