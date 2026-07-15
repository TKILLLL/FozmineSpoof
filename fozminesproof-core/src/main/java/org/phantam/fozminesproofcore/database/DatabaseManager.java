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
     * Lưu dữ liệu Bot. Tối ưu: Đóng gói chạy bất đồng bộ Async bảo vệ TPS của server hoàn hảo
     */
    @Override
    public void saveFakePlayer(FakePlayerData data) {
        String query = "INSERT INTO " + tableName + " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "world=?, x=?, y=?, z=?, yaw=?, pitch=?, is_active=?;";

        CompletableFuture.runAsync(() -> {
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
                Bukkit.getLogger().warning("⚠ Lỗi Async khi lưu dữ liệu FakePlayer '" + data.getName() + "': " + e.getMessage());
            }
        });
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

    /**
     * Xóa Bot. Tối ưu: Đóng gói chạy Async ngầm cô lập
     */
    @Override
    public void deleteFakePlayer(String name) {
        String query = "DELETE FROM " + tableName + " WHERE name = ?;";

        CompletableFuture.runAsync(() -> {
            try (Connection con = this.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                Bukkit.getLogger().warning("⚠ Lỗi Async khi xóa dữ liệu bot '" + name + "': " + e.getMessage());
            }
        });
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
}
