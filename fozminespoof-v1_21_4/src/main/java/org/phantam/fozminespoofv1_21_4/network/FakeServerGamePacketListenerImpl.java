package org.phantam.fozminespoofv1_21_4.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Custom packet listener for fake players.
 * Overrides send() to block all outgoing packets, preventing
 * any data from being transmitted over the fake connection.
 */
public class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

    public FakeServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player) {
        super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op: block all packet sending from the fake player
    }
}