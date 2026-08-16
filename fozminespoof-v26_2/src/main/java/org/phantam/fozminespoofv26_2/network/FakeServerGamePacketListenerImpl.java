package org.phantam.fozminespoofv26_2.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Custom packet listener blocking outbound packet transmission for fake players in 26.2.
 */
public class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

    public FakeServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player) {
        super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op: suppress outbound network dispatch
    }
}