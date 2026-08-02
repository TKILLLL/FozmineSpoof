package org.phantam.fozminespoofcore.database;

import org.bukkit.Bukkit;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.database.queries.*;

import java.sql.*;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Optimized SQLite-based implementation using WAL Mode and Memory Cache.
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
            // Set busy_timeout = 5000ms to avoid locked exceptions
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath + "?busy_timeout=5000");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
                stmt.execute("PRAGMA cache_size=-64000;");
            }

            createTable();
            Bukkit.getLogger().log(Level.INFO, "[SQLiteDatabaseManager] SQLite WAL mode connection established successfully.");
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Failed to initialize SQLite: " + e.getMessage(), e);
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
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Failed to create table: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Bukkit.getLogger().log(Level.INFO, "[SQLiteDatabaseManager] SQLite connection closed.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error closing connection: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveFakePlayer(FakePlayerData data) {
        try {
            new InsertPlayerSQLiteQuery(tableName, data).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveFakePlayers(Collection<FakePlayerData> players) {
        if (players == null || players.isEmpty()) return;
        try {
            new InsertPlayersBatchSQLiteQuery(tableName, players).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Batch save failed, falling back: " + e.getMessage(), e);
            for (FakePlayerData data : players) {
                saveFakePlayer(data);
            }
        }
    }

    @Override
    public synchronized Optional<FakePlayerData> loadFakePlayer(String name) {
        try {
            return new SelectPlayerQuery(tableName, name).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized Collection<FakePlayerData> loadAllPlayers() {
        try {
            return new SelectAllPlayersQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading all bots: " + e.getMessage(), e);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public synchronized Collection<FakePlayerData> loadInactivePlayers() {
        try {
            return new SelectInactivePlayersQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading inactive bots: " + e.getMessage(), e);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public synchronized void deleteFakePlayer(String name) {
        try {
            new DeletePlayerQuery(tableName, name).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized int getActiveBotCount() {
        try {
            return new CountActiveBotsQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error counting active bots: " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public synchronized int getInactiveBotCount() {
        try {
            return new CountInactiveBotsQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error counting inactive bots: " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public synchronized void sendProxySyncData(String bungeeName, String name, int activeCount, int inactiveCount) {
        try {
            new ProxySyncSQLiteQuery("proxy_sync_" + bungeeName, name, activeCount, inactiveCount).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error syncing proxy data: " + e.getMessage(), e);
        }
    }
}