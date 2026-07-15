package org.phantam.fozminesproofcore.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofcore.FozmineSproofCore;

public class KeepAliveTask extends BukkitRunnable {

    private final FozmineSproofCore plugin;

    public KeepAliveTask(FozmineSproofCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Kiểm tra an toàn: Nếu plugin bị tắt hoặc Bridge bị dọn dẹp đột ngột, tự hủy nhiệm vụ ngầm
        if (!plugin.isEnabled() || plugin.getBridge() == null) {
            this.cancel();
            return;
        }

        try {
            // Đẩy gói tin làm mới hiển thị Bot và Tablist xuống Pipeline mạng Netty ảo
            plugin.getBridge().sendKeepAlivePackets();
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Lỗi xảy ra khi gửi KeepAlive Packets cho Fake Players: " + e.getMessage());
        }
    }
}
