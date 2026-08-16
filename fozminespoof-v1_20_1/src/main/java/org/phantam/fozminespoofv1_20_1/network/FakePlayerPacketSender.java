package org.phantam.fozminespoofv1_20_1.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Broadcasts packets to all real players to show, update, or hide a fake player in 1.20.1.
 * Excludes simulated fake player connections to prevent recursive packet loops.
 */
public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    private int randomLatency() {
        return ThreadLocalRandom.current().nextInt(20, 201); // 20 - 200 ms
    }

    /**
     * Sends all packets required to display a fake player to every real player.
     * Includes tablist entry, entity spawn, and head rotation.
     *
     * @param fakePlayer the fake player to show
     * @param name       the player name (kept for interface consistency)
     * @param hideTab    if true, skip sending tablist entry
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer, String name, boolean hideTab) {
        fakePlayer.latency = randomLatency();

        if (!hideTab) {
            ClientboundPlayerInfoUpdatePacket tabPacket =
                    ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer));
            broadcastExcept(fakePlayer.getUUID(), tabPacket);
        }

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);
        ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
                fakePlayer,
                (byte) (fakePlayer.getYRot() * 256.0F / 360.0F)
        );

        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
        broadcastExcept(fakePlayer.getUUID(), headPacket);
    }

    /**
     * Sends latency update packet to refresh player ping bars on real clients.
     *
     * @param excludedUuid the UUID of the fake player being updated
     * @param packet       the packet containing the updated latency entry
     */
    public void sendLatencyPacket(UUID excludedUuid, ClientboundPlayerInfoUpdatePacket packet) {
        broadcastExcept(excludedUuid, packet);
    }

    /**
     * Sends packets to remove a fake player from all real players' client.
     * Cleans up both the entity and the tablist entry.
     *
     * @param uuid     the UUID of the fake player
     * @param entityId the entity ID of the fake player
     */
    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        broadcastExcept(uuid, destroyPacket);
        broadcastExcept(uuid, removeTabPacket);
    }

    /**
     * Broadcasts a packet to all real players except the specified UUID.
     * Skips simulated bot connections using fast listener type inspection.
     *
     * @param excludedUuid the UUID to exclude
     * @param packet       the packet to send
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer player : playerList.players) {
            if (player == null || player.getUUID().equals(excludedUuid)) continue;

            if (player.connection != null && !(player.connection instanceof FakeServerGamePacketListenerImpl)) {
                player.connection.send(packet);
            }
        }
    }
}