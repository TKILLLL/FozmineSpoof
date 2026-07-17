package org.phantam.fozminesproofv1_19_4.factory;

import com.mojang.authlib.GameProfile;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.net.InetSocketAddress;
import java.util.UUID;

public class FakePlayerFactory {

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Paper 1.19.4 dựa trên class Connection cung cấp
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        try {
            // Nạp thuộc tính Skin bản quyền từ Mojang
            profile = server.getSessionService().fillProfileProperties(profile, true);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Fozminesproof] Khong the tu dong tai skin cho " + name + " do loi ket noi Mojang!");
        }

        // Khởi tạo thực thể Bot
        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile);

        // Tạo luồng xử lý ảo độc lập chống nghẽn và sập Main Thread
        EventLoop mockEventLoop = new LocalEventLoopGroup(1).next();

        // Tạo kênh mạng ảo Netty chạy hoàn toàn trên RAM
        EmbeddedChannel embeddedChannel = new EmbeddedChannel() {
            @Override
            public EventLoop eventLoop() {
                return mockEventLoop;
            }
        };

        // Khởi tạo Connection gốc hướng SERVERBOUND theo constructor trong mã nguồn bạn gửi
        Connection fakeConnection = new Connection(PacketFlow.SERVERBOUND) {
            private PacketListener currentListener;

            @Override
            public void setListener(PacketListener listener) {
                this.currentListener = listener;
                super.setListener(listener);
            }

            @Override
            public PacketListener getPacketListener() {
                return this.currentListener;
            }

            @Override
            public boolean isConnected() {
                return true; // Ép trạng thái luôn giữ kết nối ổn định
            }
        };

        // GÁN TRỰC TIẾP VÌ BIẾN LÀ PUBLIC TRONG CLASS BẠN CUNG CẤP
        fakeConnection.channel = embeddedChannel;
        fakeConnection.address = new InetSocketAddress("127.0.0.1", 25565);
        fakeConnection.preparing = false;

        // Đăng ký chính xác Attribute Giao thức PLAY dựa vào ATTRIBUTE_PROTOCOL thu được từ mã nguồn
        embeddedChannel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        fakeConnection.protocol = ConnectionProtocol.PLAY;

        // Khởi tạo bộ lắng nghe mạng hoàn chỉnh cho ServerPlayer
        fakePlayer.connection = new ServerGamePacketListenerImpl(server, fakeConnection, fakePlayer);

        // Thiết lập vị trí và góc nhìn thực tế khi spawn
        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}