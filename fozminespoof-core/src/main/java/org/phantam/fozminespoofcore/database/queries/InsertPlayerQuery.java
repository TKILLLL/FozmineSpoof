package org.phantam.fozminespoofcore.database.queries;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Inserts or updates a single fake player record.
 */
public class InsertPlayerQuery implements Query<Void> {

    private final String tableName;
    private final FakePlayerData data;

    public InsertPlayerQuery(String tableName, FakePlayerData data) {
        this.tableName = tableName;
        this.data = data;
    }

    @Override
    public Void execute(Connection connection) throws SQLException {
        String sql = "INSERT INTO " + tableName +
                " (name, uuid, world, x, y, z, yaw, pitch, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                "yaw=VALUES(yaw), pitch=VALUES(pitch), is_active=VALUES(is_active);";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getName());
            ps.setString(2, data.getUuid().toString());
            ps.setString(3, data.getWorldName());
            ps.setDouble(4, data.getX());
            ps.setDouble(5, data.getY());
            ps.setDouble(6, data.getZ());
            ps.setFloat(7, data.getYaw());
            ps.setFloat(8, data.getPitch());
            ps.setBoolean(9, data.isActive());
            ps.executeUpdate();
        }
        return null;
    }
}