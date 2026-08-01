package org.phantam.fozminesproofv1_21_1.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

/**
 * Fake NetworkManager that provides a virtual connection for fake players in 1.21.4.
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

        // In 1.21.4, protocol attributes may not be accessible or needed.
        // Since we override send() to no-op, we don't need to set these attributes.
        // The channel is closed, so no packets will be transmitted anyway.
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
    public void send(Packet<?> packet, PacketSendListener listener) {
        // If a listener is provided, immediately mark it as successful
        // to prevent hanging callbacks.
        if (listener != null) {
            try {
                listener.onSuccess();
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