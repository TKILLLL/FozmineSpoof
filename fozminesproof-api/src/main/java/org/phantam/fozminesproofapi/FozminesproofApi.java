package org.phantam.fozminesproofapi;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Interface cầu nối trung gian (NMS Bridge API) cho plugin FozmineSproof.
 * Lớp này được đặt tại module độc lập 'fozminesproof-api' để tách biệt logic
 * giữa tầng Core và tầng NMS đa phiên bản.
 */
public interface FozminesproofApi {

    /**
     * Khởi tạo và đưa một Fake Player (Người chơi giả lập) vào hệ thống thế giới của máy chủ.
     *
     * @param name Tên hiển thị trên đầu và trên Tablist của người chơi giả lập
     * @param uuid UUID định danh duy nhất để quản lý thực thể
     * @param loc  Vị trí tọa độ thế giới và góc nhìn ban đầu khi xuất hiện
     * @return Đối tượng Player của Bukkit đại diện cho thực thể người chơi giả lập vừa tạo
     */
    Player spawnPlayer(String name, UUID uuid, Location loc);

    /**
     * Hủy kích hoạt, xóa mô hình 3D và giải phóng hoàn toàn bộ nhớ của người chơi giả lập khỏi hệ thống.
     *
     * @param uuid UUID định danh của người chơi giả lập cần xóa
     */
    void despawnPlayer(UUID uuid);

    /**
     * Cập nhật skin cho NPC dựa vào giá trị texture thô lấy từ Mojang API
     */
    void updatePlayerSkin(UUID uuid, String texture, String signature);

    /**
     * @return Số lượng Fake Player thực tế đang hoạt động trên Server
     */
    int getFakePlayersCount();

    /**
     * Gửi lại chuỗi gói tin hiển thị (Tablist và Mô hình) cho toàn bộ Bot đang hoạt động.
     * Hàm này được gọi định kỳ bởi Bukkit Runnable để sửa lỗi Bot biến mất khi Player re-log/di chuyển.
     */
    void sendKeepAlivePackets();

    /**
     * Giả lập gói tin chat NMS (ServerboundChatPacket) chạy thẳng vào Core xử lý của Mojang
     * Giúp đánh lừa tất cả plugin chat (LPC, EssentialsChat...) định dạng tự động và tự ghi log
     */
    void broadcastNMSChat(Player player, String message);
}
