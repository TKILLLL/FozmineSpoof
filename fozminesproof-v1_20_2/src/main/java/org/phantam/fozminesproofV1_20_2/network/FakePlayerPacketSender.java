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
     * Phát chuỗi gói tin hiển thị NPC ra toàn Server và ép hiển thị trên Tablist
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer, String name) {
        // Tạo một entry chi tiết chứa đầy đủ dữ liệu trạng thái mạng giả lập cho Bot
        ClientboundPlayerInfoUpdatePacket.Entry playerEntry = new ClientboundPlayerInfoUpdatePacket.Entry(
                fakePlayer.getUUID(),
                fakePlayer.getGameProfile(),
                true,                 // listed = true: Ép buộc hiển thị lên giao diện Tablist công khai
                0,                    // Latency (Ping) = 0ms
                GameType.SURVIVAL,    // Chế độ chơi sinh tồn
                Component.literal(name), // Tên hiển thị trên Tablist
                null                  // Sửa lỗi hiển thị Chat Session (Không cần thiết cho Bot)
        );

        // Đóng gói tổ hợp 4 hành động bắt buộc của Mojang 1.20.2 để Client vẽ tên Bot lên màn hình Tab
        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE
                ),
                List.of(playerEntry)
        );

        // Tạo gói tin sinh khối mô hình thực thể người chơi 3D
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer);

        // Gửi đồng loạt chuỗi dữ liệu tới toàn bộ người chơi thực tế đang trực tuyến
        broadcastExcept(fakePlayer.getUUID(), tabPacket);
        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
    }

    /**
     * Phát chuỗi gói tin hủy bỏ NPC khỏi thế giới và xóa tên khỏi Tablist
     */
    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        // Tiến hành xóa mô hình 3D trước, sau đó gỡ nhãn tên trên Tablist để tránh lỗi ghost name
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
