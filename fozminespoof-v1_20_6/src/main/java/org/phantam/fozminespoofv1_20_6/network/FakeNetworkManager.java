package org.phantam.fozminespoofv1_20_6.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

/**
 * Fake NetworkManager that provides a virtual connection for fake players in 1.20.6.
 * Allows plugins (e.g., ProtocolLib) to interact with the bot as if it were a real player,
 * but prevents actual packet transmission over the wire.
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
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op: block outgoing packet transmission
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
    public void tick() {}

    @Override
    public void handleDisconnection() {}
}