package org.phantam.fozminespoofcore.database;

import org.bukkit.Bukkit;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;
import org.phantam.fozminespoofapi.model.FakePlayerData;
import org.phantam.fozminespoofcore.database.queries.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class SQLiteDatabaseManager implements IFakePlayerDatabase {

    private final String dbPath;
    private final String tableName;
    private final ReentrantLock dbLock = new ReentrantLock();
    private Connection connection;

    public SQLiteDatabaseManager(String dbPath, String tableName) {
        this.dbPath = dbPath;
        this.tableName = tableName;
    }

    @Override
    public void setup() {
        dbLock.lock();
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath + "?busy_timeout=10000");

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
        } finally {
            dbLock.unlock();
        }
    }

    private void createTable() throws SQLException {
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
        }
    }

    @Override
    public void close() {
        dbLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Bukkit.getLogger().log(Level.INFO, "[SQLiteDatabaseManager] SQLite connection closed.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error closing connection: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
    }

    @Override
    public void saveFakePlayer(FakePlayerData data) {
        dbLock.lock();
        try {
            new InsertPlayerSQLiteQuery(tableName, data).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error saving bot '" + data.getName() + "': " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
    }

    @Override
    public void saveFakePlayers(Collection<FakePlayerData> players) {
        if (players == null || players.isEmpty()) return;
        dbLock.lock();
        try {
            new InsertPlayersBatchSQLiteQuery(tableName, players).execute(connection);
        } catch (SQLException e) {
            for (FakePlayerData data : players) {
                try {
                    new InsertPlayerSQLiteQuery(tableName, data).execute(connection);
                } catch (Exception ignored) {
                }
            }
        } finally {
            dbLock.unlock();
        }
    }

    @Override
    public Optional<FakePlayerData> loadFakePlayer(String name) {
        dbLock.lock();
        try {
            return new SelectPlayerQuery(tableName, name).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading bot '" + name + "': " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
        return Optional.empty();
    }

    @Override
    public Collection<FakePlayerData> loadAllPlayers() {
        dbLock.lock();
        try {
            return new SelectAllPlayersQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading all bots: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public Collection<FakePlayerData> loadInactivePlayers() {
        dbLock.lock();
        try {
            return new SelectInactivePlayersQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error loading inactive bots: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public void deleteFakePlayer(String name) {
        dbLock.lock();
        try {
            new DeletePlayerQuery(tableName, name).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.WARNING, "[SQLiteDatabaseManager] Error deleting bot '" + name + "': " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
    }

    @Override
    public int getActiveBotCount() {
        dbLock.lock();
        try {
            return new CountActiveBotsQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error counting active bots: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
        return 0;
    }

    @Override
    public int getInactiveBotCount() {
        dbLock.lock();
        try {
            return new CountInactiveBotsQuery(tableName).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error counting inactive bots: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
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
        dbLock.lock();
        try {
            new ProxySyncSQLiteQuery("proxy_sync_" + bungeeName, serverNodeName, activeCount, inactiveCount).execute(connection);
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[SQLiteDatabaseManager] Error syncing proxy data: " + e.getMessage(), e);
        } finally {
            dbLock.unlock();
        }
    }
}