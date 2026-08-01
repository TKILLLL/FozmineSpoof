package org.phantam.fozminesproofcore.database;

import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofcore.utils.DebugLogger;

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
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: initialized with table '%s', path='%s'",
                tableName, dbPath);
    }

    @Override
    public void setup() {
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: setting up SQLite connection...");
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTable();

            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: SQLite connection established.");
            Bukkit.getLogger().log(Level.INFO,
                    "[SQLiteDatabaseManager] SQLite connection established.");
        } catch (Exception e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: failed to initialize: %s", e.getMessage());
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

        DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: creating table with SQL: %s", sql);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_active ON " + tableName + " (is_active);");
            DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: table '%s' created/verified", tableName);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: failed to create table: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Failed to create table: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: closing connection...");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Bukkit.getLogger().log(Level.INFO,
                        "[SQLiteDatabaseManager] SQLite connection closed.");
                DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: connection closed.");
            }
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error closing: %s", e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error closing connection: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: saving bot '%s'", data.getName());

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
            DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: saved '%s'", data.getName());

        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error saving '%s': %s",
                    data.getName(), e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: loading bot '%s'", name);

        String query = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FakePlayerData data = mapResultSet(rs);
                    DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: loaded '%s' (active=%s)",
                            name, data.isActive());
                    return Optional.of(data);
                }
            }
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error loading '%s': %s",
                    name, e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: loading all players from table '%s'", tableName);

        List<FakePlayerData> list = new ArrayList<>();
        String query = "SELECT * FROM " + tableName + ";";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: loaded %d players", list.size());

        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error loading all players: %s", e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error loading all bots: " + e.getMessage(), e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public void deleteFakePlayer(String name) {
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: deleting bot '%s'", name);

        String query = "DELETE FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, name);
            int rows = ps.executeUpdate();
            DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: deleted '%s', rows affected: %d",
                    name, rows);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error deleting '%s': %s",
                    name, e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[SQLiteDatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public int getActiveBotCount() {
        int count = countBots(true);
        DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: active bot count = %d", count);
        return count;
    }

    @Override
    public int getInactiveBotCount() {
        int count = countBots(false);
        DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: inactive bot count = %d", count);
        return count;
    }

    @Override
    public void sendProxySyncData(String bungeeName, String name, int activeCount, int inactiveCount) {
        DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: sync proxy data: bungee=%s, name=%s, active=%d, inactive=%d",
                bungeeName, name, activeCount, inactiveCount);

        String sql = "INSERT OR REPLACE INTO " + bungeeName +
                " (name, active_bot, deactive_bot) VALUES (?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, activeCount);
            ps.setInt(3, inactiveCount);
            ps.executeUpdate();
            DebugLogger.logFine(Bukkit.getLogger(), "SQLiteDatabaseManager: proxy sync data sent");
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error syncing proxy data: %s", e.getMessage());
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
            DebugLogger.log(Bukkit.getLogger(), "SQLiteDatabaseManager: error counting %s bots: %s",
                    active ? "active" : "inactive", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[SQLiteDatabaseManager] Error counting " + (active ? "active" : "inactive") + " bots: " + e.getMessage(), e);
        }
        return 0;
    }
}