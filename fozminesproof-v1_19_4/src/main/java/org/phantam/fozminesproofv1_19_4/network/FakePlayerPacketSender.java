package org.phantam.fozminesproofv1_19_4.network;

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
     * Phát chuỗi gói tin hiển thị NPC ra toàn Server và ép hiển thị trên Tablist (Dành riêng cho 1.19.4)
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer) {
        // Cấu hình đầy đủ tổ hợp 4 hành động mạng bắt buộc để kích hoạt hiển thị Tablist trên Client 1.19.4
        // Bản 1.19.4 cho phép truyền trực tiếp đối tượng thực thể fakePlayer vào danh sách xử lý
        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE
                ),
                List.of(fakePlayer)
        );

        // Tạo gói tin xuất hiện mô hình thực thể 3D thế mạng cho AddPlayerPacket cũ của Mojang
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);

        // Tiến hành phân phát đồng loạt gói tin mạng tới toàn bộ người chơi thực tế
        broadcastExcept(fakePlayer.getUUID(), tabPacket);
        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
    }

    /**
     * Phát chuỗi gói tin hủy bỏ NPC khỏi thế giới và xóa tên khỏi Tablist
     */
    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        // Giải phóng mô hình thực thể 3D trước, sau đó xóa tên khỏi danh sách Tablist
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
