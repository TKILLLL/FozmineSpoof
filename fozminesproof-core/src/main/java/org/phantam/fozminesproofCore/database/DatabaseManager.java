package org.phantam.fozminesproofCore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.phantam.fozminesproofApi.database.FakePlayerData;
import org.phantam.fozminesproofApi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofCore.config.ConfigManager;

import java.sql.*;
import java.util.*;

public class DatabaseManager implements IFakePlayerDatabase {

    private HikariDataSource dataSource;
    private final ConfigManager.DatabaseCredentials credentials;
    private final String tableName;

    public DatabaseManager(ConfigManager.DatabaseCredentials credentials, String tableName) {
        this.credentials = credentials;
        this.tableName = tableName;
    }

    @Override
    public void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database());
        config.setUsername(credentials.user());
        config.setPassword(credentials.password());
        config.setMaximumPoolSize(10);

        this.dataSource = new HikariDataSource(config);

        // Tạo bảng riêng biệt dựa theo tên cấu hình Database.name
        try (Connection con = dataSource.getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "name VARCHAR(16) NOT NULL, uuid VARCHAR(36) NOT NULL, world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                    "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, is_active BOOLEAN DEFAULT FALSE, " +
                    "PRIMARY KEY (name), INDEX idx_active (is_active));");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        String query = "INSERT INTO " + tableName + " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "world=?, x=?, y=?, z=?, yaw=?, pitch=?, is_active=?;";

        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
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
            e.printStackTrace();
        }
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        String query = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        List<FakePlayerData> list = new ArrayList<>();
        String query = "SELECT * FROM " + tableName + ";";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void deleteFakePlayer(String name) {
        String query = "DELETE FROM " + tableName + " WHERE name = ?;";
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getBoolean("is_active")
        );
    }
}
