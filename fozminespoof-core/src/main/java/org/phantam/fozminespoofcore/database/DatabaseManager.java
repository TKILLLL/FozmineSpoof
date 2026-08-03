package org.phantam.fozminespoofcore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofapi.utils.DebugLogger;
import org.phantam.fozminespoofcore.database.queries.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
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
            Bukkit.getLogger().log(Level.INFO, "[DatabaseManager] MySQL connection pool initialized successfully.");
        } catch (Exception e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: failed to initialize HikariCP: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Failed to initialize HikariCP: " + e.getMessage(), e);
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
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            DebugLogger.logFine(Bukkit.getLogger(), "DatabaseManager: table '%s' created/verified", tableName);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: failed to create table: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Failed to create table '" + tableName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: closing MySQL connection pool...");
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Bukkit.getLogger().log(Level.INFO, "[DatabaseManager] MySQL connection pool closed.");
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: MySQL connection pool closed.");
        }
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        executeTask(() -> {
            try (Connection con = getConnection()) {
                new InsertPlayerQuery(tableName, data).execute(con);
            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error saving '%s': %s", data.getName(), e.getMessage());
                Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void saveFakePlayers(Collection<FakePlayerData> players) {
        if (players == null || players.isEmpty()) return;
        DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: batch saving " + players.size() + " players...");

        executeTask(() -> {
            try (Connection con = getConnection()) {
                new InsertPlayersBatchQuery(tableName, players).execute(con);
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: batch save completed.");
            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: batch save error: %s", e.getMessage());
                // fallback: save one by one
                for (FakePlayerData data : players) {
                    saveFakePlayer(data);
                }
            }
        });
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        try (Connection con = getConnection()) {
            return new SelectPlayerQuery(tableName, name).execute(con);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error loading '%s': %s", name, e.getMessage());
            Bukkit.getLogger().log(Level.WARNING, "[DatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        try (Connection con = getConnection()) {
            return new SelectAllPlayersQuery(tableName).execute(con);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error loading all players: %s", e.getMessage());
            Bukkit.getLogger().log(Level.WARNING, "[DatabaseManager] Error loading all bots: " + e.getMessage(), e);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public Collection<FakePlayerData> loadInactivePlayers() {
        try (Connection con = getConnection()) {
            return new SelectInactivePlayersQuery(tableName).execute(con);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error loading inactive players: %s", e.getMessage());
            Bukkit.getLogger().log(Level.WARNING, "[DatabaseManager] Error loading inactive bots: " + e.getMessage(), e);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public void deleteFakePlayer(String name) {
        executeTask(() -> {
            try (Connection con = getConnection()) {
                new DeletePlayerQuery(tableName, name).execute(con);
            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error deleting '%s': %s", name, e.getMessage());
                Bukkit.getLogger().log(Level.WARNING, "[DatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
            }
        });
    }

    @Override
    public int getActiveBotCount() {
        try (Connection con = getConnection()) {
            return new CountActiveBotsQuery(tableName).execute(con);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error counting active bots: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Error counting active bots: " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public int getInactiveBotCount() {
        try (Connection con = getConnection()) {
            return new CountInactiveBotsQuery(tableName).execute(con);
        } catch (SQLException e) {
            DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error counting inactive bots: %s", e.getMessage());
            Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Error counting inactive bots: " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Periodically synchronises fake player statistics with the proxy database.
     * <p>
     * This task runs asynchronously and updates the proxy sync table with the
     * current active and inactive bot counts for this server node. The interval
     * is configurable via {@code Database.bridging-setting.update-interval}.
     * </p>
     *
     * @author Phantam
     * @version 2.0.0
     */
    @Override
    public void sendProxySyncData(String bungeeName, String serverNodeName, int activeCount, int inactiveCount) {
        executeTask(() -> {
            try (Connection con = getConnection()) {
                new ProxySyncQuery("proxy_sync_" + bungeeName, serverNodeName, activeCount, inactiveCount).execute(con);
            } catch (SQLException e) {
                DebugLogger.log(Bukkit.getLogger(), "DatabaseManager: error syncing proxy data: %s", e.getMessage());
                Bukkit.getLogger().log(Level.SEVERE, "[DatabaseManager] Error syncing proxy data: " + e.getMessage(), e);
            }
        });
    }

    // ---- Private helpers ----

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes a database task either synchronously or asynchronously based on plugin state.
     */
    private void executeTask(Runnable task) {
        if (Bukkit.getPluginManager().getPlugin("fozminespoof-core") != null &&
                Bukkit.getPluginManager().getPlugin("fozminespoof-core").isEnabled()) {
            CompletableFuture.runAsync(task);
        } else {
            task.run(); // sync during shutdown
        }
    }
}