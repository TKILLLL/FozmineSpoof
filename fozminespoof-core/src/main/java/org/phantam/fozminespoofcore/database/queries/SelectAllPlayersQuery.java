package org.phantam.fozminespoofcore.database.queries;

import org.phantam.fozminespoofapi.model.FakePlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Selects all fake players from the database.
 */
public class SelectAllPlayersQuery implements Query<Collection<FakePlayerData>> {

    private final String tableName;

    public SelectAllPlayersQuery(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public Collection<FakePlayerData> execute(Connection connection) throws SQLException {
        String sql = "SELECT * FROM " + tableName + ";";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                Collection<FakePlayerData> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
                return Collections.unmodifiableCollection(list);
            }
        }
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