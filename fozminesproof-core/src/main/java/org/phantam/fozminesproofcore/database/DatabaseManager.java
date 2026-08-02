package org.phantam.fozminesproofcore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;
import org.phantam.fozminesproofapi.model.FakePlayerData;
import org.phantam.fozminesproofapi.utils.DebugLogger;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * MySQL-based implementation of the fake player database using HikariCP connection pool.
 */
public class DatabaseManager implements IFakePlayerDatabase {

    private final DatabaseCredentialFactory.DatabaseCredentials credentials;
    private final String tableName;
    private HikariDataSource dataSource;

    public DatabaseManager(DatabaseCredentialFactory.DatabaseCredentials credentials, String tableName) {
        this.credentials = credentials;
        this.tableName = tableName;
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: initialized with table '%s'", tableName);
    }

    @Override
    public void setup() {
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: setting up MySQL connection...");
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database());
            config.setUsername(credentials.user());
            config.setPassword(credentials.password());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setConnectionTimeout(5000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            this.dataSource = new HikariDataSource(config);
            createTable();

            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: MySQL connection pool initialized successfully.");
            Bukkit.getLogger().log(Level.INFO,
                    "[DatabaseManager] MySQL connection pool initialized successfully.");

        } catch (Exception e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: failed to initialize HikariCP: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[DatabaseManager] Failed to initialize HikariCP: " + e.getMessage(), e);
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "name VARCHAR(16) NOT NULL PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "world VARCHAR(64) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, " +
                "pitch FLOAT NOT NULL, " +
                "is_active BOOLEAN DEFAULT FALSE, " +
                "INDEX idx_active (is_active));";

        DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: creating table with SQL: %s", sql);

        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: table '%s' created/verified", tableName);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: failed to create table: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[DatabaseManager] Failed to create table '" + tableName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: closing MySQL connection pool...");
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Bukkit.getLogger().log(Level.INFO,
                    "[DatabaseManager] MySQL connection pool closed.");
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: MySQL connection pool closed.");
        }
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: saving bot '%s'", data.getName());

        String query = "INSERT INTO " + tableName +
                " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                "yaw=VALUES(yaw), pitch=VALUES(pitch), is_active=VALUES(is_active);";

        Runnable task = () -> {
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(query)) {

                ps.setString(1, data.getName());
                ps.setString(2, data.getUuid().toString());
                ps.setString(3, data.getWorldName());
                ps.setDouble(4, data.getX());
                ps.setDouble(5, data.getY());
                ps.setDouble(6, data.getZ());
                ps.setFloat(7, data.getYaw());
                ps.setFloat(8, data.getPitch());
                ps.setBoolean(9, data.isActive());

                int rows = ps.executeUpdate();
                DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: saved '%s', rows affected: %d",
                        data.getName(), rows);

            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error saving '%s': %s",
                        data.getName(), e.getMessage());
                Bukkit.getLogger().log(Level.SEVERE,
                        "[DatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
            }
        };

        executeTask(task);
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: loading bot '%s'", name);

        String query = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FakePlayerData data = mapResultSet(rs);
                    DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: loaded '%s' (active=%s)",
                            name, data.isActive());
                    return Optional.of(data);
                }
            }
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error loading '%s': %s", name, e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[DatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: loading all players from table '%s'", tableName);

        List<FakePlayerData> list = new ArrayList<>();
        String query = "SELECT * FROM " + tableName + ";";
        try (Connection con = getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: loaded %d players", list.size());

        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error loading all players: %s", e.getMessage());
            Bukkit.getLogger().log(Level.WARNING,
                    "[DatabaseManager] Error loading all bots: " + e.getMessage(), e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public void deleteFakePlayer(String name) {
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: deleting bot '%s'", name);

        String query = "DELETE FROM " + tableName + " WHERE name = ?;";
        Runnable task = () -> {
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, name);
                int rows = ps.executeUpdate();
                DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: deleted '%s', rows affected: %d",
                        name, rows);
            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error deleting '%s': %s",
                        name, e.getMessage());
                Bukkit.getLogger().log(Level.WARNING,
                        "[DatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
            }
        };
        executeTask(task);
    }

    @Override
    public int getActiveBotCount() {
        int count = countBots(true);
        DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: active bot count = %d", count);
        return count;
    }

    @Override
    public int getInactiveBotCount() {
        int count = countBots(false);
        DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: inactive bot count = %d", count);
        return count;
    }

    @Override
    public void sendProxySyncData(String bungeeName, String name, int activeCount, int inactiveCount) {
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + bungeeName + " (" +
                "name VARCHAR(64) NOT NULL PRIMARY KEY, " +
                "active_bot INT DEFAULT 0, " +
                "deactive_bot INT DEFAULT 0)";
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute(createTableSql);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[DatabaseManager] Failed to create proxy sync table: " + e.getMessage(), e);
            return;
        }

        String sql = "INSERT INTO " + bungeeName +
                " (name, active_bot, deactive_bot) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE active_bot = VALUES(active_bot), deactive_bot = VALUES(deactive_bot);";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, activeCount);
            ps.setInt(3, inactiveCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[DatabaseManager] Error syncing proxy data: " + e.getMessage(), e);
        }
    }

    // ---- Helper methods ----

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: getConnection() called but dataSource is null");
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    private FakePlayerData mapResultSet(ResultSet rs) throws SQLException {
        return new FakePlayerData.Builder()
                .name(rs.getString("name"))
                .uuid(UUID.fromString(rs.getString("uuid")))
                .world(rs.getString("world"))
                .location(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"))
                .active(rs.getBoolean("is_active"))
                .build();
    }

    /**
     * Executes a database task either synchronously or asynchronously based on plugin state.
     * If the plugin is disabled (shutdown), runs synchronously to avoid race conditions.
     *
     * @param task the runnable task
     */
    private void executeTask(Runnable task) {
        if (Bukkit.getPluginManager().getPlugin("fozminesproof-core") != null &&
                Bukkit.getPluginManager().getPlugin("fozminesproof-core").isEnabled()) {
            CompletableFuture.runAsync(task);
        } else {
            DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: plugin disabled, running task synchronously");
            task.run(); // sync during shutdown
        }
    }

    /**
     * Counts bots by active status.
     *
     * @param active true for active, false for inactive
     * @return count
     */
    private int countBots(boolean active) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error counting %s bots: %s",
                    active ? "active" : "inactive", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE,
                    "[DatabaseManager] Error counting " + (active ? "active" : "inactive") + " bots: " + e.getMessage(), e);
        }
        return 0;
    }
}