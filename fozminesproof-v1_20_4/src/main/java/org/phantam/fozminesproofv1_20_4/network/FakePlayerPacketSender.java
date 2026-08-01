package org.phantam.fozminesproofv1_20_4.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;

/**
 * Broadcasts packets to all real players to show or hide a fake player.
 * Excludes the fake player itself to prevent recursive packet loops.
 */
public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    /**
     * Sends all packets required to display a fake player to every real player.
     * Includes tablist entry, entity spawn, and head rotation.
     *
     * @param fakePlayer the fake player to show
     * @param name       the player name (unused but kept for clarity)
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer, String name) {
        ClientboundPlayerInfoUpdatePacket tabPacket =
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer));

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);

        ClientboundRotateHeadPacket headPacket =
                new ClientboundRotateHeadPacket(fakePlayer,
                        (byte) (fakePlayer.getYRot() * 256.0F / 360.0F));

        broadcastExcept(fakePlayer.getUUID(), tabPacket);
        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
        broadcastExcept(fakePlayer.getUUID(), headPacket);
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
     * Broadcasts a packet to all real players except the one with the given UUID.
     * Skips players whose connection uses an EmbeddedChannel (i.e., fake players).
     *
     * @param excludedUuid the UUID to exclude
     * @param packet       the packet to send
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer player : playerList.players) {
            if (player == null) continue;
            if (player.getUUID().equals(excludedUuid)) continue;

            if (player.connection != null && player.connection.connection != null) {
                // Never send packets back into an embedded channel (fake connection)
                if (!(player.connection.connection.channel instanceof EmbeddedChannel)) {
                    player.connection.send(packet);
                }
            }
        }
    }
}