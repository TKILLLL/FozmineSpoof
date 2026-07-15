package org.phantam.fozminesproofv1_19_4;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofv1_19_4.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_19_4.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NMSBridge_v1_19_4 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);

        activeFakePlayers.put(uuid, fakePlayer);

        // --- ĐOẠN ĐĂNG KÝ ĐỒNG BỘ VÀO HỆ THỐNG MÁY CHỦ (1.19.4) ---
        // Thêm Bot vào danh sách người chơi trực tuyến của Server để Bukkit.getPlayer() nhận diện công khai
        if (!server.getPlayerList().players.contains(fakePlayer)) {
            server.getPlayerList().players.add(fakePlayer);
        }
        // Thêm thực thể vào thế giới cấp độ NMS để máy chủ xử lý tick dữ liệu ảo
        level.addNewPlayer(fakePlayer);
        // ----------------------------------------------------------

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer);

        return fakePlayer.getBukkitEntity();
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        // --- ĐOẠN XÓA KHỎI HỆ THỐNG MÁY CHỦ (1.19.4) ---
        server.getPlayerList().players.remove(fakePlayer);
        // ------------------------------------

        fakePlayer.discard();
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature) {
        ServerPlayer fakePlayer = activeFakePlayers.get(uuid);
        if (fakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        GameProfile profile = fakePlayer.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", texture, signature));

        packetSender.sendSpawnPackets(fakePlayer);
    }

    @Override
    public void sendKeepAlivePackets() {
        if (this.activeFakePlayers.isEmpty()) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        this.activeFakePlayers.forEach((uuid, fakePlayer) -> {
            packetSender.sendSpawnPackets(fakePlayer);
        });
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            // 1. Ép kiểu Bukkit Player sang ServerPlayer của NMS 1.20.2
            net.minecraft.server.level.ServerPlayer nmsPlayer =
                    ((CraftPlayer) player).getHandle();

            // 2. Khởi tạo cấu trúc gói tin chat thô chuẩn Mojang 1.20.2
            ServerboundChatPacket fakeChatPacket =
                    new net.minecraft.network.protocol.game.ServerboundChatPacket(
                            message,
                            java.time.Instant.now(),
                            0L,
                            null,
                            new net.minecraft.network.chat.LastSeenMessages.Update(
                                    0,
                                    new java.util.BitSet() // Khắc phục lỗi: Sử dụng BitSet trống thay thế cho Optional
                            )
                    );

            // 3. Đẩy thẳng vào bộ lắng nghe hệ thống, kích hoạt tự động chuỗi xử lý chat chính quy của Server
            if (nmsPlayer.connection != null) {
                nmsPlayer.connection.handleChat(fakeChatPacket);
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Fozminesproof] Khong the gui packet chat NMS cho: " + player.getName());
            e.printStackTrace();
        }
    }

    @Override
    public int getFakePlayersCount() {
        return this.activeFakePlayers.size();
    }
}
