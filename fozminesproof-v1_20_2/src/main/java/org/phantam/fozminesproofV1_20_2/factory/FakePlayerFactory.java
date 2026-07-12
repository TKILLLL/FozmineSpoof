package org.phantam.fozminesproofV1_20_2.factory;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

public class FakePlayerFactory {

    /**
     * Khởi tạo đối tượng ServerPlayer chuẩn cấu trúc Mojang 1.20.2 và tự động nạp Skin bản quyền đồng bộ
     */
    public static ServerPlayer create(MinecraftServer server, ServerLevel level, String name, UUID uuid, Location loc) {
        GameProfile profile = new GameProfile(uuid, name);

        try {
            ProfileResult result = server.getSessionService().fetchProfile(uuid, true);
            if (result != null && result.profile() != null) {
                profile = result.profile();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Fozminesproof] Khong the tu dong tai skin cho " + name + " do loi ket noi Mojang!");
        }

        ClientInformation clientInformation = new ClientInformation(
                "en_us",      // Ngôn ngữ mặc định
                2,                  // Tầm nhìn giả lập
                ChatVisiblity.FULL, // Chế độ hiển thị chat
                true,               // Cho phép màu sắc chat
                127,                // Bitmask bật toàn bộ Skin Parts (Áo khoác, nón, tay áo...)
                HumanoidArm.RIGHT,  // Tay thuận
                false,              // Bộ lọc văn bản
                false               // Hiển thị trong danh sách ẩn
        );

        ServerPlayer fakePlayer = new ServerPlayer(server, level, profile, clientInformation);

        fakePlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        fakePlayer.setRot(loc.getYaw(), loc.getPitch());

        return fakePlayer;
    }
}
