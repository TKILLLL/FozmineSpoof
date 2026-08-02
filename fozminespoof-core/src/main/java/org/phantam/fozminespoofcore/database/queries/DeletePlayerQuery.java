package org.phantam.fozminespoofcore.database.queries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Deletes a fake player by name.
 */
public class DeletePlayerQuery implements Query<Void> {

    private final String tableName;
    private final String name;

    public DeletePlayerQuery(String tableName, String name) {
        this.tableName = tableName;
        this.name = name;
    }

    @Override
    public Void execute(Connection connection) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        return null;
    }
}