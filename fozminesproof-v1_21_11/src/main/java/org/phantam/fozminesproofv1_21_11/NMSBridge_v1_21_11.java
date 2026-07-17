package org.phantam.fozminesproofv1_21_11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofv1_21_11.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_21_11.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NMSBridge_v1_21_11 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        // Khởi tạo thực thể an toàn thông qua nhà máy tạo Bot
        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);

        activeFakePlayers.put(uuid, fakePlayer);

        // ĐĂNG KÝ VÀO DANH SÁCH MÁY CHỦ CHỐNG LỖI NULL POINTER
        if (!server.getPlayerList().players.contains(fakePlayer)) {
            server.getPlayerList().players.add(fakePlayer);
        }

        // VÁ LỖI CHÍ MẠNG 1.21.11: Thay thế addNewPlayer bằng addFreshEntity chuẩn cấu trúc vòng lặp Mojang mới
        level.addFreshEntity(fakePlayer);

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendSpawnPackets(fakePlayer, name);

        return fakePlayer.getBukkitEntity();
    }

    @Override
    public void despawnPlayer(UUID uuid) {
        ServerPlayer fakePlayer = activeFakePlayers.remove(uuid);
        if (fakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());
        packetSender.sendDespawnPackets(uuid, fakePlayer.getId());

        server.getPlayerList().players.remove(fakePlayer);

        // Hủy bỏ thực thể an toàn, loại bỏ triệt để khỏi World Ticking của Server 1.21.11
        fakePlayer.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature) {
        ServerPlayer oldFakePlayer = activeFakePlayers.get(uuid);
        if (oldFakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = (ServerLevel) oldFakePlayer.level();
        Location currentLoc = oldFakePlayer.getBukkitEntity().getLocation();
        String name = oldFakePlayer.getGameProfile().name(); // Sử dụng name() dạng Record chuẩn hóa

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        // 1. Phát gói tin Despawn gỡ bỏ thực thể cũ ra khỏi màn hình Client người chơi
        packetSender.sendDespawnPackets(uuid, oldFakePlayer.getId());

        // 2. Dọn dẹp triệt để thực thể cũ khỏi danh sách quản lý mạng và ticking của Core Server
        server.getPlayerList().players.remove(oldFakePlayer);
        oldFakePlayer.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        // 3. VÁ LỖI CONSTRUCTOR PROPERTYMAP: Sử dụng Multimap trung gian của Guava để gom dữ liệu Skin
        com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> tempMultimap = com.google.common.collect.LinkedHashMultimap.create();
        tempMultimap.put("textures", new com.mojang.authlib.properties.Property("textures", texture, signature));

        // Khởi tạo đối tượng PropertyMap chuẩn xác theo constructor (final Multimap<String, Property> properties) của bạn
        com.mojang.authlib.properties.PropertyMap newProperties = new com.mojang.authlib.properties.PropertyMap(tempMultimap);

        // Khởi tạo một đối tượng GameProfile Record mới chứa tập hợp thuộc tính Skin mới
        GameProfile newProfile = new GameProfile(uuid, name, newProperties);

        // 4. Khởi tạo một đối tượng ServerPlayer hoàn toàn mới thông qua Factory nâng cao nhận vào Profile mới
        ServerPlayer newFakePlayer = FakePlayerFactory.createWithProfile(server, level, newProfile, currentLoc);

        // 5. Đăng ký lại thực thể mới vào hệ thống máy chủ và map dữ liệu hoạt động
        activeFakePlayers.put(uuid, newFakePlayer);
        if (!server.getPlayerList().players.contains(newFakePlayer)) {
            server.getPlayerList().players.add(newFakePlayer);
        }
        level.addFreshEntity(newFakePlayer);

        // 6. Phát lại chuỗi gói tin Spawn để ép buộc Client người chơi vẽ lại mô hình kèm Skin mới ngay lập tức
        packetSender.sendSpawnPackets(newFakePlayer, newProfile.name());
    }

    @Override
    public void sendKeepAlivePackets() {
        if (this.activeFakePlayers.isEmpty()) return;

        MinecraftServer server = ((Bukkit.getServer() instanceof CraftServer) ? ((CraftServer) Bukkit.getServer()).getServer() : null);
        if (server == null) return;

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        this.activeFakePlayers.forEach((uuid, fakePlayer) -> {
            packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().name());
        });
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) return;

        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

            // VÁ LỖI CHAT COMPONENT: Chuyển đổi mã màu an toàn thông qua Spigot/Paper Chat Component Mappings
            net.minecraft.network.chat.Component finalComponent = CraftChatMessage.fromStringOrNull(message);
            if (finalComponent == null) return;

            // VÁ LỖI BROADCAST 1.21.11: Gửi tin nhắn trực tiếp qua hệ thống phân phát đồng bộ Packet
            // Giúp ngăn ngừa lỗi Main-thread freeze do sự khác biệt tham số broadcastSystemMessage
            for (ServerPlayer serverPlayer : server.getPlayerList().players) {
                if (serverPlayer.connection != null) {
                    serverPlayer.sendSystemMessage(finalComponent);
                }
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
