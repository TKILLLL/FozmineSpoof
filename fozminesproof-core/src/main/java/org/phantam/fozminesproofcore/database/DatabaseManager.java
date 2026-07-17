package org.phantam.fozminesproofcore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager implements IFakePlayerDatabase {

    private final DatabaseCredentialFactory.DatabaseCredentials credentials;
    private final String tableName;
    private HikariDataSource dataSource;

    public DatabaseManager(DatabaseCredentialFactory.DatabaseCredentials credentials, String tableName) {
        this.credentials = credentials;
        this.tableName = tableName;
    }

    @Override
    public void setup() {
        try {
            HikariConfig config = new HikariConfig();
            // Cấu hình JDBC Driver và chuỗi kết nối tối ưu cho MySQL/MariaDB
            config.setJdbcUrl("jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database());
            config.setUsername(credentials.user());
            config.setPassword(credentials.password());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(600000); // 10 phút
            config.setMaxLifetime(1800000); // 30 phút
            config.setConnectionTimeout(5000); // Thử lại sau 5 giây nếu timeout

            // Các thuộc tính phụ trợ giúp tăng tốc độ truy vấn MySQL độc quyền của HikariCP
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            this.dataSource = new HikariDataSource(config);
            this.createTable();

        } catch (Exception e) {
            Bukkit.getLogger().severe("❌ Không thể khởi tạo kết nối HikariCP tới Cơ sở dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Dựng cấu trúc bảng tĩnh độc lập khi khởi động hệ thống
     */
    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "name VARCHAR(16) NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "world VARCHAR(64) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, " +
                "pitch FLOAT NOT NULL, " +
                "is_active BOOLEAN DEFAULT FALSE, " +
                "PRIMARY KEY (name), " +
                "INDEX idx_active (is_active));";

        try (Connection con = dataSource.getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khởi tạo bảng cơ sở dữ liệu '" + tableName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Lưu dữ liệu Bot.
     * Tối ưu thông minh: Tự động chạy Async khi server đang vận hành để bảo vệ TPS,
     * và tự động chuyển sang Đồng bộ (Sync) khi tắt plugin để tránh lỗi sập kết nối Hikari.
     */
    @Override
    public void saveFakePlayer(FakePlayerData data) {
        String query = "INSERT INTO " + tableName + " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "world=?, x=?, y=?, z=?, yaw=?, pitch=?, is_active=?;";

        // Đóng gói toàn bộ logic truy vấn SQL vào một khối thực thi độc lập (Runnable)
        Runnable saveTask = () -> {
            try (Connection con = this.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, data.getName());
                ps.setString(2, data.getUuid().toString());
                ps.setString(3, data.getWorld());
                ps.setDouble(4, data.getX());
                ps.setDouble(5, data.getY());
                ps.setDouble(6, data.getZ());
                ps.setFloat(7, data.getYaw());
                ps.setFloat(8, data.getPitch());
                ps.setBoolean(9, data.isActive());

                ps.setString(10, data.getWorld());
                ps.setDouble(11, data.getX());
                ps.setDouble(12, data.getY());
                ps.setDouble(13, data.getZ());
                ps.setFloat(14, data.getYaw());
                ps.setFloat(15, data.getPitch());
                ps.setBoolean(16, data.isActive());

                ps.executeUpdate();
            } catch (SQLException e) {
                // Đổi sang log nghiêm trọng nếu hệ thống đang tắt để dễ quản lý
                Bukkit.getLogger().severe("⚠ Lỗi ghi dữ liệu FakePlayer '" + data.getName() + "': " + e.getMessage());
            }
        };

        // KIỂM TRA TRẠNG THÁI: Lấy instance của plugin để check xem có đang bị tắt (onDisable) hay không
        org.bukkit.plugin.Plugin currentPlugin = Bukkit.getPluginManager().getPlugin("fozminesproof-core");

        if (currentPlugin == null || !currentPlugin.isEnabled()) {
            // NẾU PLUGIN ĐANG TẮT/RELOAD: Ép chạy đồng bộ lập tức trên luồng chính của Bukkit.
            // Điều này ép Server phải đợi lưu xong dữ liệu của bot này rồi mới chạy xuống lệnh database.close()
            saveTask.run();
        } else {
            // NẾU PLUGIN ĐANG CHẠY BÌNH THƯỜNG: Tiếp tục đẩy vào hàng đợi Async để không gây gián đoạn TPS
            CompletableFuture.runAsync(saveTask);
        }
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        String query = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (Connection con = this.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("⚠ Lỗi khi truy vấn thông tin bot '" + name + "': " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        List<FakePlayerData> list = new ArrayList<>();
        String query = "SELECT * FROM " + tableName + ";";

        try (Connection con = this.getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("⚠ Lỗi khi tải toàn bộ danh sách FakePlayer từ SQL: " + e.getMessage());
        }
        // Trả về danh sách Read-Only an toàn không bị chỉnh sửa cấu trúc ở bên ngoài
        return Collections.unmodifiableList(list);
    }

    @Override
    public void deleteFakePlayer(String name) {
        String query = "DELETE FROM " + tableName + " WHERE name = ?;";
        Runnable deleteTask = () -> {
            try (Connection con = this.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                Bukkit.getLogger().warning("⚠ Lỗi khi xóa dữ liệu bot '" + name + "': " + e.getMessage());
            }
        };

        org.bukkit.plugin.Plugin currentPlugin = Bukkit.getPluginManager().getPlugin("fozminesproof-core");
        if (currentPlugin == null || !currentPlugin.isEnabled()) {
            deleteTask.run(); // Chạy đồng bộ khi tắt plugin
        } else {
            CompletableFuture.runAsync(deleteTask); // Chạy async khi vận hành
        }
    }

    /**
     * Hàm phụ trợ lấy Connection nhanh kèm cơ chế kiểm tra lỗi Null an toàn
     */
    private Connection getConnection() throws SQLException {
        if (this.dataSource == null) {
            throw new SQLException("HikariDataSource chưa được thiết lập hoặc bị null!");
        }
        return this.dataSource.getConnection();
    }

    /**
     * Kỹ thuật Data Mapper bóc tách ResultSet đổ vào Java POJO Object
     */
    private FakePlayerData mapResultSet(ResultSet rs) throws SQLException {
        return new FakePlayerData(
                rs.getString("name"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getBoolean("is_active")
        );
    }

    /**
     * Đếm tổng số lượng bot đang hoạt động (is_active = true)
     * @return Số lượng bot đang active (int)
     */
    @Override
    public int getActiveBotCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = TRUE;";

        try (Connection con = dataSource.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đếm số lượng Active Bot từ bảng '" + tableName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Đếm tổng số lượng bot đang dừng hoạt động (is_active = false)
     * @return Số lượng bot đang deactive (int)
     */
    @Override
    public int getDeactiveBotCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = FALSE;";

        try (Connection con = dataSource.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đếm số lượng Deactive Bot từ bảng '" + tableName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Gửi và đồng bộ dữ liệu số lượng bot của Proxy lên cơ sở dữ liệu
     *
     * @param bungee_name Tên bảng lưu trữ thông tin proxy
     * @param name        Tên của Proxy/Bungee cần đồng bộ
     * @param activeBot   Số lượng bot đang hoạt động
     * @param deactiveBot Số lượng bot đang dừng hoạt động
     */
    @Override
    public void sendProxySyncData(String bungee_name, String name, int activeBot, int deactiveBot) {
        // Sử dụng câu lệnh UPSERT (Insert hoặc Update nếu đã tồn tại khóa chính 'name')
        String sql = "INSERT INTO " + bungee_name + " (name, active_bot, deactive_bot) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE active_bot = ?, deactive_bot = ?;";

        try (Connection con = dataSource.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Các tham số cho phần INSERT
            pstmt.setString(1, name);
            pstmt.setInt(2, activeBot);
            pstmt.setInt(3, deactiveBot);

            // Các tham số cho phần UPDATE (nếu trùng khóa chính 'name')
            pstmt.setInt(4, activeBot);
            pstmt.setInt(5, deactiveBot);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đồng bộ dữ liệu Proxy lên bảng '" + bungee_name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

}
