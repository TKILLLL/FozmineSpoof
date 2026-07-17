package org.phantam.fozminesproofv1_20_4.factory;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public class FakePlayerFactory {

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Mojang 1.20.2 và tự động nạp Skin bản quyền đồng bộ
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        try {
            ProfileResult result = server.getSessionService().fetchProfile(uuid, true);
            if (result != null && result.profile() != null) {
                profile = result.profile();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Fozminesproof] Khong the tu dong tai skin cho " + name + " do loi ket noi Mojang!");
        }

        ClientInformation clientInformation = new ClientInformation(
                "en_us",            // Ngôn ngữ mặc định
                2,                  // Tầm nhìn giả lập
                ChatVisiblity.FULL, // Chế độ hiển thị chat
                true,               // Cho phép màu sắc chat
                127,                // Bitmask bật toàn bộ Skin Parts
                HumanoidArm.RIGHT,  // Tay thuận
                false,              // Bộ lọc văn bản
                false               // Hiển thị trong danh sách ẩn
        );

        // Sử dụng lớp tối ưu FakeServerPlayer đã chặn hoàn toàn logic tick nặng
        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile, clientInformation);

        // --- GIẢI PHÁP TỐI HẬU: TRUYỀN EMBEDDED CHANNEL ĐỂ CHỐNG LỖI EVENTLOOP VÀ NULL CHANNEL ---

        // Khởi tạo một EventLoop thực thi ảo chạy ngầm cố định cho kênh mạng ảo
        EventLoop mockEventLoop = new LocalEventLoopGroup(1).next();

        // Khởi tạo một Kênh mạng ảo chạy hoàn toàn trong bộ nhớ RAM của Netty và override eventLoop() chống lỗi sập server
        EmbeddedChannel embeddedChannel = new EmbeddedChannel() {
            @Override
            public EventLoop eventLoop() {
                return mockEventLoop;
            }
        };

        // VÁ LỖI CHÍ MẠNG THEO MÃ NGUỒN CỦA BẠN:
        // Lấy CodecData gói tin hướng Clientbound và Serverbound trực tiếp từ cấu trúc tĩnh của ConnectionProtocol.PLAY
        // Điều này giúp hàm setListener() kiểm tra sâu bên trong lấy được dữ liệu, triệt tiêu lỗi sập máy chủ
        embeddedChannel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
        embeddedChannel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND));

        // Khởi tạo Connection gốc của Mojang với hướng mạng SERVERBOUND
        Connection fakeConnection = new Connection(PacketFlow.SERVERBOUND) {
            private PacketListener currentListener;

            @Override
            public void setListener(PacketListener listener) {
                this.currentListener = listener;
                // Đồng bộ bộ lắng nghe mạng vào thẳng cấu trúc kênh ảo Netty
                super.setListener(listener);
            }

            @Override
            public PacketListener getPacketListener() {
                return this.currentListener;
            }

            @Override
            public boolean isConnected() {
                return true; // Giữ Bot luôn ở trạng thái kết nối
            }

            @Override
            public SocketAddress getRemoteAddress() {
                // Trả về null để lớp gửi gói tin FakePlayerPacketSender nhận diện đúng và bỏ qua Bot
                return new InetSocketAddress("127.0.0.1", 25565);
            }
        };

        // TIẾN HÀNH INJECT KÊNH MẠNG ẢO VÀO BIẾN NỘI BỘ 'channel' CỦA CONNECTION GỐC
        fakeConnection.channel = embeddedChannel;
        fakeConnection.address = new InetSocketAddress("127.0.0.1", 25565);

        // Khởi tạo Cookie giả lập chứa dữ liệu cấu hình thô phù hợp 3 tham số của 1.20.2
        CommonListenerCookie fakeCookie = new CommonListenerCookie(
                profile,
                0,
                clientInformation
        );

        // Tiến hành gắn Listener mạng giả lập của Bot vào cấu trúc Connection an toàn tuyệt đối
        fakePlayer.connection = new ServerGamePacketListenerImpl(server, fakeConnection, fakePlayer, fakeCookie);

        // -------------------------------------------------------------------------------------

        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}
