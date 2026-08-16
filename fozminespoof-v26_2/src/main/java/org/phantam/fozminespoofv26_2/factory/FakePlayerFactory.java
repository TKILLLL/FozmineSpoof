package org.phantam.fozminespoofv26_2.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.phantam.fozminespoofv26_2.network.FakeNetworkManager;
import org.phantam.fozminespoofv26_2.network.FakeServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Factory for creating initialized fake ServerPlayer instances in Minecraft 26.2.
 */
public final class FakePlayerFactory {

    private FakePlayerFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ServerPlayer create(MinecraftServer server, ServerLevel level,
                                      String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);
        ClientInformation clientInfo = ClientInformation.createDefault();

        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile, clientInfo);

        FakeNetworkManager networkManager = new FakeNetworkManager();
        fakePlayer.connection = new FakeServerGamePacketListenerImpl(server, networkManager, fakePlayer);

        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}