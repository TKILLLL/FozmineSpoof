package org.phantam.fozminesproofv1_21_11.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FakeServerPlayer extends ServerPlayer {

    /**
     * Khởi tạo FakeServerPlayer và kích hoạt trạng thái bất tử hệ thống Paper 1.21.11
     */
    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);

        // VÁ LỖI CHÍ MẠNG 1.21.11: Gọi trực tiếp thuộc tính bất tử của Mojang trong constructor.
        // Giải pháp này biến Bot thành thực thể miễn nhiễm hoàn toàn với mọi damage (Quái đánh, người chơi chém, Void, Lava)
        // mà không cần override bất kỳ hàm bị khóa 'final' nào của lớp cha.
        this.setInvulnerable(true);
    }

    @Override
    public void tick() {
        // CHẶN LOGIC TICK NẶNG: Thay vì gọi super.tick() kế thừa từ Avatar làm Bot tự động tính toán vật lý phức tạp,
        // di chuyển mạng và va chạm, ta chỉ gọi baseTick() để giữ lại các cơ chế tối thiểu của một thực thể (như rơi xuống hư vô Void).
        // Giúp giữ Bot đứng im hoàn toàn và tiết kiệm tối đa CPU/TPS cho hệ thống đa luồng.
        this.baseTick();
    }

    @Override
    public boolean isSpectator() {
        // Chặn trạng thái Spectator để Client người chơi thực tế luôn vẽ mô hình 3D hiển thị đầy đủ của Bot
        return false;
    }

    @Override
    public boolean isCreative() {
        // Giả lập trạng thái sáng tạo (Creative) để hệ thống AI của quái vật (MythicMobs, Vanilla Mobs)
        // tự động bỏ qua, không nhắm mục tiêu (Target) tấn công Bot gây giật lag hành vi thực thể
        return true;
    }
}
