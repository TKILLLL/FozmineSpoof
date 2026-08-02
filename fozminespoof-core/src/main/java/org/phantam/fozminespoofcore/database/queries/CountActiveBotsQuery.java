package org.phantam.fozminespoofcore.database.queries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Counts active bots (is_active = true).
 */
public class CountActiveBotsQuery implements Query<Integer> {

    private final String tableName;

    public CountActiveBotsQuery(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public Integer execute(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE is_active = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, true);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}