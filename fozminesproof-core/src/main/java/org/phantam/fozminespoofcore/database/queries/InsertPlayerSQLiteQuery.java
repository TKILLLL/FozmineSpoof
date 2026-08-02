package org.phantam.fozminespoofcore.database.queries;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Inserts or replaces a single fake player record (SQLite version).
 */
public class InsertPlayerSQLiteQuery implements Query<Void> {

    private final String tableName;
    private final FakePlayerData data;

    public InsertPlayerSQLiteQuery(String tableName, FakePlayerData data) {
        this.tableName = tableName;
        this.data = data;
    }

    @Override
    public Void execute(Connection connection) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + tableName +
                " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        }
        return null;
    }
}