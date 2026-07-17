package org.phantam.fozminesproofv1_21_4.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FakeServerPlayer extends ServerPlayer {

    /**
     * Khởi tạo FakeServerPlayer và kích hoạt trạng thái bất tử hệ thống chuẩn hóa 1.21.4
     */
    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);

        // VÁ LỖI CHÍ MẠNG LOCK FINAL METHOD 1.21.4:
        // Thiết lập trạng thái bất tử trực tiếp từ hàm khởi tạo gốc của hệ thống Entity.
        // Chặn đứng hoàn toàn mọi nguồn sát thương ngoài thế giới mà không cần ghi đè hàm hurt().
        this.setInvulnerable(true);
    }

    @Override
    public void tick() {
        // CHẶN TOÀN BỘ LOGIC TICK NẶNG: Không tính toán đói, không chạy cập nhật di chuyển mạng gốc nặng nề.
        // Chỉ giữ lại baseTick() tối thiểu để xử lý cơ chế thực thể cơ bản (như biến mất khi lọt xuống hư vô Void).
        // Tiết kiệm tối đa hiệu năng CPU/TPS cho Server.
        this.baseTick();
    }

    // XÓA BỎ hoàn toàn doTick() và hurt() không hợp lệ để dọn sạch lỗi biên dịch lớp cha

    @Override
    public boolean isSpectator() {
        // Ép buộc hệ thống coi Bot không phải Spectator để Client luôn luôn vẽ và hiển thị mô hình 3D
        return false;
    }

    @Override
    public boolean isCreative() {
        // Giả lập trạng thái sáng tạo để hệ thống trí tuệ nhân tạo (AI Mobs) bỏ qua, không nhắm mục tiêu tấn công Bot
        return true;
    }
}
