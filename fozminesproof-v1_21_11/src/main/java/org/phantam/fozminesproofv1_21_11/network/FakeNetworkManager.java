package org.phantam.fozminesproofv1_21_11.network;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

/**
 * Fake NetworkManager that provides a virtual connection for fake players in 1.21.11.
 * Allows plugins (e.g., ProtocolLib) to interact with the bot as if it were a real player,
 * but prevents any actual packet transmission to avoid encoding errors.
 */
public class FakeNetworkManager extends Connection {

    public FakeNetworkManager() {
        super(PacketFlow.SERVERBOUND);

        // Create an embedded channel that is deliberately closed to block all I/O
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
        // No need to set protocol attributes; they are handled by the pipeline.
    }

    @Override
    public boolean isConnected() {
        // Return false to prevent any actual network activity
        return false;
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op: block all packet sending
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        // If a listener is provided, immediately mark it as successful
        // to prevent hanging callbacks.
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {
                // Ignore any exceptions during listener callback
            }
        }
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        // If a listener is provided, immediately mark it as successful
        // to prevent hanging callbacks.
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {
                // Ignore any exceptions during listener callback
            }
        }
    }

    @Override
    public void tick() {
        // No tick logic needed
    }

    @Override
    public void handleDisconnection() {
        // No disconnection logic needed
    }
}