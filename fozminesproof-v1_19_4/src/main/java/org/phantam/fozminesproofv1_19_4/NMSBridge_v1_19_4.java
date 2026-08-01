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
import org.phantam.fozminesproofv1_19_4.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_19_4.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NMSBridge_v1_19_4 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();

    private Plugin getPluginInstance() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("FozmineSproofCore");
        if (plugin == null) {
            plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass());
        }
        return plugin;
    }

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);

        activeFakePlayers.put(uuid, fakePlayer);

        Connection connection = fakePlayer.connection.connection;
        server.getPlayerList().placeNewPlayer(connection, fakePlayer);

        Player bukkitPlayer = fakePlayer.getBukkitEntity();
        bukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer, name);

        return bukkitPlayer;
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        server.getPlayerList().players.remove(fakePlayer);
        if (fakePlayer.getLevel() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) fakePlayer.getLevel();
            level.removePlayerImmediately(fakePlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        fakePlayer.discard();
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature) {
        ServerPlayer oldFakePlayer = activeFakePlayers.get(uuid);
        if (oldFakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = (ServerLevel) oldFakePlayer.getLevel();
        Location currentLoc = oldFakePlayer.getBukkitEntity().getLocation();
        String name = oldFakePlayer.getGameProfile().getName();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        packetSender.sendDespawnPackets(uuid, oldFakePlayer.getId());

        server.getPlayerList().players.remove(oldFakePlayer);
        level.removePlayerImmediately(oldFakePlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        oldFakePlayer.discard();

        ServerPlayer newFakePlayer = FakePlayerFactory.create(server, level, name, uuid, currentLoc);

        GameProfile profile = newFakePlayer.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", texture, signature));

        activeFakePlayers.put(uuid, newFakePlayer);

        Connection connection = newFakePlayer.connection.connection;
        server.getPlayerList().placeNewPlayer(connection, newFakePlayer);

        Player newBukkitPlayer = newFakePlayer.getBukkitEntity();
        newBukkitPlayer.setMetadata("NPC", new FixedMetadataValue(getPluginInstance(), true));

        packetSender.sendSpawnPackets(newFakePlayer, profile.getName());
    }

    @Override
    public void sendKeepAlivePackets() {
        if (this.activeFakePlayers.isEmpty()) return;

        MinecraftServer server = ((Bukkit.getServer() instanceof CraftServer) ? ((CraftServer) Bukkit.getServer()).getServer() : null);
        if (server == null) return;

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        this.activeFakePlayers.forEach((uuid, fakePlayer) -> {
            packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().getName());
        });
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) return;

        try {
            net.minecraft.server.MinecraftServer server =
                    ((org.bukkit.craftbukkit.v1_19_R3.CraftServer) Bukkit.getServer()).getServer();

            net.minecraft.network.chat.Component[] components = CraftChatMessage.fromString(message);
            if (components.length == 0) return;

            net.minecraft.network.chat.MutableComponent finalComponent = net.minecraft.network.chat.Component.empty();

            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            server.getPlayerList().broadcastSystemMessage(finalComponent, false);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[Fozminesproof] Khong the gui packet chat NMS cho: " + player.getName());
            e.printStackTrace();
        }
    }

    @Override
    public int getFakePlayersCount() {
        return this.activeFakePlayers.size();
    }
}