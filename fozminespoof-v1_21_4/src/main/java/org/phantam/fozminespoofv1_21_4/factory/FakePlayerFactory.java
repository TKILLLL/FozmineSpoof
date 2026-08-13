package org.phantam.fozminespoofv1_21_4.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.phantam.fozminespoofv1_21_4.network.FakeNetworkManager;
import org.phantam.fozminespoofv1_21_4.network.FakeServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Factory for creating fully initialised fake ServerPlayer instances.
 * Sets up a fake network connection and injects a custom packet listener.
 */
public final class FakePlayerFactory {

    private FakePlayerFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a fake player instance with a complete network simulation.
     *
     * @param server the Minecraft server instance
     * @param level  the target world
     * @param name   the player's name
     * @param uuid   the player's UUID
     * @param loc    spawn location
     * @return a fully configured ServerPlayer (fake)
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level,
                                      String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        // Attempt to fetch skin properties from Mojang API.
        // In 1.20.4, fillProfileProperties may not be available or may have different signature.
        // We skip it here to avoid compilation errors; skin can be set later via updatePlayerSkin.
        /*
        try {
            profile = server.getSessionService().fillProfileProperties(profile, true);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[FakePlayerFactory] Failed to fetch skin for " + name +
                            ". Using default/offline skin.");
        }
        */

        // Create the custom player instance
        ClientInformation clientInfo = ClientInformation.createDefault();
        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile, clientInfo);

        // Inject fake network components
        FakeNetworkManager networkManager = new FakeNetworkManager();
        fakePlayer.connection = new FakeServerGamePacketListenerImpl(server, networkManager, fakePlayer);

        // Set position and rotation
        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}