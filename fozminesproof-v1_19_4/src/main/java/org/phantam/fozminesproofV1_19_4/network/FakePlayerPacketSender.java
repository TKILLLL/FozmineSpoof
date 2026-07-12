package org.phantam.fozminesproofV1_19_4.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    /**
     * Phát chuỗi gói tin hiển thị NPC ra toàn Server (Dành riêng cho 1.19.4)
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer) {
        // 1. Tạo gói tin cập nhật Tablist (Đặc trưng 1.19.4 cho phép đưa thẳng thực thể vào List)
        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                List.of(fakePlayer)
        );

        // 2. Tạo gói tin xuất hiện mô hình thực thể 3D thế mạng cho AddPlayerPacket cũ
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);

        // 3. Tiến hành đồng bộ hóa
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
     * Hàm phụ trợ phân phát Packet mạng đến mọi người chơi thực tế đang trực tuyến
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer realPlayer : playerList.players) {
            if (realPlayer.getUUID().equals(excludedUuid)) continue;
            realPlayer.connection.send(packet);
        }
    }
}
