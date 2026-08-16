package org.phantam.fozminespoofv26_1_1.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Packet broadcaster for fake player presence and tablist latency updates in 26.1.1.
 */
public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    private int randomLatency() {
        return ThreadLocalRandom.current().nextInt(20, 201);
    }

    public void sendSpawnPackets(ServerPlayer fakePlayer, String name, boolean hideTab) {
        if (!hideTab) {
            int latency = randomLatency();

            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                    fakePlayer.getUUID(),
                    fakePlayer.getGameProfile(),
                    true,
                    latency,
                    fakePlayer.gameMode.getGameModeForPlayer(),
                    fakePlayer.getDisplayName(),
                    true,
                    0,
                    null
            );

            ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                    ),
                    List.of(entry)
            );
            broadcastExcept(fakePlayer.getUUID(), tabPacket);
        }

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                fakePlayer.getId(),
                fakePlayer.getUUID(),
                fakePlayer.getX(),
                fakePlayer.getY(),
                fakePlayer.getZ(),
                fakePlayer.getXRot(),
                fakePlayer.getYRot(),
                EntityType.PLAYER,
                0,
                Vec3.ZERO,
                fakePlayer.getYHeadRot()
        );

        ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
                fakePlayer,
                (byte) (fakePlayer.getYRot() * 256.0F / 360.0F)
        );

        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
        broadcastExcept(fakePlayer.getUUID(), headPacket);
    }

    public void sendLatencyPacket(UUID excludedUuid, ClientboundPlayerInfoUpdatePacket packet) {
        broadcastExcept(excludedUuid, packet);
    }

    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        broadcastExcept(uuid, destroyPacket);
        broadcastExcept(uuid, removeTabPacket);
    }

    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer player : playerList.players) {
            if (player == null || player.getUUID().equals(excludedUuid)) continue;

            if (player.connection != null && !(player.connection instanceof FakeServerGamePacketListenerImpl)) {
                player.connection.send(packet);
            }
        }
    }
}