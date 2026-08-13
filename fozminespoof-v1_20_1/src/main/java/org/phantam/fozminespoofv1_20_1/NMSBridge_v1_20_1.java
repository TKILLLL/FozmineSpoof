package org.phantam.fozminespoofv1_20_1;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.phantam.fozminespoofapi.FozminespoofApi;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofv1_20_1.factory.FakePlayerFactory;
import org.phantam.fozminespoofv1_20_1.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NMSBridge_v1_20_1 implements FozminespoofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();
    private Plugin pluginInstance;

    private Plugin getPluginInstance() {
        if (pluginInstance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("fozminespoof-core");
            if (plugin == null) {
                plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass());
            }
            pluginInstance = plugin;
        }
        return pluginInstance;
    }

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc, boolean hideTab) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: spawnPlayer(%s, %s, hideTab=%s)", name, uuid, hideTab);

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);
        activeFakePlayers.put(uuid, fakePlayer);

        Connection connection = fakePlayer.connection.connection;
        server.getPlayerList().placeNewPlayer(connection, fakePlayer);

        Player bukkitPlayer = fakePlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer, name, hideTab);

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: spawnPlayer completed for %s", name);

        return bukkitPlayer;
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: despawnPlayer(%s)", uuid);

        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: despawnPlayer(%s) not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        server.getPlayerList().players.remove(fakePlayer);
        ServerLevel level = fakePlayer.serverLevel();
        level.removePlayerImmediately(fakePlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        fakePlayer.discard();

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: despawnPlayer completed for %s", uuid);
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature, boolean hideTab) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: updatePlayerSkin(%s, texture=%s, hideTab=%s)",
                uuid, texture != null ? "provided" : "null", hideTab);

        ServerPlayer oldPlayer = activeFakePlayers.get(uuid);
        if (oldPlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: updatePlayerSkin(%s) player not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = oldPlayer.serverLevel();
        Location currentLoc = oldPlayer.getBukkitEntity().getLocation();
        String name = oldPlayer.getGameProfile().getName();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        packetSender.sendDespawnPackets(uuid, oldPlayer.getId());
        server.getPlayerList().players.remove(oldPlayer);
        level.removePlayerImmediately(oldPlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        oldPlayer.discard();

        ServerPlayer newPlayer = FakePlayerFactory.create(server, level, name, uuid, currentLoc);

        GameProfile profile = newPlayer.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", texture, signature));

        activeFakePlayers.put(uuid, newPlayer);

        Connection connection = newPlayer.connection.connection;
        server.getPlayerList().placeNewPlayer(connection, newPlayer);

        Player bukkitPlayer = newPlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        packetSender.sendSpawnPackets(newPlayer, profile.getName(), hideTab);

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Updated skin for player '" + name + "'");
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: updatePlayerSkin completed for %s", name);
    }

    @Override
    public void sendKeepAlivePackets() {
        if (activeFakePlayers.isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: sendKeepAlivePackets - no active players");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: sendKeepAlivePackets - %d active players",
                activeFakePlayers.size());

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        if (server == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: sendKeepAlivePackets - server is null");
            return;
        }

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        activeFakePlayers.forEach((uuid, fakePlayer) ->
                packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().getName(), false)
        );

        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: sendKeepAlivePackets completed");
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: broadcastNMSChat - invalid input");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: broadcastNMSChat - %s: %s", player.getName(), message);

        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

            net.minecraft.network.chat.Component[] components =
                    CraftChatMessage.fromString(message);
            if (components.length == 0) {
                DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: broadcastNMSChat - no components");
                return;
            }

            net.minecraft.network.chat.MutableComponent finalComponent =
                    net.minecraft.network.chat.Component.empty();

            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            server.getPlayerList().broadcastSystemMessage(finalComponent, false);
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: broadcastNMSChat - sent");

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[NMSBridge] Failed to broadcast NMS chat for player "
                            + player.getName() + ": " + e.getMessage(), e);
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_20_1: broadcastNMSChat - error: %s", e.getMessage());
        }
    }

    @Override
    public int getFakePlayersCount() {
        int count = activeFakePlayers.size();
        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_20_1: getFakePlayersCount = %d", count);
        return count;
    }

    @Override
    public boolean isFakePlayer(UUID uuid) {
        boolean isFake = uuid != null && activeFakePlayers.containsKey(uuid);
        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: isFakePlayer(%s) = %b", uuid, isFake);
        return isFake;
    }
}