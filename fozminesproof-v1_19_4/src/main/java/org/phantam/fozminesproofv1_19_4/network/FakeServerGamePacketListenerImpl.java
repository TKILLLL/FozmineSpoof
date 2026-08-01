package org.phantam.fozminesproofv1_19_4.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

    public FakeServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player) {
        super(server, connection, player);
    }

    @Override
    public void send(Packet<?> packet) {
    }
}