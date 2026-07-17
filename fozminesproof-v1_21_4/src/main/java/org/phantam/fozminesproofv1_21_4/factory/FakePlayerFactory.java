package org.phantam.fozminesproofv1_21_4.factory;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
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

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Paper 1.21.4
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

        // TÍNH TOÁN BITMASK TRANG PHỤC TỪ ENUM BẠN CUNG CẤP
        int combinedBitmask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            combinedBitmask |= part.getMask();
        }

        // TẬN DỤNG HÀM TẠO MẶC ĐỊNH VÀ GHI ĐÈ BITMASK SKIN ĐỂ TRÁNH LỖI CONSTRUCTOR KHÔNG KHỚP THAM SỐ
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

        // Khởi tạo EventLoop ảo chạy độc lập
        EventLoop mockEventLoop = new LocalEventLoopGroup(1).next();

        // Khởi tạo Kênh mạng ảo chạy trên RAM
        EmbeddedChannel embeddedChannel = new EmbeddedChannel() {
            @Override
            public EventLoop eventLoop() {
                return mockEventLoop;
            }
        };

        // VÁ LỖI BIÊN DỊCH: Loại bỏ hoàn toàn phương thức setListener bị lỗi override 1.21.4
        Connection fakeConnection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public boolean isConnected() {
                return true; // Giữ trạng thái mạng của Bot luôn trực tuyến
            }
        };

        // INJECT KÊNH MẠNG VÀ IP ẢO QUA REFLECTION QUÉT ĐỘNG CHỐNG THAY ĐỔI TÊN BIẾN PRIVATE TRÊN 1.21.4
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
                // Tìm trường boolean 'preparing' nếu có để tắt trạng thái chờ mạng của Netty
                if (field.getType() == boolean.class && (field.getName().equals("preparing") || field.getName().equals("m"))) {
                    field.setAccessible(true);
                    field.setBoolean(fakeConnection, false);
                }
            }
        } catch (Exception ignored) {}

        // Khởi tạo Cookie đồng bộ mạng
        CommonListenerCookie fakeCookie = new CommonListenerCookie(
                profile,
                0,
                clientInformation,
                false
        );

        // Đăng ký Listener mạng - Luồng này sẽ tự gán PacketListener vào Connection một cách hợp lệ
        fakePlayer.connection = new ServerGamePacketListenerImpl(server, fakeConnection, fakePlayer, fakeCookie);

        // Thiết lập vị trí
        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}
