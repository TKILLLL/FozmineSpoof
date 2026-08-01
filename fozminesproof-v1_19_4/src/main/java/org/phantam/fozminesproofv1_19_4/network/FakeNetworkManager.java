package org.phantam.fozminesproofv1_19_4.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

/**
 * NetworkManager giả lập cho FakePlayer, cung cấp địa chỉ IP và kênh kết nối ảo
 * để các plugin có thể xem bot như một người chơi thực nhưng không gửi packet thật.
 */
public class FakeNetworkManager extends Connection {

    public FakeNetworkManager() {
        super(PacketFlow.SERVERBOUND);

        this.channel = new EmbeddedChannel() {
            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public boolean isActive() {
                return false;
            }
        };

        this.address = new InetSocketAddress("127.0.0.1", 25565);
        this.preparing = false;
        this.protocol = ConnectionProtocol.PLAY;
        this.channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void send(Packet<?> packet) {
        // NO-OP: Chặn gửi gói tin ra mạng
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        if (listener != null) {
            try {
                listener.onSuccess();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public void handleDisconnection() {
    }
}