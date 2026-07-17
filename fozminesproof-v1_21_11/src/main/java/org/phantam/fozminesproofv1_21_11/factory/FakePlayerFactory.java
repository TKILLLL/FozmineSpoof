package org.phantam.fozminesproofv1_21_11.factory;

import com.mojang.authlib.GameProfile;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public class FakePlayerFactory {

    // GIẢI PHÁP TỐI ƯU HIỆU NĂNG: Khởi tạo một cụm EventLoop dùng chung cố định cho toàn bộ Bot
    // Ngăn chặn hành vi tạo thread rác vô tội vạ gây sập RAM và sụt giảm nghiêm trọng TPS của Server
    private static final EventLoop SHARED_MOCK_EVENT_LOOP = new LocalEventLoopGroup(1).next();

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Mojang 1.21.11 và tự động nạp Skin bản quyền đồng bộ
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        try {
            // Lấy dịch vụ mạng thông qua trường 'services' ẩn bằng Reflection
            net.minecraft.server.Services serverServices = null;
            try {
                java.lang.reflect.Field servicesField = MinecraftServer.class.getDeclaredField("services");
                servicesField.setAccessible(true);
                serverServices = (net.minecraft.server.Services) servicesField.get(server);
            } catch (Exception ignored) {}

            if (serverServices != null) {
                // ĐỒNG BỘ 100% THEO FILE SERVICES RECORD BẠN GỬI:
                // Sử dụng 'profileResolver()' thế hệ mới của 1.21.11 thay thế cho sessionService cũ.
                // Quét động phương thức để tự động tương thích chính xác với Yarn/Mojang Mapping trong IDE của bạn.
                net.minecraft.server.players.ProfileResolver resolver = serverServices.profileResolver();

                try {
                    // Thử gọi trực tiếp nếu trong IDE ánh xạ tên phương thức rõ ràng
                    // profile = resolver.fillProfileProperties(profile);

                    // Giải pháp phản chiếu an toàn tuyệt đối, tự động tìm hàm nhận vào duy nhất 1 tham số GameProfile
                    for (java.lang.reflect.Method method : resolver.getClass().getDeclaredMethods()) {
                        if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == GameProfile.class) {
                            method.setAccessible(true);
                            profile = (GameProfile) method.invoke(resolver, profile);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Fozminesproof] Khong the tu dong tai skin cho " + name + " do loi ket noi Mojang!");
        }

        return createWithProfile(server, level, profile, loc);
    }


    /**
     * Hàm khởi tạo nâng cao nhận trực tiếp GameProfile Record phục vụ cơ chế đổi Skin bất biến
     */
    public static ServerPlayer createWithProfile(MinecraftServer server, ServerLevel level, GameProfile profile, Location loc) {
        // Tính toán gộp toàn bộ mặt nạ trang phục để hiển thị đầy đủ lớp áo phụ/mũ của Skin Bot
        int combinedBitmask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            combinedBitmask |= part.getMask();
        }

        // Tận dụng hàm tạo mặc định của Mojang và nạp chính xác cấu trúc 9 tham số bắt buộc của 1.21.11
        ClientInformation defaultInfo = ClientInformation.createDefault();
        ClientInformation clientInformation = new ClientInformation(
                defaultInfo.language(),
                defaultInfo.viewDistance(),
                defaultInfo.chatVisibility(),
                defaultInfo.chatColors(),
                combinedBitmask,                  // Kích hoạt 100% layer skin phụ cho Bot
                defaultInfo.mainHand(),
                defaultInfo.textFilteringEnabled(),
                defaultInfo.allowsListing(),
                net.minecraft.server.level.ParticleStatus.ALL
        );

        // Khởi tạo thực thể Bot
        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile, clientInformation);

        // Khởi tạo Kênh mạng ảo chạy hoàn toàn trong bộ nhớ RAM, gán trực tiếp EventLoop dùng chung tối ưu
        EmbeddedChannel embeddedChannel = new EmbeddedChannel() {
            @Override
            public EventLoop eventLoop() {
                return SHARED_MOCK_EVENT_LOOP;
            }
        };

        // Khởi tạo Connection gốc của Mojang dạng SERVERBOUND bảo vệ trạng thái trực tuyến
        Connection fakeConnection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public boolean isConnected() {
                return true;
            }
        };

        // INJECT KÊNH MẠNG VÀ IP ẢO QUA REFLECTION ĐỘNG PHÙ HỢP CẤU TRÚC 1.21.11
        try {
            for (java.lang.reflect.Field field : Connection.class.getDeclaredFields()) {
                if (field.getType() == io.netty.channel.Channel.class) {
                    field.setAccessible(true);
                    field.set(fakeConnection, embeddedChannel);
                }
                if (field.getType() == SocketAddress.class) {
                    field.setAccessible(true);
                    field.set(fakeConnection, new InetSocketAddress("127.0.0.1", 25565));
                }
                if (field.getType() == boolean.class && (field.getName().equals("preparing") || field.getName().equals("m"))) {
                    field.setAccessible(true);
                    field.setBoolean(fakeConnection, false);
                }
            }
        } catch (Exception ignored) {}

        // VÁ LỖI KHỚP 100% THEO FILE COOKIE BẠN GỬI: Gọi hàm createInitial với đúng 2 tham số (profile, false)
        CommonListenerCookie fakeCookie = CommonListenerCookie.createInitial(profile, false);

        // ĐỒNG BỘ HIỂN THỊ SKIN: Inject đối tượng ClientInformation (đã bật 100% layer skin) vào trong Cookie qua Reflection
        try {
            for (java.lang.reflect.Field field : CommonListenerCookie.class.getDeclaredFields()) {
                if (field.getType() == ClientInformation.class) {
                    field.setAccessible(true);
                    field.set(fakeCookie, clientInformation);
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Đăng ký Listener mạng hoàn chỉnh vào hệ thống máy chủ
        fakePlayer.connection = new ServerGamePacketListenerImpl(server, fakeConnection, fakePlayer, fakeCookie);

        // Thiết lập vị trí spawn thực tế
        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}
