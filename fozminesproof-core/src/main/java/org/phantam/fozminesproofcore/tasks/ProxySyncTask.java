package org.phantam.fozminesproofcore.tasks;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofcore.config.ConfigManager;
import org.phantam.fozminesproofcore.database.DatabaseManager;

public class ProxySyncTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final IFakePlayerDatabase iFakePlayerDatabase;
    private final ConfigManager configManager;

    public ProxySyncTask(JavaPlugin plugin, IFakePlayerDatabase iFakePlayerDatabase, ConfigManager configManager) {
        this.plugin = plugin;
        this.iFakePlayerDatabase = iFakePlayerDatabase;
        this.configManager = configManager;
    }

    @Override
    public void run() {
        try {
            int activeBots = iFakePlayerDatabase.getActiveBotCount();
            int deactiveBots = iFakePlayerDatabase.getDeactiveBotCount();

            // Đẩy data xuống database bằng Thread Async này
            iFakePlayerDatabase.sendProxySyncData(
                    configManager.getBungeeName(),
                    configManager.getRawDatabaseName(),
                    activeBots,
                    deactiveBots
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi khi đồng bộ dữ liệu Proxy: " + e.getMessage());
        } finally {
            // Kiểm tra nếu plugin vẫn đang hoạt động thì mới lập lịch vòng lặp tiếp theo
            if (plugin.isEnabled()) {
                // Lấy khoảng thời gian delay ngẫu nhiên mới (được tính bằng giây)
                // Giả sử hàm getProxyUpdateInterval() nằm trong ConfigManager hoặc class Main
                int nextDelaySeconds = configManager.getProxyUpdateInterval();
                long nextDelayTicks = nextDelaySeconds * 20L;

                // Tự chạy lại chính nó bất đồng bộ sau khoảng delay ngẫu nhiên mới
                new ProxySyncTask(plugin, iFakePlayerDatabase, configManager)
                        .runTaskLaterAsynchronously(plugin, nextDelayTicks);
            }
        }
    }
}