package org.phantam.fozminesproofv1_21_4.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
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

public class FakePlayerPacketSender {

    private final PlayerList playerList;

    public FakePlayerPacketSender(PlayerList playerList) {
        this.playerList = playerList;
    }

    /**
     * Phát chuỗi gói tin hiển thị NPC ra toàn Server và ép hiển thị trên Tablist
     */
    public void sendSpawnPackets(ServerPlayer fakePlayer, String name) {
        // Khởi tạo gói tin cập nhật danh sách người chơi (Tablist) thông qua hàm static chính thức của Mojang 1.21.4
        ClientboundPlayerInfoUpdatePacket tabPacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer));

        // VÁ LỖI CONSTRUCTOR 1.21.4: Khởi tạo gói tin sinh khối thực thể dựa vào BlockPos của Bot theo đúng file .class bạn cung cấp
        BlockPos botPos = fakePlayer.blockPosition();
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(fakePlayer, 0, botPos);

        // VÁ LỖI QUAY ĐẦU BOT: Đồng bộ góc quay của đầu trùng khớp với hướng nhìn cơ thể thực tế (Yaw)
        ClientboundRotateHeadPacket rotateHeadPacket = new ClientboundRotateHeadPacket(fakePlayer, (byte) (fakePlayer.getYRot() * 256.0F / 360.0F));

        // Gửi đồng loạt chuỗi dữ liệu tới toàn bộ người chơi thực tế đang trực tuyến
        broadcastExcept(fakePlayer.getUUID(), tabPacket);
        broadcastExcept(fakePlayer.getUUID(), spawnPacket);
        broadcastExcept(fakePlayer.getUUID(), rotateHeadPacket);
    }

    /**
     * Phát chuỗi gói tin hủy bỏ NPC khỏi thế giới và xóa tên khỏi Tablist
     */
    public void sendDespawnPackets(UUID uuid, int entityId) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
        ClientboundPlayerInfoRemovePacket removeTabPacket = new ClientboundPlayerInfoRemovePacket(List.of(uuid));

        broadcastExcept(uuid, destroyPacket);
        broadcastExcept(uuid, removeTabPacket);
    }

    /**
     * Hàm phụ trợ phân phát Packet mạng đến mọi người chơi thực tế trừ các Bot ảo sử dụng kênh mạng RAM
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer realPlayer : playerList.players) {
            if (realPlayer == null) continue;

            if (realPlayer.getUUID().equals(excludedUuid)) continue;

            if (realPlayer.connection != null && realPlayer.connection.connection != null) {
                // Kiểm tra chống gửi ngược gói tin vào mạng ảo EmbeddedChannel của Bot gây tràn bộ nhớ RAM ảo
                if (!(realPlayer.connection.connection.channel instanceof EmbeddedChannel)) {
                    realPlayer.connection.send(packet);
                }
            }
        }
    }
}
