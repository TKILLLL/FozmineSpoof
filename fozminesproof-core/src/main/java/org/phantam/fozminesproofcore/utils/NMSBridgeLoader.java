package org.phantam.fozminesproofcore.utils;

import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.FozminesproofApi;
import java.util.logging.Logger;

public final class NMSBridgeLoader {

    private NMSBridgeLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Tự động nhận diện phiên bản máy chủ và nạp Module NMS tương ứng qua Reflection
     * @param logger Bộ ghi log của plugin để hiển thị báo cáo lỗi
     * @return Thực thể FozminesproofApi hoặc null nếu thất bại
     */
    public static FozminesproofApi loadBridge(Logger logger) {
        String rawVersion = Bukkit.getServer().getMinecraftVersion();
        String targetVersionKey = resolveVersionKey(rawVersion);

        // BIỆN PHÁP BẢO VỆ AN TOÀN: Nếu phiên bản không được hỗ trợ chính thức, chặn khởi chạy ngay lập tức
        if (targetVersionKey == null) {
            logger.severe("❌ Máy chủ đang chạy phiên bản Minecraft không được hỗ trợ: " + rawVersion);
            return null;
        }

        String className = "org.phantam.fozminesproofv" + targetVersionKey + ".NMSBridge_v" + targetVersionKey;

        try {
            Class<?> clazz = Class.forName(className);
            return (FozminesproofApi) clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            logger.severe("❌ Không tìm thấy lớp xử lý hệ thống cho phiên bản: " + rawVersion);
            logger.severe("📍 Đường dẫn tìm kiếm thất bại: " + className);
        } catch (NoSuchMethodException e) {
            logger.severe("❌ Module NMS thiếu hàm khởi tạo rỗng (Constructor)! Phiên bản: " + rawVersion);
        } catch (Exception e) {
            logger.severe("🚨 Lỗi nghiêm trọng khi khởi tạo module NMS Bridge qua kỹ thuật Reflection!");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Phân tích chuỗi phiên bản thô của máy chủ để ánh xạ chính xác đến Module NMS tương thích.
     * Chống lỗi nạp chéo tiểu mục (sub-version) gây crash gói tin mạng (Netty Packet Codec).
     */
    private static String resolveVersionKey(String rawVersion) {
        switch (rawVersion) {
            case "1.19.4":
                return "1_19_4";
            case "1.20.2":
                return "1_20_2";
            case "1.20.4":
                return "1_20_4";
            case "1.21.4":
                return "1_21_4";
            case "1.21.11":
                return "1_21_11";
            default:
                return null;
        }
    }
}