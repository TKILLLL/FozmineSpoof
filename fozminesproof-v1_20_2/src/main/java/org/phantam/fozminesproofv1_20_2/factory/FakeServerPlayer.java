package org.phantam.fozminesproofv1_20_2.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public class FakeServerPlayer extends ServerPlayer {

    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
    }

    @Override
    public void tick() {
        // CHẶN TOÀN BỘ LOGIC TICK NẶNG: Không tính toán đói, không tăng giảm thời gian hiệu ứng,
        // không chạy cơ chế cập nhật trạng thái di chuyển mạng gốc của Mojang.
        // Giúp giữ Bot đứng im và tiết kiệm tối đa CPU/TPS cho Server.

        this.baseTick(); // Chỉ giữ lại các logic gốc tối thiểu (như khử thực thể khi rơi xuống hư vô)
    }

    @Override
    public void doTick() {
        // Chặn hoàn toàn logic tick phụ thuộc của Player thông thường
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Trả về false để biến Bot thành trạng thái bất tử (Invulnerable)
        // Tránh việc Bot bị chết bởi quái vật hoặc người chơi khác gây lỗi biến mất thực thể ngầm
        return false;
    }

    @Override
    public boolean isSpectator() {
        // Ép buộc hệ thống coi Bot không phải Spectator để Client luôn vẽ mô hình 3D
        return false;
    }

    @Override
    public boolean isCreative() {
        // Giả lập trạng thái sáng tạo để chặn quái vật nhắm mục tiêu (Target) tấn công Bot
        return true;
    }
}
