package org.phantam.fozminesproofCore;

import org.bukkit.plugin.java.JavaPlugin;
import org.phantam.fozminesproofApi.FozminesproofApi;
import org.phantam.fozminesproofCore.commands.CommandManager;

import java.lang.reflect.InvocationTargetException;

public class FozmineSproofCore extends JavaPlugin {

    private FozminesproofApi bridge;

    @Override
    public void onEnable() {
        if (!setupBridge()) {
            this.getLogger().severe("Không tìm thấy Module NMS tương thích với phiên bản Server hiện tại!");
            this.getLogger().severe("Plugin sẽ tự động tắt để tránh gây lỗi dữ liệu.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.getLogger().info("FozmineSproof đã kích hoạt Bridge NMS thành công!");

        if (this.getCommand("sproof") != null) {
            this.getCommand("sproof").setExecutor(new CommandManager(this));
        }
    }

    private boolean setupBridge() {
        // Lấy phiên bản Minecraft thô của server (Ví dụ: "1.20.2", "1.20.4", "1.19.4")
        String rawVersion = this.getServer().getMinecraftVersion();
        String targetVersionKey;

        // CƠ CHẾ GOM CỤM PHIÊN BẢN: Giúp tương thích ngược trong cùng một nhánh lớn
        if (rawVersion.startsWith("1.19")) {
            // Định hướng toàn bộ nhánh 1.19.x (nếu có) về module xử lý ổn định nhất là 1.19.4
            targetVersionKey = "1_19_4";
        } else if (rawVersion.startsWith("1.20")) {
            // Định hướng toàn bộ nhánh 1.20.x (1.20.1, 1.20.4...) về module chung 1.20.2 của bạn
            targetVersionKey = "1_20_2";
        } else {
            targetVersionKey = rawVersion.replace('.', '_');
        }

        String className = "org.phantam.fozminesproofV" + targetVersionKey + ".NMSBridge_v" + targetVersionKey;

        try {
            Class<?> clazz = Class.forName(className);
            this.bridge = (FozminesproofApi) clazz.getConstructor().newInstance();
            return this.bridge != null;

        } catch (ClassNotFoundException e) {
            this.getLogger().warning("Không tìm thấy lớp xử lý hệ thống cho phiên bản thực tế: " + rawVersion);
            this.getLogger().warning("Đường dẫn tìm kiếm thất bại: " + className);
            return false;
        } catch (NoSuchMethodException e) {
            this.getLogger().severe("Không tìm thấy hàm khởi tạo trống (No-args constructor) trong lớp: " + className);
            return false;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            this.getLogger().severe("Lỗi nghiêm trọng khi khởi tạo module NMS Bridge qua kỹ thuật Reflection!");
            e.printStackTrace();
            return false;
        }
    }

    public FozminesproofApi getBridge() { // Đổi kiểu trả về của hàm Getter
        return this.bridge;
    }
}
