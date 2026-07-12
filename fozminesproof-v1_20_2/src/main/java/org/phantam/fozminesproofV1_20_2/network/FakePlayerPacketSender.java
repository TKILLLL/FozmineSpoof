package org.phantam.fozminesproofV1_20_2.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    /**
     * Phát chuỗi gói tin hiển thị NPC ra toàn Server
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer, String name) {
        // 1. Tạo gói tin Tablist
        ClientboundPlayerInfoUpdatePacket.Entry playerEntry = new ClientboundPlayerInfoUpdatePacket.Entry(
                fakePlayer.getUUID(),
                fakePlayer.getGameProfile(),
                true,
                0,
                GameType.SURVIVAL,
                Component.literal(name),
                null
        );

        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                List.of(playerEntry)
        );

        // 2. Tạo gói tin mô hình thực thể 3D
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);

        // 3. Gửi đồng loạt
        broadcastExcept(fakePlayer.getUUID(), tabPacket);
        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
    }

    /**
     * Phát chuỗi gói tin hủy bỏ NPC khỏi thế giới của người chơi khác
     */
    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        broadcastExcept(uuid, destroyPacket);
        broadcastExcept(uuid, removeTabPacket);
    }

    /**
     * Hàm phụ trợ phân phát Packet mạng đến mọi người chơi thực tế
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer realPlayer : playerList.players) {
            if (realPlayer.getUUID().equals(excludedUuid)) continue;
            realPlayer.connection.send(packet);
        }
    }
}
