package org.phantam.fozminespoofcore.database.queries;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Selects a single fake player by name.
 */
public class SelectPlayerQuery implements Query<Optional<FakePlayerData>> {

    private final String tableName;
    private final String name;

    public SelectPlayerQuery(String tableName, String name) {
        this.tableName = tableName;
        this.name = name;
    }

    @Override
    public Optional<FakePlayerData> execute(Connection connection) throws SQLException {
        String sql = "SELECT * FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
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
}