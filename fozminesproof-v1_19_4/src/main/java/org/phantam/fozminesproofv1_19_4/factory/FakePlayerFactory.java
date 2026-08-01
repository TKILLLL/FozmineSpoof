package org.phantam.fozminesproofv1_19_4.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.phantam.fozminesproofv1_19_4.network.FakeNetworkManager;
import org.phantam.fozminesproofv1_19_4.network.FakeServerGamePacketListenerImpl;

import java.util.UUID;

public class FakePlayerFactory {

    /**
     * Khởi tạo đối tượng ServerPlayer với kết nối mạng giả lập hoàn chỉnh
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        try {
            profile = server.getSessionService().fillProfileProperties(profile, true);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Fozminesproof] Khong the tu dong tai skin cho " + name + " do loi ket noi Mojang!");
        }

        ServerPlayer fakePlayer = new FakeServerPlayer(server, level, profile);

        FakeNetworkManager networkManager = new FakeNetworkManager();

        fakePlayer.connection = new FakeServerGamePacketListenerImpl(server, networkManager, fakePlayer);

        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}