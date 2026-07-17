package org.phantam.fozminesproofv1_21_11.network;

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
        // VÁ LỖI KHỞI TẠO TABLIST: Sử dụng phương thức tĩnh có sẵn trong mã nguồn gói tin hệ thống của bạn.
        // Hàm này tự động đóng gói toàn bộ Action cần thiết và bọc dữ liệu thực thể mà không lo sai lệch tham số Record.
        ClientboundPlayerInfoUpdatePacket tabPacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer));

        // VÁ LỖI CONSTRUCTOR SPAWN ENTITY 1.21.11: Gọi chính xác hàm tạo nhận 3 tham số (Entity, int, BlockPos)
        // khớp hoàn toàn với file .class decompiled bạn cung cấp, sử dụng vị trí khối thực tế của Bot.
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

        // Tiến hành xóa mô hình 3D trước, sau đó gỡ nhãn tên trên Tablist để tránh lỗi ghost name trên giao diện người chơi
        broadcastExcept(uuid, destroyPacket);
        broadcastExcept(uuid, removeTabPacket);
    }

    /**
     * Hàm phụ trợ phân phát Packet mạng đến mọi người chơi thực tế trừ Bot
     */
    private void broadcastExcept(UUID excludedUuid, Packet<?> packet) {
        for (ServerPlayer realPlayer : playerList.players) {
            if (realPlayer == null) continue;

            if (realPlayer.getUUID().equals(excludedUuid)) continue;

            if (realPlayer.connection != null && realPlayer.connection.connection != null) {
                // Kiểm tra xem kênh mạng có phải kênh ảo của Bot hay không, ngăn chặn gửi ngược packet vào RAM ảo gây rò rỉ bộ nhớ
                if (!(realPlayer.connection.connection.channel instanceof EmbeddedChannel)) {
                    realPlayer.connection.send(packet);
                }
            }
        }
    }
}
