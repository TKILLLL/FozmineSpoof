package org.phantam.fozminesproofv1_20_4;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.phantam.fozminesproofapi.FozminesproofApi;
import org.phantam.fozminesproofv1_20_4.factory.FakePlayerFactory;
import org.phantam.fozminesproofv1_20_4.network.FakePlayerPacketSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NMSBridge_v1_20_4 implements FozminesproofApi {

    private final Map<UUID, ServerPlayer> activeFakePlayers = new ConcurrentHashMap<>();

    @Override
    public Player spawnPlayer(String name, UUID uuid, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        ServerPlayer fakePlayer = FakePlayerFactory.create(server, level, name, uuid, loc);

        activeFakePlayers.put(uuid, fakePlayer);

        // --- ĐOẠN ĐĂNG KÝ QUAN TRỌNG VÀO HỆ THỐNG MÁY CHỦ ---
        if (!server.getPlayerList().players.contains(fakePlayer)) {
            server.getPlayerList().players.add(fakePlayer);
        }
        level.addNewPlayer(fakePlayer);
        // ----------------------------------------------------

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

        // --- ĐOẠN XÓA KHỎI HỆ THỐNG MÁY CHỦ ---
        server.getPlayerList().players.remove(fakePlayer);

        if (fakePlayer.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) fakePlayer.level();
            level.removePlayerImmediately(fakePlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        // ------------------------------------

        fakePlayer.discard();
    }

    @Override
    public void updatePlayerSkin(UUID uuid, String texture, String signature) {
        ServerPlayer oldFakePlayer = activeFakePlayers.get(uuid);
        if (oldFakePlayer == null) return;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = (ServerLevel) oldFakePlayer.level();
        Location currentLoc = oldFakePlayer.getBukkitEntity().getLocation();
        String name = oldFakePlayer.getGameProfile().getName();

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        // 1. Phát gói tin Despawn gỡ bỏ thực thể cũ ra khỏi màn hình Client người chơi thực tế
        packetSender.sendDespawnPackets(uuid, oldFakePlayer.getId());

        // 2. Dọn dẹp triệt để thực thể cũ khỏi danh sách quản lý mạng và ticking của Core Server
        server.getPlayerList().players.remove(oldFakePlayer);
        level.removePlayerImmediately(oldFakePlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        oldFakePlayer.discard();

        // 3. Khởi tạo một đối tượng ServerPlayer hoàn toàn mới thông qua Factory để nạp Skin mới sạch sẽ
        ServerPlayer newFakePlayer = FakePlayerFactory.create(server, level, name, uuid, currentLoc);

        // 4. Inject thủ công chuỗi Skin Texture bản quyền mới vào cấu trúc GameProfile của thực thể mới
        GameProfile profile = newFakePlayer.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", texture, signature));

        // 5. Đăng ký lại thực thể mới vào hệ thống máy chủ và map dữ liệu hoạt động
        activeFakePlayers.put(uuid, newFakePlayer);
        if (!server.getPlayerList().players.contains(newFakePlayer)) {
            server.getPlayerList().players.add(newFakePlayer);
        }
        level.addNewPlayer(newFakePlayer);

        // 6. Phát lại chuỗi gói tin Spawn để ép buộc Client người chơi cập nhật và vẽ lại Skin mới ngay lập tức
        packetSender.sendSpawnPackets(newFakePlayer, profile.getName());
    }

    @Override
    public void sendKeepAlivePackets() {
        if (this.activeFakePlayers.isEmpty()) return;

        MinecraftServer server = ((Bukkit.getServer() instanceof CraftServer) ? ((CraftServer) Bukkit.getServer()).getServer() : null);
        if (server == null) return;

        FakePlayerPacketSender packetSender = new FakePlayerPacketSender(server.getPlayerList());

        this.activeFakePlayers.forEach((uuid, fakePlayer) -> {
            packetSender.sendSpawnPackets(fakePlayer, fakePlayer.getGameProfile().getName());
        });
    }

    @Override
    public void broadcastNMSChat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) return;

        try {
            // 1. Lấy instance của MinecraftServer
            net.minecraft.server.MinecraftServer server =
                    ((CraftServer) Bukkit.getServer()).getServer();

            // 2. Chuyển đổi chuỗi chứa mã màu thành mảng Component Mojang hoàn chỉnh
            net.minecraft.network.chat.Component[] components = CraftChatMessage.fromString(message);
            if (components.length == 0) return;

            // SỬA TẠI ĐÂY: Khởi tạo một MutableComponent rỗng làm gốc để chứa toàn bộ cấu trúc chat
            net.minecraft.network.chat.MutableComponent finalComponent = net.minecraft.network.chat.Component.empty();

            // Duyệt qua toàn bộ mảng và nối đuôi liên tục để giữ nguyên vẹn 100% định dạng màu sắc lẫn chữ nội dung
            for (net.minecraft.network.chat.Component comp : components) {
                finalComponent.append(comp);
            }

            // 3. Phát tin nhắn đã được hợp nhất ra toàn Server dưới dạng tin nhắn Hệ Thống Mở Rộng
            // GIỮ NGUYÊN 100% chuỗi chat-format của bạn và XÓA HOÀN TOÀN dấu < > mặc định
            server.getPlayerList().broadcastSystemMessage(finalComponent, false);

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