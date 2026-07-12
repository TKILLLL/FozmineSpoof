package org.phantam.fozminesproofV1_19_4.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

public class FakePlayerFactory {

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Mojang 1.19.4 và tự động nạp Skin bản quyền
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);
        GameProfile filledProfile = profile;

        try {
            filledProfile = server.getProfileCache().get(name)
                    .orElse(server.getSessionService().fillProfileProperties(profile, true));
        } catch (Exception e) {
            Bukkit.getLogger().warning("Không tìm thấy skin cho " + name);
        }

        ServerPlayer fakePlayer = new ServerPlayer(server, level, filledProfile);

        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}
