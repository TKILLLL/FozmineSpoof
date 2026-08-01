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
import org.phantam.fozminesproofv1_21_11.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_21_11.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * NMS bridge implementation for Minecraft 1.21.11.
 * Handles fake player creation, spawning, despawning, skin updates, and chat broadcasts.
 */
public class NMSBridge_v1_21_11 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();
    private Plugin pluginInstance;

    /**
     * Lazy-initialises and caches the plugin instance for metadata attachment.
     *
     * @return the plugin instance
     */
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
    public Player spawnPlayer(String name, UUID uuid, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);

        activeFakePlayers.put(uuid, fakePlayer);

        // Register the fake player with the server's player list
        Connection connection = fakePlayer.connection.connection;
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(fakePlayer.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, fakePlayer, cookie);

        // Mark as NPC in Bukkit metadata for other plugins to detect
        Player bukkitPlayer = fakePlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        // Broadcast spawn packets to all real players
        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer, name);

        Bukkit.getLogger().log(Level.INFO,
                "[NMSBridge] Spawned fake player '" + name + "' in version 1.21.11");

        return bukkitPlayer;
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        // Broadcast despawn packets
        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        // Remove from server's player list and world
        server.getPlayerList().players.remove(fakePlayer);
        // In 1.21.11+, use level() which returns ServerLevel
        ServerLevel level = fakePlayer.level();
        level.removePlayerImmediately(fakePlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        fakePlayer.discard();

        Bukkit.getLogger().log(Level.INFO,
                "[NMSBridge] Despawned fake player with UUID: " + uuid);
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature) {
        ServerPlayer oldPlayer = activeFakePlayers.get(uuid);
        if (oldPlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        // In 1.21.11+, use level() which returns ServerLevel
        ServerLevel level = oldPlayer.level();
        Location currentLoc = oldPlayer.getBukkitEntity().getLocation();
        // GameProfile is a record in 1.21.11; use name() accessor
        String name = oldPlayer.getGameProfile().name();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        // Remove old player
        packetSender.sendDespawnPackets(uuid, oldPlayer.getId());
        server.getPlayerList().players.remove(oldPlayer);
        level.removePlayerImmediately(oldPlayer,
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        oldPlayer.discard();

        // Create new player with updated skin
        ServerPlayer newPlayer = FakePlayerFactory.create(server, level, name, uuid, currentLoc);

        GameProfile profile = newPlayer.getGameProfile();
        // In 1.21.11, GameProfile is a record; use properties() accessor.
        profile.properties().removeAll("textures");
        profile.properties().put("textures", new Property("textures", texture, signature));

        activeFakePlayers.put(uuid, newPlayer);

        // Register new player
        Connection connection = newPlayer.connection.connection;
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(newPlayer.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, newPlayer, cookie);

        Player bukkitPlayer = newPlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        // Spawn new player - profile.name() is the accessor for record
        packetSender.sendSpawnPackets(newPlayer, profile.name());

        Bukkit.getLogger().log(Level.INFO,
                "[NMSBridge] Updated skin for player '" + name + "'");
    }

    @Override
    public void sendKeepAlivePackets() {
        if (activeFakePlayers.isEmpty()) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        if (server == null) return;

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        activeFakePlayers.forEach((uuid, fakePlayer) ->
                packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().name())
        );
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) return;

        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

            net.minecraft.network.chat.Component[] components =
                    CraftChatMessage.fromString(message);
            if (components.length == 0) return;

            net.minecraft.network.chat.MutableComponent finalComponent =
                    net.minecraft.network.chat.Component.empty();

            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            server.getPlayerList().broadcastSystemMessage(finalComponent, false);

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[NMSBridge] Failed to broadcast NMS chat for player "
                            + player.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int getFakePlayersCount() {
        return activeFakePlayers.size();
    }
}