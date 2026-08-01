package org.phantam.fozminesproofv1_19_4;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofcore.utils.DebugLogger;
import org.phantam.fozminesproofv1_19_4.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_19_4.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NMSBridge_v1_19_4 implements FozminesproofApi {

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
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: spawnPlayer(%s, %s, %s, hideTab=%s)",
                name, uuid, loc, hideTab);

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

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Spawned fake player '" + name + "' in version 1.19.4");
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: spawnPlayer completed for %s", name);

        return bukkitPlayer;
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: despawnPlayer(%s)", uuid);

        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: despawnPlayer(%s) not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        server.getPlayerList().players.remove(fakePlayer);
        ServerLevel level = fakePlayer.getLevel();
        level.removePlayerImmediately(fakePlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        fakePlayer.discard();

        Bukkit.getLogger().log(Level.INFO, "[NMSBridge] Despawned fake player with UUID: " + uuid);
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: despawnPlayer completed for %s", uuid);
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature, boolean hideTab) {
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: updatePlayerSkin(%s, texture=%s, hideTab=%s)",
                uuid, texture != null ? "provided" : "null", hideTab);

        ServerPlayer oldPlayer = activeFakePlayers.get(uuid);
        if (oldPlayer == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: updatePlayerSkin(%s) player not found", uuid);
            return;
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = oldPlayer.getLevel();
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
        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: updatePlayerSkin completed for %s", name);
    }

    @Override
    public void sendKeepAlivePackets() {
        if (activeFakePlayers.isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: sendKeepAlivePackets - no active players");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: sendKeepAlivePackets - %d active players",
                activeFakePlayers.size());

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        if (server == null) {
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: sendKeepAlivePackets - server is null");
            return;
        }

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        activeFakePlayers.forEach((uuid, fakePlayer) ->
                packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().getName(), false)
        );

        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: sendKeepAlivePackets completed");
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) {
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: broadcastNMSChat - invalid input");
            return;
        }

        DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: broadcastNMSChat - %s: %s", player.getName(), message);

        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

            net.minecraft.network.chat.Component[] components =
                    CraftChatMessage.fromString(message);
            if (components.length == 0) {
                DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: broadcastNMSChat - no components");
                return;
            }

            net.minecraft.network.chat.MutableComponent finalComponent =
                    net.minecraft.network.chat.Component.empty();

            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            server.getPlayerList().broadcastSystemMessage(finalComponent, false);
            DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: broadcastNMSChat - sent");

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[NMSBridge] Failed to broadcast NMS chat for player "
                            + player.getName() + ": " + e.getMessage(), e);
            DebugLogger.log(Bukkit.getLogger(), "NMSBridge_v1_19_4: broadcastNMSChat - error: %s", e.getMessage());
        }
    }

    @Override
    public int getFakePlayersCount() {
        int count = activeFakePlayers.size();
        DebugLogger.logFine(Bukkit.getLogger(), "NMSBridge_v1_19_4: getFakePlayersCount = %d", count);
        return count;
    }
}