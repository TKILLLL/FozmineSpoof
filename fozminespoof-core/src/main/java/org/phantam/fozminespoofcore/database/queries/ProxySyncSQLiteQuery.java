package org.phantam.fozminespoofcore.database.queries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Inserts or updates proxy sync data for SQLite databases.
 * Uses escaped backtick identifiers to support hyphenated server node cluster names.
 */
public class ProxySyncSQLiteQuery implements Query<Void> {

    private final String tableName;
    private final String serverNodeName;
    private final int activeCount;
    private final int inactiveCount;

    public ProxySyncSQLiteQuery(String tableName, String serverNodeName, int activeCount, int inactiveCount) {
        this.tableName = tableName;
        this.serverNodeName = serverNodeName;
        this.activeCount = activeCount;
        this.inactiveCount = inactiveCount;
    }

    @Override
    public Void execute(Connection connection) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS `" + tableName + "` " +
                "(`name` TEXT NOT NULL PRIMARY KEY, `active_bot` INTEGER DEFAULT 0, `deactive_bot` INTEGER DEFAULT 0);";
        try (PreparedStatement ps = connection.prepareStatement(createSql)) {
            ps.execute();
        }

        String sql = "INSERT OR REPLACE INTO `" + tableName + "` (`name`, `active_bot`, `deactive_bot`) VALUES (?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverNodeName);
            ps.setInt(2, activeCount);
            ps.setInt(3, inactiveCount);
            ps.executeUpdate();
        }
        return null;
    }
}