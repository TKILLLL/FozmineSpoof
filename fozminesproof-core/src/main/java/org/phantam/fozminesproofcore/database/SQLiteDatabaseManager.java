package org.phantam.fozminesproofcore.database;

import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofapi.model.FakePlayerData;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * SQLite-based implementation of the fake player database.
 * Lightweight, file-based storage suitable for single-server setups.
 */
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
            createTable();
            Bukkit.getLogger().log(Level.INFO,
                    "[SQLiteDatabaseManager] SQLite connection established.");
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Failed to initialize SQLite: " + e.getMessage(), e);
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_active ON " + tableName + " (is_active);");
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Failed to create table: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Bukkit.getLogger().log(Level.INFO,
                        "[SQLiteDatabaseManager] SQLite connection closed.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error closing connection: " + e.getMessage(), e);
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
            ps.setString(3, data.getWorldName());
            ps.setDouble(4, data.getX());
            ps.setDouble(5, data.getY());
            ps.setDouble(6, data.getZ());
            ps.setDouble(7, data.getYaw());
            ps.setDouble(8, data.getPitch());
            ps.setInt(9, data.isActive() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
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
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
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
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error loading all bots: " + e.getMessage(), e);
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
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public int getActiveBotCount() {
        return countBots(true);
    }

    @Override
    public int getInactiveBotCount() {
        return countBots(false);
    }

    @Override
    public void sendProxySyncData(String bungeeName, String name, int activeCount, int inactiveCount) {
        String sql = "INSERT OR REPLACE INTO " + bungeeName +
                " (name, active_bot, deactive_bot) VALUES (?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, activeCount);
            ps.setInt(3, inactiveCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Error syncing proxy data: " + e.getMessage(), e);
        }
    }

    private FakePlayerData mapResultSet(ResultSet rs) throws SQLException {
        return new FakePlayerData.Builder()
                .name(rs.getString("name"))
                .uuid(UUID.fromString(rs.getString("uuid")))
                .world(rs.getString("world"))
                .location(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"))
                .active(rs.getInt("is_active") == 1)
                .build();
    }

    private int countBots(boolean active) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Error counting " + (active ? "active" : "inactive") + " bots: " + e.getMessage(), e);
        }
        return 0;
    }
}