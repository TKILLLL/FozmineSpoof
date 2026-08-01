package org.phantam.fozminesproofcore.database;

import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.sql.*;
import java.util.*;

public class SQLiteDatabaseManager implements IFakePlayerDatabase {

    private final String dbPath;
    private final String tableName;
    private Connection connection;

    public SQLiteDatabaseManager(String dbPath, String tableName) {
        this.dbPath = dbPath;
        this.tableName = tableName;
    }

    @Override
    public void setup() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            this.createTable();
        } catch (Exception e) {
            Bukkit.getLogger().severe("❌ Lỗi khởi tạo kết nối SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "name TEXT NOT NULL PRIMARY KEY, " +
                "uuid TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "x REAL NOT NULL, " +
                "y REAL NOT NULL, " +
                "z REAL NOT NULL, " +
                "yaw REAL NOT NULL, " +
                "pitch REAL NOT NULL, " +
                "is_active INTEGER DEFAULT 0" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            // Tạo index riêng
            String indexSql = "CREATE INDEX IF NOT EXISTS idx_active ON " + tableName + " (is_active);";
            stmt.execute(indexSql);
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi tạo bảng SQLite '" + tableName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        String query = "INSERT OR REPLACE INTO " + tableName +
                " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, data.getName());
            ps.setString(2, data.getUuid().toString());
            ps.setString(3, data.getWorld());
            ps.setDouble(4, data.getX());
            ps.setDouble(5, data.getY());
            ps.setDouble(6, data.getZ());
            ps.setDouble(7, data.getYaw());
            ps.setDouble(8, data.getPitch());
            ps.setInt(9, data.isActive() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("⚠ Lỗi ghi dữ liệu FakePlayer '" + data.getName() + "': " + e.getMessage());
        }
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        String query = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
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
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("⚠ Lỗi khi tải toàn bộ danh sách FakePlayer từ SQLite: " + e.getMessage());
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public void deleteFakePlayer(String name) {
        String query = "DELETE FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().warning("⚠ Lỗi khi xóa dữ liệu bot '" + name + "': " + e.getMessage());
        }
    }

    @Override
    public int getActiveBotCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = 1;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đếm số lượng Active Bot: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getDeactiveBotCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = 0;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đếm số lượng Deactive Bot: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void sendProxySyncData(String bungee_name, String name, int activeBot, int deactiveBot) {
        // SQLite không hỗ trợ UPSERT trực tiếp, dùng INSERT OR REPLACE
        String sql = "INSERT OR REPLACE INTO " + bungee_name + " (name, active_bot, deactive_bot) " +
                "VALUES (?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, activeBot);
            ps.setInt(3, deactiveBot);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("❌ Lỗi khi đồng bộ dữ liệu Proxy: " + e.getMessage());
        }
    }

    private FakePlayerData mapResultSet(ResultSet rs) throws SQLException {
        return new FakePlayerData(
                rs.getString("name"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                (float) rs.getDouble("yaw"),
                (float) rs.getDouble("pitch"),
                rs.getInt("is_active") == 1
        );
    }
}