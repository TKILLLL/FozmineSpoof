package org.phantam.fozminespoofcore.database.queries;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

/**
 * Batch inserts or replaces multiple fake player records (SQLite version).
 */
public class InsertPlayersBatchSQLiteQuery implements Query<Void> {

    private final String tableName;
    private final Collection<FakePlayerData> players;

    public InsertPlayersBatchSQLiteQuery(String tableName, Collection<FakePlayerData> players) {
        this.tableName = tableName;
        this.players = players;
    }

    @Override
    public Void execute(Connection connection) throws SQLException {
        if (players == null || players.isEmpty()) return null;

        String sql = "INSERT OR REPLACE INTO " + tableName +
                " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (FakePlayerData data : players) {
                ps.setString(1, data.getName());
                ps.setString(2, data.getUuid().toString());
                ps.setString(3, data.getWorldName());
                ps.setDouble(4, data.getX());
                ps.setDouble(5, data.getY());
                ps.setDouble(6, data.getZ());
                ps.setDouble(7, data.getYaw());
                ps.setDouble(8, data.getPitch());
                ps.setInt(9, data.isActive() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            connection.rollback();
            connection.setAutoCommit(true);
            throw e;
        }
        return null;
    }
}